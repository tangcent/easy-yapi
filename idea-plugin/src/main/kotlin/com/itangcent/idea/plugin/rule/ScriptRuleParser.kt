package com.itangcent.idea.plugin.rule

import com.google.inject.Inject
import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil
import com.itangcent.annotation.script.ScriptIgnore
import com.itangcent.annotation.script.ScriptTypeName
import com.itangcent.common.logger.traceError
import com.itangcent.common.utils.SimpleExtensible
import com.itangcent.common.utils.asBool
import com.itangcent.common.utils.mapToTypedArray
import com.itangcent.http.RequestUtils
import com.itangcent.idea.plugin.api.MethodInferHelper
import com.itangcent.idea.plugin.json.Json5Formatter
import com.itangcent.intellij.config.rule.*
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.getPropertyValue
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
import com.itangcent.intellij.psi.JsonOption
import com.itangcent.intellij.psi.PsiClassUtils
import javax.script.ScriptContext
import javax.script.ScriptEngine
import javax.script.SimpleScriptContext

abstract class ScriptRuleParser : RuleParser {

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
    protected val logger: Logger? = null

    override fun parseBooleanRule(rule: String): BooleanRule? {
        return BooleanRule.of { context ->
            return@of eval(rule, context).asBool()
        }
    }

    override fun parseStringRule(rule: String): StringRule? {
        return StringRule.of { context ->
            return@of eval(rule, context)?.toPrettyString()
        }
    }

    override fun parseEventRule(rule: String): EventRule? {
        return EventRule.of {
            eval(rule, it)
        }
    }

    private fun eval(ruleScript: String, context: RuleContext): Any? {
        return try {
            val simpleScriptContext = SimpleScriptContext()


            context.exts()?.forEach {
                simpleScriptContext.setAttribute(it.key, it.value, ScriptContext.ENGINE_SCOPE)
            }

            val contextForScript: RuleContext? = (context as? BaseScriptRuleContext)
                    ?: context.getCore()?.let { contextOf(it, context.getResource()) }
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

    /**
     * support is js:
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
    open inner class BaseScriptRuleContext : SimpleExtensible, RuleContext {

        protected var psiElement: PsiElement? = null

        constructor(psiElement: PsiElement) {
            this.psiElement = psiElement
        }

        constructor()

        @ScriptIgnore
        override fun getResource(): PsiElement? {
            return psiElement
        }

        override fun getName(): String? {
            return psiElement!!.getPropertyValue("name")?.toString()
        }

        @ScriptIgnore
        override fun asPsiDocCommentOwner(): PsiDocCommentOwner? {
            if (psiElement is PsiDocCommentOwner) {
                return psiElement as PsiDocCommentOwner
            }
            return null
        }

        @ScriptIgnore
        override fun asPsiModifierListOwner(): PsiModifierListOwner? {
            if (psiElement is PsiModifierListOwner) {
                return psiElement as PsiModifierListOwner
            }
            return null
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
            return annotationHelper!!.findAnnMap(getResource(), name)
        }

        /**
         * it.annMaps("annotation_name"):List<Map<String, Any?>>?
         */
        fun annMaps(name: String): List<Map<String, Any?>>? {
            return annotationHelper!!.findAnnMaps(getResource(), name)
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
            return annotationHelper!!.findAttr(getResource(), name, attr)
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
            return docHelper!!.findDocsByTagAndName(getResource(),
                    tag, subTag)
        }

        fun hasModifier(modifier: String): Boolean {
            return asPsiModifierListOwner()?.hasModifierProperty(modifier) ?: false
        }

        fun modifiers(): List<String>? {
            return psiElement?.let { jvmClassHelper!!.extractModifiers(it) }
        }

        fun sourceCode(): String? {
            return psiElement?.text
        }

        fun defineCode(): String? {
            return psiElement?.let { jvmClassHelper!!.defineCode(it) }
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
    open inner class ScriptPsiClassContext(protected val psiClass: PsiClass)
        : BaseScriptRuleContext(psiClass), ScriptClassContext {

        override fun contextType(): String {
            return "class"
        }

        open fun methods(): Array<ScriptPsiMethodContext> {
            return jvmClassHelper!!.getAllMethods(psiClass)
                    .mapToTypedArray { ScriptPsiMethodContext(it) }
        }

        override fun methodCnt(): Int {
            return jvmClassHelper!!.getAllMethods(psiClass).size
        }

        open fun fields(): Array<ScriptPsiFieldContext> {
            return jvmClassHelper!!.getAllFields(psiClass)
                    .mapToTypedArray { ScriptPsiFieldContext(it) }
        }

        override fun fieldCnt(): Int {
            return jvmClassHelper!!.getAllFields(psiClass).size
        }

        override fun isExtend(superClass: String): Boolean {
            return jvmClassHelper!!.isInheritor(psiClass, superClass)
        }

        override fun isMap(): Boolean {
            return jvmClassHelper!!.isMap(PsiTypesUtil.getClassType(psiClass))
        }

        override fun isCollection(): Boolean {
            return jvmClassHelper!!.isCollection(PsiTypesUtil.getClassType(psiClass))
        }

        override fun isArray(): Boolean {
            return psiClass.qualifiedName?.endsWith("[]") ?: false
        }

        override fun isNormalType(): Boolean {
            return jvmClassHelper!!.isNormalType(name())
        }

        @ScriptIgnore
        override fun asPsiModifierListOwner(): PsiModifierListOwner? {
            return psiClass
        }

        override fun getName(): String? {
            return psiClass.qualifiedName
        }

        override fun getSimpleName(): String? {
            return psiClass.name
        }

        override fun toJson(readGetter: Boolean): String? {
            val option = if (readGetter) JsonOption.READ_GETTER else JsonOption.NONE
            return (jvmClassHelper!!.resolveClassToType(psiClass)?.let {
                psiClassHelper!!.getTypeObject(it, psiClass, option)
            } ?: psiClassHelper!!.getFields(psiClass)).let { RequestUtils.parseRawBody(it) }
        }

        override fun toJson5(readGetter: Boolean): String? {
            val option = if (readGetter) JsonOption.ALL else JsonOption.READ_COMMENT
            return (jvmClassHelper!!.resolveClassToType(psiClass)?.let {
                psiClassHelper!!.getTypeObject(it, psiClass, option)
            } ?: psiClassHelper!!.getFields(psiClass)).let {
                ActionContext.getContext()!!.instance(Json5Formatter::class).format(it)
            }
        }

        override fun toString(): String {
            return name()
        }
    }

    @ScriptTypeName("class")
    inner class ScriptExplicitClassContext(private val explicitClass: ExplicitClass) : ScriptPsiClassContext(explicitClass.psi()) {

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

        override fun toJson(readGetter: Boolean): String? {
            val option = if (readGetter) JsonOption.READ_GETTER else JsonOption.NONE
            return psiClassHelper!!.getTypeObject(explicitClass.asDuckType(), psiClass, option)
                    ?.let { RequestUtils.parseRawBody(it) }
        }

        override fun toJson5(readGetter: Boolean): String? {
            val option = if (readGetter) JsonOption.ALL else JsonOption.READ_COMMENT
            return psiClassHelper!!.getTypeObject(explicitClass.asDuckType(), psiClass, option)
                    ?.let { ActionContext.getContext()!!.instance(Json5Formatter::class).format(it) }
        }

        @ScriptIgnore
        override fun getCore(): Any? {
            return explicitClass
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
        fun containingClass(): ScriptPsiClassContext {
            return ScriptPsiClassContext(psiField.containingClass!!)
        }

        /**
         * attention:it should not be used in [json.rule.field.name]
         */
        fun jsonName(): String? {
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
    inner class ScriptExplicitFieldContext(private val explicitField: ExplicitField) : ScriptPsiFieldContext(explicitField.psi()) {

        override fun type(): ScriptClassContext {
            return ScriptDuckTypeContext(explicitField.getType(), explicitField.psi())
        }

        @ScriptIgnore
        override fun getCore(): Any? {
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
            return (psiElement as PsiMethod).returnType?.let { ScriptPsiTypeContext(it) }
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

        @ScriptIgnore
        override fun asPsiModifierListOwner(): PsiModifierListOwner? {
            return psiMethod
        }

        override fun getName(): String? {
            return psiMethod.name
        }

        fun jsonName(): String? {
            return psiClassHelper!!.getJsonFieldName(psiMethod)
        }

        fun type(): ScriptClassContext? {
            return returnType()
        }

        fun jsonType(): ScriptPsiTypeContext? {
            return psiMethod.returnType?.let { classRuleConfig!!.tryConvert(it, psiMethod) }
                    ?.let { ScriptPsiTypeContext(it) }
        }

        open fun returnJson(needInfer: Boolean = false, readGetter: Boolean = true): String? {
            val psiType = psiMethod.returnType ?: return null
            return when {
                needInfer && (!duckTypeHelper!!.isQualified(psiType, psiMethod) ||
                        PsiClassUtils.isInterface(psiType)) -> {
                    logger!!.info("try infer return type of method[" + PsiClassUtils.fullNameOfMethod(psiMethod) + "]")
                    methodReturnInferHelper!!.inferReturn(psiMethod)
                }
                readGetter -> psiClassHelper!!.getTypeObject(psiType, psiMethod, JsonOption.READ_GETTER)
                else -> psiClassHelper!!.getTypeObject(psiType, psiMethod, JsonOption.NONE)
            }?.let { RequestUtils.parseRawBody(it) }
        }

        override fun toString(): String {
            return containingClass().name() + "#" + psiMethod.name
        }
    }

    @ScriptTypeName("method")
    inner class ScriptExplicitMethodContext(private val explicitMethod: ExplicitMethod) : ScriptPsiMethodContext(explicitMethod.psi()) {

        override fun returnType(): ScriptClassContext? {
            return explicitMethod.getReturnType()?.let { ScriptDuckTypeContext(it) }
        }

        override fun returnJson(needInfer: Boolean, readGetter: Boolean): String? {
            val duckType = explicitMethod.getReturnType() ?: return null
            return when {
                needInfer && (!duckTypeHelper!!.isQualified(duckType) ||
                        jvmClassHelper!!.isInterface(duckType)) -> {
                    logger!!.info("try infer return type of method[" + PsiClassUtils.fullNameOfMethod(psiMethod) + "]")
                    methodReturnInferHelper!!.inferReturn(psiMethod)
                }
                readGetter -> psiClassHelper!!.getTypeObject(duckType, psiMethod, JsonOption.READ_GETTER)
                else -> psiClassHelper!!.getTypeObject(duckType, psiMethod, JsonOption.NONE)
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

        @ScriptIgnore
        override fun getCore(): Any? {
            return explicitMethod
        }
    }

    /**
     * it.name:String
     * it.type:class
     * it.isVarArgs:Boolean
     */
    @ScriptTypeName("arg")
    open inner class ScriptPsiParameterContext(private val psiParameter: PsiParameter) : BaseScriptRuleContext(psiParameter) {
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
            return psiParameter.name
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
    inner class ScriptExplicitParameterContext(private val explicitParam: ExplicitParameter) : ScriptPsiParameterContext(explicitParam.psi()) {

        override fun type(): ScriptClassContext {
            return explicitParam.getType()?.let { ScriptDuckTypeContext(it) } ?: super.type()
        }

        @ScriptIgnore
        override fun getCore(): Any? {
            return explicitParam
        }

        override fun method(): ScriptPsiMethodContext? {
            return ScriptExplicitMethodContext(explicitParam.containMethod())
        }

        override fun declaration(): RuleContext {
            return ScriptExplicitMethodContext(explicitParam.containMethod())
        }
    }

    @ScriptTypeName("class")
    interface ScriptClassContext {

        fun isExtend(superClass: String): Boolean

        fun isMap(): Boolean

        fun isCollection(): Boolean

        fun isArray(): Boolean

        fun isNormalType(): Boolean

        fun fieldCnt(): Int

        fun methodCnt(): Int

        fun toJson(readGetter: Boolean): String?

        fun toJson5(readGetter: Boolean): String?
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
    inner class ScriptPsiTypeContext(private val psiType: PsiType) : BaseScriptRuleContext(), ScriptClassContext {

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

        fun methods(): Array<ScriptPsiMethodContext> {
            return (getResource() as? PsiClass)?.let { psiElement ->
                return@let jvmClassHelper!!.getAllMethods(psiElement)
                        .mapToTypedArray { ScriptPsiMethodContext(it) }
            } ?: emptyArray()
        }

        fun fields(): Array<ScriptPsiFieldContext> {
            return (getResource() as? PsiClass)?.let { psiElement ->
                return@let jvmClassHelper!!.getAllFields(psiElement)
                        .mapToTypedArray { ScriptPsiFieldContext(it) }
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

        override fun isNormalType(): Boolean {
            return jvmClassHelper!!.isNormalType(name())
        }

        override fun fieldCnt(): Int {
            return (getResource() as? PsiClass)?.let { psiElement ->
                return@let jvmClassHelper!!.getAllFields(psiElement)
                        .mapToTypedArray { ScriptPsiFieldContext(it) }
            }?.size ?: 0
        }

        override fun methodCnt(): Int {
            return (getResource() as? PsiClass)?.let { psiElement ->
                return@let jvmClassHelper!!.getAllMethods(psiElement)
                        .mapToTypedArray { ScriptPsiMethodContext(it) }
            }?.size ?: 0
        }

        override fun toJson(readGetter: Boolean): String? {
            return psiClassHelper!!.getTypeObject(psiType, getResource()!!,
                    if (readGetter) JsonOption.READ_GETTER else JsonOption.NONE
            )?.let { RequestUtils.parseRawBody(it) }
        }

        override fun toJson5(readGetter: Boolean): String? {
            return psiClassHelper!!.getTypeObject(psiType, getResource()!!,
                    if (readGetter) JsonOption.ALL else JsonOption.READ_COMMENT
            )?.let {
                ActionContext.getContext()!!.instance(Json5Formatter::class).format(it)
            }
        }

        override fun toString(): String {
            return name()
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
    inner class ScriptDuckTypeContext : BaseScriptRuleContext, ScriptClassContext {

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
        override fun getCore(): Any? {
            return duckType
        }

        override fun getName(): String? {
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

        fun methods(): Array<ScriptPsiMethodContext> {
            return (getResource() as? PsiClass)?.let { psiElement ->
                return@let jvmClassHelper!!.getAllMethods(psiElement)
                        .mapToTypedArray { ScriptPsiMethodContext(it) }
            } ?: emptyArray()
        }

        fun fields(): Array<ScriptPsiFieldContext> {
            return (getResource() as? PsiClass)?.let { psiElement ->
                return@let jvmClassHelper!!.getAllFields(psiElement)
                        .mapToTypedArray { ScriptPsiFieldContext(it) }
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

        override fun isNormalType(): Boolean {
            return jvmClassHelper!!.isNormalType(name())
        }

        override fun fieldCnt(): Int {
            return (getResource() as? PsiClass)?.let { psiElement ->
                return@let jvmClassHelper!!.getAllFields(psiElement)
                        .mapToTypedArray { ScriptPsiFieldContext(it) }
            }?.size ?: 0
        }

        override fun methodCnt(): Int {
            return (getResource() as? PsiClass)?.let { psiElement ->
                return@let jvmClassHelper!!.getAllMethods(psiElement)
                        .mapToTypedArray { ScriptPsiMethodContext(it) }
            }?.size ?: 0
        }

        override fun toJson(readGetter: Boolean): String? {
            val resource: PsiElement = getResource() ?: return null
            return psiClassHelper!!.getTypeObject(duckType, resource,
                    if (readGetter) JsonOption.READ_GETTER else JsonOption.NONE
            )?.let { RequestUtils.parseRawBody(it) }
        }

        override fun toJson5(readGetter: Boolean): String? {
            val resource: PsiElement = getResource() ?: return null
            return psiClassHelper!!.getTypeObject(duckType, resource,
                    if (readGetter) JsonOption.ALL else JsonOption.READ_COMMENT
            )?.let {
                ActionContext.getContext()!!.instance(Json5Formatter::class).format(it)
            }
        }

        override fun toString(): String {
            return name()
        }
    }
}

