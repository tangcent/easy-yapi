package com.itangcent.easyapi.channel.apipost

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.settings.SettingBinder
import com.itangcent.easyapi.core.settings.settings
import com.itangcent.easyapi.core.settings.update
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * Tests for [ApipostSettingsPanel] — the two-hop project picker.
 *
 * The picker exists because a project id is an unreadable hex string that used
 * to be copied by hand out of the ApiPost web URL, and because ApiPost forces a
 * two-hop lookup: `project/list` requires `team_id`, so teams are loaded first
 * (`REVIEW.md §6.11.1`).
 *
 * What is pinned here is the part that can silently corrupt a setting:
 *
 * - an entry fetched from the API reads `name (project_id)` and must be stored
 *   as the **id alone**;
 * - an id typed by hand must round-trip unchanged;
 * - an id that is **not** among the fetched rows must survive a load, or the
 *   next `Apply` would blank a setting the user never touched.
 *
 * The fetch itself (thread → `invokeLater`) is deliberately not exercised: it is
 * the same wiring the Postman panel uses and asserting on it would only buy
 * flakiness. The fetch *on open* is pinned at the step that happens on the EDT
 * — the client being built — which is enough to say whether a load was started
 * without waiting for a thread.
 *
 * Uses [EasyApiLightCodeInsightFixtureTestCase] so
 * [com.itangcent.easyapi.core.settings.DefaultSettingBinder] is registered in
 * `setUp` — the panel reads and writes through it.
 */
class ApipostSettingsPanelTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var panel: ApipostSettingsPanel

    override fun setUp() {
        super.setUp()
        // The fake client is never called: nothing in these tests presses Load.
        panel = ApipostSettingsPanel(project, factoryOf(FakeApipostApiClient()))
    }

    // ─── id extraction ──────────────────────────────────────────────────

    fun testAFetchedEntryIsStoredAsTheIdAlone() {
        assertEquals("1f54abcd", ApipostSettingsPanel.extractProjectId("My Project (1f54abcd)"))
    }

    fun testABareIdEntryIsLeftAlone() {
        // Not typed any more — the combo is pick-only. A bare id still shows up
        // as the entry for a project outside the loaded team.
        assertEquals("1f54abcd", ApipostSettingsPanel.extractProjectId("1f54abcd"))
    }

    fun testANameContainingParenthesesStillYieldsTheId() {
        assertEquals("p2", ApipostSettingsPanel.extractProjectId("Report (draft) (p2)"))
    }

    // ─── round-trip through the setting ─────────────────────────────────

    fun testApplyToPersistsTheIdParsedOutOfTheCombo() {
        panel.setProjectId("My Project (abc-123)")

        panel.applyTo(current())

        assertEquals("abc-123", project.settings<ApipostSettings>().apipostProjectId)
    }

    fun testApplyToPersistsABareIdEntry() {
        panel.setProjectId("abc-123")

        panel.applyTo(current())

        assertEquals("abc-123", project.settings<ApipostSettings>().apipostProjectId)
    }

    fun testResetFromShowsThePersistedIdImmediately() {
        // No token is configured here, so the fetch-on-open does not fire; the
        // point is that the stored value is visible before anything is fetched.
        save { apipostProjectId = "saved-1" }

        panel.resetFrom(null)

        assertEquals("saved-1", panel.projectIdText())
    }

    fun testIsModifiedIsFalseAfterResetAndTrueAfterAChange() {
        save { apipostProjectId = "saved-1" }
        panel.resetFrom(null)
        assertFalse(panel.isModified(current()))

        panel.setProjectId("saved-2")
        assertTrue(panel.isModified(current()))
    }

    // ─── combo population ──────────────────────────────────────────────

    fun testALoadSelectsTheProjectThatIsAlreadyConfigured() {
        save { apipostProjectId = "p2" }
        panel.resetFrom(null)

        panel.applyProjects(listOf(project("p1", "Alpha"), project("p2", "Beta"), project("p3", "Gamma")))

        assertEquals("p2", panel.projectIdText())
    }

    fun testAnIdOutsideTheLoadedTeamIsKeptRatherThanDropped() {
        // Switching the team row must not blank the field: the id may belong to
        // another team, and losing it would silently clear a setting on Apply.
        save { apipostProjectId = "manual-1" }
        panel.resetFrom(null)

        panel.applyProjects(listOf(project("p1", "Alpha")))

        assertEquals("manual-1", panel.projectIdText())
    }

    fun testTheProjectRowIsPickOnly() {
        // A hand-typed project id fails silently — that is why the picker exists.
        assertFalse(panel.isProjectEditable())
    }

    fun testTheTokenFieldIsWidthCapped() {
        // The value is a ~150-character JWT; unbounded it would set the width of
        // the whole dialog. Same cap as PostmanSettingsPanel.
        assertTrue("token field should be capped at 30 columns, was ${panel.tokenColumns()}",
            panel.tokenColumns() <= 30)
    }

    fun testBothRowsHaveTheirOwnReloadControl() {
        assertEquals(listOf("Load", "Refresh"), panel.reloadButtonTexts())
    }

    fun testALoadSelectsNothingWhenNoProjectIsConfigured() {
        // A combo always selects its first row. Without this, the fetch that
        // runs when the panel opens would pick the first project on its own and
        // the next Apply would persist a target the user never chose.
        // Clear explicitly: the PROJECT-scoped state survives between test
        // methods, so this one must not depend on what ran before it.
        save { apipostProjectId = null }
        panel.resetFrom(null)

        panel.applyProjects(listOf(project("p1", "Alpha"), project("p2", "Beta")))

        assertEquals("", panel.projectIdText())
        assertFalse(panel.isModified(current()))
        panel.applyTo(current())
        assertEquals(null, project.settings<ApipostSettings>().apipostProjectId)
    }

    fun testReloadingTheTeamsLeavesTheConfiguredProjectAlone() {
        save { apipostProjectId = "saved-1" }
        panel.resetFrom(null)

        panel.applyTeams(listOf(ApipostTeam("t1", "Team One"), ApipostTeam("t2", "Team Two")))

        assertEquals("a team reload must not disturb the project of this repository",
            "saved-1", panel.projectIdText())
    }

    fun testTheFirstTeamIsSelectedSoASingleTeamAccountNeedsOneClick() {
        panel.applyTeams(listOf(ApipostTeam("t1", "Team One")))

        assertEquals("t1", panel.selectedTeamId())
    }

    fun testSwitchingTheTeamRowChangesWhichTeamIsQueried() {
        panel.applyTeams(listOf(ApipostTeam("t1", "Team One"), ApipostTeam("t2", "Team Two")))

        panel.selectTeam(1)

        assertEquals("t2", panel.selectedTeamId())
    }

    // ─── the fetch that runs when the panel opens ────────────────────────

    fun testOpeningThePanelLoadsOnItsOwnWhenATokenIsConfigured() {
        // A picker that shows nothing until prodded is not much of a picker.
        val factory = CountingFactory(FakeApipostApiClient(teams = listOf(ApipostTeam("t1", "Team One"))))
        withToken("tok") {
            val opening = ApipostSettingsPanel(project, factory)

            opening.resetFrom(null)

            assertEquals("opening the panel should fetch once", 1, factory.created)
        }
    }

    fun testOpeningThePanelWithoutATokenLoadsNothing() {
        // Without a token every route answers 30001, and warning about a token
        // the user has not typed yet would be noise.
        val factory = CountingFactory(FakeApipostApiClient())
        val opening = ApipostSettingsPanel(project, factory)

        opening.resetFrom(null)

        assertEquals(0, factory.created)
    }

    fun testResettingDoesNotFetchAgain() {
        // `resetFrom` also runs when the user presses Reset; a settings dialog
        // must not turn into a request loop.
        val factory = CountingFactory(FakeApipostApiClient())
        withToken("tok") {
            val opening = ApipostSettingsPanel(project, factory)
            opening.resetFrom(null)

            opening.resetFrom(null)

            assertEquals("one fetch per panel", 1, factory.created)
        }
    }

    // ─── fixtures ───────────────────────────────────────────────────────

    private fun project(id: String, name: String) = ApipostProject(projectId = id, teamId = "t1", name = name)

    private fun current(): ApipostSettings = SettingBinder.getInstance(project).read(ApipostSettings::class)

    private fun save(block: ApipostSettings.() -> Unit) {
        SettingBinder.getInstance(project).update(ApipostSettings::class, block)
    }

    private fun factoryOf(client: ApipostApiClient) = ApipostApiClientFactory { _, _, _ -> client }

    /**
     * Counts how many clients were built — i.e. how many loads were started.
     *
     * The token lives in APPLICATION scope, so it would otherwise leak into the
     * next test method and make the panel fetch there too; [withToken] puts it
     * back afterwards.
     */
    private class CountingFactory(private val client: ApipostApiClient) : ApipostApiClientFactory {
        var created = 0
        override fun create(baseUrl: String, token: String, project: Project): ApipostApiClient =
            client.also { created++ }
    }

    private fun withToken(token: String, block: () -> Unit) {
        save { apipostToken = token }
        try {
            block()
        } finally {
            save { apipostToken = null }
        }
    }
}
