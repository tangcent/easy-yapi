package com.itangcent.idea.plugin.configurable

import com.intellij.openapi.project.Project
import com.itangcent.idea.plugin.dialog.EasyApiSettingAIGUI

/**
 * Configurable for AI integration settings
 */
class EasyApiSettingAIConfigurable(myProject: Project?) : AbstractEasyApiConfigurable(myProject) {

    override fun getId(): String {
        return "preference.EasyApiConfigurable.AI"
    }

    override fun getDisplayName(): String {
        return "AI Integration"
    }

    override fun createGUI(): EasyApiSettingGUI {
        return EasyApiSettingAIGUI()
    }
} 