package com.itangcent.easyapi.ide.dialog

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBTextField
import com.itangcent.easyapi.core.context.ActionContext
import com.itangcent.easyapi.core.threading.backgroundAsync
import com.itangcent.easyapi.core.threading.swing
import com.itangcent.easyapi.exporter.model.ExportFormat
import com.itangcent.easyapi.exporter.model.OutputConfig
import com.itangcent.easyapi.exporter.model.PostmanExportOptions
import com.itangcent.easyapi.exporter.postman.CachedPostmanApiClient
import com.itangcent.easyapi.exporter.postman.PostmanApiClient
import com.itangcent.easyapi.exporter.postman.asCached
import com.itangcent.easyapi.http.HttpClientProvider
import com.itangcent.easyapi.settings.SettingBinder
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.event.ItemEvent
import javax.swing.*

/**
 * Dialog for configuring and initiating API exports.
 *
 * Provides a unified UI for exporting APIs to multiple formats:
 * - Markdown (file-based)
 * - Postman (workspace and collection selection)
 *
 * ## Features
 * - Format selection dropdown with dynamic options panel
 * - Postman: Workspace and collection selection with API integration
 * - File export: Output directory and filename configuration
 *
 * @param project The IntelliJ project context
 * @param endpointCount The number of endpoints to export (shown in title)
 * @see ExportDialogResult for the dialog output
 */
class ExportDialog(
    private val project: Project,
    endpointCount: Int
) : DialogWrapper(project) {

    private val actionContext by lazy {
        ActionContext.forProject(project)
    }
    private val formatComboBox = JComboBox(ExportFormat.values()).apply {
        selectedItem = ExportFormat.MARKDOWN
    }

    private val outputDirField = TextFieldWithBrowseButton().apply {
        text = project.basePath ?: ""
        addBrowseFolderListener(
            project, FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle("Select Output Directory")
                .withDescription("Choose the directory to export API files to")
        )
    }

    private val fileNameField = JBTextField().apply {
        text = "api_export"
        columns = 30
    }

    // --- Postman fields ---
    private data class PostmanCollectionItem(val name: String, val id: String, val uid: String? = null) {
        override fun toString(): String = name
    }

    private data class PostmanWorkspaceItem(val name: String, val id: String) {
        override fun toString(): String = name
    }

    private val postmanWorkspaces = mutableListOf<PostmanWorkspaceItem>()
    private val postmanCollectionItems = mutableListOf<PostmanCollectionItem>()
    private val postmanWorkspaceComboBox = ComboBox<String>().apply { isEditable = false }
    private val postmanCollectionComboBox = ComboBox<String>().apply { isEditable = true }
    private val postmanRefreshButton = JButton("Refresh").apply {
        addActionListener { refreshPostmanData() }
    }

    /** The collection the user last picked from the dropdown. null if "(New)" or not yet selected. */
    private var selectedPostmanCollection: PostmanCollectionItem? = null
    private var postmanDataLoaded = false
    private var postmanClient: CachedPostmanApiClient? = null

    private val cardLayout = CardLayout()
    private val optionsPanel = JPanel(cardLayout)

    private val fileOptionsPanel: JPanel
    private val postmanOptionsPanel: JPanel

    var selectedFormat: ExportFormat = ExportFormat.MARKDOWN
        private set

    var outputConfig: OutputConfig = OutputConfig.DEFAULT
        private set

    init {
        title = "Export API Endpoints ($endpointCount endpoints)"

        fileOptionsPanel = createFileOptionsPanel()
        postmanOptionsPanel = createPostmanOptionsPanel()

        optionsPanel.add(fileOptionsPanel, FILE_OPTIONS)
        optionsPanel.add(postmanOptionsPanel, POSTMAN_OPTIONS)

        loadDefaultValues()

        formatComboBox.addItemListener { event ->
            if (event.stateChange == ItemEvent.SELECTED) {
                updateOptionsPanel()
            }
        }

        init()
        updateOptionsPanel()
    }

    private fun loadDefaultValues() {
        // No default values to load currently
    }

    private fun loadPostmanDataFromApi() {
        val settings = SettingBinder.getInstance(project).read()
        val token = settings.postmanToken

        if (token.isNullOrBlank()) {
            postmanWorkspaceComboBox.model = DefaultComboBoxModel(arrayOf("No token configured"))
            postmanCollectionComboBox.model = DefaultComboBoxModel(arrayOf(defaultNewCollectionName()))
            (postmanCollectionComboBox.editor.editorComponent as? JTextField)?.text = defaultNewCollectionName()
            return
        }

        postmanWorkspaceComboBox.model = DefaultComboBoxModel(arrayOf("Loading..."))
        postmanCollectionComboBox.model = DefaultComboBoxModel(arrayOf("Loading..."))

        val httpClient = HttpClientProvider.getInstance(actionContext).getClient()
        val client = PostmanApiClient(token, httpClient = httpClient).asCached()
        postmanClient = client

        backgroundAsync {
            try {
                val workspaces = client.listWorkspaces(useCache = true)
                val workspaceItems = workspaces.map { PostmanWorkspaceItem(it.name, it.id) }

                swing {
                    postmanWorkspaces.clear()
                    postmanWorkspaces.addAll(workspaceItems)

                    if (postmanWorkspaces.isNotEmpty()) {
                        postmanWorkspaceComboBox.model = DefaultComboBoxModel(
                            postmanWorkspaces.map { it.toString() }.toTypedArray()
                        )
                        val savedWs = settings.postmanWorkspace
                        val savedIdx = postmanWorkspaces.indexOfFirst { it.id == savedWs }
                        postmanWorkspaceComboBox.selectedIndex = if (savedIdx >= 0) savedIdx else 0
                    } else {
                        postmanWorkspaceComboBox.model = DefaultComboBoxModel(arrayOf("No workspaces found"))
                        populateCollectionCombo(emptyList())
                    }
                }

                // Load collections for the initially selected workspace
                if (workspaceItems.isNotEmpty()) {
                    val wsIdx = swing { postmanWorkspaceComboBox.selectedIndex }
                    if (wsIdx >= 0 && wsIdx < postmanWorkspaces.size) {
                        loadCollectionsForWorkspace(client, postmanWorkspaces[wsIdx].id)
                    }
                }
            } catch (e: Exception) {
                swing {
                    postmanWorkspaceComboBox.model = DefaultComboBoxModel(arrayOf("Failed to load"))
                    populateCollectionCombo(emptyList())
                }
            }
        }
    }

    private suspend fun loadCollectionsForWorkspace(client: CachedPostmanApiClient, workspaceId: String) {
        try {
            val collections = client.listCollections(workspaceId, useCache = true)
            swing {
                populateCollectionCombo(collections.map { PostmanCollectionItem(it.name, it.id, it.uid) })
            }
        } catch (_: Exception) {
            swing {
                populateCollectionCombo(emptyList())
            }
        }
    }

    /**
     * Populates the editable collection combo with existing collections.
     * If an existing collection matches the inferred name (project name) — either
     * exactly or with a timestamp suffix from a previous export — select it by default.
     * Otherwise prepend a "(New): xxx" option.
     */
    private fun populateCollectionCombo(collections: List<PostmanCollectionItem>) {
        postmanCollectionItems.clear()
        postmanCollectionItems.addAll(collections)
        selectedPostmanCollection = null

        val inferredName = defaultNewCollectionName()
        // Exact match first, then prefix match (e.g. "my-project-20260328...")
        val matchIdx = collections.indexOfFirst { it.name == inferredName }
            .takeIf { it >= 0 }
            ?: collections.indexOfFirst { it.name.startsWith("$inferredName-") }

        val hasNewEntry = matchIdx < 0
        val entries = mutableListOf<String>()
        if (hasNewEntry) {
            entries.add("(New): $inferredName")
        }
        collections.forEach { entries.add(it.name) }

        postmanCollectionComboBox.model = DefaultComboBoxModel(entries.toTypedArray())

        if (matchIdx >= 0) {
            selectedPostmanCollection = collections[matchIdx]
            postmanCollectionComboBox.selectedIndex = matchIdx
            (postmanCollectionComboBox.editor.editorComponent as? JTextField)?.text = collections[matchIdx].name
        } else {
            postmanCollectionComboBox.selectedIndex = 0
            (postmanCollectionComboBox.editor.editorComponent as? JTextField)?.text = inferredName
        }
    }

    private fun defaultNewCollectionName(): String = project.name

    private fun refreshPostmanData() {
        postmanDataLoaded = false
        ensurePostmanDataLoaded()
    }

    override fun createCenterPanel(): JComponent {
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(createFormatPanel())
            add(Box.createVerticalStrut(10))
            add(optionsPanel)
        }
    }

    private fun createFormatPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            add(JLabel("Export Format:"), BorderLayout.WEST)
            add(formatComboBox, BorderLayout.CENTER)
        }
    }

    private fun createFileOptionsPanel(): JPanel {
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(JPanel(BorderLayout()).apply {
                add(JLabel("Output Directory:"), BorderLayout.WEST)
                add(outputDirField, BorderLayout.CENTER)
            })
            add(Box.createVerticalStrut(5))
            add(JPanel(BorderLayout()).apply {
                add(JLabel("File Name (without extension):"), BorderLayout.WEST)
                add(fileNameField, BorderLayout.CENTER)
            })
        }
    }

    private fun createPostmanOptionsPanel(): JPanel {
        // Track which collection the user picks from the dropdown
        postmanCollectionComboBox.addItemListener { e ->
            if (e.stateChange == ItemEvent.SELECTED) {
                val idx = postmanCollectionComboBox.selectedIndex
                val hasNewEntry = postmanCollectionItems.isEmpty()
                        || postmanCollectionComboBox.getItemAt(0)?.startsWith("(New)") == true
                val collectionIdx = if (hasNewEntry) idx - 1 else idx
                if (collectionIdx >= 0 && collectionIdx < postmanCollectionItems.size) {
                    selectedPostmanCollection = postmanCollectionItems[collectionIdx]
                } else {
                    selectedPostmanCollection = null
                }
            }
        }

        // Reload collections when workspace changes
        postmanWorkspaceComboBox.addItemListener { e ->
            if (e.stateChange == ItemEvent.SELECTED) {
                val idx = postmanWorkspaceComboBox.selectedIndex
                if (idx >= 0 && idx < postmanWorkspaces.size) {
                    postmanCollectionComboBox.model = DefaultComboBoxModel(arrayOf("Loading..."))
                    val client = postmanClient ?: return@addItemListener
                    backgroundAsync {
                        loadCollectionsForWorkspace(client, postmanWorkspaces[idx].id)
                    }
                }
            }
        }

        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(JPanel(BorderLayout(8, 0)).apply {
                add(JLabel("Workspace:"), BorderLayout.WEST)
                add(postmanWorkspaceComboBox, BorderLayout.CENTER)
                add(postmanRefreshButton, BorderLayout.EAST)
            })
            add(Box.createVerticalStrut(5))
            add(JPanel(BorderLayout(8, 0)).apply {
                add(JLabel("Collection:"), BorderLayout.WEST)
                add(postmanCollectionComboBox, BorderLayout.CENTER)
            })
        }
    }

    private fun updateOptionsPanel() {
        val format = formatComboBox.selectedItem as ExportFormat
        when (format) {
            ExportFormat.POSTMAN -> {
                cardLayout.show(optionsPanel, POSTMAN_OPTIONS)
                ensurePostmanDataLoaded()
            }

            else -> cardLayout.show(optionsPanel, FILE_OPTIONS)
        }
    }

    private fun ensurePostmanDataLoaded() {
        if (!postmanDataLoaded) {
            postmanDataLoaded = true
            loadPostmanDataFromApi()
        }
    }

    override fun doOKAction() {
        selectedFormat = formatComboBox.selectedItem as ExportFormat

        val config = when (selectedFormat) {
            ExportFormat.POSTMAN -> {
                val wsIdx = postmanWorkspaceComboBox.selectedIndex
                val ws = if (wsIdx >= 0 && wsIdx < postmanWorkspaces.size) postmanWorkspaces[wsIdx] else null

                val collectionText = ((postmanCollectionComboBox.editor.editorComponent as? JTextField)?.text
                    ?: postmanCollectionComboBox.selectedItem?.toString())?.trim()
                    ?.removePrefix("(New): ")?.trim().orEmpty()

                // If the user picked an existing collection from the dropdown and
                // hasn't changed the text, update that specific collection.
                // Otherwise create a new one with whatever name is in the editor.
                val remembered = selectedPostmanCollection
                val isUpdate = remembered != null && collectionText == remembered.name

                val postmanOptions = PostmanExportOptions(
                    selectedWorkspaceId = ws?.id,
                    selectedWorkspaceName = ws?.name,
                    selectedCollectionId = if (isUpdate) (remembered!!.uid ?: remembered.id) else null,
                    selectedCollectionName = collectionText.ifEmpty { null },
                    useCustomCollection = !isUpdate
                )
                OutputConfig(postmanOptions = postmanOptions)
            }

            else -> {
                OutputConfig(
                    outputDir = outputDirField.text.takeIf { it.isNotBlank() },
                    fileName = fileNameField.text.takeIf { it.isNotBlank() }
                )
            }
        }

        outputConfig = config
        super.doOKAction()
    }

    companion object {
        private const val FILE_OPTIONS = "FILE_OPTIONS"
        private const val POSTMAN_OPTIONS = "POSTMAN_OPTIONS"

        fun show(
            project: Project,
            endpointCount: Int
        ): ExportDialogResult? {
            val dialog = ExportDialog(project, endpointCount)
            return if (dialog.showAndGet()) {
                ExportDialogResult(
                    format = dialog.selectedFormat,
                    outputConfig = dialog.outputConfig
                )
            } else {
                null
            }
        }
    }
}

/**
 * Result from the [ExportDialog] after user confirmation.
 *
 * @param format The selected export format
 * @param outputConfig The output configuration for the export
 */
data class ExportDialogResult(
    val format: ExportFormat,
    val outputConfig: OutputConfig
)
