package com.itangcent.idea.plugin.configurable

import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.intellij.extend.rx.AutoComputer

abstract class AbstractEasyApiSettingGUI : EasyApiSettingGUI {

    protected var settingsInstance: Settings? = null

    protected var autoComputer: AutoComputer = AutoComputer()

    override fun checkUI(): Boolean {
        return true
    }

    override fun getSettings(): Settings {
        return (settingsInstance ?: Settings()).copy()
    }

    override fun setSettings(settings: Settings) {
        autoComputer.value(this::settingsInstance, settings.copy())
    }

    override fun readSettings(settings: Settings) {
        readSettings(settings, getSettings())
    }

    protected abstract fun readSettings(settings: Settings, from: Settings)
}