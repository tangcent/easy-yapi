package com.itangcent.easyapi.ide.fieldformat

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginId
import com.itangcent.easyapi.logging.IdeaLog

/**
 * Dynamic action group that exposes field-format actions (ToJson, ToJson5,
 * ToProperties, ToYaml, …).
 *
 * Mirrors
 * [com.itangcent.easyapi.ide.action.ChannelQuickActionGroup]: each
 * [FieldFormatChannel] extension is registered with [ActionManager] using a
 * stable ID and the plugin's [PluginId], so IntelliJ's Keymap system can
 * discover and categorize it.
 *
 * Registration is triggered eagerly via [ensureActionsRegistered] (called from
 * [com.itangcent.easyapi.ide.action.ChannelActionInitActivity]) and lazily via
 * [getChildren] as a fallback.
 *
 * ## Adding a new format
 *
 * Register a new [FieldFormatChannel] in `plugin.xml` — the action appears
 * here automatically. No code changes in this class.
 */
class FieldFormatActionGroup : DefaultActionGroup(), IdeaLog {

    companion object {
        const val GROUP_ID = "com.itangcent.idea.easy_api.actions.FieldFormatGroup"
        const val ACTION_ID_PREFIX = "com.itangcent.easy_api.actions.fieldformat."
        private val PLUGIN_ID = PluginId.getId("com.itangcent.idea.plugin.easy-yapi")

        private val CHANNEL_EP = ExtensionPointName.create<FieldFormatChannel>(
            "com.itangcent.idea.plugin.easy-yapi.fieldFormatChannel"
        )

        /**
         * Registers all field-format actions with [ActionManager] (with plugin ID
         * for keymap categorization) and adds them as children of this group.
         * Safe to call multiple times (idempotent).
         */
        fun ensureActionsRegistered() {
            val actionManager = ActionManager.getInstance()
            val group = actionManager.getAction(GROUP_ID) as? FieldFormatActionGroup ?: return

            CHANNEL_EP.extensionList.forEach { channel ->
                val actionId = ACTION_ID_PREFIX + channel.id
                if (actionManager.getAction(actionId) == null) {
                    val action = FieldFormatAction(channel)
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
