package com.itangcent.easyapi.ide.fieldformat

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.itangcent.easyapi.settings.SettingBinder
import com.itangcent.easyapi.settings.module.GeneralSettings
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

/**
 * IDE-fixture tests for [FieldFormatActionGroup.refreshActions] and
 * [FieldFormatAction.update] (Task A.6, Req A4.1–A4.3, Decision A5).
 *
 * Mirrors [com.itangcent.easyapi.ide.action.ChannelQuickActionGroupRefreshTest].
 * Verifies that:
 * - disabling a format hides its action (per-context presentation visible=false)
 *   without unregistering it (keymap stability);
 * - re-enabling makes it visible again;
 * - refreshActions is idempotent.
 *
 * Visibility is verified by calling [FieldFormatAction.update] with a
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
class FieldFormatActionGroupRefreshTest : EasyApiLightCodeInsightFixtureTestCase() {

    private val actionManager: ActionManager get() = ActionManager.getInstance()

    /** Action IDs registered during the test, to be unregistered in tearDown. */
    private val registeredActionIds = mutableListOf<String>()
    private var registeredGroup: Boolean = false

    override fun setUp() {
        super.setUp()
        // Ensure the FieldFormatActionGroup is registered. The Light fixture
        // does not always load plugin.xml actions, so register a fresh instance
        // under the stable GROUP_ID if none is present.
        if (actionManager.getAction(FieldFormatActionGroup.GROUP_ID) == null) {
            actionManager.registerAction(
                FieldFormatActionGroup.GROUP_ID,
                FieldFormatActionGroup()
            )
            registeredGroup = true
        }
    }

    override fun tearDown() {
        try {
            // Unregister any field-format actions this test registered.
            registeredActionIds.forEach { id ->
                if (actionManager.getAction(id) != null) {
                    actionManager.unregisterAction(id)
                }
            }
            // Restore GeneralSettings to defaults so enablement changes don't leak.
            SettingBinder.getInstance(project).save(GeneralSettings())
            // Unregister the group only if this test registered it.
            if (registeredGroup && actionManager.getAction(FieldFormatActionGroup.GROUP_ID) != null) {
                actionManager.unregisterAction(FieldFormatActionGroup.GROUP_ID)
            }
        } finally {
            super.tearDown()
        }
    }

    private fun jsonActionId(): String =
        FieldFormatActionGroup.ACTION_ID_PREFIX + "json"

    /**
     * Disables [channelId] by adding it to `disabledFieldFormatChannels` and saving
     * (invalidates the binder cache so the next read is fresh).
     */
    private fun disableFormat(channelId: String) {
        SettingBinder.getInstance(project).save(
            GeneralSettings(disabledFieldFormatChannels = arrayOf(channelId))
        )
    }

    /**
     * Re-enables all formats by clearing the field-format preferences (so every
     * format falls back to its `enabledByDefault`, which is `true` for all four
     * shipping formats — Decision A2).
     */
    private fun enableAllFormats() {
        SettingBinder.getInstance(project).save(GeneralSettings())
    }

    private fun registerJsonActionIfNeeded() {
        // ensureActionsRegistered registers actions for formats in
        // getEnabledChannels() (filtered by isEnabled). json is default-on, so
        // when enabled it gets an action.
        FieldFormatActionGroup.ensureActionsRegistered(project)
        val id = jsonActionId()
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

    // --- Req A4.1: disabling a format hides its action ---

    fun testRefreshActions_hidesDisabledFormatAction() {
        // Start with json enabled (default-on, no preference) so its action gets registered.
        enableAllFormats()
        registerJsonActionIfNeeded()
        val action = actionManager.getAction(jsonActionId())
        assertNotNull(
            "json action should be registered when the format is enabled",
            action
        )
        // Sanity: while enabled, update() resolves visible=true.
        assertTrue(
            "While json is enabled, the action's update() should set isVisible=true",
            effectiveVisibility(action!!)
        )

        // Disable json and refresh.
        disableFormat("json")
        FieldFormatActionGroup.refreshActions(project)

        assertFalse(
            "After disabling + refreshActions, the json action's update() should set isVisible=false",
            effectiveVisibility(action)
        )
    }

    // --- Req A4.3: re-enabling a format shows its action ---

    fun testRefreshActions_showsReEnabledFormatAction() {
        // Register the action while enabled, then disable+refresh to hide it.
        enableAllFormats()
        registerJsonActionIfNeeded()
        val action = actionManager.getAction(jsonActionId())
        assertNotNull("json action should be registered when enabled", action)

        disableFormat("json")
        FieldFormatActionGroup.refreshActions(project)
        assertFalse(
            "Action should be hidden (update→isVisible=false) while disabled",
            effectiveVisibility(action!!)
        )

        // Re-enable and refresh — the action should become visible again.
        enableAllFormats()
        FieldFormatActionGroup.refreshActions(project)
        assertTrue(
            "After re-enabling + refreshActions, the json action's update() should set isVisible=true",
            effectiveVisibility(action)
        )
    }

    // --- Decision A5: refreshActions does NOT unregister actions ---

    fun testRefreshActions_doesNotUnregisterAction() {
        enableAllFormats()
        registerJsonActionIfNeeded()
        val id = jsonActionId()
        assertNotNull("Action should be registered", actionManager.getAction(id))

        // Disable and refresh — the action must stay registered (keymap stability).
        disableFormat("json")
        FieldFormatActionGroup.refreshActions(project)
        assertNotNull(
            "After disabling + refreshActions, the json action must remain registered (keymap stability)",
            actionManager.getAction(id)
        )
    }

    // --- Idempotency ---

    fun testRefreshActions_isIdempotent() {
        enableAllFormats()
        registerJsonActionIfNeeded()
        val action = actionManager.getAction(jsonActionId())
        assertNotNull("Action should be registered", action)

        FieldFormatActionGroup.refreshActions(project)
        val visibleAfterFirst = effectiveVisibility(action!!)
        FieldFormatActionGroup.refreshActions(project)
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
        val groupId = FieldFormatActionGroup.GROUP_ID
        val original = actionManager.getAction(groupId)
        if (original != null && !registeredGroup) {
            // The group was registered by plugin.xml; we cannot safely unregister
            // it, so just verify refreshActions doesn't throw with the group present.
            FieldFormatActionGroup.refreshActions(project)
            return
        }
        if (registeredGroup) {
            actionManager.unregisterAction(groupId)
            registeredGroup = false
        }
        // No group registered → refreshActions must be a no-op, not throw.
        FieldFormatActionGroup.refreshActions(project)
    }

    // --- FieldFormatAction.update edge cases ---

    private class StubFieldFormatChannel(
        override val id: String = "stub",
        override val displayName: String = "Stub",
        override val actionText: String = "ToStub",
        override val enabledByDefault: Boolean = true
    ) : FieldFormatChannel {
        override suspend fun format(
            project: com.intellij.openapi.project.Project,
            psiClass: com.intellij.psi.PsiClass
        ): String = ""
    }

    fun testUpdate_nullProject_setsVisibleFalse() {
        // When project is null, FieldFormatAction.update sets isVisible=false
        // (the `?: false` branch).
        val action = FieldFormatAction(StubFieldFormatChannel())
        val presentation = Presentation()
        val event = AnActionEvent.createEvent(
            DataContext.EMPTY_CONTEXT,
            presentation,
            "test",
            ActionUiKind.NONE,
            null
        )
        action.update(event)
        assertFalse(
            "When project is null, update() should set isVisible=false",
            event.presentation.isVisible
        )
    }

    // --- ensureActionsRegistered must not throw when the group is absent ---

    fun testEnsureActionsRegistered_doesNotThrowWhenGroupAbsent() {
        val groupId = FieldFormatActionGroup.GROUP_ID
        val original = actionManager.getAction(groupId)
        if (original != null && !registeredGroup) {
            // The group was registered by plugin.xml; we cannot safely unregister
            // it, so just verify ensureActionsRegistered doesn't throw with the
            // group present.
            FieldFormatActionGroup.ensureActionsRegistered(project)
            return
        }
        if (registeredGroup) {
            actionManager.unregisterAction(groupId)
            registeredGroup = false
        }
        // No group registered → ensureActionsRegistered must be a no-op, not throw.
        FieldFormatActionGroup.ensureActionsRegistered(project)
    }
}
