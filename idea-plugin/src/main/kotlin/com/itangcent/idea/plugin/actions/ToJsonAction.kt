package com.itangcent.idea.plugin.actions

import com.google.inject.Inject
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiType
import com.itangcent.common.logger.traceError
import com.itangcent.idea.plugin.api.export.EasyApiConfigReader
import com.itangcent.idea.plugin.config.RecommendConfigReader
import com.itangcent.idea.plugin.rule.SuvRuleParser
import com.itangcent.idea.utils.CustomizedPsiClassHelper
import com.itangcent.idea.utils.RuleComputeListenerRegistry
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.config.rule.RuleComputeListener
import com.itangcent.intellij.config.rule.RuleParser
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.file.DefaultLocalFileRepository
import com.itangcent.intellij.file.LocalFileRepository
import com.itangcent.intellij.jvm.JvmClassHelper
import com.itangcent.intellij.jvm.PsiClassHelper
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.ContextSwitchListener
import com.itangcent.intellij.util.ActionUtils
import com.itangcent.intellij.util.ToolUtils
import javax.swing.Icon

/**
 * @author tangcent
 */
abstract class ToJsonAction : BasicAnAction {

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

    override fun afterBuildActionContext(event: AnActionEvent, builder: ActionContext.ActionContextBuilder) {
        super.afterBuildActionContext(event, builder)

        builder.bind(RuleParser::class) { it.with(SuvRuleParser::class).singleton() }

        builder.bind(LocalFileRepository::class) { it.with(DefaultLocalFileRepository::class).singleton() }

        builder.bind(ConfigReader::class, "delegate_config_reader") { it.with(EasyApiConfigReader::class).singleton() }
        builder.bind(ConfigReader::class) { it.with(RecommendConfigReader::class).singleton() }

        builder.bind(RuleComputeListener::class) { it.with(RuleComputeListenerRegistry::class).singleton() }
        builder.bind(PsiClassHelper::class) { it.with(CustomizedPsiClassHelper::class).singleton() }
    }

    override fun actionPerformed(actionContext: ActionContext, project: Project?, anActionEvent: AnActionEvent) {
        super.actionPerformed(actionContext, project, anActionEvent)

        try {
            actionContext.runInWriteUI {
                val currentClass = ActionUtils.findCurrentClass()
                if (currentClass == null) {
                    logger!!.info("no class be selected!")
                    return@runInWriteUI
                }
                contextSwitchListener!!.switchTo(currentClass)
                val psiType = jvmClassHelper!!.resolveClassToType(currentClass)
                val json = parseToJson(currentClass, psiType)
                actionContext.runAsync {
                    ToolUtils.copy2Clipboard(json)
                    logger!!.log("\n$json\n")
                }
            }
        } catch (e: Exception) {
            logger!!.traceError("To json failed", e)
        }
    }

    abstract fun parseToJson(psiClass: PsiClass, type: PsiType?): String
}
