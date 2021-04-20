package com.itangcent.idea.plugin.settings.helper

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.idea.plugin.settings.SettingBinder

@Singleton
class BuiltInConfigSettingsHelper {

    @Inject
    private lateinit var settingBinder: SettingBinder

    fun builtInConfig(): String? {
        return settingBinder.read().builtInConfig
    }
}