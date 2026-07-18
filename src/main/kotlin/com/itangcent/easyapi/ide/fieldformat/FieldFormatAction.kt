package com.itangcent.easyapi.ide.fieldformat

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.itangcent.easyapi.core.event.ActionCompletedTopic
import com.itangcent.easyapi.core.event.ActionCompletedTopic.Companion.syncPublish
import com.itangcent.easyapi.ide.support.NotificationUtils
import com.itangcent.easyapi.ide.support.SelectedHelper
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.logging.console
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
) : AnAction(channel.actionText), IdeaLog {

    override fun update(e: AnActionEvent) {
        // Re-evaluate visibility per-display-context (NOT templatePresentation,
        // which the platform forbids mutating — Presentation.assertNotTemplatePresentation).
        // This is the chokepoint for "hide not unregister" (Decision A5): a
        // disabled format's action stays registered (keymap IDs remain stable)
        // but is hidden from the menu because isVisible resolves to false.
        e.presentation.isVisible = e.project?.let {
            FieldFormatChannelRegistry.getInstance(it).isEnabled(channel)
        } ?: false
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val psiClass = SelectedHelper.resolveSelection(e)?.psiClass() ?: return
        val console = project.console
        console.info("FieldFormatAction.actionPerformed: channel=${channel.id}, class=${psiClass.qualifiedName}")
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
}
