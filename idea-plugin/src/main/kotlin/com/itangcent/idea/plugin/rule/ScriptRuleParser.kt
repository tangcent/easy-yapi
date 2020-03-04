package com.itangcent.idea.plugin.rule

import com.google.inject.Inject
import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil
import com.itangcent.annotation.script.ScriptIgnore
import com.itangcent.annotation.script.ScriptTypeName
import com.itangcent.idea.plugin.api.MethodInferHelper
import com.itangcent.idea.utils.RequestUtils
import com.itangcent.intellij.config.rule.*
import com.itangcent.intellij.extend.getPropertyValue
import com.itangcent.intellij.extend.toBoolean
import com.itangcent.intellij.extend.toPrettyString
import com.itangcent.intellij.jvm.*
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
            return@of eval(rule, context).toBoolean()
        }
    }

    override fun parseStringRule(rule: String): StringRule? {
        return StringRule.of { context ->
            return@of eval(rule, context)?.toPrettyString()
        }
    }

    private fun eval(ruleScript: String, context: RuleContext): Any? {
        return try {
            val simpleScriptContext = SimpleScriptContext()
            val contextForScript: RuleContext? = (context as? BaseScriptRuleContext) ?: contextOf(
                    context.getCore() ?: context.getResource()!!, context.getResource()!!)
            simpleScriptContext.setAttribute("it", contextForScript, ScriptContext.ENGINE_SCOPE)
            initScriptContext(simpleScriptContext, context)
            getScriptEngine().eval(ruleScript, simpleScriptContext)
        } catch (e: UnsupportedScriptException) {
            logger?.error("unsupported script type:${e.getType()},script:$ruleScript")
            null
        }
    }

    protected abstract fun getScriptEngine(): ScriptEngine

    protected open fun initScriptContext(scriptContext: ScriptContext, context: RuleContext) {

    }

    override fun contextOf(target: kotlin.Any, context: com.intellij.psi.PsiElement?): RuleContext {
        return when (target) {
            is PsiClass -> ScriptPsiClassContext(target)
            is PsiField -> ScriptPsiFieldContext(target)
            is PsiMethod -> ScriptPsiMethodContext(target)
            is PsiParameter -> ScriptPsiParameterContext(target)
            is PsiType -> ScriptPsiTypeContext(target)
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
    @ScriptIgnore("getResource", "asPsiDocCommentOwner", "asPsiModifierListOwner")
    open inner class BaseScriptRuleContext : RuleContext {

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
         * it.ann("annotation_name","attr"):String?
         */
        fun ann(name: String, attr: String): String? {
            return annotationHelper!!.findAttrAsString(getResource(), name, attr)
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
    inner class ScriptPsiClassContext(private val psiClass: PsiClass) : BaseScriptRuleContext(psiClass) {
        override fun contextType(): String {
            return "class"
        }

        fun methods(): Array<ScriptPsiMethodContext> {
            return jvmClassHelper!!.getAllMethods(psiClass)
                    .map { ScriptPsiMethodContext(it) }
                    .toTypedArray()
        }

        fun methodCnt(): Int {
            return jvmClassHelper!!.getAllMethods(psiClass).size
        }

        fun fields(): Array<ScriptPsiFieldContext> {
            return jvmClassHelper!!.getAllFields(psiClass)
                    .map { ScriptPsiFieldContext(it) }
                    .toTypedArray()
        }

        fun fieldCnt(): Int {
            return jvmClassHelper!!.getAllFields(psiClass).size
        }

        fun isExtend(superClass: String): Boolean {
            var currClass: PsiClass? = psiClass
            do {
                if (superClass == currClass!!.qualifiedName) {
                    return true
                }
                currClass = currClass.superClass
            } while (currClass != null && currClass.name != "Object")
            return false
        }

        fun isMap(): Boolean {
            return jvmClassHelper!!.isMap(PsiTypesUtil.getClassType(psiClass))
        }

        fun isCollection(): Boolean {
            return jvmClassHelper!!.isCollection(PsiTypesUtil.getClassType(psiClass))
        }

        fun isArray(): Boolean {
            return psiClass.qualifiedName?.endsWith("[]") ?: false
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

        fun toJson(readGetter: Boolean): String? {
            val option = if (readGetter) JsonOption.READ_GETTER else JsonOption.NONE
            return (jvmClassHelper!!.resolveClassToType(psiClass)?.let {
                psiClassHelper!!.getTypeObject(it, psiClass, option)
            } ?: psiClassHelper!!.getFields(psiClass)).let { RequestUtils.parseRawBody(it) }
        }

        override fun toString(): String {
            return name()
        }
    }

    /**
     * it.type:class
     * it.containingClass:class
     * it.jsonName:String
     */
    @ScriptTypeName("field")
    inner class ScriptPsiFieldContext(private val psiField: PsiField) : BaseScriptRuleContext(psiField) {
        override fun contextType(): String {
            return "field"
        }

        fun type(): ScriptPsiTypeContext {
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

    /**
     * it.returnType:class
     * it.isVarArgs:Boolean
     * it.args:arg[]
     * it.argTypes:class[]
     * it.argCnt:int
     * it.containingClass:class
     */
    @ScriptTypeName("method")
    inner class ScriptPsiMethodContext(private val psiMethod: PsiMethod) : BaseScriptRuleContext(psiMethod) {

        override fun contextType(): String {
            return "method"
        }

        /**
         * Returns the return type of the method.
         *
         * @return the method return type, or null if the method is a constructor.
         */
        fun returnType(): ScriptPsiTypeContext? {
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
        fun args(): Array<ScriptPsiParameterContext> {
            return psiMethod.parameterList.parameters.map { ScriptPsiParameterContext(it) }
                    .toTypedArray()
        }

        /**
         * Returns the array of method parameters type
         */
        fun argTypes(): Array<ScriptPsiTypeContext> {
            return psiMethod.parameterList.parameters.map { ScriptPsiTypeContext(it.type) }
                    .toTypedArray()
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
        fun containingClass(): ScriptPsiClassContext {
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

        fun type(): ScriptPsiTypeContext? {
            return returnType()
        }

        fun jsonType(): ScriptPsiTypeContext? {
            return psiMethod.returnType?.let { classRuleConfig!!.tryConvert(it, psiMethod) }?.let { ScriptPsiTypeContext(it) }
        }

        fun returnJson(needInfer: Boolean = false, readGetter: Boolean = true): String? {
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

    /**
     * it.name:String
     * it.type:class
     * it.isVarArgs:Boolean
     */
    @ScriptTypeName("arg")
    inner class ScriptPsiParameterContext(private val psiParameter: PsiParameter) : BaseScriptRuleContext(psiParameter) {
        override fun contextType(): String {
            return "param"
        }

        fun type(): ScriptPsiTypeContext {
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

        override fun toString(): String {
            return name()
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
    inner class ScriptPsiTypeContext(private val psiType: PsiType) : BaseScriptRuleContext() {
        override fun contextType(): String {
            return "class"
        }

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
                is SinglePrimitiveDuckType -> duckType.psiType().name
                is SingleDuckType -> duckType.psiClass().name
                is ArrayDuckType -> getDuckTypeSimpleName(duckType.componentType()) + "[]"
                else -> duckType.toString()
            }
        }

        fun methods(): Array<ScriptPsiMethodContext> {
            return (getResource() as? PsiClass)?.let { psiElement ->
                return@let jvmClassHelper!!.getAllMethods(psiElement)
                        .map { ScriptPsiMethodContext(it) }
                        .toTypedArray()
            } ?: emptyArray()
        }

        fun fields(): Array<ScriptPsiFieldContext> {
            return (getResource() as? PsiClass)?.let { psiElement ->
                return@let jvmClassHelper!!.getAllFields(psiElement)
                        .map { ScriptPsiFieldContext(it) }
                        .toTypedArray()
            } ?: emptyArray()
        }

        fun isExtend(superClass: String): Boolean {
            return getResource()?.let { psiClass ->
                var currClass: PsiClass? = psiClass as PsiClass
                do {
                    if (superClass == currClass!!.qualifiedName) {
                        return true
                    }
                    currClass = currClass.superClass
                } while (currClass != null && currClass.name != "Object")
                return false
            } ?: false
        }

        fun isMap(): Boolean {
            return jvmClassHelper!!.isMap(psiType)
        }

        fun isCollection(): Boolean {
            return jvmClassHelper!!.isCollection(psiType)
        }

        fun isArray(): Boolean {
            return duckType is ArrayDuckType
        }

        fun toJson(readGetter: Boolean): String? {
            return psiClassHelper!!.getTypeObject(psiType, getResource()!!,
                    if (readGetter) JsonOption.READ_GETTER else JsonOption.NONE
            )?.let { RequestUtils.parseRawBody(it) }
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
}

