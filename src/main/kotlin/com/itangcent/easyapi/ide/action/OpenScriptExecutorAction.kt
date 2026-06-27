package com.itangcent.easyapi.ide.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.itangcent.easyapi.ide.script.ScriptExecutorDialog
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.logging.console

/**
 * Action to open the script executor dialog.
 *
 * @see ScriptExecutorDialog for the dialog
 */
class OpenScriptExecutorAction : AnAction(), IdeaLog {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val console = project.console
        console.info("OpenScriptExecutorAction.actionPerformed: project=${project.name}")
        ScriptExecutorDialog(project).show()
    }
}
