package com.itangcent.idea.plugin.actions

import com.google.inject.Inject
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiType
import com.itangcent.idea.plugin.json.Json5Formatter
import com.itangcent.idea.plugin.json.JsonFormatter
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.jvm.PsiClassHelper
import com.itangcent.intellij.psi.JsonOption

/**
 * @author tangcent
 */
class FieldsToJson5Action : ToJsonAction("To Json5") {

    @Inject
    private val psiClassHelper: PsiClassHelper? = null

    @Inject
    private val jsonFormatter: JsonFormatter? = null

    override fun actionName(): String {
        return "FieldsToJson5Action"
    }

    override fun afterBuildActionContext(event: AnActionEvent, builder: ActionContext.ActionContextBuilder) {
        super.afterBuildActionContext(event, builder)
        builder.bind(JsonFormatter::class) { it.with(Json5Formatter::class).singleton() }
    }

    override fun parseToJson(psiClass: PsiClass, type: PsiType?): String {
        val obj = psiClassHelper!!.getTypeObject(type, psiClass, JsonOption.ALL)
        return jsonFormatter!!.format(obj)
    }
}
