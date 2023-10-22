package com.itangcent.idea.plugin.rule

import com.google.inject.Inject
import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil
import com.itangcent.annotation.script.ScriptIgnore
import com.itangcent.annotation.script.ScriptTypeName
import com.itangcent.common.logger.traceError
import com.itangcent.common.utils.asBool
import com.itangcent.common.utils.mapToTypedArray
import com.itangcent.http.RequestUtils
import com.itangcent.idea.plugin.api.MethodInferHelper
import com.itangcent.idea.plugin.format.Json5Formatter
import com.itangcent.intellij.config.rule.*
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.toPrettyString
import com.itangcent.intellij.jvm.*
import com.itangcent.intellij.jvm.duck.ArrayDuckType
import com.itangcent.intellij.jvm.duck.DuckType
import com.itangcent.intellij.jvm.duck.SingleDuckType
import com.itangcent.intellij.jvm.duck.SingleUnresolvedDuckType
import com.itangcent.intellij.jvm.element.ExplicitClass
import com.itangcent.intellij.jvm.element.ExplicitField
import com.itangcent.intellij.jvm.element.ExplicitMethod
import com.itangcent.intellij.jvm.element.ExplicitParameter
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.ClassRuleConfig
import com.itangcent.intellij.psi.PsiClassUtils
import java.util.*
import javax.script.ScriptContext
import javax.script.ScriptEngine
import javax.script.SimpleScriptContext

abstract class ScriptRuleParser : AbstractRuleParser() {

    @Inject
    protected val duckTypeHelper: DuckTypeHelper? = null

    @Inject
    protected val psiClassHelper: PsiClassHelper? = null

    @Inject
    protected val classRuleConfig: ClassRuleConfig? = null

    @Inject
    protected val docHelper: DocHelper? = null

    @Inject
    protected val annotationHelper: AnnotationHelper? = null

    @Inject
    protected val jvmClassHelper: JvmClassHelper? = null

    @Inject
    protected val methodReturnInferHelper: MethodInferHelper? = null

    @Inject
    protected lateinit var actionContext: ActionContext

    @Inject
    protected val logger: Logger? = null
    override fun parseAnyRule(rule: String): AnyRule? {
        return { context ->
            eval(rule, context)
        }
    }

    override fun parseBooleanRule(rule: String): BooleanRule? {
        return { context ->
            eval(rule, context).asBool()
        }
    }

    override fun parseStringRule(rule: String): StringRule? {
        return { context ->
            eval(rule, context)?.toPrettyString()
        }
    }

    override fun parseEventRule(rule: String): EventRule? {
        return {
            eval(rule, it)
            Unit
        }
    }

    private fun eval(ruleScript: String, context: RuleContext): Any? {
        return try {
            val simpleScriptContext = SimpleScriptContext()

            context.exts()?.forEach {
                simpleScriptContext.setAttribute(
                    it.key,
                    wrap(
                        obj = it.value,
                        context = context.getPsiContext(),
                        shouldCopy = false
                    ),
                    ScriptContext.ENGINE_SCOPE
                )
            }

            val contextForScript: RuleContext? =
                (context as? BaseScriptRuleContext) ?: context.getCore()?.let { contextOf(it, context.getResource()) }
                ?: context.getResource()?.let { contextOf(it, context.getResource()) }
            if (contextForScript != null) {
                simpleScriptContext.setAttribute("it", contextForScript, ScriptContext.ENGINE_SCOPE)
            }

            initScriptContext(simpleScriptContext, context)
            getScriptEngine().eval(ruleScript, simpleScriptContext)
        } catch (e: UnsupportedScriptException) {
            logger?.error("unsupported script type:${e.getType()},script:$ruleScript")
            null
        } catch (e: Exception) {
            logger?.traceError("error eval script:$ruleScript", e)
            null
        }
    }

    protected abstract fun getScriptEngine(): ScriptEngine

    protected open fun initScriptContext(scriptContext: ScriptContext, context: RuleContext) {

    }

    override fun contextOf(target: Any, context: PsiElement?): RuleContext {
        return when (target) {
            is PsiClass -> ScriptPsiClassContext(target)
            is PsiField -> ScriptPsiFieldContext(target)
            is PsiMethod -> ScriptPsiMethodContext(target)
            is PsiParameter -> ScriptPsiParameterContext(target)
            is PsiType -> ScriptPsiTypeContext(target)
            is ExplicitClass -> ScriptExplicitClassContext(target)
            is ExplicitField -> ScriptExplicitFieldContext(target)
            is ExplicitMethod -> ScriptExplicitMethodContext(target)
            is ExplicitParameter -> ScriptExplicitParameterContext(target)
            is DuckType -> ScriptDuckTypeContext(target)
            is PsiElement -> BaseScriptRuleContext(target)
            is String -> StringRuleContext(target, context!!)
            else -> throw IllegalArgumentException("unable to build context of:$target")
        }
    }

    private fun wrap(obj: Any?, context: PsiElement?, shouldCopy: Boolean = true): Any? {
        if (obj == null) {
            return null
        }

        if (obj is RuleContext) {
            return obj
        }

        val className = obj::class.qualifiedName ?: return obj
        if (className.startsWith("com.intellij") || className.startsWith("com.itangcent.intellij.jvm")) {
            return try {
                contextOf(obj, context)
            } catch (e: IllegalArgumentException) {
                obj
            }
        }

        if (shouldCopy) {
            when (obj) {
                is Map<*, *> -> {
                    val copy = LinkedHashMap<Any?, Any?>()
                    for ((k, v) in obj.entries) {
                        copy[k] = wrap(v, context)
                    }
                    return copy
                }

                is Collection<*> -> {
                    val copy = LinkedList<Any?>()
                    for (ele in obj) {
                        copy.add(wrap(ele, context))
                    }
                    return copy
                }

                is Array<*> -> {
                    val copy = LinkedList<Any?>()
                    for (ele in obj) {
                        copy.add(wrap(ele, context))
                    }
                    return copy
                }
            }
        }
        return obj
    }

    /**
     * support usages:
     *
     * it.name():String
     * it.hasAnn("annotation_name"):Boolean
     * it.ann("annotation_name"):String?
     * it.ann("annotation_name","attr"):String?
     * it.doc():String
     * it.doc("tag"):String?
     * it.doc("tag","subTag"):String?
     * it.hasDoc("tag"):Boolean
     * it.hasModifier("modifier"):Boolean
     * it.sourceCode():String
     */
    @ScriptIgnore("getResource", "getCore", "asPsiDocCommentOwner", "asPsiModifierListOwner")
    open inner class BaseScriptRuleContext : SuvRuleContext {
        constructor(psiElement: PsiElement?) : super(psiElement)
        constructor() : super()

        @Suppress("UNCHECKED_CAST")
        private fun <T> wrapAs(obj: Any?): T? {
            return wrap(obj, getPsiContext()) as T?
        }


        fun name(): String {
            return getName() ?: ""
        }

        /**
         * it.hasAnn("annotation_name"):Boolean
         */
        fun hasAnn(name: String): Boolean {
            return annotationHelper!!.hasAnn(getResource(), name)
        }

        /**
         * it.ann("annotation_name"):String?
         */
        fun ann(name: String): String? {
            return ann(name, "value")
        }

        /**
         * it.annMap("annotation_name"):Map<String, Any?>?
         */
        fun annMap(name: String): Map<String, Any?>? {
            return wrapAs(annotationHelper!!.findAnnMap(getResource(), name))
        }

        /**
         * it.annMaps("annotation_name"):List<Map<String, Any?>>?
         */
        fun annMaps(name: String): List<Map<String, Any?>>? {
            return wrapAs(annotationHelper!!.findAnnMaps(getResource(), name))
        }

        /**
         * it.ann("annotation_name","attr"):String?
         */
        fun ann(name: String, attr: String): String? {
            return annotationHelper!!.findAttrAsString(getResource(), name, attr)
        }

        /**
         * it.ann("annotation_name"):Any?
         */
        fun annValue(name: String): Any? {
            return annValue(name, "value")
        }

        /**
         * it.ann("annotation_name","attr"):Any?
         */
        fun annValue(name: String, attr: String): Any? {
            return wrapAs(annotationHelper!!.findAttr(getResource(), name, attr))
        }

        /**
         * it.doc():String
         */
        fun doc(): String? {
            return docHelper!!.getAttrOfDocComment(getResource())
        }

        /**
         * it.doc("tag"):String?
         */
        fun doc(tag: String): String? {
            return docHelper!!.findDocByTag(getResource(), tag)
        }

        /**
         * it.docs("tag"):List<String>?
         */
        fun docs(tag: String): List<String>? {
            return docHelper!!.findDocsByTag(getResource(), tag)
        }

        /**
         * it.hasDoc("tag"):Boolean
         */
        fun hasDoc(tag: String): Boolean {
            return docHelper!!.hasTag(getResource(), tag)
        }

        /**
         * it.doc("tag","subTag"):String?
         */
        fun doc(tag: String, subTag: String): String? {
            return docHelper!!.findDocsByTagAndName(
                getResource(), tag, subTag
            )
        }

        fun hasModifier(modifier: String): Boolean {
            return asPsiModifierListOwner()?.hasModifierProperty(modifier) ?: false
        }

        fun modifiers(): List<String> {
            return getResource()?.let { jvmClassHelper!!.extractModifiers(it) } ?: emptyList()
        }

        fun sourceCode(): String? {
            return actionContext.callInReadUI { getResource()?.text }
        }

        fun defineCode(): String? {
            return getResource()?.let { jvmClassHelper!!.defineCode(it) }
        }

        open fun contextType(): String {
            return "unknown"
        }
    }

    /**
     * it.methods():method[]
     * it.methodCnt():int
     * it.field():field[]
     * it.fieldCnt():int
     * it.isExtend("cls"):Boolean
     * it.isMap():Boolean
     * it.isCollection():Boolean
     * it.isArray():Boolean
     * @see ScriptPsiTypeContext
     */
    @ScriptTypeName("class")
    open inner class ScriptPsiClassContext(protected val psiClass: PsiClass) : ScriptClassContext(psiClass) {

        override fun contextType(): String {
            return "class"
        }

        override fun methods(): Array<ScriptPsiMethodContext> {
            return jvmClassHelper!!.getAllMethods(psiClass).mapToTypedArray { ScriptPsiMethodContext(it) }
        }

        override fun methodCnt(): Int {
            return jvmClassHelper!!.getAllMethods(psiClass).size
        }

        override fun fields(): Array<ScriptPsiFieldContext> {
            return jvmClassHelper!!.getAllFields(psiClass).mapToTypedArray { ScriptPsiFieldContext(it) }
        }

        override fun fieldCnt(): Int {
            return jvmClassHelper!!.getAllFields(psiClass).size
        }

        override fun isExtend(superClass: String): Boolean {
            return ActionContext.getContext()!!.callInReadUI {
                jvmClassHelper!!.isInheritor(psiClass, superClass)
            } ?: false
        }

        override fun isMap(): Boolean {
            return jvmClassHelper!!.isMap(PsiTypesUtil.getClassType(psiClass))
        }

        override fun isCollection(): Boolean {
            return jvmClassHelper!!.isCollection(PsiTypesUtil.getClassType(psiClass))
        }

        override fun isArray(): Boolean {
            return name().endsWith("[]")
        }

        /**
         * Returns whether this class is a primitive
         */
        override fun isPrimitive(): Boolean {
            return jvmClassHelper!!.isPrimitive(name())
        }

        /**
         * Returns whether this class is a primitive wrapper
         */
        override fun isPrimitiveWrapper(): Boolean {
            return jvmClassHelper!!.isPrimitiveWrapper(name())
        }

        /**
         * Returns whether the given {@code type} is a primitive or primitive wrapper
         * or {@code String}、{@code Object}
         */
        override fun isNormalType(): Boolean {
            return jvmClassHelper!!.isNormalType(name())
        }

        override fun isInterface(): Boolean {
            return psiClass.isInterface
        }

        override fun isAnnotationType(): Boolean {
            return psiClass.isAnnotationType
        }

        override fun isEnum(): Boolean {
            return psiClass.isEnum
        }

        @ScriptIgnore
        override fun asPsiModifierListOwner(): PsiModifierListOwner? {
            return psiClass
        }

        override fun getName(): String? {
            return actionContext.callInReadUI { psiClass.qualifiedName }
        }

        override fun getSimpleName(): String? {
            return actionContext.callInReadUI { psiClass.name }
        }

        override fun toObject(
            readGetter: Boolean, readSetter: Boolean,
            readComment: Boolean,
        ): Any? {
            return jvmClassHelper!!.resolveClassToType(psiClass)?.let {
                psiClassHelper!!.getTypeObject(
                    it, psiClass, JsonOption.NONE.or(readGetter, readSetter, readComment)
                )
            } ?: psiClassHelper!!.getFields(psiClass, JsonOption.NONE.or(readGetter, readSetter, readComment))
        }

        override fun superClass(): ScriptClassContext? {
            if (psiClass.isInterface || psiClass.isAnnotationType || psiClass.isEnum) {
                return null
            }
            extends()?.takeIf { it.isNotEmpty() }?.get(0)?.let {
                return it
            }
            return psiClass.superClass?.let { ScriptPsiClassContext(it) }
        }

        /**
         * Returns the list of classes that this class or interface extends.
         *
         * @return the extends list, or null for anonymous classes.
         */
        override fun extends(): Array<ScriptClassContext>? {
            return psiClass.extendsList?.referencedTypes?.mapToTypedArray {
                ScriptPsiTypeContext(it)
            }
        }

        /**
         * Returns the list of interfaces that this class implements.
         *
         * @return the implements list, or null for anonymous classes
         */
        override fun implements(): Array<ScriptClassContext>? {
            return psiClass.implementsList?.referencedTypes?.mapToTypedArray {
                ScriptPsiTypeContext(it)
            }
        }

        override fun toString(): String {
            return name()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ScriptClassContext) return false
            return toString() == other.toString()
        }

        override fun hashCode(): Int {
            return toString().hashCode()
        }
    }

    @ScriptTypeName("class")
    inner class ScriptExplicitClassContext(private val explicitClass: ExplicitClass) :
        ScriptPsiClassContext(explicitClass.psi()) {

        override fun methods(): Array<ScriptPsiMethodContext> {
            val methods = explicitClass.methods()
            if (methods.isEmpty()) return emptyArray()
            return methods.mapToTypedArray { ScriptExplicitMethodContext(it) }
        }

        override fun fields(): Array<ScriptPsiFieldContext> {
            val fields = explicitClass.fields()
            if (fields.isEmpty()) return emptyArray()
            return fields.mapToTypedArray { ScriptExplicitFieldContext(it) }
        }

        override fun toObject(
            readGetter: Boolean, readSetter: Boolean,
            readComment: Boolean,
        ): Any? {
            return psiClassHelper!!.getTypeObject(
                explicitClass.asDuckType(), psiClass, JsonOption.NONE.or(readGetter, readSetter, readComment)
            ) ?: super.toObject(readGetter, readSetter, readComment)
        }

        /**
         * Returns the list of classes that this class or interface extends.
         *
         * @return the extends list, or null for anonymous classes.
         */
        override fun extends(): Array<ScriptClassContext>? {
            return explicitClass.extends()?.mapToTypedArray {
                ScriptExplicitClassContext(it)
            }
        }

        /**
         * Returns the list of interfaces that this class implements.
         *
         * @return the implements list, or null for anonymous classes
         */
        override fun implements(): Array<ScriptClassContext>? {
            return explicitClass.implements()?.mapToTypedArray {
                ScriptExplicitClassContext(it)
            }
        }

        @ScriptIgnore
        override fun getCore(): Any {
            return explicitClass
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ScriptClassContext) return false
            return toString() == other.toString()
        }

        override fun hashCode(): Int {
            return toString().hashCode()
        }
    }

    /**
     * it.type:class
     * it.containingClass:class
     * it.jsonName:String
     */
    @ScriptTypeName("field")
    open inner class ScriptPsiFieldContext(private val psiField: PsiField) : BaseScriptRuleContext(psiField) {
        override fun contextType(): String {
            return "field"
        }

        open fun type(): ScriptClassContext {
            return ScriptPsiTypeContext(psiField.type)
        }

        @ScriptIgnore
        override fun asPsiModifierListOwner(): PsiModifierListOwner? {
            return psiField
        }

        override fun getName(): String? {
            return psiField.name
        }

        /**
         * Returns the class containing the member.
         *
         * @return the containing class.
         */
        open fun containingClass(): ScriptPsiClassContext {
            return ScriptPsiClassContext(psiField.containingClass!!)
        }

        /**
         * Returns the class define the member.
         *
         * @return the defining class.
         */
        open fun defineClass(): ScriptPsiClassContext {
            return containingClass()
        }

        /**
         * attention:it should not be used in [json.rule.field.name]
         */
        fun jsonName(): String {
            return psiClassHelper!!.getJsonFieldName(psiField)
        }

        fun jsonType(): ScriptPsiTypeContext {
            return ScriptPsiTypeContext(classRuleConfig!!.tryConvert(psiField.type, psiField))
        }

        override fun toString(): String {
            return containingClass().name() + "#" + psiField.name
        }
    }

    @ScriptTypeName("field")
    inner class ScriptExplicitFieldContext(private val explicitField: ExplicitField) :
        ScriptPsiFieldContext(explicitField.psi()) {

        override fun containingClass(): ScriptPsiClassContext {
            return ScriptExplicitClassContext(explicitField.containClass())
        }

        override fun defineClass(): ScriptPsiClassContext {
            return ScriptExplicitClassContext(explicitField.defineClass())
        }

        override fun type(): ScriptClassContext {
            return ScriptDuckTypeContext(explicitField.getType(), explicitField.psi())
        }

        @ScriptIgnore
        override fun getCore(): Any {
            return explicitField
        }
    }

    /**
     * it.returnType:class
     * it.isVarArgs:Boolean
     * it.args:arg[]
     * it.argTypes:class[]
     * it.argCnt:int
     * it.containingClass:class
     */
    @ScriptTypeName("method")
    open inner class ScriptPsiMethodContext(protected val psiMethod: PsiMethod) : BaseScriptRuleContext(psiMethod) {

        override fun contextType(): String {
            return "method"
        }

        /**
         * Returns the return type of the method.
         *
         * @return the method return type, or null if the method is a constructor.
         */
        open fun returnType(): ScriptClassContext? {
            return psiMethod.getResolvedReturnType()?.let { ScriptPsiTypeContext(it) }
        }

        /**
         * Checks if the method accepts a variable number of arguments.
         *
         * @return true if the method is varargs, false otherwise.
         */
        fun isVarArgs(): Boolean {
            return psiMethod.isVarArgs
        }

        /**
         * Returns the array of method parameters
         */
        open fun args(): Array<ScriptPsiParameterContext> {
            return psiMethod.parameterList.parameters.mapToTypedArray { ScriptPsiParameterContext(it) }
        }

        /**
         * Returns the array of method parameters type
         */
        open fun argTypes(): Array<ScriptClassContext> {
            return psiMethod.parameterList.parameters.mapToTypedArray { ScriptPsiTypeContext(it.type) }
        }

        /**
         * Returns the number of method parameters
         */
        fun argCnt(): Int {
            return psiMethod.parameterList.parametersCount
        }

        /**
         * Returns the class containing the member.
         *
         * @return the containing class.
         */
        open fun containingClass(): ScriptPsiClassContext {
            return ScriptPsiClassContext(psiMethod.containingClass!!)
        }

        /**
         * Returns the class define the member.
         *
         * @return the defining class.
         */
        open fun defineClass(): ScriptPsiClassContext {
            return containingClass()
        }

        @ScriptIgnore
        override fun asPsiModifierListOwner(): PsiModifierListOwner? {
            return psiMethod
        }

        override fun getName(): String? {
            return actionContext.callInReadUI { psiMethod.name }
        }

        fun jsonName(): String {
            return psiClassHelper!!.getJsonFieldName(psiMethod)
        }

        fun type(): ScriptClassContext? {
            return returnType()
        }

        fun jsonType(): ScriptPsiTypeContext? {
            return psiMethod.returnType?.let { classRuleConfig!!.tryConvert(it, psiMethod) }
                ?.let { ScriptPsiTypeContext(it) }
        }

        open fun returnObject(
            needInfer: Boolean = false, readGetter: Boolean = true,
            readSetter: Boolean = true,
        ): Any? {
            val psiType = psiMethod.getResolvedReturnType() ?: return null
            return when {
                needInfer && (!duckTypeHelper!!.isQualified(
                    psiType,
                    psiMethod
                ) || PsiClassUtils.isInterface(psiType)) -> {
                    logger!!.info("try infer return type of method[" + PsiClassUtils.fullNameOfMethod(psiMethod) + "]")
                    methodReturnInferHelper!!.inferReturn(psiMethod)
                }

                else -> psiClassHelper!!.getTypeObject(
                    psiType, psiMethod, JsonOption.NONE.or(readGetter, readSetter)
                )
            }
        }

        open fun returnJson(
            needInfer: Boolean = false, readGetter: Boolean = true,
            readSetter: Boolean = true,
        ): String? {
            val psiType = psiMethod.getResolvedReturnType() ?: return null
            return when {
                needInfer && (!duckTypeHelper!!.isQualified(
                    psiType,
                    psiMethod
                ) || PsiClassUtils.isInterface(psiType)) -> {
                    logger!!.info("try infer return type of method[" + PsiClassUtils.fullNameOfMethod(psiMethod) + "]")
                    methodReturnInferHelper!!.inferReturn(psiMethod)
                }

                else -> psiClassHelper!!.getTypeObject(
                    psiType, psiMethod, JsonOption.NONE.or(readGetter, readSetter)
                )
            }?.let { RequestUtils.parseRawBody(it) }
        }

        override fun toString(): String {
            return containingClass().name() + "#" + psiMethod.name
        }
    }

    @ScriptTypeName("method")
    inner class ScriptExplicitMethodContext(private val explicitMethod: ExplicitMethod) :
        ScriptPsiMethodContext(explicitMethod.psi()) {

        override fun returnType(): ScriptClassContext? {
            return explicitMethod.getReturnType()?.let { ScriptDuckTypeContext(it) }
        }

        override fun returnObject(needInfer: Boolean, readGetter: Boolean, readSetter: Boolean): Any? {
            val duckType = explicitMethod.getReturnType() ?: return null
            return when {
                needInfer && !duckTypeHelper!!.isQualified(duckType) -> {
                    logger!!.info("try infer return type of method[" + PsiClassUtils.fullNameOfMethod(psiMethod) + "]")
                    methodReturnInferHelper!!.inferReturn(psiMethod)
                }

                else -> psiClassHelper!!.getTypeObject(
                    duckType, psiMethod, JsonOption.NONE.or(readGetter, readSetter)
                )
            }
        }

        override fun returnJson(
            needInfer: Boolean, readGetter: Boolean,
            readSetter: Boolean,
        ): String? {
            val duckType = explicitMethod.getReturnType() ?: return null
            return when {
                needInfer && !duckTypeHelper!!.isQualified(duckType) -> {
                    logger!!.info("try infer return type of method[" + PsiClassUtils.fullNameOfMethod(psiMethod) + "]")
                    methodReturnInferHelper!!.inferReturn(psiMethod)
                }

                else -> psiClassHelper!!.getTypeObject(
                    duckType, psiMethod, JsonOption.NONE.or(readGetter, readSetter)
                )
            }?.let { RequestUtils.parseRawBody(it) }
        }

        override fun args(): Array<ScriptPsiParameterContext> {
            val parameters = explicitMethod.getParameters()
            if (parameters.isEmpty()) return emptyArray()
            return parameters.mapToTypedArray { ScriptExplicitParameterContext(it) }
        }

        override fun argTypes(): Array<ScriptClassContext> {
            val parameters = explicitMethod.getParameters()
            if (parameters.isEmpty()) return emptyArray()
            return parameters.mapToTypedArray { ScriptDuckTypeContext(it.getType()!!, it.psi()) }
        }

        override fun containingClass(): ScriptPsiClassContext {
            return ScriptExplicitClassContext(explicitMethod.containClass())
        }

        override fun defineClass(): ScriptPsiClassContext {
            return ScriptExplicitClassContext(explicitMethod.defineClass())
        }

        @ScriptIgnore
        override fun getCore(): Any {
            return explicitMethod
        }
    }

    /**
     * it.name:String
     * it.type:class
     * it.isVarArgs:Boolean
     */
    @ScriptTypeName("arg")
    open inner class ScriptPsiParameterContext(private val psiParameter: PsiParameter) :
        BaseScriptRuleContext(psiParameter) {
        override fun contextType(): String {
            return "param"
        }

        open fun type(): ScriptClassContext {
            return ScriptPsiTypeContext(psiParameter.type)
        }

        /**
         * Checks if the parameter accepts a variable number of arguments.
         *
         * @return true if the parameter is a vararg, false otherwise
         */
        fun isVarArgs(): Boolean {
            return psiParameter.isVarArgs
        }

        override fun getName(): String? {
            return actionContext.callInReadUI { psiParameter.name }
        }

        @ScriptIgnore
        override fun asPsiModifierListOwner(): PsiModifierListOwner? {
            return psiParameter
        }

        /**
         * Returns the method which declare the param.
         * May be null
         */
        open fun method(): ScriptPsiMethodContext? {
            return declaration() as? ScriptPsiMethodContext
        }

        /**
         * Returns the element which declare the param.
         */
        open fun declaration(): RuleContext {
            return psiParameter.declarationScope.let {
                contextOf(it, it)
            }
        }

        override fun toString(): String {
            return name()
        }
    }

    @ScriptTypeName("arg")
    inner class ScriptExplicitParameterContext(private val explicitParam: ExplicitParameter) :
        ScriptPsiParameterContext(explicitParam.psi()) {

        override fun type(): ScriptClassContext {
            return explicitParam.getType()?.let { ScriptDuckTypeContext(it) } ?: super.type()
        }

        @ScriptIgnore
        override fun getCore(): Any {
            return explicitParam
        }

        override fun method(): ScriptPsiMethodContext {
            return ScriptExplicitMethodContext(explicitParam.containMethod())
        }

        override fun declaration(): RuleContext {
            return ScriptExplicitMethodContext(explicitParam.containMethod())
        }
    }

    @ScriptTypeName("class")
    abstract inner class ScriptClassContext : BaseScriptRuleContext {
        constructor(psiElement: PsiElement) : super(psiElement)
        constructor() : super()

        abstract fun methods(): Array<ScriptPsiMethodContext>

        abstract fun fields(): Array<ScriptPsiFieldContext>

        abstract fun isExtend(superClass: String): Boolean

        abstract fun isMap(): Boolean

        abstract fun isCollection(): Boolean

        abstract fun isArray(): Boolean

        /**
         * Returns whether this class is a primitive
         */
        abstract fun isPrimitive(): Boolean

        /**
         * Returns whether this class is a primitive wrapper
         */
        abstract fun isPrimitiveWrapper(): Boolean

        /**
         * Returns whether the given {@code type} is a primitive or primitive wrapper
         * or {@code String}、{@code Object}
         */
        abstract fun isNormalType(): Boolean

        /**
         * Checks if the class is an interface.
         *
         * @return true if the class is an interface, false otherwise.
         */
        abstract fun isInterface(): Boolean

        /**
         * Checks if the class is an annotation type.
         *
         * @return true if the class is an annotation type, false otherwise
         */
        abstract fun isAnnotationType(): Boolean

        /**
         * Checks if the class is an enumeration.
         *
         * @return true if the class is an enumeration, false otherwise.
         */
        abstract fun isEnum(): Boolean

        abstract fun fieldCnt(): Int

        abstract fun methodCnt(): Int

        fun toObject(): Any? {
            return toObject(readGetter = true, readSetter = true, readComment = false)
        }

        abstract fun toObject(
            readGetter: Boolean, readSetter: Boolean,
            readComment: Boolean,
        ): Any?

        fun toJson(): String? {
            return toJson(readGetter = true, readSetter = true)
        }

        fun toJson(readGetter: Boolean, readSetter: Boolean): String? {
            return toObject(readGetter, readSetter, false)
                ?.let { RequestUtils.parseRawBody(it) }
        }

        fun toJson5(): String? {
            return toJson5(false, false)
        }

        fun toJson5(readGetter: Boolean, readSetter: Boolean): String? {
            return toObject(readGetter, readSetter, true)?.let {
                actionContext.instance(Json5Formatter::class).format(it)
            }
        }

        /**
         * Returns the base class of this class.
         * If this class represents either the
         * [Object] class, an interface, a primitive type, or void, then
         * null is returned.
         * {class A extend B} -> B
         * {class A} -> java.lang.Object
         * {class A implement IA} -> java.lang.Object
         * {class A extend B implement IA} -> B
         * {interface IA} -> null
         * {interface IA extend IB} -> null
         *
         * @return the base class. May return null when jdk is not configured, so no java.lang.Object is found,
         * or for java.lang.Object itself
         */
        abstract fun superClass(): ScriptClassContext?

        /**
         * Returns the list of classes that this class or interface extends.
         *
         * @return the extends list, or null for anonymous classes.
         */
        abstract fun extends(): Array<ScriptClassContext>?

        /**
         * Returns the list of interfaces that this class implements.
         *
         * @return the implements list, or null for anonymous classes
         */
        abstract fun implements(): Array<ScriptClassContext>?

    }

    /**
     * it.methods():method[]
     * it.isExtend(""):Boolean
     * it.isSuper(""):Boolean
     * it.isMap():Boolean
     * it.isCollection():Boolean
     * it.isArray():Boolean
     * @see ScriptPsiClassContext
     */
    @ScriptTypeName("class")
    inner class ScriptPsiTypeContext(private val psiType: PsiType) : ScriptClassContext() {

        override fun contextType(): String {
            return "class"
        }

        @ScriptIgnore
        override fun getPsiContext(): PsiElement? {
            return getResource() ?: jvmClassHelper!!.resolveClassInType(psiType)
        }

        private var duckType: DuckType? = null

        override fun getName(): String? {
            return duckType?.canonicalText()
        }

        override fun getSimpleName(): String? {
            return getDuckTypeSimpleName(duckType)
        }

        private fun getDuckTypeSimpleName(duckType: DuckType?): String? {
            return when (duckType) {
                null -> null
                is SingleUnresolvedDuckType -> duckType.psiType().presentableText
                is SingleDuckType -> duckType.psiClass().name
                is ArrayDuckType -> getDuckTypeSimpleName(duckType.componentType()) + "[]"
                else -> duckType.toString()
            }
        }

        override fun methods(): Array<ScriptPsiMethodContext> {
            return (getResource() as? PsiClass)?.let { psiElement ->
                return@let jvmClassHelper!!.getAllMethods(psiElement).mapToTypedArray { ScriptPsiMethodContext(it) }
            } ?: emptyArray()
        }

        override fun fields(): Array<ScriptPsiFieldContext> {
            return (getResource() as? PsiClass)?.let { psiElement ->
                return@let jvmClassHelper!!.getAllFields(psiElement).mapToTypedArray { ScriptPsiFieldContext(it) }
            } ?: emptyArray()
        }

        override fun isExtend(superClass: String): Boolean {
            return jvmClassHelper!!.isInheritor(psiType, superClass)
        }

        override fun isMap(): Boolean {
            return jvmClassHelper!!.isMap(psiType)
        }

        override fun isCollection(): Boolean {
            return jvmClassHelper!!.isCollection(psiType)
        }

        override fun isArray(): Boolean {
            return duckType is ArrayDuckType
        }

        /**
         * Returns whether the given {@code type} is a primitive or primitive wrapper
         * or {@code String}、{@code Object}
         */
        override fun isNormalType(): Boolean {
            return jvmClassHelper!!.isNormalType(name())
        }

        override fun isInterface(): Boolean {
            return (getResource() as? PsiClass)?.isInterface ?: false
        }

        override fun isAnnotationType(): Boolean {
            return (getResource() as? PsiClass)?.isAnnotationType ?: false
        }

        override fun isEnum(): Boolean {
            return (getResource() as? PsiClass)?.isEnum ?: false
        }

        /**
         * Returns whether this class is a primitive
         */
        override fun isPrimitive(): Boolean {
            return jvmClassHelper!!.isPrimitive(name())
        }

        /**
         * Returns whether this class is a primitive wrapper
         */
        override fun isPrimitiveWrapper(): Boolean {
            return jvmClassHelper!!.isPrimitiveWrapper(name())
        }

        override fun fieldCnt(): Int {
            return (getResource() as? PsiClass)?.let { psiElement ->
                return@let jvmClassHelper!!.getAllFields(psiElement).mapToTypedArray { ScriptPsiFieldContext(it) }
            }?.size ?: 0
        }

        override fun methodCnt(): Int {
            return (getResource() as? PsiClass)?.let { psiElement ->
                return@let jvmClassHelper!!.getAllMethods(psiElement).mapToTypedArray { ScriptPsiMethodContext(it) }
            }?.size ?: 0
        }

        override fun toObject(
            readGetter: Boolean,
            readSetter: Boolean,
            readComment: Boolean,
        ): Any? {
            return psiClassHelper!!.getTypeObject(
                psiType, getResource()!!, JsonOption.NONE.or(readGetter, readSetter, readComment)
            )
        }

        override fun superClass(): ScriptClassContext? {
            val psiClass = getResource() as? PsiClass ?: return null
            if (psiClass.isInterface || psiClass.isAnnotationType || psiClass.isEnum) {
                return null
            }
            extends()?.takeIf { it.isNotEmpty() }?.get(0)?.let {
                return it
            }
            return psiClass.superClass?.let { ScriptPsiClassContext(it) }
        }

        /**
         * Returns the list of classes that this class or interface extends.
         *
         * @return the extends list, or null for anonymous classes.
         */
        override fun extends(): Array<ScriptClassContext>? {
            val duckType = duckTypeHelper!!.resolve(psiType, getPsiContext()!!) as? SingleDuckType ?: return null
            return duckTypeHelper.explicit(duckType).extends()?.mapToTypedArray {
                ScriptExplicitClassContext(it)
            }
        }

        /**
         * Returns the list of interfaces that this class implements.
         *
         * @return the implements list, or null for anonymous classes
         */
        override fun implements(): Array<ScriptClassContext>? {
            val duckType = duckTypeHelper!!.resolve(psiType, getPsiContext()!!) as? SingleDuckType ?: return null
            return duckTypeHelper.explicit(duckType).implements()?.mapToTypedArray {
                ScriptExplicitClassContext(it)
            }
        }

        override fun toString(): String {
            return name()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ScriptClassContext) return false
            return toString() == other.toString()
        }

        override fun hashCode(): Int {
            return toString().hashCode()
        }

        init {
            duckType = duckTypeHelper!!.ensureType(psiType)
            if (duckType != null && duckType is SingleDuckType) {
                this.psiElement = (duckType as SingleDuckType).psiClass()
            }
        }
    }

    /**
     * it.methods():method[]
     * it.isExtend(""):Boolean
     * it.isSuper(""):Boolean
     * it.isMap():Boolean
     * it.isCollection():Boolean
     * it.isArray():Boolean
     * @see ScriptPsiClassContext
     */
    @ScriptTypeName("class")
    inner class ScriptDuckTypeContext : ScriptClassContext {

        private val duckType: DuckType

        constructor(duckType: DuckType) : super() {
            this.duckType = duckType
            if (duckType is SingleDuckType) {
                this.psiElement = duckType.psiClass()
            }
        }

        constructor(duckType: DuckType, psiElement: PsiElement) : super(psiElement) {
            this.duckType = duckType
        }

        override fun contextType(): String {
            return "class"
        }

        @ScriptIgnore
        override fun getPsiContext(): PsiElement? {
            return getResource()
        }

        @ScriptIgnore
        override fun getCore(): Any {
            return duckType
        }

        override fun getName(): String {
            return duckType.canonicalText()
        }

        override fun getSimpleName(): String? {
            return getDuckTypeSimpleName(duckType)
        }

        private fun getDuckTypeSimpleName(duckType: DuckType?): String? {
            return when (duckType) {
                null -> null
                is SingleUnresolvedDuckType -> duckType.psiType().presentableText
                is SingleDuckType -> duckType.psiClass().name
                is ArrayDuckType -> getDuckTypeSimpleName(duckType.componentType()) + "[]"
                else -> duckType.toString()
            }
        }

        override fun methods(): Array<ScriptPsiMethodContext> {
            return (getResource() as? PsiClass)?.let { psiElement ->
                return@let jvmClassHelper!!.getAllMethods(psiElement).mapToTypedArray { ScriptPsiMethodContext(it) }
            } ?: emptyArray()
        }

        override fun fields(): Array<ScriptPsiFieldContext> {
            return (getResource() as? PsiClass)?.let { psiElement ->
                return@let jvmClassHelper!!.getAllFields(psiElement).mapToTypedArray { ScriptPsiFieldContext(it) }
            } ?: emptyArray()
        }

        override fun isExtend(superClass: String): Boolean {
            return jvmClassHelper!!.isInheritor(duckType, superClass)
        }

        override fun isMap(): Boolean {
            return jvmClassHelper!!.isMap(duckType)
        }

        override fun isCollection(): Boolean {
            return jvmClassHelper!!.isCollection(duckType)
        }

        override fun isArray(): Boolean {
            return duckType is ArrayDuckType
        }

        /**
         * Returns whether the given {@code type} is a primitive or primitive wrapper
         * or {@code String}、{@code Object}
         */
        override fun isNormalType(): Boolean {
            return jvmClassHelper!!.isNormalType(name())
        }

        override fun isInterface(): Boolean {
            return duckType is SingleDuckType && duckType.psiClass().isInterface
        }

        override fun isAnnotationType(): Boolean {
            return duckType is SingleDuckType && duckType.psiClass().isAnnotationType
        }

        override fun isEnum(): Boolean {
            return duckType is SingleDuckType && duckType.psiClass().isEnum
        }

        /**
         * Returns whether this class is a primitive
         */
        override fun isPrimitive(): Boolean {
            return jvmClassHelper!!.isPrimitive(name())
        }

        /**
         * Returns whether this class is a primitive wrapper
         */
        override fun isPrimitiveWrapper(): Boolean {
            return jvmClassHelper!!.isPrimitiveWrapper(name())
        }

        override fun fieldCnt(): Int {
            return (getResource() as? PsiClass)?.let { psiElement ->
                return@let jvmClassHelper!!.getAllFields(psiElement).mapToTypedArray { ScriptPsiFieldContext(it) }
            }?.size ?: 0
        }

        override fun methodCnt(): Int {
            return (getResource() as? PsiClass)?.let { psiElement ->
                return@let jvmClassHelper!!.getAllMethods(psiElement).mapToTypedArray { ScriptPsiMethodContext(it) }
            }?.size ?: 0
        }

        override fun toObject(
            readGetter: Boolean,
            readSetter: Boolean,
            readComment: Boolean,
        ): Any? {
            val resource: PsiElement = getResource() ?: return null
            return psiClassHelper!!.getTypeObject(
                duckType, resource, JsonOption.NONE.or(
                    readGetter,
                    readSetter,
                    readComment
                )
            )
        }

        override fun superClass(): ScriptClassContext? {
            val duckType = this.duckType as? SingleDuckType ?: return null
            val psiClass = duckType.psiClass()
            if (psiClass.isInterface || psiClass.isAnnotationType || psiClass.isEnum) {
                return null
            }
            extends()?.takeIf { it.isNotEmpty() }?.get(0)?.let {
                return it
            }
            return psiClass.superClass?.let { ScriptPsiClassContext(it) }
        }

        override fun toString(): String {
            return name()
        }

        /**
         * Returns the list of classes that this class or interface extends.
         *
         * @return the extends list, or null for anonymous classes.
         */
        override fun extends(): Array<ScriptClassContext>? {
            val duckType = this.duckType as? SingleDuckType ?: return null
            return duckTypeHelper!!.explicit(duckType).extends()?.mapToTypedArray {
                ScriptExplicitClassContext(it)
            }
        }

        /**
         * Returns the list of interfaces that this class implements.
         *
         * @return the implements list, or null for anonymous classes
         */
        override fun implements(): Array<ScriptClassContext>? {
            val duckType = this.duckType as? SingleDuckType ?: return null
            return duckTypeHelper!!.explicit(duckType).implements()?.mapToTypedArray {
                ScriptExplicitClassContext(it)
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ScriptClassContext) return false
            return toString() == other.toString()
        }

        override fun hashCode(): Int {
            return toString().hashCode()
        }
    }
}

private fun Int.or(
    readGetter: Boolean,
    readSetter: Boolean,
    readComment: Boolean = false,
): Int {
    var option = this
    if (readGetter) {
        option = option or JsonOption.READ_GETTER
    }
    if (readSetter) {
        option = option or JsonOption.READ_SETTER
    }
    if (readComment) {
        option = option or JsonOption.READ_COMMENT
    }
    return option
}