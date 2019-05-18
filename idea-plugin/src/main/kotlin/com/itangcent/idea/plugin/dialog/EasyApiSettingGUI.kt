package com.itangcent.idea.plugin.dialog

import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.intellij.extend.rx.AutoComputer
import com.itangcent.intellij.extend.rx.consistent
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.JTextArea

class EasyApiSettingGUI {
    private var pullNewestDataBeforeCheckBox: JCheckBox? = null

    private var postmanTokenTextArea: JTextArea? = null

    private var rootPanel: JPanel? = null

    public fun getRootPanel(): JPanel? {
        return rootPanel
    }

    private var settings: Settings? = null

    private var autoComputer: AutoComputer = AutoComputer()

    fun onCreate(settings: Settings) {
        autoComputer.bind(pullNewestDataBeforeCheckBox!!)
                .consistent(this, "settings.pullNewestDataBefore")

        autoComputer.bind(postmanTokenTextArea!!)
                .consistent(this, "settings.postmanToken")

        autoComputer.value(this::settings, settings)
    }

    fun getSettings(): Settings {
        if (settings == null) {
            settings = Settings()
        }
        return settings!!
    }

}
