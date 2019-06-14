package com.itangcent.idea.plugin.actions

import com.intellij.openapi.components.ServiceManager
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.utils.ConfigurableLogger
import com.itangcent.intellij.actions.KotlinAnAction
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.logger.ConsoleRunnerLogger
import com.itangcent.intellij.logger.Logger
import javax.swing.Icon

abstract class BasicAnAction : KotlinAnAction {
    constructor() : super()
    constructor(icon: Icon?) : super(icon)
    constructor(text: String?) : super(text)
    constructor(text: String?, description: String?, icon: Icon?) : super(text, description, icon)

    override fun onBuildActionContext(builder: ActionContext.ActionContextBuilder) {
        super.onBuildActionContext(builder)
        builder.bindInstance("plugin.name", "easy_api")

        builder.bind(SettingBinder::class) { it.toInstance(ServiceManager.getService(SettingBinder::class.java)) }
        builder.bind(Logger::class) { it.with(ConfigurableLogger::class).singleton() }
        builder.bind(Logger::class, "delegate.logger") { it.with(ConsoleRunnerLogger::class).singleton() }

    }
}