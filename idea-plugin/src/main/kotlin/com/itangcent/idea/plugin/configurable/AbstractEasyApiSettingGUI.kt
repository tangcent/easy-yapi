package com.itangcent.idea.plugin.configurable

import com.itangcent.idea.plugin.settings.Settings

abstract class AbstractEasyApiSettingGUI : EasyApiSettingGUI {

    protected var settingsInstance: Settings? = null

    override fun onCreate() {
        //NOP
    }

    override fun getSettings(): Settings {
        return (settingsInstance ?: Settings()).copy().also { readSettings((it)) }
    }

    override fun setSettings(settings: Settings) {
        this.settingsInstance = settings
    }

    override fun readSettings(settings: Settings) {
    }
}