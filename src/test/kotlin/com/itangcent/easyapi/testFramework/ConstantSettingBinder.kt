package com.itangcent.easyapi.testFramework

import com.itangcent.easyapi.settings.SettingBinder
import com.itangcent.easyapi.settings.Settings

class ConstantSettingBinder(
    private var settings: Settings = Settings()
) : SettingBinder {

    override fun read(): Settings = settings

    override fun tryRead(): Settings = settings

    override fun save(settings: Settings?) {
        if (settings != null) {
            this.settings = settings
        }
    }

    fun updateSettings(updater: Settings.() -> Unit) {
        settings.updater()
    }
}
