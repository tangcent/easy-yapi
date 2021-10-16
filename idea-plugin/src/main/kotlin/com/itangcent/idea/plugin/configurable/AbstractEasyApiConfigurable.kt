package com.itangcent.idea.plugin.configurable

import com.google.inject.Inject
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.itangcent.common.kit.toJson
import com.itangcent.idea.plugin.api.export.core.EasyApiConfigReader
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.extend.rx.ThrottleHelper
import com.itangcent.intellij.file.DefaultLocalFileRepository
import com.itangcent.intellij.file.LocalFileRepository
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.logger.SystemLogger
import com.itangcent.suv.http.ConfigurableHttpClientProvider
import com.itangcent.suv.http.HttpClientProvider
import javax.swing.JComponent

abstract class AbstractEasyApiConfigurable(private var myProject: Project?) : SearchableConfigurable {

    private lateinit var easyApiConfigurableGUI: EasyApiSettingGUI

    @Inject
    private lateinit var settingBinder: SettingBinder

    private val throttle = ThrottleHelper().build(this::class.qualifiedName!!)

    override fun isModified(): Boolean {
        val pre = settingBinder.read()
        val inUI = easyApiConfigurableGUI.getSettings()
        if (pre == inUI) {
            return false
        }
        if (throttle.acquire(5000L)) {
            LOG.info("settings is modified:\npre:----\n${pre.toJson()}\ninUI:----\n${inUI.toJson()}\n----\n")
        }
        return true
    }

    override fun apply() {
        settingBinder.read()
            .also { easyApiConfigurableGUI.readSettings(it) }
            .let { settingBinder.save(it) }
    }

    private var context: ActionContext? = null

    override fun createComponent(): JComponent? {

        LOG.info("create component of ${this::class.qualifiedName}")

        val builder = ActionContext.builder()

        builder.bindInstance("plugin.name", "easy_api")
        myProject?.let { builder.bindInstance(Project::class, it) }
        builder.bind(Logger::class) { it.with(SystemLogger::class).singleton() }
        builder.bind(HttpClientProvider::class) { it.with(ConfigurableHttpClientProvider::class).singleton() }
        builder.bind(ConfigReader::class) { it.with(EasyApiConfigReader::class).singleton() }
        builder.bind(LocalFileRepository::class) { it.with(DefaultLocalFileRepository::class).singleton() }

        afterBuildActionContext(builder)

        val context = builder.build()

        this.context = context

        context.init(this)

        easyApiConfigurableGUI = createGUI()

        context.init(easyApiConfigurableGUI)
        context.runAsync {
            context.runInSwingUI {
                easyApiConfigurableGUI.onCreate()
                easyApiConfigurableGUI.setSettings(settingBinder.read().copy())
                checkUI()
            }
        }
        return easyApiConfigurableGUI.getRootPanel()
    }

    private fun checkUI() {
        context!!.runAsync {
            for (i in 0..8) {
                Thread.sleep(250)
                if (easyApiConfigurableGUI.checkUI()) {
                    continue
                }
                LOG.error("checkUI failed.Try refresh ui again.")
                context!!.runInSwingUI {
                    easyApiConfigurableGUI.setSettings(easyApiConfigurableGUI.getSettings())
                    checkUI()
                }
                break
            }
        }
    }

    open fun afterBuildActionContext(builder: ActionContext.ActionContextBuilder) {}

    protected abstract fun createGUI(): EasyApiSettingGUI

    override fun reset() {
        easyApiConfigurableGUI.setSettings(settingBinder.read().copy())
    }

    override fun disposeUIResources() {
        super.disposeUIResources()
        context?.stop(true)
    }

    init {
        LOG.info("create instance of ${this::class.qualifiedName}")
    }
}

private val LOG = org.apache.log4j.Logger.getLogger(EasyApiConfigurable::class.java)