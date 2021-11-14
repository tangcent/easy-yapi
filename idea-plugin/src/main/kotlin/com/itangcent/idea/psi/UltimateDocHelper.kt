package com.itangcent.idea.psi

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.PsiClass
import com.itangcent.common.utils.appendln
import com.itangcent.idea.plugin.api.export.core.ClassExportRuleKeys
import com.itangcent.idea.plugin.api.export.core.DocParseHelper
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.jvm.DocHelper

@Singleton
class UltimateDocHelper {

    @Inject
    private val docHelper: DocHelper? = null

    @Inject(optional = true)
    private val docParseHelper: DocParseHelper? = null

    @Inject
    private val ruleComputer: RuleComputer? = null

    fun findUltimateDescOfClass(cls: PsiClass): String? {
        var docText = docHelper!!.getAttrOfDocComment(cls)
        ruleComputer!!.computer(ClassExportRuleKeys.CLASS_DOC, cls)?.let {
            docText = docText.appendln(it)
        }
        return when {
            docText.isNullOrBlank() -> cls.name
            docParseHelper != null -> docParseHelper.resolveLinkInAttr(docText, cls)
            else -> docText
        }
    }

}