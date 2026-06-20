package com.itangcent.easyapi.ide.action

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.itangcent.easyapi.ide.fieldformat.FieldFormatActionGroup

/**
 * Eagerly registers channel export actions and field-format actions with the
 * IntelliJ action system at project startup so they appear in Settings > Keymap.
 */
class ChannelActionInitActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        ChannelQuickActionGroup.ensureActionsRegistered()
        FieldFormatActionGroup.ensureActionsRegistered()
    }
}
