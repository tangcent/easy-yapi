package com.itangcent.idea.plugin.settings.group

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.intellij.psi.JsonOption

@Singleton
class JsonSetting {

    @Inject
    private val settingBinder: SettingBinder? = null

    fun defaultJsonOption(): Int {
        return jsonOption(0)
    }

    fun jsonOption(jsonOption: Int): Int {
        return if (settingBinder!!.read().readGetter) {
            jsonOption.or(JsonOption.READ_GETTER)
        } else {
            jsonOption
        }
    }
}