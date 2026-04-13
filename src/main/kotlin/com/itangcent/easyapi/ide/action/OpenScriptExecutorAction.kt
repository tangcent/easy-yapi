package com.itangcent.easyapi.ide.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.itangcent.easyapi.ide.script.ScriptExecutorDialog

/**
 * Action to open the script executor dialog.
 *
 * @see ScriptExecutorDialog for the dialog
 */
class OpenScriptExecutorAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ScriptExecutorDialog(project).show()
    }
}
