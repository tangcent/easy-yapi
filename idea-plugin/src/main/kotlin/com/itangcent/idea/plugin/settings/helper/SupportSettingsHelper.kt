package com.itangcent.idea.plugin.settings.helper

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.idea.plugin.settings.SettingBinder

@Singleton
@Deprecated("use @ConditionOnSetting")
class SupportSettingsHelper {

    @Inject
    private lateinit var settingBinder: SettingBinder

    fun methodDocEnable(): Boolean {
        return settingBinder.read().methodDocEnable
    }

    fun genericEnable(): Boolean {
        return settingBinder.read().genericEnable
    }
}