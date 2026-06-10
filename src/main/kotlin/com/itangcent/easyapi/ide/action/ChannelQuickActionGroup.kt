package com.itangcent.easyapi.ide.action

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginId
import com.itangcent.easyapi.exporter.channel.ApiChannel
import com.itangcent.easyapi.logging.IdeaLog

/**
 * Dynamic action group that exposes channel-specific export actions.
 *
 * Each [ChannelExportAction] is registered with [ActionManager] using a stable ID
 * and the plugin's [PluginId], so IntelliJ's Keymap system can:
 * - Discover the action (stable ID)
 * - Place it under the "EasyYapi" category (plugin ID)
 *
 * Registration is triggered eagerly via [ensureActionsRegistered] (called from
 * [ChannelActionInitActivity]) and lazily via [getChildren] as a fallback.
 */
class ChannelQuickActionGroup : DefaultActionGroup(), IdeaLog {

    companion object {
        const val ACTION_ID_PREFIX = "com.itangcent.easy_api.actions.channel."
        private const val GROUP_ID = "com.itangcent.idea.easy_api.actions.ChannelQuickExportGroup"
        private val PLUGIN_ID = PluginId.getId("com.itangcent.idea.plugin.easy-yapi")

        private val CHANNEL_EP =
            ExtensionPointName.create<ApiChannel>("com.itangcent.idea.plugin.easy-yapi.apiChannel")

        /**
         * Registers all channel actions with [ActionManager] (with plugin ID for
         * keymap categorization) and adds them as children of this group.
         * Safe to call multiple times (idempotent).
         */
        fun ensureActionsRegistered() {
            val actionManager = ActionManager.getInstance()
            val group = actionManager.getAction(GROUP_ID) as? ChannelQuickActionGroup ?: return

            CHANNEL_EP.extensionList
                .filter { it.exposeAsAction }
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
        ensureActionsRegistered()
        return super.getChildren(e)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}
