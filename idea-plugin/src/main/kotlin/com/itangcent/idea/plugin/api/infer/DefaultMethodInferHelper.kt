package com.itangcent.idea.plugin.api.infer

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.*
import com.intellij.psi.util.*
import com.itangcent.common.kit.KVUtils
import com.itangcent.common.logger.Log
import com.itangcent.common.logger.traceError
import com.itangcent.common.utils.*
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.helper.IntelligentSettingsHelper
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.jvm.*
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.ClassRuleKeys
import com.itangcent.intellij.psi.ObjectHolder
import com.itangcent.intellij.jvm.psi.PsiClassUtil 
import com.itangcent.intellij.psi.getOrResolve
import com.itangcent.intellij.util.Magics
import com.siyeh.ig.psiutils.ClassUtils
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.*

/**
 * Handles inferring the return type of methods
 * It should be executed within the UI thread.
 */
@Singleton
class DefaultMethodInferHelper : MethodInferHelper {

    @Inject
    private lateinit var logger: Logger

    @Inject
    private val psiClassHelper: PsiClassHelper? = null

    @Inject
    private val ruleComputer: RuleComputer? = null

    @Inject
    private val duckTypeHelper: DuckTypeHelper? = null

    @Inject
    private val actionContext: ActionContext? = null

    @Inject
    private val jvmClassHelper: JvmClassHelper? = null

    @Inject
    private val psiResolver: PsiResolver? = null

    @Inject
    protected val settingBinder: SettingBinder? = null

    @Inject
    protected lateinit var intelligentSettingsHelper: IntelligentSettingsHelper

    //Cache for storing the results of static method invocations to avoid recomputation.
    private val staticMethodCache: HashMap<Pair<PsiMethod, Array<Any?>?>, Any?> = HashMap()

    //Tracks the current stack of method inferences to avoid infinite recursion.
    private val methodStack: Stack<Infer> = Stack()

    //JSON options for controlling the serialization behavior in the inference process.
    private var jsonOption: Int = JsonOption.ALL

    private var simpleJsonOption: Int = jsonOption and JsonOption.READ_GETTER.inv()

    //Limits the depth of object graph exploration to prevent excessively deep or infinite recursion.
    private var maxObjectDeep: Int = 4

    //A cache for methods that do not require arguments to infer their return types.
    private val emptyCallMethodCache: HashMap<PsiMethod, Any?> = HashMap()

    /**
     * Main methods for inferring the return type of a given PsiMethod.
     */
    override fun inferReturn(psiMethod: PsiMethod, option: Int): Any? {
        return emptyCallMethodCache.safeComputeIfAbsent(psiMethod) {
            return@safeComputeIfAbsent inferReturn(psiMethod, null, null, option)
        }.cleanInvalidKeys()
    }

    override fun inferReturn(psiMethod: PsiMethod, caller: Any?, args: Array<Any?>?, option: Int): Any? {
        return inferReturn(null, psiMethod, caller, args, option)
    }

    fun inferReturn(
        context: PsiElement?,
        psiMethod: PsiMethod,
        caller: Any? = null,
        args: Array<Any?>?,
        option: Int = DEFAULT_OPTION,
    ): Any? {
        actionContext!!.checkStatus()
        if (methodStack.size < intelligentSettingsHelper.inferMaxDeep()) {
            try {
                var inferRet: Any?
                inferRet = callSimpleMethod(context, psiMethod, caller, args)
                if (inferRet == CALL_FAILED) {
                    if (allowQuickCall(option)) {
                        val returnType = psiMethod.returnType
                        if (returnType != null && !PsiClassUtil.isInterface(returnType)
                            && duckTypeHelper!!.isQualified(returnType, psiMethod)
                        ) {
                            return psiClassHelper!!.getTypeObject(psiMethod.returnType, psiMethod, jsonOption)
                        }
                    }

                    inferRet = inferReturnUnsafely(psiMethod, caller, args, option)
                }

                if (!nullOrEmpty(inferRet)) {

                    val byType = psiClassHelper!!.getTypeObject(psiMethod.returnType, psiMethod, jsonOption)

                    return findComplexResult(valueOf(inferRet).resolveCycle(), byType)
                }
            } catch (e: Exception) {
                logger.traceError("infer error", e)
                //infer failed
            }
        }
        val returnType = psiMethod.returnType ?: return null
        if (returnType.presentableText == "void" || returnType.presentableText == "Void") {
            return null
        }
        return DirectVariable { psiClassHelper!!.getTypeObject(psiMethod.returnType, psiMethod, jsonOption) }
    }

    /**
     * Cleans up the inferred return values by removing invalid keys, based on predefined criteria.
     */
    private fun Any?.cleanInvalidKeys(): Any? {
        when (this) {
            null -> return null
            is Collection<*> -> {
                if (this.isEmpty() || this.size == 1) {
                    return this
                }
                val copy: ArrayList<Any?> = ArrayList()
                for (o in this) {
                    if (isValidKey(o)) {
                        copy.add(o.cleanInvalidKeys())
                    }
                }
                if (copy.isEmpty()) {
                    copy.addAll(this)
                }
                copy.sortByDescending { pointOf(it) }
                return copy
            }

            is Map<*, *> -> {
                if (this.isEmpty() || this.size == 1) {
                    return this
                }
                val copy: HashMap<Any?, Any?> = LinkedHashMap()
                this.forEach { (k, v) ->
                    if (isValidKey(k) || isValidKey(v)) {
                        copy[k] = v.cleanInvalidKeys()
                    }
                }
                if (copy.isEmpty()) {
                    copy.putAll(this)
                }
                return copy
            }

            is Variable -> return this.getValue().cleanInvalidKeys()
        }
        return this

    }

    /**
     * Computes a "point" score for an object based on its complexity and contents.
     * This score helps in prioritizing more informative or complex inferred values.
     */
    private fun pointOf(obj: Any?): Int {
        when (obj) {
            null -> return 0
            is Collection<*> -> {
                return 2 + obj.map { pointOf(it) }.sum()
            }

            is Map<*, *> -> {
                return 3 + obj.entries.map { pointOf(it.key) + pointOf(it.value) }.sum()
            }

            is Variable -> return pointOf(obj.getValue())
            is String -> return if (obj.isEmpty()) 1 else 2
        }
        return 2

    }

    private fun nullOrEmpty(obj: Any?): Boolean {
        when (obj) {
            null -> return true
            is Collection<*> -> return obj.isEmpty()
            is Map<*, *> -> return obj.isEmpty()
            is Array<*> -> return obj.isEmpty()
        }

        return false
    }

    private fun isValidKey(obj: Any?): Boolean {
        when (obj) {
            null -> return true
            is String -> return obj.isNotEmpty()
            is Collection<*> -> return obj.isNotEmpty()
            is Map<*, *> -> return obj.isNotEmpty()
            is Array<*> -> return obj.isNotEmpty()
        }

        return true
    }

    /**
     * Attempts a simple method call based on static analysis and predefined patterns,
     * like getter/setter methods, to quickly infer the return type without deep analysis.
     * e.g.
     * static method
     * getter/setter
     * method of collection(Set/List/Map...)
     */
    private fun callSimpleMethod(
        context: PsiElement?,
        psiMethod: PsiMethod,
        caller: Any? = null,
        args: Array<Any?>?,
    ): Any? {
        actionContext!!.checkStatus()
        try {
            if (psiMethod.hasModifierProperty("static")) {
                val unboxedArgs = unboxArgs(args)
                val key = psiMethod to unboxedArgs
                if (staticMethodCache.containsKey(key)) {
                    logger.info("cached:$key")
                    return staticMethodCache[key]
                }
                val tryCallRet = tryCallStaticMethod(psiMethod, unboxedArgs)
                if (tryCallRet != CALL_FAILED) {
                    return tryCallRet
                }
                val inferRet = tryInfer(MethodReturnInfer(psiMethod, caller, unboxedArgs, this))
                staticMethodCache[key] = inferRet
                return inferRet
            }

            //resolve getter
            if (PropertyUtil.isSimpleGetter(psiMethod)) {
                val realCaller = valueOf(caller)
                val field = PropertyUtil.getFieldOfGetter(psiMethod) ?: return null
                return asMap(realCaller)?.let { MappedVariable(it, psiClassHelper!!.getJsonFieldName(field)) }
            }

            //resolve setter
            if (PropertyUtil.isSimpleSetter(psiMethod)) {
                val realCaller = valueOf(caller)
                val field = PropertyUtil.getFieldOfSetter(psiMethod) ?: return null
                asMap(realCaller)?.set(psiClassHelper!!.getJsonFieldName(field), valueOf(args?.get(0)))
                return null
            }

            if (COLLECTION_METHODS.contains(psiMethod.name)) {
                init(psiMethod)

                val realCaller = valueOf(caller)

                if (psiMethod.name == "put" && isSuperMethod(psiMethod, map_put_method!!)) {

                    if (args != null) {
                        valueOf(args[0])?.let {
                            asMap(realCaller)?.put(it, valueOf(args[1]))
                            val attr = findAttrFromContext(context)
                            if (attr.notNullOrBlank()) {
                                KVUtils.addComment(asMap(realCaller)!!, it.toString(), attr)
                            }
                        }
                    }
                    return null
                }

                if (psiMethod.name == "get" && isSuperMethod(psiMethod, map_get_method!!)) {
                    if (args != null) {
                        asMap(realCaller)?.get(valueOf(args[0])!!)
                    }
                    return null
                }

                if (psiMethod.name == "putAll" && isSuperMethod(psiMethod, map_putAll_method!!)) {
                    if (args != null) {
                        asMap(valueOf(args[0]))?.let { asMap(realCaller)?.putAll(it) }
                    }
                    return null
                }

                if (psiMethod.name == "add" && isSuperMethod(psiMethod, collection_add_method!!)) {
                    if (args != null) {
                        asList(realCaller)?.add(valueOf(args[0]))
                    }
                    return null
                }

                if (psiMethod.name == "addAll" && isSuperMethod(psiMethod, collection_addAll_method!!)) {
                    if (args != null) {
                        asList(args[0])?.let { asList(realCaller)?.addAll(it) }
                    }
                    return null
                }
            }
        } catch (e: Exception) {
            logger.error("error to infer method return type:" + e.message)
            logger.traceError(e)
        }

        return CALL_FAILED
    }

    /**
     * Handles the inference process when the simple approach fails. This method
     * performs a more detailed analysis, potentially involving recursive inference,
     * to determine the method's return type.
     */
    private fun inferReturnUnsafely(psiMethod: PsiMethod, caller: Any? = null, args: Array<Any?>?, option: Int): Any? {
        actionContext!!.checkStatus()
        val realCaller = valueOf(caller)

        //try quickly infer
        if (allowQuickCall(option)) {
            try {
                return tryInfer(QuicklyMethodReturnInfer(psiMethod, this))
            } catch (_: Exception) {
            }
        }

        return tryInfer(MethodReturnInfer(psiMethod, realCaller, args, this))
    }

    @Suppress("UNCHECKED_CAST")
    fun addComment(info: HashMap<Any, Any?>, field: Any, attr: String?) {
        var comment = info["@comment"]
        if (comment == null) {
            comment = HashMap<String, String>()
            info["@comment"] = comment
        }
        (comment as HashMap<Any?, Any?>)[field] = attr
    }

    private fun tryInfer(infer: Infer): Any? {
        LOG.info("tryInfer:$infer")
        actionContext!!.checkStatus()
        try {//find recursive call
            methodStack.filter { it.callMethod() == infer.callMethod() }
                .forEach { return it.possible() }
        } catch (ignore: Exception) {
        }
        try {
            methodStack.push(infer)
            return infer.infer()
        } finally {
            methodStack.pop()
        }
    }

    private fun tryCallStaticMethod(psiMethod: PsiMethod, args: Array<Any?>?): Any? {
        actionContext!!.checkStatus()
        try {
            val psiCls = psiMethod.containingClass ?: return null
            val cls: Class<*>?
            try {
                cls = Class.forName(psiCls.qualifiedName)
            } catch (e: ClassNotFoundException) {
                return CALL_FAILED
            }
            val methodName = psiMethod.name
            val argCount = args?.size ?: 0

            val candidateMethod: ArrayList<Method> = ArrayList()
            cls.methods?.filterTo(candidateMethod) { Modifier.isStatic(it.modifiers) && it.name == methodName }

            if (candidateMethod.size == 0) return null//no found

            if (candidateMethod.size == 1) {
                try {
                    return callMethod(null, candidateMethod[0], args)
                } catch (e: Exception) {
                }
            }

            if (candidateMethod.size > 1) {
                candidateMethod.filter { it.parameterCount == argCount }
                    .forEach {
                        try {
                            return callMethod(null, it, args)
                        } catch (e: Exception) {
                        }
                    }

                candidateMethod.filter { it.parameterCount != argCount }
                    .forEach {
                        try {
                            return callMethod(null, it, args)
                        } catch (e: Exception) {
                        }
                    }
            }

            return CALL_FAILED


        } catch (e: Exception) {
            logger.warn("error in infer method return type")
            logger.traceError(e)
        }
        return CALL_FAILED
    }

    private fun unboxArgs(args: Array<Any?>?): Array<Any?>? {
        if (args == null) return null
        if (args.isEmpty()) return args
        val needUnbox = args.any { it != null && it is Variable }
        if (needUnbox) {
            val copyArgs = Array<Any?>(args.size) { null }
            args.forEachIndexed { index, arg -> copyArgs[index] = valueOf(arg) }
            return copyArgs
        }
        return args
    }

    @Suppress("UNCHECKED_CAST")
    private fun callMethod(caller: Any?, method: Method, args: Array<Any?>?): Any? {
        actionContext!!.checkStatus()

        val argCount = args?.size ?: 0
        return when {
            argCount != method.parameterCount -> if (args != null) {
                val fixArgs: Array<Any?> = Arrays.copyOf(args, method.parameterCount)
                method.invoke(caller, fixArgs)
            } else {
                val fixArgs = Array<Any?>(method.parameterCount) { null }
                method.invoke(caller, fixArgs)
            }

            args == null || args.isEmpty() -> method.invoke(caller)
            args.size == 1 -> method.invoke(caller, args[0])
            args.size == 2 -> method.invoke(caller, args[0], args[1])
            args.size == 3 -> method.invoke(caller, args[0], args[1], args[2])
            args.size == 4 -> method.invoke(caller, args[0], args[1], args[2], args[3])
            else -> method.invoke(caller, *args)
        }
    }

    private fun findAttrFromContext(context: PsiElement?): String? {
        if (context == null) return null

        var comment = findNextEndOfLineComment(context)
        if (comment.notNullOrBlank()) return comment
        comment = findPreLineEndOfLineComment(context)
        return comment
    }

    /**
     * Find the entire line of comments on the previous line
     */
    private fun findPreLineEndOfLineComment(context: PsiElement?): String? {
        if (context == null) return null
        var prevSibling = context.prevSibling ?: return findPreLineEndOfLineComment(context.parent)

        while (prevSibling is PsiWhiteSpace) {
            prevSibling = prevSibling.prevSibling ?: return null
        }
        if (isEndOfLineComment(prevSibling)) {
            val preOfPre = prevSibling.prevSibling
            if (preOfPre != null && preOfPre !is PsiWhiteSpace) {
                return null
            }
            return getTextOfEndOfLineComment(prevSibling)
        }

        return null
    }

    /**
     * Find the comment at the end of the current line
     */
    private fun findNextEndOfLineComment(element: PsiElement?): String? {
        if (element == null) return null
        var nextSibling = element.nextSibling
        if (nextSibling is PsiJavaToken && nextSibling.tokenType == JavaTokenType.SEMICOLON) {//next token is semicolon
            nextSibling = nextSibling.nextSibling
            if (isEndOfLineComment(nextSibling)) {
                return getTextOfEndOfLineComment(nextSibling)
            }
        }
        return null
    }

    /**
     * Determine the current element is the end of line comment
     */
    private fun isEndOfLineComment(element: PsiElement?): Boolean {
        if (element == null) return false
        return element is PsiComment && element.tokenType == JavaTokenType.END_OF_LINE_COMMENT
    }

    /**
     * Get the content of the end of the line comment
     */
    private fun getTextOfEndOfLineComment(element: PsiElement): String? {
        var comment: String = element.text
        if (comment.startsWith("//")) {
            comment = comment.removePrefix("//")
            if (!comment.isBlank()) return comment
        }
        return null
    }

    fun init(content: PsiElement) {
        if (map_put_method != null) return

        val mapClass = ClassUtils.findClass(CommonClassNames.JAVA_UTIL_MAP, content)!!
        map_put_method = mapClass.findMethodsByName("put", false)[0]
        map_get_method = mapClass.findMethodsByName("get", false)[0]
        map_putAll_method = mapClass.findMethodsByName("putAll", false)[0]

        val collectionClass = ClassUtils.findClass(CommonClassNames.JAVA_UTIL_COLLECTION, content)!!
        collection_add_method = collectionClass.findMethodsByName("add", false)[0]
        collection_addAll_method = collectionClass.findMethodsByName("addAll", false)[0]
    }

    private var map_put_method: PsiMethod? = null
    private var map_get_method: PsiMethod? = null
    private var map_putAll_method: PsiMethod? = null

    private var collection_add_method: PsiMethod? = null
    private var collection_addAll_method: PsiMethod? = null

    companion object : Log() {

        private const val ALLOW_QUICK_CALL: Int = 0b0001

        const val DEFAULT_OPTION = ALLOW_QUICK_CALL

        //Determines if a method allows for quick call-based inference.
        fun allowQuickCall(option: Int): Boolean {
            return (option and ALLOW_QUICK_CALL) != 0
        }

        //Static failure object used to signal that a method call cannot be inferred.
        private val CALL_FAILED = Any()

        //A set of method names considered for quick invocation without deep analysis.
        private val COLLECTION_METHODS = setOf("put", "set", "add", "addAll", "putAll")

        private fun isSuperMethod(method: PsiMethod, superMethod: PsiMethod): Boolean {
            if (method == superMethod) return true

            if (method.name != superMethod.name) {
                return false
            }

            if (PsiSuperMethodUtil.isSuperMethod(method, superMethod)) {
                return true
            }

            for (findSuperMethod in method.findSuperMethods()) {
                if (findSuperMethod == superMethod) {
                    return true
                }
            }

            if (method.parameters.size != superMethod.parameters.size) {
                return false
            }

            return PsiClassUtil.hasImplement(method.containingClass, superMethod.containingClass)

        }

        fun valueOf(obj: Any?): Any? {
            try {
                return when {
                    !needCompute(obj) -> obj
                    obj is Variable -> valueOf(obj.getValue())
                    obj is ObjectHolder -> valueOf(obj.getOrResolve())
                    obj is MutableMap<*, *> -> {
                        val copy = linkedMapOf<Any?, Any?>()
                        obj.entries.forEach { copy[valueOf(it.key)] = valueOf(it.value) }
                        return copy
                    }

                    obj is Array<*> -> {
                        val copy = LinkedList<Any?>()
                        obj.any { copy.add(valueOf(it)) }
                        return copy.toArray()
                    }

                    obj is Collection<*> -> {
                        val copy = LinkedList<Any?>()
                        obj.any { copy.add(valueOf(it)) }
                        return copy
                    }

                    else -> obj
                }
            } catch (e: Exception) {
                LOG.traceError("failed compute valueOf $obj", e)
                return null
            }
        }

        private fun needCompute(obj: Any?, deep: Int = 0): Boolean {
            if (deep > 10) {
                return false
            }
            return when (obj) {
                null -> false
                is Variable -> true
                is ObjectHolder -> true
                is MutableMap<*, *> -> {
                    obj.entries.any { needCompute(it.key, deep + 1) || needCompute(it.value, deep + 1) }
                }

                is Array<*> -> {
                    obj.any { needCompute(it, deep + 1) }
                }

                is Collection<*> -> {
                    obj.any { needCompute(it, deep + 1) }
                }

                else -> false
            }
        }

        fun assignment(target: Any?, value: Any?) {
            if (target is Variable) {
                target.setValue(value)
            }
        }

        @Suppress("UNCHECKED_CAST")
        fun asMap(obj: Any?): HashMap<Any, Any?>? {
            return obj as? HashMap<Any, Any?>?
        }

        @Suppress("UNCHECKED_CAST")
        fun asList(obj: Any?): ArrayList<Any?>? {
            if (obj == null) return null
            if (obj !is ArrayList<*>) {
                return null
            }
            return obj as ArrayList<Any?>?
        }

        fun findComplexResult(a: Any?, b: Any?): Any? {

            if (a == null) {
                return valueOf(b)
            } else if (b == null) {
                return valueOf(a)
            }

            if (a == b) {
                return a
            }

            if (a is Map<*, *> && b is Map<*, *>) {
                when {
                    a.size < b.size -> return b
                    a.size > b.size -> return a
                }
            }

            if (a is Collection<*> && b is Collection<*>) {
                when {
                    a.size < b.size -> return b
                    a.size > b.size -> return a
                }
            }

            val ua = valueOf(a)
            val ub = valueOf(b)

            if (ua is Map<*, *> && ub is Map<*, *>) {
                when {
                    ua.size < ub.size -> return ub
                    ua.size > ub.size -> return ua
                }
            }

            if (ua is Collection<*> && ub is Collection<*>) {
                when {
                    ua.size < ub.size -> return ub
                    ua.size > ub.size -> return ua
                }
            }

            val uaStr = GsonUtils.toJsonSafely(ua)
            val ubStr = GsonUtils.toJsonSafely(ub)
            if (ubStr.length > uaStr.length) {
                return ub
            }

            return ua
        }

    }

    private fun getSimpleFields(psiClass: PsiClass): Map<String, Any?>? {
        return getSimpleFields(psiClass, 0)
    }

    private fun getSimpleFields(psiClass: PsiClass, deep: Int): Map<String, Any?>? {
        if (deep >= maxObjectDeep) {
            return null
        }
        actionContext!!.checkStatus()
        val fields = linkedMapOf<String, Any?>()
        for (field in jvmClassHelper!!.getAllFields(psiClass)) {
            if (jvmClassHelper.isStaticFinal(field)) {
                continue
            }
            val type = field.type
            val name = psiClassHelper!!.getJsonFieldName(field)

            if (type is PsiPrimitiveType) {       //primitive Type
                fields[name] = PsiTypesUtil.getDefaultValue(type)
                continue
            }
            //reference Type
            if (psiClassHelper.isNormalType(type)) {//normal Type
                fields[name] = psiClassHelper.getDefaultValue(type)
                continue
            }

            fields[name] = DirectVariable { getSimpleFields(type, psiClass, deep + 1) }
        }
        return fields
    }

    private fun getSimpleFields(psiType: PsiType?, context: PsiElement): Any? {
        actionContext!!.checkStatus()
        when {
            psiType == null -> return null
            psiType is PsiPrimitiveType -> return PsiTypesUtil.getDefaultValue(psiType)
            psiClassHelper!!.isNormalType(psiType) -> return psiClassHelper.getDefaultValue(psiType)
            psiType is PsiArrayType -> {
                return psiClassHelper.getTypeObject(psiType, context, simpleJsonOption)
            }

            jvmClassHelper!!.isCollection(psiType) -> {   //list type
                return psiClassHelper.getTypeObject(psiType, context, jsonOption)
            }

            jvmClassHelper.isMap(psiType) -> {   //map type
                return psiClassHelper.getTypeObject(psiType, context, simpleJsonOption)
            }

            jvmClassHelper.isEnum(psiType) -> {
                //return "" by default
                return ""
            }

            else -> {
                val typeCanonicalText = psiType.canonicalText
                if (typeCanonicalText.contains('<') && typeCanonicalText.endsWith('>')) {
                    return psiClassHelper.getTypeObject(psiType, context, simpleJsonOption)
                } else {
                    val paramCls = PsiUtil.resolveClassInClassTypeOnly(psiType)
                    if (paramCls != null && ruleComputer!!.computer(ClassRuleKeys.TYPE_IS_FILE, paramCls) == true) {
                        return Magics.FILE_STR
                    }
                    return paramCls?.let { getSimpleFields(it, 1) }
                }
            }
        }
    }

    private fun getSimpleFields(psiType: PsiType?, context: PsiElement, deep: Int): Any? {
        if (deep >= maxObjectDeep) {
            return null
        }
        actionContext!!.checkStatus()
        when {
            psiType == null -> return null
            psiType is PsiPrimitiveType -> return PsiTypesUtil.getDefaultValue(psiType)
            psiClassHelper!!.isNormalType(psiType) -> return psiClassHelper.getDefaultValue(psiType)
            psiType is PsiArrayType -> {
                return psiClassHelper.getTypeObject(psiType, context, simpleJsonOption)
            }

            jvmClassHelper!!.isCollection(psiType) -> {   //list type
                return psiClassHelper.getTypeObject(psiType, context, simpleJsonOption)
            }

            jvmClassHelper.isMap(psiType) -> {   //map type
                return psiClassHelper.getTypeObject(psiType, context, simpleJsonOption)
            }

            else -> {
                val paramCls = PsiUtil.resolveClassInClassTypeOnly(psiType) ?: return null
                if (ruleComputer!!.computer(ClassRuleKeys.TYPE_IS_FILE, paramCls) == true) {
                    return Magics.FILE_STR
                }
                return getSimpleFields(paramCls, deep + 1)
            }
        }
    }

    interface Variable : Visional {
        fun getValue(): Any?

        fun setValue(value: Any?)

        fun addLazyAction(lazyAction: LazyAction)
    }

    abstract class AbstractVariable : Variable {
        protected var computer = false

        protected var lazyActions: LinkedList<LazyAction>? = null

        override fun getValue(): Any? {
            if (!computer) {
                computer = true
                compute()
            }
            val computedValue = getComputedValue()
            if (computedValue == this) {
                LOG.error("recursive call getValue")
                return null
            }
            return computedValue
        }

        abstract fun getComputedValue(): Any?

        override fun addLazyAction(lazyAction: LazyAction) {
            if (this.lazyActions == null) {
                this.lazyActions = LinkedList()
            }
            this.lazyActions!!.add(lazyAction)
            computer = false
        }

        fun compute() {
            this.lazyActions?.forEach { it() }
            this.lazyActions?.clear()
        }
    }

    class MappedVariable : AbstractVariable {
        private val target: HashMap<Any, Any?>

        private val name: Any

        constructor(target: HashMap<Any, Any?>, name: Any) {
            this.target = target
            this.name = name
        }

        override fun getComputedValue(): Any? = target[name]

        override fun setValue(value: Any?) {
            //todo:merge?
            target[name] = value
        }

        override fun toString(): String {
            return getValue().toString()
        }
    }

    class DirectVariable : AbstractVariable {

        protected var holderValue: Any? = null

        constructor() : super()

        constructor(holderValue: Any?) : super() {
            this.holderValue = holderValue
        }

        constructor(lazyInit: () -> Any?) : super() {
            this.addLazyAction {
                holderValue = lazyInit()
                if (holderValue is Unit) {
                    holderValue = lazyInit()
                }
            }
        }

        override fun setValue(value: Any?) {
            if (value != this) {
                this.holderValue = value
            }
        }

        override fun getComputedValue(): Any? {
            return this.holderValue
        }
    }

    /*
     * Defines the contract for inference strategies, allowing different approaches
     * to be implemented and utilized based on the context and needs of the inference
     * process.
     */
    interface Infer {
        fun infer(): Any?

        fun possible(): Any?

        fun callMethod(): Any?
    }

    /**
     * Abstract implementation of an inference strategy, providing common functionality
     * and utilities for handling method return inference. This includes managing local
     * variables, processing PSI elements, and handling control flow constructs.
     */
    abstract class AbstractMethodReturnInfer(
        var caller: Any? = null,
        val args: Array<Any?>?,
        val methodReturnInferHelper: DefaultMethodInferHelper,
    ) : Infer {

        var localParams: HashMap<String, Any?> = HashMap()

        var fields: HashMap<Any, Any?>? = null

        var returnVal: Any? = null

        private var inits: HashSet<String> = HashSet()

        protected open fun processStatement(statement: PsiStatement): Any? {

            when (statement) {
                is PsiLabeledStatement -> {
                    val labelIdentifier = statement.labelIdentifier.text
                    val variable = findVariable(labelIdentifier) ?: return null
                    if (statement.statement != null) {
                        val lazyProcessStatement = statement.statement!!
                        variable.addLazyAction {
                            processStatement(lazyProcessStatement)?.let { variable.setValue(it) }
                        }
                    }
                    return variable
                }

                is PsiIfStatement -> {
                    statement.elseBranch?.let { processStatement(it) }
                    statement.thenBranch?.let { processStatement(it) }
                }

                is PsiBlockStatement -> {
                    processBlock(statement.codeBlock)
                }

                is PsiTryStatement -> {
                    statement.tryBlock?.let { processBlock(it) }
                    for (catchBlock in statement.catchBlocks) {
                        processBlock(catchBlock)
                    }
                }

                is PsiForStatement -> {
                    statement.initialization?.let { processStatement(it) }
                    statement.update?.let { processStatement(it) }
                    statement.body?.let { processStatement(it) }
                }

                is PsiForeachStatement -> {
                    statement.iteratedValue?.let { processExpression(it) }
                    statement.body?.let { processStatement(it) }
                }

                is PsiWhileStatement -> statement.body?.let { processStatement(it) }
                is PsiDoWhileStatement -> statement.body?.let { processStatement(it) }

                is PsiExpressionStatement -> processExpression(statement.expression)
                is PsiDeclarationStatement -> {
                    for (declaredElement in statement.declaredElements) {
                        processElement(declaredElement)
                    }
                }

                is PsiReturnStatement -> {
                    val returnValue = statement.returnValue
                    if (returnValue != null) {
                        returnVal = findComplexResult(returnVal, processExpression(returnValue))
                    }
                }

                is PsiThrowStatement -> {
                    //ignore
                }

                else -> {
                    methodReturnInferHelper.logger.debug("no matched statement:${statement::class} - ${statement.text}")
                }
            }

            return null
        }

        protected open fun processElement(psiElement: PsiElement): Any? {
            return processElement(psiElement, getThis())
        }

        protected open fun processElement(psiElement: PsiElement, context: Any?): Any? {

            when (psiElement) {
                is PsiExpression -> return processExpression(psiElement)
                is PsiStatement -> return processStatement(psiElement)
                is PsiLocalVariable -> {
                    val variableName = psiElement.name ?: return null
                    val variable = findVariable(variableName) ?: return null

                    if (inits.add(variableName)) {
                        val variableType = psiElement.type

                        if (!PsiClassUtil.isInterface(variableType) && methodReturnInferHelper.duckTypeHelper!!.isQualified(
                                variableType,
                                psiElement
                            )
                        ) {
                            variable.addLazyAction {
                                variable.setValue(
                                    methodReturnInferHelper.psiClassHelper!!.getTypeObject(
                                        variableType,
                                        psiElement,
                                        methodReturnInferHelper.jsonOption
                                    )
                                )
                            }
                        } else {
                            variable.addLazyAction {
                                val processValue: Any? = if (psiElement.initializer != null) {
                                    findComplexResult(
                                        processExpression(psiElement.initializer!!),
                                        methodReturnInferHelper.getSimpleFields(
                                            psiElement.type,
                                            psiElement
                                        )
                                    )
                                } else {
                                    methodReturnInferHelper.getSimpleFields(
                                        psiElement.type,
                                        psiElement
                                    )
                                }
                                variable.setValue(processValue)
                            }
                        }
                    }
                    return variable
                }

                is PsiEnumConstant -> {
                    return resolveEnumFields(psiElement)
                }

                is PsiField -> {
                    if (methodReturnInferHelper.jvmClassHelper!!.isStaticFinal(psiElement)) {
                        return processStaticField(psiElement)
                    }
                    val fieldName =
                        methodReturnInferHelper.psiClassHelper!!.getJsonFieldName(psiElement)
                            .removePrefix("this.")
                    (context as? Map<*, *>)?.let { return findVariableIn(fieldName, context) }

                    return if (fields?.containsKey(fieldName) == true) {
                        findVariableIn(fieldName, fields)
                    } else {
                        DirectVariable { methodReturnInferHelper.getSimpleFields(psiElement.type, psiElement) }
                    }
                }

                is PsiParameter -> {
                    return psiElement.name?.let { findVariable(it) }
                }

                is PsiAnonymousClass -> {
                    processAnonymousClass(psiElement)
                    return null
                }

                is PsiClassInitializer -> {
                    processBlock(psiElement.body)
                }

                is PsiKeyword -> {
                    //todo:any keyword return null??
                    return null
                }

                is PsiReferenceParameterList -> {
                    //todo:what does PsiReferenceParameterList mean
                    return null
                }

                is PsiWhiteSpace -> {
                    //ignore white space
                    return null
                }

                is PsiJavaCodeReferenceElement -> {
                    //todo:what does PsiJavaCodeReferenceElement mean
                    return null
                }

                is PsiExpressionList -> {
                    val list = ArrayList<Any?>()
                    for (expression in psiElement.expressions) {
                        list.add(processExpression(expression))
                    }
                    return list
                }

                is PsiArrayAccessExpression -> {
                    val array = processExpression(psiElement.arrayExpression) ?: return null
                    if (array is Array<*> && array.size > 0) {
                        var index = psiElement.indexExpression?.let { processExpression(it) } ?: 0
                        if (index !is Int) {
                            index = 0
                        }
                        return array[index]
                    }

                }

                is PsiLambdaExpression -> {
                    return psiElement.body?.let { processElement(it) }
                }

                else -> {
                    //ignore
//                    methodReturnInferHelper.logger.debug("no matched ele ${psiElement::class.qualifiedName}:${psiElement.text}")
                }
            }
            return null
        }

        @Suppress("UNCHECKED_CAST")
        fun resolveEnumFields(value: PsiEnumConstant): Map<String, Any?>? {

            val constantInfo = methodReturnInferHelper.psiResolver!!.resolveEnumFields(0, value) ?: return null

            return constantInfo["params"] as? Map<String, Any?>
        }

        protected open fun processExpression(psiExpression: PsiExpression): Any? {
            when (psiExpression) {
                is PsiAssignmentExpression -> {
                    var le = processExpression(psiExpression.lExpression)
                    if (le == null) {
                        le = findVariable(psiExpression.lExpression.text)
                    }
                    //todo
                    val re = psiExpression.rExpression?.let { processExpression(it) }
                    assignment(le, re)

                    return le
                }

                is PsiNewExpression -> return processNewExpression(psiExpression)
                is PsiCallExpression -> {

                    val callMethod = psiExpression.resolveMethod() ?: return null

                    val args = psiExpression.argumentList?.expressions?.mapToTypedArray { processExpression(it) }

                    var caller: Any? = null
                    if (!callMethod.hasModifierProperty("static")) {
                        caller = findCaller(psiExpression)
                    }
                    val ret = DirectVariable {
                        methodReturnInferHelper.inferReturn(psiExpression, callMethod, caller, args)
                    }
                    if (caller != null && caller is Variable) {
                        caller.addLazyAction { ret.getValue() }
                    }
                    args?.forEach { arg ->
                        if (arg != null && arg is Variable) {
                            arg.addLazyAction { ret.getValue() }
                        }
                    }
                    return ret
                }

                is PsiReferenceExpression -> {

                    val qualifierExpression = psiExpression.qualifierExpression
                    val qualifier: Any?
                    if (qualifierExpression == null) {
                        qualifier = getThis()
                    } else {
                        qualifier = processExpression(qualifierExpression)
                        if (qualifier != null && qualifier is String) {
                            return findVariable(qualifier)
                        }
                    }
                    val resolve = psiExpression.resolve()
                    if (resolve != null) return processElement(resolve, qualifier)
                }

                is PsiThisExpression -> return getThis()
                is PsiLiteralExpression -> return psiExpression.value
                is PsiBinaryExpression -> return processBinaryExpression(psiExpression)
                is PsiUnaryExpression -> return processUnaryExpression(psiExpression)
                is PsiLambdaExpression -> return psiExpression.body?.let { processElement(it) }
                else -> {
                    //ignore
//                    methodReturnInferHelper.logger.debug("no matched exp ${psiExpression::class.qualifiedName}:${psiExpression.text}")
                    return null
                }
            }
            return null
        }

        protected fun processNewExpression(psiNewExpression: PsiNewExpression): Any? {
            val args = psiNewExpression.argumentList?.expressions?.mapToTypedArray { processExpression(it) }
            return DirectVariable {
                methodReturnInferHelper.NewExpressionInfer(
                    psiNewExpression,
                    args,
                    methodReturnInferHelper
                ).infer()
            }
        }

        private fun processBinaryExpression(psiExpression: PsiBinaryExpression): Any? {
            val op = psiExpression.operationSign

            when (op.tokenType) {
                JavaTokenType.PLUS -> {
                    val lOperand = valueOf(processExpression(psiExpression.lOperand)) ?: return null
                    val rOperand = psiExpression.rOperand?.let { valueOf(processExpression(it)) }
                    return when (lOperand) {
                        is String -> lOperand + (rOperand ?: "")
                        is Int -> lOperand.plus((rOperand ?: 0) as Int)
                        is Long -> lOperand.plus((rOperand ?: 0) as Long)
                        is Short -> lOperand.plus((rOperand ?: 0) as Short)
                        is Byte -> lOperand.plus((rOperand ?: 0) as Byte)
                        is Float -> lOperand.plus((rOperand ?: 0) as Float)
                        is Double -> lOperand.plus((rOperand ?: 0) as Double)
                        else -> {
                            lOperand
                        }
                    }
                }

                JavaTokenType.MINUS -> {
                    val lOperand = valueOf(processExpression(psiExpression.lOperand)) ?: return null
                    val rOperand = psiExpression.rOperand?.let { valueOf(processExpression(it)) }
                    return when (lOperand) {
                        is Int -> lOperand.minus((rOperand ?: 0) as Int)
                        is Long -> lOperand.minus((rOperand ?: 0) as Long)
                        is Short -> lOperand.minus((rOperand ?: 0) as Short)
                        is Byte -> lOperand.minus((rOperand ?: 0) as Byte)
                        is Float -> lOperand.minus((rOperand ?: 0) as Float)
                        is Double -> lOperand.minus((rOperand ?: 0) as Double)
                        else -> {
                            lOperand
                        }
                    }
                }

                JavaTokenType.ASTERISK -> {
                    val lOperand = valueOf(processExpression(psiExpression.lOperand)) ?: return null
                    val rOperand = psiExpression.rOperand?.let { valueOf(processExpression(it)) }
                    return when (lOperand) {
                        is Int -> lOperand.times((rOperand ?: 0) as Int)
                        is Long -> lOperand.times((rOperand ?: 0) as Long)
                        is Short -> lOperand.times((rOperand ?: 0) as Short)
                        is Byte -> lOperand.times((rOperand ?: 0) as Byte)
                        is Float -> lOperand.times((rOperand ?: 0) as Float)
                        is Double -> lOperand.times((rOperand ?: 0) as Double)
                        else -> {
                            lOperand
                        }
                    }
                }

                JavaTokenType.DIV -> {
                    val lOperand = valueOf(processExpression(psiExpression.lOperand)) ?: return null
                    val rOperand = psiExpression.rOperand?.let { valueOf(processExpression(it)) }
                    return when (lOperand) {
                        is Int -> lOperand.div((rOperand ?: 0) as Int)
                        is Long -> lOperand.div((rOperand ?: 0) as Long)
                        is Short -> lOperand.div((rOperand ?: 0) as Short)
                        is Byte -> lOperand.div((rOperand ?: 0) as Byte)
                        is Float -> lOperand.div((rOperand ?: 0) as Float)
                        is Double -> lOperand.div((rOperand ?: 0) as Double)
                        else -> {
                            lOperand
                        }
                    }
                }

                JavaTokenType.AND -> {
                    val lOperand = valueOf(processExpression(psiExpression.lOperand)) ?: return null
                    val rOperand = psiExpression.rOperand?.let { valueOf(processExpression(it)) }
                    return when (lOperand) {
                        is Boolean -> lOperand and ((rOperand ?: true) as Boolean)
                        is Int -> lOperand.and((rOperand ?: 0) as Int)
                        is Long -> lOperand.and((rOperand ?: 0) as Long)
                        else -> {
                            lOperand
                        }
                    }
                }

                JavaTokenType.OR -> {
                    val lOperand = valueOf(processExpression(psiExpression.lOperand)) ?: return null
                    val rOperand = psiExpression.rOperand?.let { valueOf(processExpression(it)) }
                    return when (lOperand) {
                        is Boolean -> lOperand or ((rOperand ?: true) as Boolean)
                        is Int -> lOperand.or((rOperand ?: 0) as Int)
                        is Long -> lOperand.or((rOperand ?: 0) as Long)
                        else -> {
                            lOperand
                        }
                    }
                }

                JavaTokenType.NE, JavaTokenType.LE, JavaTokenType.LE, JavaTokenType.LT, JavaTokenType.GE, JavaTokenType.GT -> return true
                else -> {
                    return valueOf(processExpression(psiExpression.lOperand))
                }
            }
        }

        private fun processUnaryExpression(psiExpression: PsiUnaryExpression): Any? {
            val op = psiExpression.operationSign
            val operand = psiExpression.operand?.let { valueOf(processExpression(it)) } ?: return null
            when (op.tokenType) {
                JavaTokenType.MINUS -> {
                    return when (operand) {
                        is Int -> -operand
                        is Long -> -operand
                        is Short -> -operand
                        is Byte -> -operand
                        is Float -> -operand
                        is Double -> -operand
                        else -> {
                            operand
                        }
                    }
                }

                JavaTokenType.EQEQ, JavaTokenType.NE -> {
                    return true
                }
            }
            return operand
        }

        protected fun processStaticField(psiField: PsiField): Any? {
            val constantVal = psiField.computeConstantValue()
            if (constantVal != null) return constantVal
            val initializer = psiField.initializer
            if (initializer != null) {
                return processExpression(initializer)
            }
            return DirectVariable { methodReturnInferHelper.getSimpleFields(psiField.type, psiField) }
        }

        protected fun processAnonymousClass(psiAnonymousClass: PsiAnonymousClass) {
            psiAnonymousClass.children.forEach {
                try {
                    processElement(it)
                } catch (e: Exception) {
                }
            }
        }

        protected fun getThis(): Any? {

            if (caller != null) {
                return caller
            }

            return this.fields
        }

        protected fun findCaller(psiCallExpression: PsiCallExpression): Any? {

            for (reference in psiCallExpression.references) {
                val ref = reference.resolve()?.let { processElement(it) }
                if (ref != null) {
                    return ref
                }
            }

            val firstChild = psiCallExpression.firstChild
            var firstChildVal: Any? = null
            if (firstChild is PsiReferenceExpression) {

                val qualifier = firstChild.qualifier
                if (qualifier != null) {
                    firstChildVal = processElement(qualifier)
                }
            }
            if (firstChildVal == null) {
                firstChildVal = processElement(firstChild)
            }
            if (firstChildVal != null) {
                return firstChildVal
            }
            return getThis()
        }

        protected fun findVariable(name: String): Variable? {

            return when {
                name.startsWith("this.") -> findVariableIn(name.removePrefix("this."), fields)
                localParams.containsKey(name) -> findVariableIn(name, localParams)
                fields?.containsKey(name) == true -> findVariableIn(name, fields)
                else -> findVariableIn(name, localParams)
            }
        }

        @Suppress("UNCHECKED_CAST")
        protected fun findVariableIn(name: String, from: Any?): Variable? {
            var target: Any? = from
            val finalName: String
            if (name.contains(".")) {
                finalName = name.substringAfterLast(".")
                val paths = name.substringBeforeLast(".").split(".")
                try {
                    for (path in paths) {
                        if (target is Map<*, *>) {
                            target = (target as Map<Any?, Any?>)[path]
                        } else {
                            return null
                        }
                    }
                } catch (e: Exception) {
                    return null
                }
            } else {
                finalName = name
            }
            val targetMap = asMap(target) ?: return null

            var variable = targetMap[finalName]
            if (variable == null) {
                variable = DirectVariable()
                targetMap[finalName] = variable
                return variable
            }
            if (variable is Variable) {
                return variable
            }
            return MappedVariable(targetMap, finalName)
        }

        fun processBlock(psiCodeBlock: PsiCodeBlock) {
            psiCodeBlock.statements.forEach {
                try {
                    processStatement(it)
                } catch (e: Exception) {
                }
            }
        }

        override fun possible(): Any? {
            return returnVal
        }
    }

    inner class MethodReturnInfer(
        private val psiMethod: PsiMethod,
        caller: Any? = null,
        args: Array<Any?>?,
        methodReturnInferHelper: DefaultMethodInferHelper,
    ) : AbstractMethodReturnInfer(caller, args, methodReturnInferHelper) {

        override fun infer(): Any? {

            if (args != null) {
                for ((index, parameter) in psiMethod.parameterList.parameters.withIndex()) {
                    if (args.size > index) {
                        parameter.name?.let { localParams[it] = args[index] }
                    }
                }
            } else {
                for (parameter in psiMethod.parameterList.parameters) {
                    val paramName = parameter.name ?: continue
                    localParams[paramName] = DirectVariable { getSimpleFields(parameter.type, psiMethod) }
                }
            }

            fullThis()

            if (!psiMethod.hasModifierProperty("static")) {
                psiMethod.containingClass?.let { initFields(it) }
            }

            val body = psiMethod.body ?: return null

            processBlock(body)

            return returnVal
        }

        private fun fullThis() {

            if (fields == null) {
                fields = if (caller != null) {
                    val realCaller = valueOf(caller)
                    if (realCaller is HashMap<*, *>) {
                        asMap(realCaller)
                    } else {
                        LinkedHashMap()
                    }
                } else {
                    LinkedHashMap()
                }
            }
        }

        private fun initFields(psiClass: PsiClass) {
            if (caller != null && caller is HashMap<*, *>) {
                this.fields = asMap(caller)
            } else if (caller != null && caller is Collection<*>) {
                this.fields = LinkedHashMap()
            } else {
                this.fields = LinkedHashMap()
                val fields = getSimpleFields(psiClass)
                if (fields != null) {
                    this.fields!!.putAll(fields)
                }
            }
        }

        override fun callMethod(): Any? {
            return psiMethod
        }

        override fun toString(): String {
            return "MethodReturnInfer(${PsiClassUtil.fullNameOfMethod(psiMethod)})"
        }
    }

    /**
     * Try to get the constant return without any arguments (include parameter and local variable)
     * Throws an exception if it finds that the return value is related to a parameter or a local variable
     */
    open inner class QuicklyMethodReturnInfer(
        private val psiMethod: PsiMethod,
        methodReturnInferHelper: DefaultMethodInferHelper,
    ) : AbstractMethodReturnInfer(null, null, methodReturnInferHelper) {

        override fun infer(): Any? {

            val body = psiMethod.body ?: return null

            val returnStatements = PsiTreeUtil.findChildrenOfType(body, PsiReturnStatement::class.java)

            if (returnStatements.isEmpty()) return null//nothing return

            for (returnStatement in returnStatements) {
                processStatement(returnStatement)
            }

            return returnVal
        }

        override fun processStatement(statement: PsiStatement): Any? {
            when (statement) {
                is PsiIfStatement -> {
                    statement.elseBranch?.let { processStatement(it) }
                    statement.thenBranch?.let { processStatement(it) }
                }

                is PsiBlockStatement -> {
                    processBlock(statement.codeBlock)
                }

                is PsiTryStatement -> {
                    statement.tryBlock?.let { processBlock(it) }
                    for (catchBlock in statement.catchBlocks) {
                        processBlock(catchBlock)
                    }
                }

                is PsiExpressionStatement -> processExpression(statement.expression)
                is PsiDeclarationStatement -> {
                    for (declaredElement in statement.declaredElements) {
                        processElement(declaredElement)
                    }
                }

                is PsiReturnStatement -> {
                    val returnValue = statement.returnValue
                    if (returnValue != null) {
                        returnVal = findComplexResult(returnVal, processExpression(returnValue))
                    }
                }

                else -> {
                    throw IllegalArgumentException("Quickly Infer Failed")
                }
            }

            return null
        }

        override fun processElement(psiElement: PsiElement): Any? {

            when (psiElement) {
                is PsiExpression -> return processExpression(psiElement)
                is PsiStatement -> return processStatement(psiElement)
                is PsiField -> {
                    if (methodReturnInferHelper.jvmClassHelper!!.isStaticFinal(psiElement)) {
                        return processStaticField(psiElement)
                    }
                    throw IllegalArgumentException("Quickly Infer Failed")
                }

                is PsiAnonymousClass -> {
                    processAnonymousClass(psiElement)
                    return null
                }

                is PsiClassInitializer -> {
                    return processBlock(psiElement.body)
                }

                else -> {
                    throw IllegalArgumentException("Quickly Infer Failed")
                }
            }
        }

        override fun processExpression(psiExpression: PsiExpression): Any? {
            when (psiExpression) {
                is PsiNewExpression -> return processNewExpression(psiExpression)
                is PsiCallExpression -> {
                    val callMethod = psiExpression.resolveMethod() ?: return null
                    val args = psiExpression.argumentList?.expressions?.mapToTypedArray { processExpression(it) }
                    return if (callMethod.hasModifierProperty("static")) {//only static can be call
                        DirectVariable { methodReturnInferHelper.inferReturn(callMethod, null, args) }
                    } else {
                        throw IllegalArgumentException("Quickly Infer Failed")
                    }
                }

                is PsiReferenceExpression -> {
                    val resolve = psiExpression.resolve()
                    if (resolve != null) return processElement(resolve)
//                    val name = psiExpression.qualifierExpression?.let { processExpression(it) }
//                    if (name != null && name is String) {
//                        throw IllegalArgumentException("Quickly Infer Failed")
//                    }
                    throw IllegalArgumentException("Quickly Infer Failed")
                }

                is PsiLiteralExpression -> return psiExpression.value
                else -> {
                    throw IllegalArgumentException("Quickly Infer Failed")
                }
            }
        }

        override fun callMethod(): Any? {
            return psiMethod
        }

        override fun toString(): String {
            return "QuicklyMethodReturnInfer(${PsiClassUtil.fullNameOfMethod(psiMethod)})"
        }
    }

    inner class NewExpressionInfer(
        private val psiNewExpression: PsiNewExpression,
        args: Array<Any?>?,
        methodReturnInferHelper: DefaultMethodInferHelper,
    ) : AbstractMethodReturnInfer(null, args, methodReturnInferHelper) {

        override fun infer(): Any? {
            val psiType = psiNewExpression.type ?: return null
            this.caller = DirectVariable {
                methodReturnInferHelper.psiClassHelper!!.getTypeObject(
                    psiType,
                    psiNewExpression,
                    methodReturnInferHelper.jsonOption
                )
            }
            this.fields = asMap((caller as DirectVariable).getValue())
            this.returnVal = this.caller//new exp return this
            psiNewExpression.children.forEach { child ->
                try {
                    processElement(child)
                } catch (e: Exception) {
                    //ignore
                }
            }

            if (jvmClassHelper!!.isMap(psiType)
                || jvmClassHelper.isCollection(psiType)
                || jvmClassHelper.isNormalType(psiType.canonicalText)
            ) {
                return returnVal
            }

            val resolveConstructor = this.psiNewExpression.resolveConstructor()
            if (resolveConstructor != null) {
                val inferReturnResult =
                    inferReturn(resolveConstructor, this.caller, args, DEFAULT_OPTION xor ALLOW_QUICK_CALL)
                if (inferReturnResult != null && inferReturnResult is Variable) {
                    inferReturnResult.getValue()
                }
                (caller as DirectVariable).getValue()
            }
            return returnVal
        }

        override fun callMethod(): Any {
            return psiNewExpression
        }

        override fun toString(): String {
            val constructor = this.psiNewExpression.resolveConstructor()
            return if (constructor == null) {
                "NewExpressionInfer(${psiNewExpression.text})"
            } else {
                "NewExpressionInfer(${PsiClassUtil.fullNameOfMethod(constructor)})"
            }
        }
    }
}

private typealias LazyAction = () -> Unit