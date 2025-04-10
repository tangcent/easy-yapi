package com.itangcent.idea.plugin.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.itangcent.common.spi.Setup
import com.itangcent.common.spi.SpiUtils
import com.itangcent.idea.config.CachedResourceResolver
import com.itangcent.idea.plugin.Initializer
import com.itangcent.idea.utils.ConfigurableLogger
import com.itangcent.intellij.actions.KotlinAnAction
import com.itangcent.intellij.config.resource.ResourceResolver
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.jvm.kotlin.KotlinAutoInject
import com.itangcent.intellij.logger.IdeaConsoleLogger
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

    override fun onBuildActionContext(event: AnActionEvent, builder: ActionContextBuilder) {

        super.onBuildActionContext(event, builder)

        builder.bind(Logger::class) { it.with(ConfigurableLogger::class).singleton() }
        builder.bind(Logger::class, "delegate.logger") { it.with(IdeaConsoleLogger::class).singleton() }

        builder.bind(ResourceResolver::class) { it.with(CachedResourceResolver::class).singleton() }

        afterBuildActionContext(event, builder)
    }

    override fun actionPerformed(actionContext: ActionContext, project: Project?, anActionEvent: AnActionEvent) {
        SpiUtils.loadServices(Initializer::class)?.forEach {
            it.init()
        }
    }

    protected open fun afterBuildActionContext(event: AnActionEvent, builder: ActionContextBuilder) {

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