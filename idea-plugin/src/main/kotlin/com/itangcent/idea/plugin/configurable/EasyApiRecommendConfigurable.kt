package com.itangcent.idea.plugin.configurable

import com.intellij.openapi.project.Project
import com.itangcent.idea.plugin.dialog.EasyApiSettingRecommendGUI

class EasyApiRecommendConfigurable(myProject: Project?) : AbstractEasyApiConfigurable(myProject) {

    override fun getId(): String {
        return "preference.EasyApiConfigurable.Recommend"
    }

    override fun getDisplayName(): String {
        return "Recommend"
    }

    override fun createGUI(): EasyApiSettingGUI {
        return EasyApiSettingRecommendGUI()
    }
}