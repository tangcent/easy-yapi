package com.itangcent.easyapi.ide.fieldformat

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiTreeUtil
import com.itangcent.easyapi.core.event.ActionCompletedTopic
import com.itangcent.easyapi.core.event.ActionCompletedTopic.Companion.syncPublish
import com.itangcent.easyapi.ide.support.NotificationUtils
import com.itangcent.easyapi.logging.IdeaConsoleProvider
import java.awt.datatransfer.StringSelection

/**
 * Generic field-format action — one instance per [FieldFormatChannel].
 *
 * Mirrors [com.itangcent.easyapi.ide.action.ChannelExportAction]: constructed
 * with a channel, finds the containing [PsiClass] from the action context,
 * calls [FieldFormatChannel.format], copies the result to clipboard, and shows
 * a notification.
 *
 * Replaces the previous abstract `FieldFormatAction` base class + three
 * subclasses (`FieldsToJsonAction`, `FieldsToJson5Action`,
 * `FieldsToPropertiesAction`). New formats are added by registering a
 * [FieldFormatChannel] extension — no new action class needed.
 *
 * @param channel the format channel this action invokes
 */
class FieldFormatAction(
    private val channel: FieldFormatChannel
) : AnAction(channel.actionText) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val psiClass = findPsiClass(e) ?: return
        val console = IdeaConsoleProvider.getInstance(project).getConsole()
        console.debug("FieldFormatAction.actionPerformed: channel=${channel.id}, class=${psiClass.qualifiedName}")
        try {
            val text = kotlinx.coroutines.runBlocking { channel.format(project, psiClass) }
            CopyPasteManager.getInstance().setContents(StringSelection(text))
            NotificationUtils.notifyInfo(
                project,
                "Fields To ${channel.displayName}",
                "Copied to clipboard"
            )
        } finally {
            project.syncPublish(ActionCompletedTopic.TOPIC)
        }
    }

    private fun findPsiClass(e: AnActionEvent): PsiClass? {
        val element = e.getData(CommonDataKeys.PSI_ELEMENT)
        if (element is PsiClass) return element
        if (element != null) return PsiTreeUtil.getParentOfType(element, PsiClass::class.java)
        val file = e.getData(CommonDataKeys.PSI_FILE) ?: return null
        return PsiTreeUtil.findChildOfType(file, PsiClass::class.java)
    }
}
