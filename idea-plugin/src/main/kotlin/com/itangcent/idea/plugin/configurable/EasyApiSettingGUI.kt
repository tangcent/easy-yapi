package com.itangcent.idea.plugin.configurable

import com.itangcent.idea.plugin.settings.Settings
import javax.swing.JComponent

interface EasyApiSettingGUI {
    fun getRootPanel(): JComponent?

    fun onCreate()

    fun checkUI(): Boolean

    fun getSettings(): Settings

    fun setSettings(settings: Settings)

    fun readSettings(settings: Settings)
}