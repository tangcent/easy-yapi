package com.itangcent.idea.plugin.settings.helper

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.intellij.jvm.JsonOption

@Singleton
class IntelligentSettingsHelper {

    @Inject
    private lateinit var settingBinder: SettingBinder

    fun queryExpanded(): Boolean {
        return settingBinder.read().queryExpanded
    }

    fun formExpanded(): Boolean {
        return settingBinder.read().formExpanded
    }

    fun readGetter(): Boolean {
        return settingBinder.read().readGetter
    }

    fun readSetter(): Boolean {
        return settingBinder.read().readSetter
    }

    fun inferEnable(): Boolean {
        return settingBinder.read().run { inferEnable or aiMethodInferEnabled }
    }

    fun inferMaxDeep(): Int {
        return settingBinder.read().inferMaxDeep
    }

    fun selectedOnly(): Boolean {
        return settingBinder.read().selectedOnly
    }

    fun jsonOptionForInput(jsonOption: Int): Int {
        return if (readSetter()) {
            jsonOption.or(JsonOption.READ_SETTER)
        } else {
            jsonOption
        }
    }

    fun jsonOptionForOutput(jsonOption: Int): Int {
        return if (readGetter()) {
            jsonOption.or(JsonOption.READ_GETTER)
        } else {
            jsonOption
        }
    }
}