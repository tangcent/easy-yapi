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
        private const val GROUP_ID = "com.itangcent.idea.easy_api.actions.ChannelQuickExportGroup"
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
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        e?.project?.let { ensureActionsRegistered(it) }
        return super.getChildren(e)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}
