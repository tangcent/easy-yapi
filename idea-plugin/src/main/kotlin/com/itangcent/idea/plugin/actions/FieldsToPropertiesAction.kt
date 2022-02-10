package com.itangcent.idea.plugin.actions

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiType
import com.itangcent.idea.plugin.api.export.core.ClassExportRuleKeys
import com.itangcent.idea.plugin.format.PropertiesFormatter
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.jvm.PsiClassHelper
import com.itangcent.intellij.psi.JsonOption

/**
 * @author tangcent
 */
class FieldsToPropertiesAction : FieldsToMessageAction("To Properties") {

    @Inject
    private val psiClassHelper: PsiClassHelper? = null

    @Inject
    private lateinit var ruleComputer: RuleComputer

    @Inject
    private lateinit var actionContext: ActionContext

    override fun actionName(): String {
        return "FieldsToPropertiesAction"
    }

    override fun formatMessage(psiClass: PsiClass, type: PsiType?): String {
        val obj = psiClassHelper!!.getTypeObject(type, psiClass, JsonOption.ALL)
        ruleComputer.computer(ClassExportRuleKeys.PROPERTIES_PREFIX, psiClass)?.let {
            actionContext.cache(ClassExportRuleKeys.PROPERTIES_PREFIX.name(), it)
        }
        return actionContext.instance(PropertiesFormatter::class).format(obj)
    }
}
