package com.itangcent.idea.plugin.actions

import com.google.inject.Inject
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.Project
import com.itangcent.common.logger.traceError
import com.itangcent.idea.plugin.api.export.EasyApiConfigReader
import com.itangcent.idea.plugin.config.RecommendConfigReader
import com.itangcent.idea.plugin.fields.FieldJsonGenerator
import com.itangcent.idea.plugin.rule.SuvRuleParser
import com.itangcent.idea.utils.CustomizedPsiClassHelper
import com.itangcent.idea.utils.RuleComputeListenerRegistry
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.config.rule.RuleComputeListener
import com.itangcent.intellij.config.rule.RuleParser
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.jvm.PsiClassHelper
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.util.ToolUtils

/**
 * @author tangcent
 */
class FieldsToJsonAction : BasicAnAction("To Json") {

    @Inject
    private val logger: Logger? = null

    override fun actionName(): String {
        return "FieldsToJsonAction"
    }

    override fun afterBuildActionContext(event: AnActionEvent, builder: ActionContext.ActionContextBuilder) {
        super.afterBuildActionContext(event, builder)

        builder.bind(RuleParser::class) { it.with(SuvRuleParser::class).singleton() }

        builder.bind(ConfigReader::class, "delegate_config_reader") { it.with(EasyApiConfigReader::class).singleton() }
        builder.bind(ConfigReader::class) { it.with(RecommendConfigReader::class).singleton() }

        builder.bind(RuleComputeListener::class) { it.with(RuleComputeListenerRegistry::class).singleton() }
        builder.bind(PsiClassHelper::class) { it.with(CustomizedPsiClassHelper::class).singleton() }
    }

    override fun actionPerformed(actionContext: ActionContext, project: Project?, anActionEvent: AnActionEvent) {
        super.actionPerformed(actionContext, project, anActionEvent)

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
            logger!!.traceError("To json failed", e)

        }
    }
}
