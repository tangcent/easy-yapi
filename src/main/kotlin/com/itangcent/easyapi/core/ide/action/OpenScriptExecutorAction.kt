package com.itangcent.easyapi.core.ide.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.itangcent.easyapi.core.ide.script.ScriptExecutorDialog
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.logging.console

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
