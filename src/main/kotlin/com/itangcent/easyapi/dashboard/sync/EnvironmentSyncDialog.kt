package com.itangcent.easyapi.dashboard.sync

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.itangcent.easyapi.exporter.postman.Workspace
import com.itangcent.easyapi.exporter.postman.model.PostmanEnvironmentInfo
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.script.env.EnvironmentService
import com.itangcent.easyapi.settings.SettingBinder
import kotlinx.coroutines.runBlocking
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.*

/**
 * Dialog for selecting environments and configuring sync options.
 *
 * Supports two modes:
 * - **Push**: Select local environments to push to Postman
 * - **Pull**: Select Postman environments to pull into local, with conflict resolution
 */
class EnvironmentSyncDialog(
    private val project: Project,
    private val mode: SyncMode,
    private val currentEnvName: String? = null
) : DialogWrapper(project) {

    private val environmentService = EnvironmentService.getInstance(project)
    private val syncService = EnvironmentSyncService.getInstance(project)

    private val envCheckBoxes = mutableListOf<JBCheckBox>()
    private val envDataList = mutableListOf<Any>() // String for push (env name), PostmanEnvironmentInfo for pull

    private val conflictStrategyCombo = JComboBox(
        arrayOf("Merge", "Replace", "Skip")
    )
    private val includeDisabledCheckBox = JBCheckBox("Include disabled variables").apply {
        isSelected = false
    }

    private val workspaceCombo = JComboBox<WorkspaceItem>()
    private var workspaces: List<Workspace> = emptyList()

    private var postmanEnvironments: List<PostmanEnvironmentInfo> = emptyList()

    /**
     * Set to `true` when the dialog is disposed, so background tasks can skip
     * Swing updates that would touch disposed components.
     */
    @Volatile
    private var disposed = false

    /**
     * Guards [workspaceCombo] against firing reloads while it is being
     * populated programmatically.
     */
    private var suppressWorkspaceListener = false

    enum class SyncMode { PUSH, PULL }

    init {
        title = when (mode) {
            SyncMode.PUSH -> "Push Environments to Postman"
            SyncMode.PULL -> "Pull Environments from Postman"
        }
        init()
    }

    override fun dispose() {
        disposed = true
        super.dispose()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(8, 8))

        // Workspace selector
        val workspacePanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(JBLabel("Workspace:"))
            add(workspaceCombo)
        }
        panel.add(workspacePanel, BorderLayout.NORTH)

        // Environment list with checkboxes
        val listPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }

        when (mode) {
            SyncMode.PUSH -> {
                val envs = environmentService.getEnvironments()
                if (envs.isEmpty()) {
                    listPanel.add(JBLabel("No local environments found."))
                } else {
                    for (env in envs) {
                        val cb = JBCheckBox("${env.name} (${env.scope.label()})")
                        if (env.name == currentEnvName) {
                            cb.isSelected = true
                        }
                        envCheckBoxes.add(cb)
                        envDataList.add(env.name)
                        listPanel.add(cb)
                    }
                }
            }

            SyncMode.PULL -> {
                listPanel.add(JBLabel("Loading environments from Postman..."))
            }
        }

        val scrollPane = JScrollPane(listPanel).apply {
            preferredSize = java.awt.Dimension(400, 250)
        }
        panel.add(scrollPane, BorderLayout.CENTER)

        // Options panel
        val optionsPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)

            // Select All / Deselect All
            val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                val selectAllBtn = JButton("Select All").apply {
                    addActionListener {
                        envCheckBoxes.forEach { it.isSelected = true }
                    }
                }
                val deselectAllBtn = JButton("Deselect All").apply {
                    addActionListener {
                        envCheckBoxes.forEach { it.isSelected = false }
                    }
                }
                add(selectAllBtn)
                add(deselectAllBtn)
            }
            add(buttonPanel)

            // Pull-specific options — conflict resolution and disabled variables in one row
            if (mode == SyncMode.PULL) {
                val pullOptionsPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                    add(JBLabel("Conflict resolution:"))
                    add(conflictStrategyCombo)
                    add(Box.createHorizontalStrut(16))
                    add(includeDisabledCheckBox)
                }
                add(pullOptionsPanel)
            }
        }
        panel.add(optionsPanel, BorderLayout.SOUTH)

        // Load workspaces and then environments
        loadWorkspaces(listPanel)

        return panel
    }

    override fun doValidate(): ValidationInfo? {
        val selected = getSelectedEnvData()
        if (selected.isEmpty()) {
            return ValidationInfo("Please select at least one environment.", envCheckBoxes.firstOrNull())
        }
        if (getSelectedWorkspaceId() == null) {
            return ValidationInfo("Please select a workspace.", workspaceCombo)
        }
        return null
    }

    /**
     * Returns the selected environment data based on mode:
     * - Push: List of local environment names
     * - Pull: List of Postman environment IDs
     */
    fun getSelectedEnvData(): List<Any> {
        return envCheckBoxes
            .filter { it.isSelected }
            .mapIndexedNotNull { index, _ ->
                if (index < envDataList.size) envDataList[index] else null
            }
    }

    /**
     * Returns the selected workspace ID, or null if no workspace is selected.
     */
    fun getSelectedWorkspaceId(): String? {
        val item = workspaceCombo.selectedItem as? WorkspaceItem
        return item?.id
    }

    /**
     * Returns the selected conflict strategy for pull mode.
     */
    fun getConflictStrategy(): ConflictStrategy {
        return when (conflictStrategyCombo.selectedItem) {
            "Replace" -> ConflictStrategy.REPLACE
            "Skip" -> ConflictStrategy.SKIP
            else -> ConflictStrategy.MERGE
        }
    }

    /**
     * Returns whether disabled variables should be included (pull mode).
     */
    fun isIncludeDisabled(): Boolean = includeDisabledCheckBox.isSelected

    private fun loadWorkspaces(listPanel: JPanel) {
        val settings = SettingBinder.getInstance(project).read()
        val defaultWorkspaceId = settings.postmanWorkspace

        if (!syncService.hasPostmanToken()) {
            workspaceCombo.addItem(WorkspaceItem("No workspaces — API token not configured", null))
            workspaceCombo.isEnabled = false
            return
        }

        // Register the workspace-change listener before fetching so the first
        // selection is not missed. `suppressWorkspaceListener` guards against
        // spurious reloads while we populate the combo programmatically.
        workspaceCombo.addActionListener {
            if (suppressWorkspaceListener) return@addActionListener
            if (mode == SyncMode.PULL && workspaces.isNotEmpty()) {
                loadPostmanEnvironments(listPanel)
            }
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Loading Postman workspaces", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val fetched = runBlocking { syncService.listWorkspaces() }
                    workspaces = fetched

                    SwingUtilities.invokeLater {
                        if (disposed) return@invokeLater
                        suppressWorkspaceListener = true
                        workspaceCombo.removeAllItems()
                        var defaultIndex = 0
                        workspaces.forEachIndexed { index, ws ->
                            workspaceCombo.addItem(WorkspaceItem(ws.name, ws.id))
                            if (ws.id == defaultWorkspaceId) {
                                defaultIndex = index
                            }
                        }
                        if (workspaces.isNotEmpty()) {
                            workspaceCombo.selectedIndex = defaultIndex
                        }
                        suppressWorkspaceListener = false

                        // Load environments for the default workspace
                        if (mode == SyncMode.PULL) {
                            loadPostmanEnvironments(listPanel)
                        }
                    }
                } catch (e: Exception) {
                    LOG.warn("loadWorkspaces: failed", e)
                    SwingUtilities.invokeLater {
                        if (disposed) return@invokeLater
                        workspaceCombo.addItem(WorkspaceItem("Failed to load workspaces", null))
                        workspaceCombo.isEnabled = false
                    }
                }
            }
        })
    }

    private fun loadPostmanEnvironments(listPanel: JPanel) {
        val workspaceId = getSelectedWorkspaceId()
        if (workspaceId == null) {
            listPanel.removeAll()
            listPanel.add(JBLabel("No workspace selected."))
            listPanel.revalidate()
            listPanel.repaint()
            return
        }

        if (!syncService.hasPostmanToken()) {
            listPanel.removeAll()
            listPanel.add(JBLabel("Postman API token not configured."))
            listPanel.revalidate()
            listPanel.repaint()
            return
        }

        // Clear existing checkboxes
        envCheckBoxes.clear()
        envDataList.clear()

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Loading Postman environments", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val fetched = runBlocking { syncService.listPostmanEnvironments(workspaceId) }
                    postmanEnvironments = fetched

                    SwingUtilities.invokeLater {
                        if (disposed) return@invokeLater
                        listPanel.removeAll()
                        if (postmanEnvironments.isEmpty()) {
                            listPanel.add(JBLabel("No environments found in this workspace."))
                        } else {
                            for (env in postmanEnvironments) {
                                val cb = JBCheckBox(env.name)
                                if (env.name == currentEnvName) {
                                    cb.isSelected = true
                                }
                                envCheckBoxes.add(cb)
                                envDataList.add(env.id)
                                listPanel.add(cb)
                            }
                        }
                        listPanel.revalidate()
                        listPanel.repaint()
                    }
                } catch (e: Exception) {
                    LOG.warn("loadPostmanEnvironments: failed", e)
                    postmanEnvironments = emptyList()
                    SwingUtilities.invokeLater {
                        if (disposed) return@invokeLater
                        listPanel.removeAll()
                        listPanel.add(JBLabel("Failed to load environments: ${e.message}"))
                        listPanel.revalidate()
                        listPanel.repaint()
                    }
                }
            }
        })
    }

    companion object : IdeaLog

    /**
     * Wrapper for displaying workspace name in combo box while retaining the ID.
     */
    data class WorkspaceItem(val displayName: String, val id: String?) {
        override fun toString(): String = displayName
    }
}
