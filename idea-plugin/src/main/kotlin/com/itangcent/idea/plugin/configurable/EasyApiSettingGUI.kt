package com.itangcent.idea.plugin.configurable

import com.itangcent.idea.plugin.settings.Settings
import javax.swing.JComponent

interface EasyApiSettingGUI {

    fun getRootPanel(): JComponent?

    fun onCreate()

    /**
     * get settings in UI
     */
    fun getSettings(): Settings

    /**
     * set settings to UI
     */
    fun setSettings(settings: Settings)

    /**
     * read settings from UI
     */
    fun readSettings(settings: Settings)
}