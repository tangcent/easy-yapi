package com.itangcent.easyapi.core.ide.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.itangcent.easyapi.core.dashboard.ApiScanner
import com.itangcent.easyapi.core.export.address
import com.itangcent.easyapi.core.feature.CoreFeatureIds
import com.itangcent.easyapi.core.feature.FeatureStateService
import com.itangcent.easyapi.core.ide.DumbModeHelper
import com.itangcent.easyapi.core.ide.support.NotificationUtils
import com.itangcent.easyapi.core.ide.support.SelectionScope
import com.itangcent.easyapi.core.internal.threading.backgroundAsync
import com.itangcent.easyapi.core.internal.threading.swing
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.logging.console
import java.awt.datatransfer.StringSelection

/**
 * Action that copies the address of the selected API endpoints to the clipboard.
 *
 * One endpoint is copied per line, in the `METHOD /path` form produced by
 * [com.itangcent.easyapi.core.export.address] — selecting a controller class
 * or a whole file copies every endpoint it declares. This complements the
 * Dashboard's per-endpoint `Copy Path` (path only) with an editor-side entry
 * point that also carries the HTTP method.
 *
 * Resolution goes through [ApiScanner.scanSelection], the same entry point
 * every export action uses, so the copied address always matches what an
 * export of the same selection would contain.
 *
 * @see EasyApiAction for the selection-based visibility contract
 */
class CopyApiUrlAction : EasyApiAction("Copy API URL"), IdeaLog {

    /**
     * Hides the entry when the `Copy API URL` feature is switched off in
     * Settings → EasyApi → Features, so opting out removes the menu item
     * instead of leaving a dead action behind.
     */
    override fun update(e: AnActionEvent) {
        super.update(e)
        if (!e.presentation.isEnabledAndVisible) return
        val state = e.project?.let { FeatureStateService.getInstance(it) } ?: return
        if (!state.isEffective(CoreFeatureIds.COPY_API_URL)) {
            e.presentation.isEnabledAndVisible = false
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selection = resolveScope(e) ?: return
        val console = project.console
        console.info("CopyApiUrlAction.actionPerformed: project=${project.name}")

        backgroundAsync {
            if (!DumbModeHelper.waitForSmartModeOrNotify(project)) return@backgroundAsync

            val text = addressText(project, selection)

            if (text.isEmpty()) {
                swing {
                    Messages.showInfoMessage(project, "No API endpoints found.", "Copy API URL")
                }
                return@backgroundAsync
            }

            swing {
                CopyPasteManager.getInstance().setContents(StringSelection(text))
                NotificationUtils.notifyInfo(project, "Copy API URL", "Copied to clipboard")
            }
        }
    }

    /**
     * Resolves [selection] into the clipboard text: one address per line,
     * deduplicated, or `""` when the selection yields no endpoint.
     *
     * Duplicates are dropped because a multi-path mapping (e.g.
     * `@PostMapping({"/add", "/admin/add"})`) is one endpoint per path, while a
     * repeated scan of the same method would otherwise list the same address
     * twice.
     *
     * @requires ReadAction context (via [ApiScanner.scanSelection])
     */
    internal suspend fun addressText(project: Project, selection: SelectionScope): String =
        ApiScanner.getInstance(project)
            .scanSelection(selection)
            .map { it.address }
            .distinct()
            .joinToString("\n")
}
