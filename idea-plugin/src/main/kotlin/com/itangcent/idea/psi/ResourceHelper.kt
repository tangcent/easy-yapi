package com.itangcent.idea.psi

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.itangcent.common.utils.append
import com.itangcent.idea.plugin.api.export.ClassExportRuleKeys
import com.itangcent.idea.plugin.api.export.DocParseHelper
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.jvm.DocHelper

@Singleton
class ResourceHelper {

    @Inject
    private val actionContext: ActionContext? = null

    @Inject
    private val docHelper: DocHelper? = null

    @Inject(optional = true)
    private val docParseHelper: DocParseHelper? = null

    @Inject
    protected val ruleComputer: RuleComputer? = null

    fun findResourceClass(resource: Any): PsiClass? {
        return when (resource) {
            is PsiResource -> resource.resourceClass()
            is PsiMethod -> actionContext!!.callInReadUI { resource.containingClass }
            is PsiClass -> resource
            else -> null
        }
    }

    /**
     * todo:should be move to DocHelper
     */
    fun findAttrOfClass(cls: PsiClass): String? {
        var docText = ruleComputer!!.computer(ClassExportRuleKeys.CLASS_DOC, cls)
        docText = docText.append(docHelper!!.getAttrOfDocComment(cls))
        return when {
            docText.isNullOrBlank() -> cls.name
            docParseHelper != null -> docParseHelper.resolveLinkInAttr(docText, cls)
            else -> docText
        }
    }

}