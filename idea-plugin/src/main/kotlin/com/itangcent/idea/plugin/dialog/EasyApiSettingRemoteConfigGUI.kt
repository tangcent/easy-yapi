package com.itangcent.idea.plugin.dialog

import com.google.inject.Inject
import com.intellij.openapi.ui.Messages
import com.intellij.ui.CheckBoxList
import com.itangcent.idea.plugin.configurable.AbstractEasyApiSettingGUI
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.idea.plugin.settings.helper.*
import com.itangcent.idea.swing.ActiveWindowProvider
import com.itangcent.idea.swing.MessagesHelper
import com.itangcent.idea.swing.MutableActiveWindowProvider
import com.itangcent.intellij.context.ActionContext
import com.itangcent.utils.ResourceUtils
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea

class EasyApiSettingRemoteConfigGUI : AbstractEasyApiSettingGUI() {

    private lateinit var rootPanel: JPanel

    private lateinit var addConfigButton: JButton

    private lateinit var removeConfigButton: JButton

    private lateinit var remoteConfig: RemoteConfig

    private lateinit var configList: CheckBoxList<String>

    private lateinit var previewRemoteConfigTextArea: JTextArea

    @Inject
    private lateinit var messagesHelper: MessagesHelper

    @Inject
    private lateinit var activeWindowProvider: ActiveWindowProvider

    @Inject
    private lateinit var actionContext: ActionContext

    @Inject
    private lateinit var remoteConfigSettingsHelper: RemoteConfigSettingsHelper


    override fun onCreate() {
        (activeWindowProvider as? MutableActiveWindowProvider)?.setActiveWindow(this.rootPanel)
        this.addConfigButton.addActionListener {
            this.addConfig()
        }
        this.removeConfigButton.addActionListener {
            this.deleteConfig()
        }
    }

    override fun getRootPanel(): JComponent {
        return rootPanel
    }

    override fun setSettings(settings: Settings) {
        super.setSettings(settings)
        remoteConfig =
            (settings.remoteConfig.takeIf { it.isNotEmpty() } ?: DEFAULT_REMOTE_CONFIG.lines().toTypedArray()).parse()
        refreshConfigList()
        this.configList.addListSelectionListener {
            refreshPreview()
        }
        this.configList.setCheckBoxListListener { index, value ->
            remoteConfig.setSelected(index, value)
        }
    }

    private fun refreshConfigList() {
        this.configList.setItems(remoteConfig.map { it.second }) { it }
        remoteConfig.forEach {
            this.configList.setItemSelected(it.second, it.first)
        }
    }

    private fun refreshPreview() {
        val index = this.configList.selectedIndex
        if (index == -1) {
            this.previewRemoteConfigTextArea.text = ""
            return
        }
        this.previewRemoteConfigTextArea.text = "Loading..."
        val config = remoteConfig[index].second
        actionContext.runAsync {
            val content = remoteConfigSettingsHelper.loadConfig(config)
            actionContext.runInSwingUI {
                if (this.configList.selectedIndex == index) {
                    this.previewRemoteConfigTextArea.text = content
                }
            }
        }
    }


    override fun readSettings(settings: Settings) {
        settings.remoteConfig = this.remoteConfig.toConfig()
    }

    private fun addConfig() {
        val config = messagesHelper.showInputDialog("Input remote config url", "", Messages.getInformationIcon())
        if (!config.isNullOrBlank()) {
            remoteConfig.add(true to config)
            this.configList.addItem(config, config, true)
            this.configList.selectedIndex = remoteConfig.size - 1
        }
        configList.list()
    }

    private fun deleteConfig() {
        this.configList.selectedIndices.forEach {
            this.remoteConfig.removeAt(it)
        }
        refreshConfigList()
    }

    companion object {
        private const val DEFAULT_REMOTE_CONFIG_NAME = ".default.remote.easy.api.config"

        val DEFAULT_REMOTE_CONFIG by lazy { ResourceUtils.readResource(DEFAULT_REMOTE_CONFIG_NAME) }
    }
}