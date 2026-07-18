package com.itangcent.easyapi.ide.action

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.exporter.channel.ChannelRegistry
import com.itangcent.easyapi.logging.IdeaLog

/**
 * Dynamic action group that exposes channel-specific export actions.
 *
 * Each [ChannelExportAction] is registered with [ActionManager] using a stable ID
 * and the plugin's [PluginId], so IntelliJ's Keymap system can:
 * - Discover the action (stable ID)
 * - Place it under the "EasyApi" category (plugin ID)
 *
 * Registration is triggered eagerly via [ensureActionsRegistered] (called from
 * [ChannelActionInitActivity]) and lazily via [getChildren] as a fallback.
 */
class ChannelQuickActionGroup : DefaultActionGroup(), IdeaLog {

    companion object {
        const val ACTION_ID_PREFIX = "com.itangcent.easy_api.actions.channel."
        internal const val GROUP_ID = "com.itangcent.idea.easy_api.actions.ChannelQuickExportGroup"
        private val PLUGIN_ID = PluginId.getId("com.itangcent.idea.plugin.easy-yapi")

        /**
         * Registers all channel actions with [ActionManager] (with plugin ID for
         * keymap categorization) and adds them as children of this group.
         * Safe to call multiple times (idempotent).
         *
         * The `channel` EP is project-scoped (`area="IDEA_PROJECT"` in plugin.xml),
         * so it MUST be read via the project's extension area — querying the EP
         * directly via [com.intellij.openapi.extensions.ExtensionPointName.extensionList]
         * looks it up in the Application container and throws
         * "Missing extension point". We delegate to [ChannelRegistry], which
         * reads the project area defensively.
         */
        fun ensureActionsRegistered(project: Project) {
            val actionManager = ActionManager.getInstance()
            val group = actionManager.getAction(GROUP_ID) as? ChannelQuickActionGroup ?: return

            ChannelRegistry.getInstance(project).getActionChannels()
                .forEach { channel ->
                    val actionId = ACTION_ID_PREFIX + channel.id
                    if (actionManager.getAction(actionId) == null) {
                        val action = ChannelExportAction(channel.id, channel.actionText ?: channel.displayName)
                        actionManager.registerAction(actionId, action, PLUGIN_ID)
                        group.addAction(action)
                    }
                }
        }

        /**
         * Re-applies the enablement filter to the action group after a settings
         * change. Newly-enabled channels get their action registered (via
         * [ensureActionsRegistered]); disabled channels' actions are hidden
         * (presentation visible=false) without unregistering, so keymap IDs
         * remain stable across enable/disable cycles (Req 5.1, 5.2, Decision 4).
         *
         * Visibility for existing actions is re-evaluated by each
         * [ChannelExportAction]'s `update(AnActionEvent)` method (per-context
         * presentation) when the menu is next shown. We deliberately do NOT
         * mutate `templatePresentation.isVisible` here — the IntelliJ Platform
         * forbids direct template-presentation mutation
         * (Presentation.assertNotTemplatePresentation). This achieves the same
         * "hide not unregister" semantics (Decision 4) while respecting the
         * platform's presentation contract.
         *
         * Safe to call when the group is not registered (no-op) and idempotent.
         */
        fun refreshActions(project: Project) {
            val actionManager = ActionManager.getInstance()
            actionManager.getAction(GROUP_ID) as? ChannelQuickActionGroup ?: return
            // Re-run the add path so newly-enabled channels are registered.
            // Visibility for existing actions is re-evaluated lazily by each
            // ChannelExportAction.update() when the menu is next displayed.
            ensureActionsRegistered(project)
        }
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        e?.project?.let { ensureActionsRegistered(it) }
        return super.getChildren(e)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}
