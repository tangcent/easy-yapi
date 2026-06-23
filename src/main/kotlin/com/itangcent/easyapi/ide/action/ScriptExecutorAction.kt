package com.itangcent.easyapi.ide.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.itangcent.easyapi.ide.script.ScriptExecutorDialog
import com.itangcent.easyapi.logging.IdeaConsoleProvider

/**
 * Action to execute scripts against the selected PSI element.
 *
 * Opens a [ScriptExecutorDialog] allowing users to test Groovy
 * expressions in the context of the currently selected element.
 *
 * @see ScriptExecutorDialog for the dialog implementation
 */
class ScriptExecutorAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val console = IdeaConsoleProvider.getInstance(project).getConsole()
        console.debug("ScriptExecutorAction.actionPerformed: project=${project.name}")
        ScriptExecutorDialog(project).show()
    }
}
