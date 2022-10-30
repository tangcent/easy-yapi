package com.itangcent.idea.plugin.dialog

import com.itangcent.common.utils.notNullOrBlank
import com.itangcent.idea.plugin.configurable.AbstractEasyApiSettingGUI
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.utils.ResourceUtils
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea

class EasyApiSettingBuiltInConfigGUI : AbstractEasyApiSettingGUI() {

    private var rootPanel: JPanel? = null

    private var builtInConfigTextArea: JTextArea? = null

    override fun getRootPanel(): JComponent? {
        return rootPanel
    }

    override fun setSettings(settings: Settings) {
        super.setSettings(settings)
        this.builtInConfigTextArea!!.text =
            settings.builtInConfig.takeIf { it.notNullOrBlank() } ?: DEFAULT_BUILT_IN_CONFIG
    }

    override fun readSettings(settings: Settings) {
        settings.builtInConfig = this.builtInConfigTextArea!!.text.takeIf { it != DEFAULT_BUILT_IN_CONFIG } ?: ""
    }

    companion object {
        private const val built_in_config_name = ".default.built.in.easy.api.config"

        val DEFAULT_BUILT_IN_CONFIG by lazy { ResourceUtils.readResource(built_in_config_name) }
    }
}