package com.itangcent.idea.plugin.configurable

import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.intellij.extend.rx.AutoComputer
import javax.swing.JComponent

abstract class AbstractEasyApiSettingGUI : EasyApiSettingGUI {

    protected var settingsInstance: Settings? = null

    protected var autoComputer: AutoComputer = AutoComputer()

    override fun getSettings(): Settings {
        return (settingsInstance ?: Settings()).copy()
    }

    override fun setSettings(settings: Settings) {
        autoComputer.value(this::settingsInstance, settings.copy())
    }

    override fun readSettings(settings: Settings) {
        this.settingsInstance?.let { readSettings(settings, it) }

    }

    protected abstract fun readSettings(settings: Settings, from: Settings)
}