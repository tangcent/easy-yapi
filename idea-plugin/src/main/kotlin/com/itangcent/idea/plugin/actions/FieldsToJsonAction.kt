package com.itangcent.idea.plugin.actions

import com.google.inject.Inject
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.Project
import com.itangcent.idea.plugin.fields.FieldJsonGenerator
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.PsiClassHelper
import com.itangcent.intellij.psi.TmTypeHelper
import com.itangcent.intellij.util.ToolUtils
import org.apache.commons.lang3.exception.ExceptionUtils

/**
 * @author tangcent
 */
class FieldsToJsonAction : BasicAnAction("To Json") {

    @Inject
    private val logger: Logger? = null

    override fun onBuildActionContext(builder: ActionContext.ActionContextBuilder) {
        super.onBuildActionContext(builder)

        builder.bind(PsiClassHelper::class) { it.singleton() }
        builder.bind(TmTypeHelper::class) { it.singleton() }
    }

    override fun actionPerformed(actionContext: ActionContext, project: Project?, anActionEvent: AnActionEvent) {

        try {
            val editor = anActionEvent.getData(PlatformDataKeys.EDITOR)
            if (editor != null) {
                val fieldJsonGenerator = FieldJsonGenerator()

                actionContext.runInWriteUI {
                    val generateFieldJson = fieldJsonGenerator.generateFieldJson()
                    ToolUtils.copy2Clipboard(generateFieldJson)
                    logger!!.log("\n$generateFieldJson\n")
                }
            }
        } catch (e: Exception) {
            logger!!.error("To json failed:" + ExceptionUtils.getStackTrace(e))
        }
    }
}
