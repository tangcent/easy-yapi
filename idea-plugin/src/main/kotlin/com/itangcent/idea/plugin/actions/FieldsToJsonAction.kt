package com.itangcent.idea.plugin.actions

import com.google.inject.Inject
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.Project
import com.itangcent.idea.plugin.fields.FieldJsonGenerator
import com.itangcent.idea.utils.traceError
import com.itangcent.intellij.config.rule.RuleParser
import com.itangcent.intellij.config.rule.SimpleRuleParser
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.DefaultPsiClassHelper
import com.itangcent.intellij.psi.DuckTypeHelper
import com.itangcent.intellij.psi.PsiClassHelper
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

        builder.bind(RuleParser::class) { it.with(SimpleRuleParser::class) }
        builder.bind(PsiClassHelper::class) { it.with(DefaultPsiClassHelper::class).singleton() }
        builder.bind(DuckTypeHelper::class) { it.singleton() }
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
            logger!!.error("To json failed")
            logger.traceError(e)
        }
    }
}
