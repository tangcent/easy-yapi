package com.itangcent.idea.plugin.rule

import com.google.inject.Inject
import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil
import com.itangcent.intellij.config.rule.*
import com.itangcent.intellij.extend.getPropertyValue
import com.itangcent.intellij.extend.toBoolean
import com.itangcent.intellij.jvm.*
import com.itangcent.intellij.logger.Logger
import javax.script.ScriptContext
import javax.script.ScriptEngine
import javax.script.SimpleScriptContext

abstract class ScriptRuleParser : RuleParser {

    @Inject
    private val duckTypeHelper: DuckTypeHelper? = null
    @Inject
    private val psiClassHelper: PsiClassHelper? = null
    @Inject
    private val docHelper: DocHelper? = null
    @Inject
    private val annotationHelper: AnnotationHelper? = null
    @Inject
    protected val jvmClassHelper: JvmClassHelper? = null
    @Inject
    private val logger: Logger? = null

    override fun parseBooleanRule(rule: String): BooleanRule? {
        return BooleanRule.of { context ->
            return@of eval(rule, context).toBoolean()
        }
    }

    override fun parseStringRule(rule: String): StringRule? {
        return StringRule.of { context ->
            return@of eval(rule, context)?.toString()
        }
    }

    private fun eval(ruleScript: String, context: RuleContext): Any? {
        try {
            val simpleScriptContext = SimpleScriptContext()
            val contextForScript: RuleContext? = when (context) {
                is BaseScriptRuleContext -> context
                else -> contextOf(context.getResource()!!, context.getResource()!!)
            }
            simpleScriptContext.setAttribute("it", contextForScript, ScriptContext.ENGINE_SCOPE)
            return getScriptEngine().eval(ruleScript, simpleScriptContext)
        } catch (e: UnsupportedScriptException) {
            logger?.error("unsupported script type:${e.getType()},script:$ruleScript")
            return null
        }
    }

    protected abstract fun getScriptEngine(): ScriptEngine

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
     */
    open inner class BaseScriptRuleContext : RuleContext {

        protected var psiElement: PsiElement? = null

        constructor(psiElement: PsiElement) {
            this.psiElement = psiElement
        }

        constructor()

        override fun getResource(): PsiElement? {
            return psiElement
        }

        override fun getName(): String? {
            return psiElement!!.getPropertyValue("name")?.toString()
        }

        override fun asPsiDocCommentOwner(): PsiDocCommentOwner {
            if (psiElement is PsiDocCommentOwner) {
                return psiElement as PsiDocCommentOwner
            }
            throw IllegalArgumentException("$psiElement has non comment")
        }

        override fun asPsiModifierListOwner(): PsiModifierListOwner {
            if (psiElement is PsiModifierListOwner) {
                return psiElement as PsiModifierListOwner
            }
            throw IllegalArgumentException("$psiElement has non annotation")
        }

        fun name(): String {
            return getName()!!
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
    inner class ScriptPsiClassContext(private val psiClass: PsiClass) : BaseScriptRuleContext(psiClass) {

        fun methods(): Array<ScriptPsiMethodContext> {
            return psiClass.allMethods.map { ScriptPsiMethodContext(it) }
                    .toTypedArray()
        }

        fun methodCnt(): Int {
            return psiClass.allMethods.size
        }

        fun fields(): Array<ScriptPsiFieldContext> {
            return psiClass.allFields
                    .map { ScriptPsiFieldContext(it) }
                    .toTypedArray()
        }

        fun fieldCnt(): Int {
            return psiClass.allFields.size
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
            return false
        }

        override fun getName(): String? {
            return psiClass.name
        }
    }

    /**
     * it.type:class
     * it.containingClass:class
     * it.jsonName:String
     */
    inner class ScriptPsiFieldContext(private val psiField: PsiField) : BaseScriptRuleContext(psiField) {

        fun type(): ScriptPsiTypeContext {
            return ScriptPsiTypeContext(psiField.type)
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
    }

    /**
     * it.returnType:class
     * it.isVarArgs:Boolean
     * it.args:arg[]
     * it.argTypes:class[]
     * it.argCnt:int
     * it.containingClass:class
     */
    inner class ScriptPsiMethodContext(private val psiMethod: PsiMethod) : BaseScriptRuleContext(psiMethod) {

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

        override fun getName(): String? {
            return psiMethod.name
        }
    }

    /**
     * it.name:String
     * it.type:class
     * it.isVarArgs:Boolean
     */
    inner class ScriptPsiParameterContext(private val psiParameter: PsiParameter) : BaseScriptRuleContext(psiParameter) {

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
    inner class ScriptPsiTypeContext(private val psiType: PsiType) : BaseScriptRuleContext() {

        private var duckType: DuckType? = null

        override fun getName(): String? {
            return duckType?.let { getDuckTypeName(it) }
        }

        private fun getDuckTypeName(duckType: DuckType): String? {
            return when (duckType) {
                is ArrayDuckType -> getDuckTypeName(duckType.componentType()) + "[]"
                is SingleDuckType -> duckType.psiClass().name
                else -> duckType.toString()
            }
        }

        fun methods(): Array<ScriptPsiMethodContext> {
            return getResource()?.let { psiElement ->
                return@let (psiElement as PsiClass).allMethods.map { ScriptPsiMethodContext(it) }
                        .toTypedArray()
            } ?: emptyArray()
        }

        fun fields(): Array<ScriptPsiFieldContext> {
            return getResource()?.let { psiElement ->
                return@let (psiElement as PsiClass).allFields
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

        init {
            duckType = duckTypeHelper!!.ensureType(psiType)
            if (duckType != null && duckType is SingleDuckType) {
                this.psiElement = (duckType as SingleDuckType).psiClass()
            }
        }
    }
}

