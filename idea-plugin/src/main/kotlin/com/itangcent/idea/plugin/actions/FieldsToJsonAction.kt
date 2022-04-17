package com.itangcent.idea.plugin.actions

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiType
import com.itangcent.idea.plugin.format.MessageFormatter
import com.itangcent.idea.plugin.format.SimpleJsonFormatter
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.jvm.PsiClassHelper
import com.itangcent.intellij.jvm.JsonOption

/**
 * @author tangcent
 */
class FieldsToJsonAction : FieldsToMessageAction("To Json") {

    @Inject
    private val psiClassHelper: PsiClassHelper? = null

    override fun actionName(): String {
        return "FieldsToJsonAction"
    }

    override fun formatMessage(psiClass: PsiClass, type: PsiType?): String {
        val obj = psiClassHelper!!.getTypeObject(type, psiClass, JsonOption.READ_GETTER.or(JsonOption.READ_SETTER))
        return ActionContext.getContext()!!.instance(SimpleJsonFormatter::class).format(obj)
    }
}
