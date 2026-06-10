package com.itangcent.easyapi.ide.action

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Eagerly registers channel export actions with the IntelliJ action system
 * at project startup so they appear in Settings > Keymap.
 */
class ChannelActionInitActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        ChannelQuickActionGroup.ensureActionsRegistered()
    }
}
