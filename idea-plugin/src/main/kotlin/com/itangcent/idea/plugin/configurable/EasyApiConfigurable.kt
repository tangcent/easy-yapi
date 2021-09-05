package com.itangcent.idea.plugin.configurable

import com.intellij.openapi.project.Project
import com.itangcent.idea.plugin.api.export.core.EasyApiConfigReader
import com.itangcent.idea.plugin.dialog.EasyApiSettingGUI
import com.itangcent.idea.plugin.settings.helper.MemoryPostmanSettingsHelper
import com.itangcent.idea.plugin.settings.helper.PostmanSettingsHelper
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.file.DefaultLocalFileRepository
import com.itangcent.intellij.file.LocalFileRepository
import com.itangcent.suv.http.ConfigurableHttpClientProvider
import com.itangcent.suv.http.HttpClientProvider

class EasyApiConfigurable(myProject: Project?) : AbstractEasyApiConfigurable(myProject) {

    override fun getId(): String {
        return "preference.EasyApiConfigurable"
    }

    override fun getDisplayName(): String {
        return "EasyApi"
    }

    override fun createGUI(): com.itangcent.idea.plugin.configurable.EasyApiSettingGUI {
        return EasyApiSettingGUI()
    }

    override fun afterBuildActionContext(builder: ActionContext.ActionContextBuilder) {
        super.afterBuildActionContext(builder)
        builder.bind(PostmanSettingsHelper::class) { it.with(MemoryPostmanSettingsHelper::class).singleton() }
    }
}