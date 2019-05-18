package com.itangcent.idea.plugin.actions

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.options.SearchableConfigurable
import com.itangcent.idea.plugin.dialog.EasyApiSettingGUI
import com.itangcent.idea.plugin.settings.SettingBinder
import javax.swing.JComponent

class EasyApiConfigurable : SearchableConfigurable {

    private var easyApiConfigurableGUI: EasyApiSettingGUI? = null

    private val instance = ServiceManager.getService(SettingBinder::class.java)

    override fun isModified(): Boolean {
        return instance.read() != easyApiConfigurableGUI?.getSettings()
    }

    override fun getId(): String {
        return "preference.EasyApiConfigurable"
    }

    override fun getDisplayName(): String {
        return "EasyApi"
    }

    override fun apply() {
        instance.save(easyApiConfigurableGUI!!.getSettings().copy())
    }

    override fun createComponent(): JComponent? {
        easyApiConfigurableGUI = EasyApiSettingGUI()

        easyApiConfigurableGUI!!.onCreate(instance.read())
        return easyApiConfigurableGUI!!.getRootPanel()
    }
}