package com.itangcent.idea.plugin.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.itangcent.common.spi.Setup
import com.itangcent.idea.config.CachedResourceResolver
import com.itangcent.idea.plugin.script.GroovyActionExtLoader
import com.itangcent.idea.plugin.script.LoggerBuffer
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.lazy
import com.itangcent.idea.utils.ConfigurableLogger
import com.itangcent.intellij.actions.ActionEventDataContextAdaptor
import com.itangcent.intellij.actions.KotlinAnAction
import com.itangcent.intellij.config.resource.ResourceResolver
import com.itangcent.intellij.constant.EventKey
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.file.DefaultLocalFileRepository
import com.itangcent.intellij.file.LocalFileRepository
import com.itangcent.intellij.jvm.kotlin.KotlinAutoInject
import com.itangcent.intellij.logger.ConsoleRunnerLogger
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.spi.IdeaAutoInject
import com.itangcent.intellij.tip.OnlyOnceInContextTipSetup
import javax.swing.Icon

abstract class BasicAnAction : KotlinAnAction {
    constructor() : super()
    constructor(icon: Icon?) : super(icon)
    constructor(text: String?) : super(text)
    constructor(text: String?, description: String?, icon: Icon?) : super(text, description, icon)

    protected open fun actionName(): String {
        return this::class.simpleName!!
    }

    override fun onBuildActionContext(event: AnActionEvent, builder: ActionContext.ActionContextBuilder) {

        super.onBuildActionContext(event, builder)
        builder.bindInstance("plugin.name", "easy_api")

        builder.bind(Logger::class) { it.with(ConfigurableLogger::class).singleton() }
        builder.bind(Logger::class, "delegate.logger") { it.with(ConsoleRunnerLogger::class).singleton() }
        builder.bind(ResourceResolver::class) { it.with(CachedResourceResolver::class).singleton() }

        afterBuildActionContext(event, builder)

        loadCustomActionExt(actionName(), ActionEventDataContextAdaptor(event), builder)
    }

    override fun actionPerformed(actionContext: ActionContext, project: Project?, anActionEvent: AnActionEvent) {
        val loggerBuffer: LoggerBuffer? = actionContext.getCache<LoggerBuffer>("LOGGER_BUF")
        loggerBuffer?.drainTo(actionContext.instance(Logger::class))
        val actionExtLoader: GroovyActionExtLoader? =
                actionContext.getCache<GroovyActionExtLoader>("GROOVY_ACTION_EXT_LOADER")
        actionExtLoader?.let { extLoader ->
            actionContext.on(EventKey.ON_COMPLETED) {
                extLoader.close()
            }
        }
    }

    protected open fun afterBuildActionContext(event: AnActionEvent, builder: ActionContext.ActionContextBuilder) {

    }

    protected fun loadCustomActionExt(
            actionName: String, event: DataContext,
            builder: ActionContext.ActionContextBuilder
    ) {
        val logger = LoggerBuffer()
        builder.cache("LOGGER_BUF", logger)
        val actionExtLoader = GroovyActionExtLoader()
        builder.cache("GROOVY_ACTION_EXT_LOADER", actionExtLoader)
        val loadActionExt = actionExtLoader.loadActionExt(event, actionName, logger)
                ?: return
        loadActionExt.init(builder)
    }

    companion object {
        init {
            Setup.load(BasicAnAction::class.java.classLoader)
            Setup.setup(OnlyOnceInContextTipSetup::class)
            Setup.setup(IdeaAutoInject::class)
            Setup.setup(KotlinAutoInject::class)
        }
    }
}