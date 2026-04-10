package com.itangcent.easyapi.ide.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.itangcent.easyapi.core.context.ActionContext
import com.itangcent.easyapi.ide.script.ScriptExecutorDialog

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
        val actionContext = ActionContext.forProject(project)
        ScriptExecutorDialog(project, actionContext).show()
    }
}
