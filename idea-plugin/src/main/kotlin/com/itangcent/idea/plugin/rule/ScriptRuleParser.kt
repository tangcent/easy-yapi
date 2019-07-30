package com.itangcent.idea.plugin.rule

import com.google.inject.Inject
import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil
import com.itangcent.intellij.config.rule.BooleanRule
import com.itangcent.intellij.config.rule.PsiElementContext
import com.itangcent.intellij.config.rule.RuleParser
import com.itangcent.intellij.config.rule.StringRule
import com.itangcent.intellij.extend.getPropertyValue
import com.itangcent.intellij.extend.toBoolean
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.*
import com.itangcent.intellij.util.DocCommentUtils
import javax.script.ScriptContext
import javax.script.ScriptEngine
import javax.script.SimpleScriptContext

abstract class ScriptRuleParser : RuleParser {

    @Inject
    private val duckTypeHelper: DuckTypeHelper? = null
    @Inject
    private val psiClassHelper: PsiClassHelper? = null
    @Inject
    private val logger: Logger? = null

    override fun parseBooleanRule(rule: String): List<BooleanRule> {
        return listOf(BooleanRule.of { context ->
            return@of eval(rule, context).toBoolean()
        })
    }

    @Deprecated(message = "it will be removed at next version")
    override fun parseBooleanRule(rule: String, delimiters: String, defaultValue: Boolean): List<BooleanRule> {
        TODO("com.itangcent.idea.plugin.rule.JsRuleParser.parseBooleanRule(java.lang.String, java.lang.String, boolean) was not implemented")
    }

    override fun parseStringRule(rule: String): List<StringRule> {
        return listOf(StringRule.of { context ->
            return@of eval(rule, context)?.toString()
        })
    }

    @Deprecated(message = "it will be removed at next version")
    override fun parseStringRule(rule: String, delimiters: String): List<StringRule> {
        TODO("com.itangcent.idea.plugin.rule.JsRuleParser.parseStringRule(java.lang.String, java.lang.String) was not implemented")
    }

    private fun eval(ruleScript: String, context: PsiElementContext): Any? {
        try {
            val simpleScriptContext = SimpleScriptContext()
            val contextForScript: PsiElementContext? = when (context) {
                is BaseScriptPsiElementContext -> context
                else -> contextOf(context.getResource()!!)
            }
            simpleScriptContext.setAttribute("it", contextForScript, ScriptContext.ENGINE_SCOPE)
            return getScriptEngine().eval(ruleScript, simpleScriptContext)
        } catch (e: UnsupportedScriptException) {
            logger?.error("unsupported script type:${e.getType()},script:$ruleScript")
            return null
        }
    }

    protected abstract fun getScriptEngine(): ScriptEngine

    override fun contextOf(psiElement: PsiElement): PsiElementContext {
        return when (psiElement) {
            is PsiClass -> ScriptPsiClassContext(psiElement)
            is PsiField -> ScriptPsiFieldContext(psiElement)
            is PsiMethod -> ScriptPsiMethodContext(psiElement)
            is PsiParameter -> ScriptPsiParameterContext(psiElement)
            is PsiType -> ScriptPsiTypeContext(psiElement)
            else -> BaseScriptPsiElementContext(psiElement)
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
    open class BaseScriptPsiElementContext : PsiElementContext {

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
            return PsiAnnotationUtils.findAnn(asPsiModifierListOwner(), name) != null
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
            return PsiAnnotationUtils.findAttr(asPsiModifierListOwner(), name, attr)
        }

        /**
         * it.doc():String
         */
        fun doc(): String? {
            return DocCommentUtils.getAttrOfDocComment(asPsiDocCommentOwner().docComment)
        }

        /**
         * it.doc("tag"):String?
         */
        fun doc(tag: String): String? {
            return DocCommentUtils.findDocsByTag(asPsiDocCommentOwner().docComment, tag)
        }

        /**
         * it.hasDoc("tag"):Boolean
         */
        fun hasDoc(tag: String): Boolean {
            return DocCommentUtils.hasTag(asPsiDocCommentOwner().docComment, tag)
        }

        /**
         * it.doc("tag","subTag"):String?
         */
        fun doc(tag: String, subTag: String): String? {
            return DocCommentUtils.findDocsByTagAndName(asPsiDocCommentOwner().docComment,
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
    inner class ScriptPsiClassContext(private val psiClass: PsiClass) : BaseScriptPsiElementContext(psiClass) {

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
            return PsiClassHelper.isMap(PsiTypesUtil.getClassType(psiClass))
        }

        fun isCollection(): Boolean {
            return PsiClassHelper.isCollection(PsiTypesUtil.getClassType(psiClass))
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
    inner class ScriptPsiFieldContext(private val psiField: PsiField) : BaseScriptPsiElementContext(psiField) {

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
    inner class ScriptPsiMethodContext(private val psiMethod: PsiMethod) : BaseScriptPsiElementContext(psiMethod) {

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
    inner class ScriptPsiParameterContext(private val psiParameter: PsiParameter) : BaseScriptPsiElementContext(psiParameter) {

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
    inner class ScriptPsiTypeContext(private val psiType: PsiType) : BaseScriptPsiElementContext() {

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
            return PsiClassHelper.isMap(psiType)
        }

        fun isCollection(): Boolean {
            return PsiClassHelper.isCollection(psiType)
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

