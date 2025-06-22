package com.itangcent.idea.plugin.actions

import com.google.inject.Inject
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiType
import com.itangcent.common.logger.traceError
import com.itangcent.idea.plugin.config.EnhancedConfigReader
import com.itangcent.idea.plugin.rule.SuvRuleParser
import com.itangcent.idea.utils.RuleComputeListenerRegistry
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.config.rule.RuleComputeListener
import com.itangcent.intellij.config.rule.RuleParser
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.file.DefaultLocalFileRepository
import com.itangcent.intellij.file.LocalFileRepository
import com.itangcent.intellij.jvm.JvmClassHelper
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.ContextSwitchListener
import com.itangcent.intellij.util.ActionUtils
import com.itangcent.intellij.util.ToolUtils
import javax.swing.Icon

/**
 * @author tangcent
 */
abstract class FieldsToMessageAction : BasicAnAction {

    @Inject
    private val logger: Logger? = null

    @Inject
    private val jvmClassHelper: JvmClassHelper? = null

    @Inject
    private val contextSwitchListener: ContextSwitchListener? = null

    constructor() : super()
    constructor(icon: Icon?) : super(icon)
    constructor(text: String?) : super(text)
    constructor(text: String?, description: String?, icon: Icon?) : super(text, description, icon)

    override fun afterBuildActionContext(event: AnActionEvent, builder: ActionContextBuilder) {
        super.afterBuildActionContext(event, builder)

        builder.bind(RuleParser::class) { it.with(SuvRuleParser::class).singleton() }

        builder.bind(LocalFileRepository::class) { it.with(DefaultLocalFileRepository::class).singleton() }

        builder.bind(RuleComputeListener::class) { it.with(RuleComputeListenerRegistry::class).singleton() }
        builder.bind(ConfigReader::class) { it.with(EnhancedConfigReader::class).singleton() }
    }

    override fun actionPerformed(actionContext: ActionContext, project: Project?, anActionEvent: AnActionEvent) {
        super.actionPerformed(actionContext, project, anActionEvent)

        try {
            actionContext.runInReadUI {
                val currentClass = ActionUtils.findCurrentClass()
                if (currentClass == null) {
                    logger!!.info("no class be selected!")
                    return@runInReadUI
                }
                contextSwitchListener!!.switchTo(currentClass)
                val psiType = jvmClassHelper!!.resolveClassToType(currentClass)
                val json = formatMessage(currentClass, psiType)
                actionContext.runAsync {
                    ToolUtils.copy2Clipboard(json)
                    logger!!.log("\n$json\n")
                }
            }
        } catch (e: Exception) {
            logger!!.traceError("parse fields failed", e)
        }
    }

    abstract fun formatMessage(psiClass: PsiClass, type: PsiType?): String
}
