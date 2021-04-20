package com.itangcent.idea.plugin.settings.helper

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.idea.plugin.settings.SettingBinder

@Singleton
class SupportSettingsHelper {

    @Inject
    private lateinit var settingBinder: SettingBinder

    fun methodDocEnable(): Boolean {
        return settingBinder.read().methodDocEnable
    }
}