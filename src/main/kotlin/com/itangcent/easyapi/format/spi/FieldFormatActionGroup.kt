package com.itangcent.easyapi.format.spi

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.logging.IdeaLog

/**
 * Dynamic action group that exposes field-format actions (ToJson, ToJson5,
 * ToProperties, ToYaml, …).
 *
 * Mirrors
 * [com.itangcent.easyapi.core.ide.action.ChannelQuickActionGroup]: each
 * [FieldFormatChannel] extension is registered with [ActionManager] using a
 * stable ID and the plugin's [PluginId], so IntelliJ's Keymap system can
 * discover and categorize it.
 *
 * Registration is triggered eagerly via [ensureActionsRegistered] (called from
 * [com.itangcent.easyapi.core.ide.action.ChannelActionInitActivity]) and lazily via
 * [getChildren] as a fallback. Enablement is filtered through
 * [FieldFormatChannelRegistry.getEnabledChannels] so disabled formats do not
 * get an action registered; visibility of already-registered actions is
 * re-evaluated per-context by [FieldFormatAction.update] (Decision A5).
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

        /**
         * Registers all **enabled** field-format actions with [ActionManager]
         * (with plugin ID for keymap categorization) and adds them as children
         * of this group. Safe to call multiple times (idempotent).
         *
         * Takes [project] so it can consult [FieldFormatChannelRegistry.isEnabled]
         * (which reads the stored preference via
         * [com.itangcent.easyapi.core.settings.SettingBinder]). The `fieldFormatChannel`
         * EP itself is application-scoped, so [FieldFormatChannelRegistry.allChannels]
         * does not need the project — only the enablement read does.
         */
        fun ensureActionsRegistered(project: Project) {
            val actionManager = ActionManager.getInstance()
            val group = actionManager.getAction(GROUP_ID) as? FieldFormatActionGroup ?: return

            FieldFormatChannelRegistry.getInstance(project).getEnabledChannels().forEach { channel ->
                val actionId = ACTION_ID_PREFIX + channel.id
                if (actionManager.getAction(actionId) == null) {
                    val action = FieldFormatAction(channel)
                    actionManager.registerAction(actionId, action, PLUGIN_ID)
                    group.addAction(action)
                }
            }
        }

        /**
         * Re-applies the enablement filter to the action group after a settings
         * change. Newly-enabled formats get their action registered (via
         * [ensureActionsRegistered]); disabled formats' actions are hidden
         * (presentation visible=false) without unregistering, so keymap IDs
         * remain stable across enable/disable cycles (Req A4.1–A4.3, Decision A5).
         *
         * Visibility for existing actions is re-evaluated by each
         * [FieldFormatAction]'s `update(AnActionEvent)` method (per-context
         * presentation) when the menu is next shown. We deliberately do NOT
         * mutate `templatePresentation.isVisible` here — the IntelliJ Platform
         * forbids direct template-presentation mutation
         * (Presentation.assertNotTemplatePresentation). This achieves the same
         * "hide not unregister" semantics (Decision A5) while respecting the
         * platform's presentation contract.
         *
         * Safe to call when the group is not registered (no-op) and idempotent.
         */
        fun refreshActions(project: Project) {
            val actionManager = ActionManager.getInstance()
            actionManager.getAction(GROUP_ID) as? FieldFormatActionGroup ?: return
            // Re-run the add path so newly-enabled formats are registered.
            // Visibility for existing actions is re-evaluated lazily by each
            // FieldFormatAction.update() when the menu is next displayed.
            ensureActionsRegistered(project)
        }
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        // Lazy fallback: register enabled actions for the current project (if any).
        // The primary registration path is ChannelActionInitActivity at startup +
        // EasyApiSettingsConfigurable.apply() via refreshActions(project).
        e?.project?.let { ensureActionsRegistered(it) }
        return super.getChildren(e)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}
