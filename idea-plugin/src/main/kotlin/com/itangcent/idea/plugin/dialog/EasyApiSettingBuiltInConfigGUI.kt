package com.itangcent.idea.plugin.dialog

import com.itangcent.common.utils.notNullOrBlank
import com.itangcent.idea.plugin.configurable.AbstractEasyApiSettingGUI
import com.itangcent.idea.plugin.settings.Settings
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea

class EasyApiSettingBuiltInConfigGUI : AbstractEasyApiSettingGUI() {

    private var rootPanel: JPanel? = null

    private var builtInConfigTextArea: JTextArea? = null

    override fun getRootPanel(): JComponent? {
        return rootPanel
    }

    override fun onCreate() {
        autoComputer.bind(this.builtInConfigTextArea!!)
            .with<String?>(this, "settingsInstance.builtInConfig")
            .eval { it.takeIf { it.notNullOrBlank() } ?: EasyApiSettingGUI.DEFAULT_BUILT_IN_CONFIG }

        autoComputer.bind<String?>(this, "settingsInstance.builtInConfig")
            .with(builtInConfigTextArea!!)
            .eval { it.takeIf { it != EasyApiSettingGUI.DEFAULT_BUILT_IN_CONFIG } ?: "" }
    }

    override fun readSettings(settings: Settings, from: Settings) {
        settings.builtInConfig = from.builtInConfig
    }
}