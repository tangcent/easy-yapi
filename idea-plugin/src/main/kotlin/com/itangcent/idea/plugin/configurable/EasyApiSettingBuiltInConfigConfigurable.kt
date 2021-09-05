package com.itangcent.idea.plugin.configurable

import com.intellij.openapi.project.Project
import com.itangcent.idea.plugin.dialog.EasyApiSettingBuiltInConfigGUI

class EasyApiSettingBuiltInConfigConfigurable(myProject: Project?) : AbstractEasyApiConfigurable(myProject) {

    override fun getId(): String {
        return "preference.EasyApiConfigurable.BuiltInConfig"
    }

    override fun getDisplayName(): String {
        return "BuiltInConfig"
    }

    override fun createGUI(): EasyApiSettingGUI {
        return EasyApiSettingBuiltInConfigGUI()
    }
}