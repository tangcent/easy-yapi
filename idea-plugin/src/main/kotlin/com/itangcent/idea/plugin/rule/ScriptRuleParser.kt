package com.itangcent.idea.plugin.rule

import com.google.inject.Inject
import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil
import com.itangcent.intellij.config.rule.*
import com.itangcent.intellij.extend.getPropertyValue
import com.itangcent.intellij.extend.toBoolean
import com.itangcent.intellij.psi.*
import com.itangcent.intellij.util.DocCommentUtils
import javax.script.ScriptContext
import javax.script.ScriptEngine
import javax.script.SimpleScriptContext

abstract class ScriptRuleParser : RuleParser {

    @Inject
    private val duckTypeHelper: DuckTypeHelper? = null

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
            return@of eval(rule, context).toString()
        })
    }

    @Deprecated(message = "it will be removed at next version")
    override fun parseStringRule(rule: String, delimiters: String): List<StringRule> {
        TODO("com.itangcent.idea.plugin.rule.JsRuleParser.parseStringRule(java.lang.String, java.lang.String) was not implemented")
    }

    private fun eval(ruleScript: String, context: PsiElementContext): Any? {
        val simpleScriptContext = SimpleScriptContext()
        simpleScriptContext.setAttribute("it", context, ScriptContext.ENGINE_SCOPE)
        return getScriptEngine().eval(ruleScript, simpleScriptContext)
    }

    protected abstract fun getScriptEngine(): ScriptEngine

    override fun contextOf(psiElement: PsiElement): PsiElementContext {
        return when (psiElement) {
            is PsiClass -> JsPsiClassContext(psiElement)
            is PsiField -> JsPsiFieldContext(psiElement)
            is PsiMethod -> JsPsiMethodContext(psiElement)
            else -> PsiUnknownContext(psiElement)
        }
    }

    /**
     * support is js:
     * it.ann("annotation_name"):String?
     * it.ann("annotation_name#value"):String?
     * it.annotation("annotation_name"):String?
     * it.annotation("annotation_name#value"):String?
     * it.doc():String
     * it.doc("tag"):String?
     * it.doc("param","param"):String?
     */
    abstract class AbstractJsPsiElementContext : PsiElementContext {

        protected var psiElement: PsiElement? = null

        constructor(psiClass: PsiElement) {
            this.psiElement = psiClass
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

        fun name(): String {
            return getName()!!
        }

        /**
         * it.ann("annotation_name"):String?
         */
        fun ann(name: String): String? {
            return annotation(name)
        }

        /**
         * it.ann("annotation_name","value"):String?
         */
        fun ann(name: String, attr: String): String? {
            return annotation(name, attr)
        }

        /**
         * it.annotation("annotation_name"):String?
         */
        fun annotation(name: String): String? {
            return annotation(name, "value")
        }

        /**
         * it.annotation("annotation_name","attr"):String?
         */
        fun annotation(name: String, attr: String): String? {
            return PsiAnnotationUtils.findAttr(asPsiDocCommentOwner(), name, attr)
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
         * it.doc("param","param"):String?
         */
        fun doc(tag: String, subTag: String): String? {
            return DocCommentUtils.findDocsByTagAndName(asPsiDocCommentOwner().docComment,
                    tag, subTag)
        }

    }

    /**
     * it.methods():method[]
     * it.isExtend(""):Boolean
     * it.isSuper(""):Boolean
     * it.isMap():Boolean
     * it.isCollection():Boolean
     * it.isArray():Boolean
     * @see JsPsiTypeContext
     */
    inner class JsPsiClassContext(private val psiClass: PsiClass) : AbstractJsPsiElementContext(psiClass) {

        fun methods(): Array<JsPsiMethodContext> {
            return psiClass.allMethods.map { JsPsiMethodContext(it) }
                    .toTypedArray()
        }

        fun fields(): Array<JsPsiFieldContext> {
            return psiClass.allFields
                    .map { JsPsiFieldContext(it) }
                    .toTypedArray()
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

    inner class JsPsiFieldContext(private val psiField: PsiField) : AbstractJsPsiElementContext(psiField) {

        fun type(): JsPsiTypeContext {
            return JsPsiTypeContext(psiField.type)
        }

        override fun getName(): String? {
            return psiField.name
        }
    }

    /**
     * it.name:String
     * it.return:class
     * it.args:arg[]
     */
    inner class JsPsiMethodContext(val psiMethod: PsiMethod) : AbstractJsPsiElementContext(psiMethod) {

        fun `return`(): JsPsiTypeContext {
            return JsPsiTypeContext((psiElement as PsiMethod).returnType!!)
        }

        override fun getName(): String? {
            return psiMethod.name
        }
    }

    /**
     * it.methods():method[]
     * it.isExtend(""):Boolean
     * it.isSuper(""):Boolean
     * it.isMap():Boolean
     * it.isCollection():Boolean
     * it.isArray():Boolean
     * @see JsPsiClassContext
     */
    inner class JsPsiTypeContext(private val psiType: PsiType) : AbstractJsPsiElementContext() {

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

        fun methods(): Array<JsPsiMethodContext> {
            return getResource()?.let { psiElement ->
                return@let (psiElement as PsiClass).allMethods.map { JsPsiMethodContext(it) }
                        .toTypedArray()
            } ?: emptyArray()
        }

        fun fields(): Array<JsPsiFieldContext> {
            return getResource()?.let { psiElement ->
                return@let (psiElement as PsiClass).allFields
                        .map { JsPsiFieldContext(it) }
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

