package com.itangcent.easyapi.channel.apipost

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.itangcent.easyapi.core.settings.SettingBinder
import com.itangcent.easyapi.core.settings.Settings
import com.itangcent.easyapi.core.settings.settings
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.settings.ui.SettingsPanel
import com.itangcent.easyapi.core.settings.update
import kotlinx.coroutines.runBlocking
import java.awt.BorderLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

/**
 * Persistent settings panel for the ApiPost channel.
 *
 * Four fields, mapped onto [ApipostSettings] — note the two storage scopes,
 * which is why "Project" reflects **this repository** while Server/Token are
 * shared by every project in the IDE:
 *
 * | Field | Setting | Scope | Notes |
 * |---|---|---|---|
 * | Server | `apipostServer` | APPLICATION | ApiPost open-API host; only assembles `/open/apis/…` URLs |
 * | Token | `apipostToken` | APPLICATION | sent as the `api-token` header; carries no project identity, so it is stored once |
 * | Team | *(not persisted)* | — | a lookup filter, not a setting: the project row carries its own `team_id` |
 * | Project | `apipostProjectId` | PROJECT | target project **of this repository** |
 *
 * ## Why picking a project takes two hops
 *
 * ApiPost has no route that lists every project a token can reach:
 * `project/list` **requires** `team_id` (omitting it answers
 * `10001 TeamId为必填字段;TeamCode为必填字段`), so the picker goes
 * `team/list` → `project/list?team_id=` (`REVIEW.md §6.11.1`). The team row is
 * therefore a *filter*, never a setting — which is also why no team field was
 * added to [ApipostSettings].
 *
 * Both rows carry their own reload control, and they are not interchangeable:
 * **Team → `Load`** re-reads the teams (the row a token change lands on) and
 * then the projects of whichever team ends up selected; **Project → `Refresh`**
 * only re-reads the projects of the team already selected. Switching the team
 * row does the same as the latter, so changing teams never costs a second
 * `team/list` round-trip.
 *
 * Opening the panel also fetches — once per panel, only when a token is already
 * configured, and **silently** (a failure goes to the log, never to a dialog).
 * A picker that shows nothing until prodded is not much of a picker, and the
 * explicit buttons stay for everything else: a token that was just typed, a
 * Reset, or a retry after a failure.
 *
 * ## Reading and writing
 *
 * Reads and writes go through [SettingBinder] so the unified state component
 * stays the single source of truth — the panel never touches state directly.
 *
 * The two combos are **pick-only** — a project id is an unreadable hex string,
 * and a hand-typed one fails silently, which is the whole reason the picker
 * exists. The safety net is [applyProjects]: an id that is already configured
 * but not among the fetched rows is appended as an extra entry instead of being
 * dropped, so a repository pointing at a project outside the loaded team keeps
 * it. [extractProjectId] therefore still accepts both `name (project_id)` and a
 * bare id.
 *
 * There is deliberately **no** version selector: the v1 `/api/convert` endpoint
 * is retired and the v2 host answers `10090 当前版本已停止维护`, so the open API
 * under [ApipostSettings.APIPOST_OPEN_HOST] is the only target (finding C/F).
 *
 * The token hint avoids naming one menu path as the single truth — community
 * sources disagree on whether it lives under 「开放平台」or「对外能力 → open API」
 * (OQ-1g) — so it points at both.
 *
 * Typed as [SettingsPanel] of [Settings] rather than `SettingsPanel<ApipostSettings>`
 * for the same reason as `OpenApiSettingsPanel`: the configurable casts every
 * channel panel through `SettingsPanel<*>`.
 *
 * @param clientFactory injected so tests can drive the load with a fake client;
 *        the default builds the real HTTP-backed one.
 */
class ApipostSettingsPanel(
    private val project: Project,
    private val clientFactory: ApipostApiClientFactory = DefaultApipostApiClientFactory(),
) : SettingsPanel<Settings> {

    private val serverField = JBTextField()

    /**
     * Capped at [TOKEN_COLUMNS] columns: the value is a ~150-character JWT, and
     * an unbounded field would let it dictate the width of the whole settings
     * dialog. Same cap as `PostmanSettingsPanel`'s token field.
     */
    private val tokenField = JBPasswordField().apply { columns = TOKEN_COLUMNS }
    private val teamCombo = ComboBox<String>().apply { isEditable = false }

    /**
     * Not editable: a project can only be **picked**, never typed.
     *
     * That is deliberate — the whole point of the picker is that a project id is
     * an unreadable hex string, and a hand-typed one fails silently. What keeps
     * this from being a dead end is [applyProjects]: an id that is already
     * configured but not among the fetched rows is appended as its own entry, so
     * a repository that points at a project outside the loaded team can still
     * keep it.
     */
    private val projectCombo = ComboBox<String>().apply { isEditable = false }

    /** Reloads the teams (and then their projects) — the row a token change lands on. */
    private val teamLoadButton = JButton("Load")

    /** Reloads just the projects of the team already selected. */
    private val projectLoadButton = JButton("Refresh")

    /**
     * Guards against the combo's own change events: filling the model fires an
     * action event, which would otherwise kick off another fetch.
     */
    private var applying = false

    /** Teams currently loaded, in display order — `null` until the first load. */
    private var loadedTeams: List<ApipostTeam> = emptyList()

    /** The project id as persisted, kept so a reload can re-select it. */
    private var savedProjectId: String = ""

    /**
     * Whether the panel already fetched on its own — see [resetFrom].
     *
     * One shot per panel on purpose: `resetFrom` runs again whenever the user
     * presses Reset, and silently re-requesting each time would turn a settings
     * dialog into a request loop.
     */
    private var autoLoaded = false

    init {
        teamLoadButton.toolTipText = "Reload the teams this token can see, then the projects of the selected team"
        projectLoadButton.toolTipText = "Reload the projects of the selected team"
        teamLoadButton.addActionListener { loadTeamsThenProjects() }
        projectLoadButton.addActionListener { loadProjectsOfSelectedTeam() }
        teamCombo.addActionListener {
            if (!applying) loadProjectsOfSelectedTeam()
        }
    }

    override val component: JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent("Server:", serverField)
        .addLabeledComponent("Token:", tokenField)
        .addLabeledComponent("Team:", teamRow())
        .addLabeledComponent("Project:", projectRow())
        .addComponentFillVertically(JPanel(), 0)
        .panel

    /** The team row reloads on its own: swapping the token is the common case. */
    private fun teamRow(): JPanel = JPanel(BorderLayout(4, 0)).apply {
        add(teamCombo, BorderLayout.CENTER)
        add(teamLoadButton, BorderLayout.EAST)
    }

    private fun projectRow(): JPanel = JPanel(BorderLayout(4, 0)).apply {
        add(projectCombo, BorderLayout.CENTER)
        add(projectLoadButton, BorderLayout.EAST)
    }

    // region load

    /**
     * `team/list` → `project/list?team_id=`.
     *
     * Most accounts have a single team, in which case the two hops collapse into
     * one click: the only team is selected and its projects load straight away.
     * With several teams the user switches the team row, which reloads just the
     * project list ([loadProjectsOfSelectedTeam]) — no second round-trip to
     * `team/list`.
     *
     * @param silent when true a failure is logged instead of shown — see
     *        [loadAsync]. Used by the fetch that runs when the panel opens.
     */
    private fun loadTeamsThenProjects(silent: Boolean = false) {
        if (loadedTeams.isEmpty()) {
            applying = true
            teamCombo.model = DefaultComboBoxModel(arrayOf(LOADING))
            applying = false
        }
        loadAsync({ it.listTeams() }, silent) { teams ->
            applyTeams(teams.getOrNull().orEmpty())
            if (loadedTeams.isNotEmpty()) loadProjectsOfSelectedTeam(silent)
        }
    }

    private fun loadProjectsOfSelectedTeam(silent: Boolean = false) {
        val teamId = selectedTeamId() ?: return
        loadAsync({ it.listProjects(teamId) }, silent) { projects ->
            applyProjects(projects.getOrNull().orEmpty())
        }
    }

    /**
     * Runs one open-API call off the EDT and hands the result back on it.
     *
     * Same shape as the Postman panel's fetch: a plain thread plus `runBlocking`
     * (the client is `suspend`), then `invokeLater` for the Swing update. A
     * transport exception becomes a [ApipostSyncResult.Failure] so the caller
     * has one path to handle.
     *
     * @param silent report a failure to the log instead of a dialog. Anything
     *        the user did not ask for — the fetch on open, the projects that
     *        follow from it — must not greet them with a modal; the rows simply
     *        stay as they are and the explicit buttons remain available.
     */
    private fun <T> loadAsync(
        call: suspend (ApipostApiClient) -> ApipostSyncResult<T>,
        silent: Boolean = false,
        onDone: (ApipostSyncResult<T>) -> Unit,
    ) {
        val token = tokenText().trim()
        if (token.isBlank()) {
            Messages.showWarningDialog("Please enter an ApiPost token first.", "ApiPost")
            return
        }
        val client = clientFactory.create(serverText().trim(), token, project)
        setLoading(true)
        thread {
            val result = runCatching { runBlocking { call(client) } }
                .getOrElse { ApipostSyncResult.Failure(it.message ?: it.javaClass.simpleName) }
            SwingUtilities.invokeLater {
                setLoading(false)
                onDone(result)
                if (!result.isSuccess) {
                    val detail = result.errorMessage()?.takeIf { it.isNotBlank() }
                        ?: "no detail reported (code ${(result as? ApipostSyncResult.Failure)?.code})"
                    if (silent) {
                        LOG.warn("ApiPost: could not load — $detail")
                    } else {
                        Messages.showWarningDialog("Could not load from ApiPost: $detail", "ApiPost")
                    }
                }
            }
        }
    }

    // endregion

    // region combo population

    /** Fills the team row; the first team is selected, which is also the common case. */
    internal fun applyTeams(teams: List<ApipostTeam>) {
        applying = true
        loadedTeams = teams
        teamCombo.model = DefaultComboBoxModel(teams.map { display(it.name, it.teamId) }.toTypedArray())
        if (teams.isNotEmpty()) teamCombo.selectedIndex = 0
        applying = false
    }

    /**
     * Fills the project row, keeping the persisted id visible.
     *
     * An id that is not among the fetched rows is appended rather than dropped:
     * the setting is per repository and the team row may have been switched to a
     * team that does not hold it, so silently losing it would blank the field on
     * the next `Apply`.
     *
     * **Nothing is selected when nothing was configured.** A combo always
     * selects its first row, and a project row that fills itself in on load
     * would write a project the user never picked — invisible until the next
     * export went somewhere unexpected. That matters most for the fetch that
     * runs when the panel opens, which the user did not ask for at all, so the
     * blank entry goes in first and stays selected until a real choice is made.
     */
    internal fun applyProjects(projects: List<ApipostProject>) {
        applying = true
        val model = DefaultComboBoxModel<String>()
        if (savedProjectId.isBlank()) model.addElement("")
        projects.forEach { model.addElement(display(it.name, it.projectId)) }
        if (savedProjectId.isNotBlank() &&
            (0 until model.size).none { extractProjectId(model.getElementAt(it)) == savedProjectId }
        ) {
            model.addElement(savedProjectId)
        }
        projectCombo.model = model
        if (savedProjectId.isBlank()) projectCombo.selectedIndex = 0 else selectProject(savedProjectId)
        applying = false
    }

    private fun selectProject(id: String) {
        if (id.isBlank()) return
        for (i in 0 until projectCombo.model.size) {
            if (extractProjectId(projectCombo.model.getElementAt(i)) == id) {
                projectCombo.selectedIndex = i
                return
            }
        }
    }

    /** Both rows share one busy state: a reload of either is a request in flight. */
    private fun setLoading(loading: Boolean) {
        teamLoadButton.isEnabled = !loading
        projectLoadButton.isEnabled = !loading
        teamLoadButton.text = if (loading) "…" else "Load"
        projectLoadButton.text = if (loading) "…" else "Refresh"
    }

    /** The team whose projects should be listed — read off the team row. */
    internal fun selectedTeamId(): String? = loadedTeams.getOrNull(teamCombo.selectedIndex)?.teamId

    /**
     * Programmatic selection for tests.
     *
     * Guarded the same way [applyTeams] and [applyProjects] are: setting the
     * selection fires the row's change event, and a programmatic change must not
     * start the fetch that a user's change starts.
     */
    internal fun selectTeam(index: Int) {
        applying = true
        teamCombo.selectedIndex = index
        applying = false
    }

    // endregion

    override fun resetFrom(settings: Settings?) {
        // Always read fresh — the passed-in `settings` is the shared umbrella,
        // not an ApipostSettings instance.
        val s = project.settings<ApipostSettings>()
        serverField.text = s.apipostServer ?: ApipostSettings.APIPOST_OPEN_HOST
        tokenField.text = s.apipostToken.orEmpty()
        savedProjectId = s.apipostProjectId.orEmpty()
        applyProjects(emptyList())
        // Fetch once on open, but only once per panel and only with a token:
        // the list is the whole point of the picker, and making the user press
        // Load to see anything is a pointless step. `resetFrom` also runs when
        // the user presses Reset, so without the flag a settings dialog would
        // re-request on every reset.
        if (tokenText().isNotBlank() && !autoLoaded) {
            autoLoaded = true
            loadTeamsThenProjects(silent = true)
        }
    }

    override fun applyTo(settings: Settings) {
        SettingBinder.getInstance(project).update(ApipostSettings::class) {
            apipostServer = serverText().trim().takeIf { it.isNotBlank() }
                ?: ApipostSettings.APIPOST_OPEN_HOST
            apipostToken = tokenText().takeIf { it.isNotBlank() }
            apipostProjectId = projectIdText().takeIf { it.isNotBlank() }
        }
    }

    override fun isModified(settings: Settings?): Boolean {
        val s = project.settings<ApipostSettings>()
        return serverText() != (s.apipostServer ?: ApipostSettings.APIPOST_OPEN_HOST) ||
                tokenText() != s.apipostToken.orEmpty() ||
                projectIdText() != s.apipostProjectId.orEmpty()
    }

    // --- Test-visible accessors ---

    internal fun serverText(): String = serverField.text.orEmpty()

    internal fun tokenText(): String = String(tokenField.password)

    /** The project id as the panel would persist it — parsed out of the combo. */
    internal fun projectIdText(): String = extractProjectId(selectedProjectEntry())

    internal fun setServer(value: String) {
        serverField.text = value
    }

    internal fun setToken(value: String) {
        tokenField.text = value
    }

    internal fun setProjectId(value: String) {
        applying = true
        projectCombo.model = DefaultComboBoxModel(arrayOf(value))
        projectCombo.selectedIndex = 0
        applying = false
    }

    /** Whether the project row accepts typing — it must not. */
    internal fun isProjectEditable(): Boolean = projectCombo.isEditable

    internal fun tokenColumns(): Int = tokenField.columns

    /** Both rows have their own reload control. */
    internal fun reloadButtonTexts(): List<String> = listOf(teamLoadButton.text, projectLoadButton.text)

    private fun selectedProjectEntry(): String = (projectCombo.selectedItem as? String).orEmpty().trim()

    companion object : IdeaLog {

        /** Width cap for the token field — see [tokenField]. */
        private const val TOKEN_COLUMNS = 30

        /** Placeholder shown on the team row while a fetch is in flight. */
        private const val LOADING = "Loading…"

        /** Matches the trailing `(id)` of a `name (id)` entry. */
        private val ID_SUFFIX = Regex("""\(([^()]+)\)\s*$""")

        /**
         * Reads a project id out of a combo entry.
         *
         * Entries fetched from `project/list` read `name (project_id)`, and an
         * id that is configured but not among them is kept as a bare entry — so
         * both forms have to round-trip unchanged rather than being discarded
         * as "not one of ours".
         */
        internal fun extractProjectId(entry: String): String {
            val trimmed = entry.trim()
            return ID_SUFFIX.find(trimmed)?.groupValues?.get(1)?.trim() ?: trimmed
        }

        private fun display(name: String, id: String): String = if (name.isBlank()) id else "$name ($id)"
    }
}
