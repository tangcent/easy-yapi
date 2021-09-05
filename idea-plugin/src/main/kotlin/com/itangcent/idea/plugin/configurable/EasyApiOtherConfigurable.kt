package com.itangcent.idea.plugin.configurable

import com.intellij.openapi.project.Project
import com.itangcent.idea.plugin.dialog.EasyApiSettingOtherGUI

class EasyApiOtherConfigurable(myProject: Project?) : AbstractEasyApiConfigurable(myProject) {

    override fun getId(): String {
        return "preference.EasyApiConfigurable.Other"
    }

    override fun getDisplayName(): String {
        return "Other"
    }

    override fun createGUI(): EasyApiSettingGUI {
        return EasyApiSettingOtherGUI()
    }
}