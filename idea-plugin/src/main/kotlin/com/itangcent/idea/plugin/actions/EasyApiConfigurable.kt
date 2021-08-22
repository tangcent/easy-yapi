package com.itangcent.idea.plugin.actions

import com.google.inject.Inject
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.itangcent.idea.plugin.api.export.core.EasyApiConfigReader
import com.itangcent.idea.plugin.dialog.EasyApiSettingGUI
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.helper.MemoryPostmanSettingsHelper
import com.itangcent.idea.plugin.settings.helper.PostmanSettingsHelper
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.file.DefaultLocalFileRepository
import com.itangcent.intellij.file.LocalFileRepository
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.logger.SystemLogger
import com.itangcent.suv.http.ConfigurableHttpClientProvider
import com.itangcent.suv.http.HttpClientProvider
import javax.swing.JComponent

class EasyApiConfigurable(private var myProject: Project?) : SearchableConfigurable {

    private var easyApiConfigurableGUI: EasyApiSettingGUI? = null

    @Inject
    private lateinit var settingBinder: SettingBinder

    override fun isModified(): Boolean {
        return settingBinder.read() != easyApiConfigurableGUI?.getSettings()
    }

    override fun getId(): String {
        return "preference.EasyApiConfigurable"
    }

    override fun getDisplayName(): String {
        return "EasyApi"
    }

    override fun apply() {
        settingBinder.save(easyApiConfigurableGUI!!.getSettings().copy())
    }

    private var context: ActionContext? = null

    override fun createComponent(): JComponent? {

        LOG.info("create component of EasyApiConfigurable")

        val builder = ActionContext.builder()

        builder.bindInstance("plugin.name", "easy_api")
        myProject?.let { builder.bindInstance(Project::class, it) }
        builder.bind(Logger::class) { it.with(SystemLogger::class).singleton() }
        builder.bind(HttpClientProvider::class) { it.with(ConfigurableHttpClientProvider::class).singleton() }
        builder.bind(ConfigReader::class) { it.with(EasyApiConfigReader::class).singleton() }
        builder.bind(LocalFileRepository::class) { it.with(DefaultLocalFileRepository::class).singleton() }
        builder.bind(PostmanSettingsHelper::class) { it.with(MemoryPostmanSettingsHelper::class).singleton() }

        val context = builder.build()

        this.context = context

        context.init(this)

        easyApiConfigurableGUI = EasyApiSettingGUI()

        context.init(easyApiConfigurableGUI!!)
        context.runAsync {
            context.runInSwingUI {
                easyApiConfigurableGUI!!.onCreate()
                easyApiConfigurableGUI!!.setSettings(settingBinder.read().copy())
            }
        }
        return easyApiConfigurableGUI!!.getRootPanel()
    }

    override fun reset() {
        easyApiConfigurableGUI!!.setSettings(settingBinder.read().copy())
    }

    override fun disposeUIResources() {
        super.disposeUIResources()
        context?.stop(true)
    }

    init {
        LOG.info("create instance of EasyApiConfigurable")
    }
}

private val LOG = org.apache.log4j.Logger.getLogger(EasyApiConfigurable::class.java)