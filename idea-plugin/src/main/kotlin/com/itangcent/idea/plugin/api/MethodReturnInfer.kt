package com.itangcent.idea.plugin.api

import com.google.inject.Inject
import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.*
import com.intellij.psi.util.*
import com.itangcent.common.utils.GsonUtils
import com.itangcent.common.utils.Visional
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.JsonOption
import com.itangcent.intellij.psi.PsiClassHelper
import com.itangcent.intellij.psi.TmTypeHelper
import com.itangcent.intellij.spring.SpringClassName
import com.itangcent.intellij.util.KV
import com.siyeh.ig.psiutils.ClassUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet

class MethodReturnInferHelper {
    @Inject
    var logger: Logger? = null

    @Inject
    val psiClassHelper: PsiClassHelper? = null

    @Inject
    val tmTypeHelper: TmTypeHelper? = null

    private val staticMethodCache: HashMap<Pair<PsiMethod, Array<Any?>?>, Any?> = HashMap()

    private val methodStack: Stack<Infer> = Stack()

    private var jsonOption: Int = JsonOption.ALL

    private var simpleJsonOption: Int = jsonOption and JsonOption.READ_GETTER.inv()

    private var maxDeep = 4

    private val maxObjectDeep: Int = 4

    fun setMaxDeep(maxDeep: Int) {
        this.maxDeep = maxDeep
    }

    fun inferReturn(psiMethod: PsiMethod, option: Int = DEFAULT_OPTION): Any? {
        return cleanInvalidKeys(inferReturn(psiMethod, null, null, option))
    }

    fun inferReturn(psiMethod: PsiMethod, caller: Any? = null, args: Array<Any?>?, option: Int = DEFAULT_OPTION): Any? {
        return inferReturn(null, psiMethod, caller, args, option)
    }

    fun inferReturn(context: PsiElement?, psiMethod: PsiMethod, caller: Any? = null, args: Array<Any?>?, option: Int = DEFAULT_OPTION): Any? {

        if (methodStack.size < maxDeep) {
            try {
                var inferRet: Any?
                inferRet = callSimpleMethod(context, psiMethod, caller, args)
                if (inferRet == CALL_FAILED) {
                    if (allowQuickCall(option)) {
                        val returnType = psiMethod.returnType
                        if (returnType != null && tmTypeHelper!!.isQualified(returnType, psiMethod)) {
                            return psiClassHelper!!.getTypeObject(psiMethod.returnType, psiMethod, jsonOption)
                        }
                    }

                    inferRet = inferReturnUnsafely(psiMethod, caller, args, option)
                }

                if (!nullOrEmpty(inferRet)) {

                    val byType = psiClassHelper!!.getTypeObject(psiMethod.returnType, psiMethod, jsonOption)

                    return findComplexResult(GsonUtils.resolveCycle(inferRet), byType)
                }
            } catch (e: Exception) {
                logger!!.error("infer error:" + ExceptionUtils.getStackTrace(e))
                //infer failed
            }
        }
        val returnType = psiMethod.returnType ?: return null
        if (returnType.presentableText == "void" || returnType.presentableText == "Void") {
            return null
        }
        return DirectVariable { psiClassHelper!!.getTypeObject(psiMethod.returnType, psiMethod, jsonOption) }
    }

    private fun cleanInvalidKeys(obj: Any?): Any? {
        when (obj) {
            null -> return null
            is Collection<*> -> {
                if (obj.isEmpty() || obj.size == 1) {
                    return obj
                }
                val copy: ArrayList<Any?> = ArrayList()
                for (o in obj) {
                    if (isValidKey(o)) {
                        copy.add(cleanInvalidKeys(o))
                    }
                }
                if (copy.isEmpty()) {
                    copy.addAll(obj)
                }
                return copy
            }
            is Map<*, *> -> {
                if (obj.isEmpty() || obj.size == 1) {
                    return obj
                }
                val copy: HashMap<Any?, Any?> = HashMap()
                obj.forEach { k, v ->
                    if (isValidKey(k) || isValidKey(v)) {
                        copy[k] = cleanInvalidKeys(v)
                    }
                }
                if (copy.isEmpty()) {
                    copy.putAll(obj)
                }
                return copy
            }
            is Variable -> return cleanInvalidKeys(obj.getValue())
        }
        return obj

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
     * Try a simple call
     * static method
     * getter/setter
     * method of collection(Set/List/Map...)
     */
    private fun callSimpleMethod(context: PsiElement?, psiMethod: PsiMethod, caller: Any? = null, args: Array<Any?>?): Any? {
        try {
            if (psiMethod.hasModifier(JvmModifier.STATIC)) {
                val unboxedArgs = unboxArgs(args)
                val key = psiMethod to unboxedArgs
                if (staticMethodCache.containsKey(key)) {
                    return staticMethodCache[key]
                }
                val tryCallRet = tayCallStaticMethod(psiMethod, unboxedArgs)
                if (tryCallRet != CALL_FAILED) {
                    return tryCallRet
                }
                val inferRet = tryInfe(MethodReturnInfer(psiMethod, caller, unboxedArgs, this))
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

            if (collection_methods.contains(psiMethod.name)) {
                init(psiMethod)

                val realCaller = valueOf(caller)

                if (psiMethod.name == "put" && isSuperMethod(psiMethod, map_put_method!!)) {

                    if (args != null) {
                        valueOf(args[0])?.let {
                            asMap(realCaller)?.put(it, valueOf(args[1]))
                            val attr = findAttrFromContext(context)
                            if (!attr.isNullOrBlank()) {
                                addComment(asMap(realCaller)!!, it, attr)
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
        }

        return CALL_FAILED
    }

    private fun inferReturnUnsafely(psiMethod: PsiMethod, caller: Any? = null, args: Array<Any?>?, option: Int): Any? {
        val realCaller = valueOf(caller)

        //try quickly infer
        if (allowQuickCall(option)) {
            try {
                return tryInfe(QuicklyMethodReturnInfer(psiMethod, this))
            } catch (e: Exception) {
            }
        }

        return tryInfe(MethodReturnInfer(psiMethod, realCaller, args, this))
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

    fun tryInfe(infer: Infer): Any? {
        //find recursive call
        methodStack.filter { it.callMethod() == infer.callMethod() }
                .forEach { return it.possible() }
        try {
            methodStack.push(infer)
            return infer.infer()
        } finally {
            methodStack.pop()
        }
    }

    fun tayCallStaticMethod(psiMethod: PsiMethod, args: Array<Any?>?): Any? {
        try {
            val psiCls = psiMethod.containingClass ?: return null
            val cls = Class.forName(psiCls.qualifiedName)
            val methodName = psiMethod.name
            val argCount = args?.size ?: 0

            val candidateMethod: ArrayList<Method> = ArrayList()
            cls.methods?.filterTo(candidateMethod) { Modifier.isStatic(it.modifiers) && it.name == methodName }

            if (candidateMethod.size == 0) return null//no found

            if (candidateMethod.size == 1) {
                return callMethod(null, candidateMethod[0], args)
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
            //find class failed or call method failed
            return CALL_FAILED
        }
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

        val argCount = args?.size ?: 0
        when {
            argCount != method.parameterCount -> if (args != null) {
                val fixArgs: Array<Any?> = Arrays.copyOf(args, method.parameterCount)
                return method.invoke(caller, fixArgs)
            } else {
                val fixArgs = Array<Any?>(method.parameterCount, { null })
                return method.invoke(caller, fixArgs)
            }
            args == null || args.isEmpty() -> return method.invoke(caller)
            args.size == 1 -> return method.invoke(caller, args[0])
            args.size == 2 -> return method.invoke(caller, args[0], args[1])
            args.size == 3 -> return method.invoke(caller, args[0], args[1], args[2])
            args.size == 4 -> return method.invoke(caller, args[0], args[1], args[2], args[3])
            else -> return method.invoke(caller, *args)
        }
    }

    private fun findAttrFromContext(context: PsiElement?): String? {
        if (context == null) return null

        var comment = findNextEndOfLineComment(context)
        if (!comment.isNullOrBlank()) return comment
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

    companion object {

        const val ALLOW_QUICK_CALL: Int = 0b0001

        val DEFAULT_OPTION = ALLOW_QUICK_CALL

        fun allowQuickCall(option: Int): Boolean {
            return (option and ALLOW_QUICK_CALL) != 0
        }

        private val loggerUnmatched: Boolean = false

        val CALL_FAILED = Object()
        val collection_methods = HashSet(Arrays.asList("put", "set", "add", "addAll", "putAll"))
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

        var map_put_method: PsiMethod? = null
        var map_get_method: PsiMethod? = null
        var map_putAll_method: PsiMethod? = null

        var collection_add_method: PsiMethod? = null
        var collection_addAll_method: PsiMethod? = null

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

            return true

        }

        fun valueOf(obj: Any?): Any? {
            return when (obj) {
                null -> null
                is Variable -> valueOf(obj.getValue())
                else -> obj
            }
        }

        fun assignment(target: Any?, value: Any?) {

            if (target is Variable) {
                target.setValue(value)
            }
        }

        @Suppress("UNCHECKED_CAST")
        fun asMap(obj: Any?): HashMap<Any, Any?>? {
            if (obj == null) return null
            if (obj !is HashMap<*, *>) {
                return null
            }
            return obj as HashMap<Any, Any?>?
        }

        @Suppress("UNCHECKED_CAST")
        fun asList(obj: Any?): ArrayList<Any?>? {
            if (obj == null) return null
            if (obj !is ArrayList<*>) {
                return null
            }
            return obj as ArrayList<Any?>?
        }

        fun merge(a: Any?, b: Any?): Any? {
            if (a == null) {
                return valueOf(b)
            } else if (b == null) {
                return valueOf(a)
            }

            val ua = valueOf(a)
            val ub = valueOf(b)

            if (ua is Map<*, *> && ub is Map<*, *>) {
                val res: HashMap<Any?, Any?> = HashMap()
                res.putAll(ua)
                res.putAll(ub)
                return res
            }

            if (ua is Collection<*> && ub is Collection<*>) {
                val res: ArrayList<Any?> = ArrayList()
                res.addAll(ua)
                res.addAll(ub)
                return res
            }

            if (ub is Map<*, *>) {
                return ub
            }

            //not merge
            return ua
        }

        fun findComplexResult(a: Any?, b: Any?): Any? {

            if (a == null) {
                return valueOf(b)
            } else if (b == null) {
                return valueOf(a)
            }

            val ua = valueOf(a)
            val ub = valueOf(b)

            if (ua is Map<*, *> && ub is Map<*, *>) {
                when {
                    ua.size < ub.size -> return ub
                    ub.size > ua.size -> return ua
                }
            }

            if (ua is Collection<*> && ub is Collection<*>) {
                when {
                    ua.size < ub.size -> return ub
                    ub.size > ua.size -> return ua
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
        val kv = KV.create<String, Any?>()
        for (field in psiClass.allFields) {
            val type = field.type
            val name = psiClassHelper!!.getJsonFieldName(field)

            if (type is PsiPrimitiveType) {       //primitive Type
                kv[name] = PsiTypesUtil.getDefaultValue(type)
                continue
            }
            //reference Type
            if (psiClassHelper.isNormalType(type.canonicalText)) {//normal Type
                kv[name] = psiClassHelper.getDefaultValue(type.canonicalText)
                continue
            }

            kv[name] = DirectVariable { getSimpleFields(type, psiClass, deep + 1) }
        }
        return kv
    }

    private fun getSimpleFields(psiType: PsiType?, context: PsiElement): Any? {
        when {
            psiType == null || psiType == PsiType.NULL -> return null
            psiType is PsiPrimitiveType -> return PsiTypesUtil.getDefaultValue(psiType)
            psiClassHelper!!.isNormalType(psiType.canonicalText) -> return psiClassHelper.getDefaultValue(psiType.canonicalText)
            psiType is PsiArrayType -> {
                return psiClassHelper.getTypeObject(psiType, context, simpleJsonOption)
            }
            psiType.canonicalText == SpringClassName.MULTIPARTFILE -> {
                return psiClassHelper.getTypeObject(psiType, context, jsonOption)
            }
            PsiClassHelper.isCollection(psiType) -> {   //list type
                return psiClassHelper.getTypeObject(psiType, context, jsonOption)
            }
            PsiClassHelper.isMap(psiType) -> {   //map type
                return psiClassHelper.getTypeObject(psiType, context, simpleJsonOption)
            }
            else -> {
                val typeCanonicalText = psiType.canonicalText
                return if (typeCanonicalText.contains('<') && typeCanonicalText.endsWith('>')) {
                    return psiClassHelper.getTypeObject(psiType, context, simpleJsonOption)
                } else {
                    val paramCls = PsiUtil.resolveClassInClassTypeOnly(psiType)
                    paramCls?.let { getSimpleFields(it, 1) }
                }
            }
        }
    }

    private fun getSimpleFields(psiType: PsiType?, context: PsiElement, deep: Int): Any? {
        if (deep >= maxObjectDeep) {
            return null
        }
        when {
            psiType == null || psiType == PsiType.NULL -> return null
            psiType is PsiPrimitiveType -> return PsiTypesUtil.getDefaultValue(psiType)
            psiClassHelper!!.isNormalType(psiType.canonicalText) -> return psiClassHelper.getDefaultValue(psiType.canonicalText)
            psiType is PsiArrayType -> {
                return psiClassHelper.getTypeObject(psiType, context, simpleJsonOption)
            }
            psiType.canonicalText == SpringClassName.MULTIPARTFILE -> {
                return psiClassHelper.getTypeObject(psiType, context, jsonOption)
            }
            PsiClassHelper.isCollection(psiType) -> {   //list type
                return psiClassHelper.getTypeObject(psiType, context, simpleJsonOption)
            }
            PsiClassHelper.isMap(psiType) -> {   //map type
                return psiClassHelper.getTypeObject(psiType, context, simpleJsonOption)
            }
            else -> {
                val paramCls = PsiUtil.resolveClassInClassTypeOnly(psiType) ?: return null
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
            return getComputedValue()
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
            //todo:meger?
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
            this.holderValue = value
        }

        override fun getComputedValue(): Any? {
            return this.holderValue
        }
    }

    interface Infer {
        fun infer(): Any?

        fun possible(): Any?

        fun callMethod(): Any?
    }

    abstract class AbstractMethodReturnInfer(var caller: Any? = null, val args: Array<Any?>?, val methodReturnInferHelper: MethodReturnInferHelper) : Infer {

        var localParams: HashMap<String, Any?> = HashMap()

        var fields: HashMap<Any, Any?>? = null

        var returnVal: Any? = null

        private var inits: HashSet<String> = HashSet()

        val psiClassHelper = ActionContext.getContext()!!.instance(PsiClassHelper::class)

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
                else -> if (loggerUnmatched) {
                    methodReturnInferHelper.logger!!.info("no matched statement:${statement::class} - ${statement.text}")
                }
            }

            return null
        }

        protected open fun processElement(psiElement: PsiElement): Any? {

            when (psiElement) {
                is PsiExpression -> return processExpression(psiElement)
                is PsiStatement -> return processStatement(psiElement)
                is PsiLocalVariable -> {
                    val variableName = psiElement.name ?: return null
                    val variable = findVariable(variableName) ?: return null

                    if (inits.add(variableName)) {
                        val variableType = psiElement.type

                        if (methodReturnInferHelper.tmTypeHelper!!.isQualified(variableType, psiElement)) {
                            variable.addLazyAction {
                                variable.setValue(methodReturnInferHelper.psiClassHelper!!.getTypeObject(variableType, psiElement))
                            }
                        } else {
                            variable.addLazyAction {
                                var processValue: Any?
                                if (psiElement.initializer != null) {
                                    processValue = processExpression(psiElement.initializer!!)
                                } else {
                                    processValue = methodReturnInferHelper.getSimpleFields(psiElement.type,
                                            psiElement)
                                }
                                variable.setValue(processValue)
                            }
                        }
                    }
                    return variable
                }
                is PsiField -> {
                    if (psiClassHelper.hasAnyModify(psiElement, PsiClassHelper.staticFinalFieldModifiers)) {
                        return processStaticField(psiElement)
                    }
                    val fieldName = methodReturnInferHelper.psiClassHelper!!.getJsonFieldName(psiElement)
                    return if (fields?.containsKey(fieldName) == true) {
                        findField(fieldName)
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
                else -> if (loggerUnmatched) {
                    methodReturnInferHelper.logger!!.info("no matched ele ${psiElement::class.qualifiedName}:${psiElement.text}")
                }
            }
            return null
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

                    val args = psiExpression.argumentList?.expressions?.map { processExpression(it) }
                            ?.toTypedArray()

                    var caller: Any? = null
                    if (!callMethod.hasModifier(JvmModifier.STATIC)) {
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

                    val resolve = psiExpression.resolve()
                    if (resolve != null) return processElement(resolve)

                    val name = psiExpression.qualifierExpression?.let { processExpression(it) }
                    if (name != null && name is String) {
                        return findVariable(name)
                    }
                }
                is PsiThisExpression -> return getThis()
                is PsiLiteralExpression -> return psiExpression.value
                is PsiBinaryExpression -> return processBinaryExpression(psiExpression)
                is PsiUnaryExpression -> return processUnaryExpression(psiExpression)
                else -> {
                    if (loggerUnmatched) {
                        methodReturnInferHelper.logger!!.info("no matched exp ${psiExpression::class.qualifiedName}:${psiExpression.text}")
                    }
                    return null
                }
            }
            return null
        }

        protected fun processNewExpression(psiNewExpression: PsiNewExpression): Any? {
            val args = psiNewExpression.argumentList?.expressions?.map { processExpression(it) }
                    ?.toTypedArray()
            return DirectVariable { methodReturnInferHelper.NewExpressionInfer(psiNewExpression, args, methodReturnInferHelper).infer() }
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

        protected fun findField(name: String): Variable? {
            return if (name.startsWith("this.")) {
                findVariableIn(name.removePrefix("this."), fields)
            } else {
                findVariableIn(name, fields)
            }
        }

        protected fun findVariableIn(name: String, from: Any?): Variable? {
            var target: Any? = from
            val finalName: String
            if (name.contains(".")) {
                finalName = name.substringAfterLast(".")
                val paths = name.substringBeforeLast(".").split(".")
                try {
                    for (path in paths) {
                        target = (target as Map<*, *>)[path]
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

        fun processBlock(psicodeBlock: PsiCodeBlock) {
            psicodeBlock.statements.forEach {
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

    inner class MethodReturnInfer(private val psiMethod: PsiMethod, caller: Any? = null, args: Array<Any?>?, methodReturnInferHelper: MethodReturnInferHelper)
        : AbstractMethodReturnInfer(caller, args, methodReturnInferHelper) {

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

            if (!psiMethod.hasModifier(JvmModifier.STATIC)) {
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
                        HashMap()
                    }
                } else {
                    HashMap()
                }
            }
        }

        private fun initFields(psiClass: PsiClass) {
            if (caller != null && caller is HashMap<*, *>) {
                this.fields = asMap(caller)
            } else {
                this.fields = HashMap()
                val fields = getSimpleFields(psiClass)
                if (fields != null) {
                    this.fields!!.putAll(fields)
                }
            }
        }

        override fun callMethod(): Any? {
            return psiMethod
        }
    }

    /**
     * Try to get the constant return without any arguments (include parameter and local variable)
     * Throws an exception if it finds that the return value is related to a parameter or a local variable
     */
    open inner class QuicklyMethodReturnInfer(val psiMethod: PsiMethod, methodReturnInferHelper: MethodReturnInferHelper)
        : AbstractMethodReturnInfer(null, null, methodReturnInferHelper) {

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
                    if (psiClassHelper.hasAnyModify(psiElement, PsiClassHelper.staticFinalFieldModifiers)) {
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
                    val args = psiExpression.argumentList?.expressions?.map { processExpression(it) }
                            ?.toTypedArray()
                    return if (callMethod.hasModifier(JvmModifier.STATIC)) {//only static can be call
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
    }

    inner class NewExpressionInfer(val psiNewExpression: PsiNewExpression, args: Array<Any?>?, methodReturnInferHelper: MethodReturnInferHelper)
        : AbstractMethodReturnInfer(null, args, methodReturnInferHelper) {

        override fun infer(): Any? {
            this.caller = DirectVariable { methodReturnInferHelper.psiClassHelper!!.getTypeObject(psiNewExpression.type, psiNewExpression, methodReturnInferHelper.jsonOption) }
            this.fields = asMap((caller as DirectVariable).getValue())
            this.returnVal = this.caller//new exp return this
            psiNewExpression.children.forEach { child ->
                try {
                    processElement(child)
                } catch (e: Exception) {
                    //ignore
                }
            }

            val resolveConstructor = this.psiNewExpression.resolveConstructor()
            if (resolveConstructor != null) {
                val inferReturnResult = inferReturn(resolveConstructor, this.caller, args, DEFAULT_OPTION xor ALLOW_QUICK_CALL)
                if (inferReturnResult != null && inferReturnResult is Variable) {
                    inferReturnResult.getValue()
                }
                (caller as DirectVariable).getValue()
            }
            return returnVal
        }

        override fun callMethod(): Any? {
            return psiNewExpression
        }
    }
}

typealias  LazyAction = () -> Unit

class DisposableLazyAction : LazyAction {
    private var disposed = false
    val delegate: LazyAction

    constructor(delegate: LazyAction) {
        this.delegate = delegate
    }

    override fun invoke() {
        if (disposed) return
        disposed = true
        delegate()
    }
}

fun LazyAction.disposable(): DisposableLazyAction {
    return DisposableLazyAction(this)
}