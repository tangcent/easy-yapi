package com.itangcent.easyapi.ide.action

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiTreeUtil
import com.itangcent.easyapi.core.context.ActionContext
import com.itangcent.easyapi.logging.IdeaLog
import java.awt.datatransfer.StringSelection

/**
 * Base class for actions that format class fields to various output formats.
 *
 * Finds the containing [PsiClass] from the action context, formats it using
 * the subclass implementation, copies the result to clipboard, and shows
 * a notification.
 *
 * @param title The display title for notifications
 * @see FieldsToJsonAction for JSON output
 * @see FieldsToJson5Action for JSON5 output
 * @see FieldsToPropertiesAction for Properties output
 */
abstract class FieldFormatAction(
    private val title: String
) : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val psiClass = findPsiClass(e) ?: return
        val ctx = ActionContext.forProject(project)
        try {
            val text = kotlinx.coroutines.runBlocking { format(project, ctx, psiClass) }
            CopyPasteManager.getInstance().setContents(StringSelection(text))
            ctx.console?.println("\n$text\n")
            Notifications.Bus.notify(
                Notification(
                    "EasyAPI Notifications",
                    title,
                    "Copied to clipboard",
                    NotificationType.INFORMATION
                ), project
            )
        } finally {
            kotlinx.coroutines.runBlocking { ctx.stop() }
        }
    }

    /**
     * Format the given class fields to a string representation.
     *
     * @param project The IntelliJ project
     * @param actionContext The action context for DI
     * @param psiClass The class to format
     * @return The formatted string representation
     */
    protected abstract suspend fun format(project: Project, actionContext: ActionContext, psiClass: PsiClass): String

    private fun findPsiClass(e: AnActionEvent): PsiClass? {
        val element = e.getData(CommonDataKeys.PSI_ELEMENT)
        if (element is PsiClass) return element
        if (element != null) return PsiTreeUtil.getParentOfType(element, PsiClass::class.java)
        val file = e.getData(CommonDataKeys.PSI_FILE) ?: return null
        return PsiTreeUtil.findChildOfType(file, PsiClass::class.java)
    }

    companion object : IdeaLog
}
