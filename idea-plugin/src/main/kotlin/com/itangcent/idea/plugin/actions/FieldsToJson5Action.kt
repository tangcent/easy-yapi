package com.itangcent.idea.plugin.actions

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiType
import com.itangcent.idea.plugin.format.Json5Formatter
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.jvm.PsiClassHelper
import com.itangcent.intellij.psi.JsonOption

/**
 * @author tangcent
 */
class FieldsToJson5Action : FieldsToMessageAction("To Json5") {

    @Inject
    private val psiClassHelper: PsiClassHelper? = null

    override fun actionName(): String {
        return "FieldsToJson5Action"
    }

    override fun formatMessage(psiClass: PsiClass, type: PsiType?): String {
        val obj = psiClassHelper!!.getTypeObject(type, psiClass, JsonOption.ALL)
        return ActionContext.getContext()!!.instance(Json5Formatter::class).format(obj)
    }
}
