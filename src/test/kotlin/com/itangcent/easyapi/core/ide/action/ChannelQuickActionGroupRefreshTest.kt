package com.itangcent.easyapi.core.ide.action

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.itangcent.easyapi.core.settings.SettingBinder
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

/**
 * IDE-fixture tests for [ChannelQuickActionGroup.refreshActions] and
 * [ChannelExportAction.update] (Task 4.3, Req 5.1, 5.2, Decision 4).
 *
 * Verifies that:
 * - disabling a channel hides its action (per-context presentation visible=false)
 *   without unregistering it (keymap stability);
 * - re-enabling makes it visible again;
 * - refreshActions is idempotent.
 *
 * Visibility is verified by calling [ChannelExportAction.update] with a
 * project-aware [AnActionEvent] — this mirrors how the IntelliJ Platform
 * re-evaluates action visibility before displaying a menu. We do NOT check
 * `templatePresentation.isVisible` because the platform forbids mutating it
 * (Presentation.assertNotTemplatePresentation).
 *
 * The action group is registered with [ActionManager] in [setUp] if it is not
 * already present (the Light fixture may not load `plugin.xml` actions), and
 * any actions registered during the test are unregistered in [tearDown] so
 * state does not leak across tests.
 */
class ChannelQuickActionGroupRefreshTest : EasyApiLightCodeInsightFixtureTestCase() {

    private val actionManager: ActionManager get() = ActionManager.getInstance()

    /** Action IDs registered during the test, to be unregistered in tearDown. */
    private val registeredActionIds = mutableListOf<String>()
    private var registeredGroup: Boolean = false

    override fun setUp() {
        super.setUp()
        // Ensure the ChannelQuickActionGroup is registered. The Light fixture
        // does not always load plugin.xml actions, so register a fresh instance
        // under the stable GROUP_ID if none is present.
        if (actionManager.getAction(ChannelQuickActionGroup.GROUP_ID) == null) {
            actionManager.registerAction(
                ChannelQuickActionGroup.GROUP_ID,
                ChannelQuickActionGroup()
            )
            registeredGroup = true
        }
    }

    override fun tearDown() {
        try {
            // Unregister any channel actions this test registered.
            registeredActionIds.forEach { id ->
                if (actionManager.getAction(id) != null) {
                    actionManager.unregisterAction(id)
                }
            }
            // Restore GeneralSettings to defaults so enablement changes don't leak.
            SettingBinder.getInstance(project).save(GeneralSettings())
            // Unregister the group only if this test registered it.
            if (registeredGroup && actionManager.getAction(ChannelQuickActionGroup.GROUP_ID) != null) {
                actionManager.unregisterAction(ChannelQuickActionGroup.GROUP_ID)
            }
        } finally {
            super.tearDown()
        }
    }

    private fun hoppscotchActionId(): String =
        ChannelQuickActionGroup.ACTION_ID_PREFIX + "hoppscotch"

    /**
     * Enables [channelId] in GeneralSettings and saves (invalidates the binder
     * cache so the next read is fresh).
     */
    private fun enableChannel(channelId: String) {
        SettingBinder.getInstance(project).save(
            GeneralSettings(enabledChannels = arrayOf(channelId))
        )
    }

    /**
     * Disables [channelId] (default-off state): clear preferences so the channel
     * falls back to its enabledByDefault (false for hoppscotch).
     */
    private fun disableChannel(channelId: String) {
        SettingBinder.getInstance(project).save(GeneralSettings())
    }

    private fun registerHoppscotchActionIfNeeded() {
        // ensureActionsRegistered registers actions for channels in
        // getActionChannels() (filtered by isEnabled). Hoppscotch has
        // exposeAsAction=true, so when enabled it gets an action.
        ChannelQuickActionGroup.ensureActionsRegistered(project)
        val id = hoppscotchActionId()
        if (actionManager.getAction(id) != null && id !in registeredActionIds) {
            registeredActionIds.add(id)
        }
    }

    /**
     * Builds a project-aware [AnActionEvent] (with a fresh per-context
     * [Presentation]) so we can invoke [AnAction.update] and inspect the
     * resulting `isVisible` state. This mirrors how the IntelliJ Platform
     * re-evaluates action visibility before showing a menu.
     */
    private fun createActionEvent(): AnActionEvent {
        val presentation = Presentation()
        val dataContext = DataContext { key ->
            if (key == CommonDataKeys.PROJECT.name) project else null
        }
        return AnActionEvent.createEvent(
            dataContext,
            presentation,
            "test",
            ActionUiKind.NONE,
            null
        )
    }

    /**
     * Returns the effective visibility of [action] by invoking its [update]
     * method with a project-aware event. This is the per-context presentation
     * (NOT the template presentation), so calling setVisible on it is safe.
     */
    private fun effectiveVisibility(action: AnAction): Boolean {
        val event = createActionEvent()
        action.update(event)
        return event.presentation.isVisible
    }

    // --- Req 5.2: disabling a channel hides its action ---

    fun testRefreshActions_hidesDisabledChannelAction() {
        // Start with hoppscotch enabled so its action gets registered.
        enableChannel("hoppscotch")
        registerHoppscotchActionIfNeeded()
        val action = actionManager.getAction(hoppscotchActionId())
        assertNotNull(
            "Hoppscotch action should be registered when the channel is enabled",
            action
        )
        // Sanity: while enabled, update() resolves visible=true.
        assertTrue(
            "While hoppscotch is enabled, the action's update() should set isVisible=true",
            effectiveVisibility(action!!)
        )

        // Disable hoppscotch and refresh.
        disableChannel("hoppscotch")
        ChannelQuickActionGroup.refreshActions(project)

        assertFalse(
            "After disabling + refreshActions, the hoppscotch action's update() should set isVisible=false",
            effectiveVisibility(action)
        )
    }

    // --- Req 5.1: re-enabling a channel shows its action ---

    fun testRefreshActions_showsReEnabledChannelAction() {
        // Register the action while enabled, then disable+refresh to hide it.
        enableChannel("hoppscotch")
        registerHoppscotchActionIfNeeded()
        val action = actionManager.getAction(hoppscotchActionId())
        assertNotNull("Hoppscotch action should be registered when enabled", action)

        disableChannel("hoppscotch")
        ChannelQuickActionGroup.refreshActions(project)
        assertFalse(
            "Action should be hidden (update→isVisible=false) while disabled",
            effectiveVisibility(action!!)
        )

        // Re-enable and refresh — the action should become visible again.
        enableChannel("hoppscotch")
        ChannelQuickActionGroup.refreshActions(project)
        assertTrue(
            "After re-enabling + refreshActions, the hoppscotch action's update() should set isVisible=true",
            effectiveVisibility(action)
        )
    }

    // --- Decision 4 / Resolved Q#2: refreshActions does NOT unregister actions ---

    fun testRefreshActions_doesNotUnregisterAction() {
        enableChannel("hoppscotch")
        registerHoppscotchActionIfNeeded()
        val id = hoppscotchActionId()
        assertNotNull("Action should be registered", actionManager.getAction(id))

        // Disable and refresh — the action must stay registered (keymap stability).
        disableChannel("hoppscotch")
        ChannelQuickActionGroup.refreshActions(project)
        assertNotNull(
            "After disabling + refreshActions, the hoppscotch action must remain registered (keymap stability)",
            actionManager.getAction(id)
        )
    }

    // --- Idempotency ---

    fun testRefreshActions_isIdempotent() {
        enableChannel("hoppscotch")
        registerHoppscotchActionIfNeeded()
        val action = actionManager.getAction(hoppscotchActionId())
        assertNotNull("Action should be registered", action)

        ChannelQuickActionGroup.refreshActions(project)
        val visibleAfterFirst = effectiveVisibility(action!!)
        ChannelQuickActionGroup.refreshActions(project)
        val visibleAfterSecond = effectiveVisibility(action)
        assertEquals(
            "Calling refreshActions twice should produce the same visibility state",
            visibleAfterFirst,
            visibleAfterSecond
        )
    }

    // --- Robustness: refreshActions must not throw when the group is absent ---

    fun testRefreshActions_doesNotThrowWhenGroupAbsent() {
        // If the group is registered, temporarily unregister it to exercise the
        // early-return path; otherwise just call refreshActions (already absent).
        val groupId = ChannelQuickActionGroup.GROUP_ID
        val original = actionManager.getAction(groupId)
        if (original != null && !registeredGroup) {
            // The group was registered by plugin.xml; we cannot safely unregister
            // it, so just verify refreshActions doesn't throw with the group present.
            ChannelQuickActionGroup.refreshActions(project)
            return
        }
        if (registeredGroup) {
            actionManager.unregisterAction(groupId)
            registeredGroup = false
        }
        // No group registered → refreshActions must be a no-op, not throw.
        ChannelQuickActionGroup.refreshActions(project)
    }

    // --- ChannelExportAction.update edge cases ---

    /**
     * Builds a null-project [AnActionEvent] (with a fresh per-context
     * [Presentation]) so we can exercise the `project == null` branch of
     * [ChannelExportAction.update].
     */
    private fun createNullProjectActionEvent(): AnActionEvent {
        val presentation = Presentation()
        return AnActionEvent.createEvent(
            DataContext.EMPTY_CONTEXT,
            presentation,
            "test",
            ActionUiKind.NONE,
            null
        )
    }

    fun testUpdate_nullProject_disablesAction() {
        val action = ChannelExportAction("any-channel", "Any Channel")
        val event = createNullProjectActionEvent()
        action.update(event)
        assertFalse(
            "When project is null, update() should set isEnabled=false",
            event.presentation.isEnabled
        )
    }

    fun testUpdate_unregisteredChannel_setsVisibleTrue() {
        // A ChannelExportAction with a channelId that is not registered in the
        // EP: getChannel returns null, so the `?: true` branch makes the action
        // visible (Decision 4 — hide not unregister; unregistered-id actions
        // default to visible so they can still be discovered in Keymap).
        val action = ChannelExportAction("nonexistent-channel", "Nonexistent")
        val event = createActionEvent()
        action.update(event)
        assertTrue(
            "When the channel is not registered, update() should set isVisible=true (?: true branch)",
            event.presentation.isVisible
        )
        // Project is present, so the action should also be enabled.
        assertTrue(event.presentation.isEnabled)
    }

    // --- ensureActionsRegistered must not throw when the group is absent ---

    fun testEnsureActionsRegistered_doesNotThrowWhenGroupAbsent() {
        val groupId = ChannelQuickActionGroup.GROUP_ID
        val original = actionManager.getAction(groupId)
        if (original != null && !registeredGroup) {
            // The group was registered by plugin.xml; we cannot safely unregister
            // it, so just verify ensureActionsRegistered doesn't throw with the
            // group present.
            ChannelQuickActionGroup.ensureActionsRegistered(project)
            return
        }
        if (registeredGroup) {
            actionManager.unregisterAction(groupId)
            registeredGroup = false
        }
        // No group registered → ensureActionsRegistered must be a no-op, not throw.
        ChannelQuickActionGroup.ensureActionsRegistered(project)
    }
}
