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
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.ExportFormat
import com.itangcent.easyapi.exporter.model.OutputConfig
import com.itangcent.easyapi.exporter.model.PostmanExportOptions
import com.itangcent.easyapi.exporter.model.YapiExportOptions
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
 * - YAPI (token-based project selection)
 * - Postman (workspace and collection selection)
 *
 * ## Features
 * - Format selection dropdown with dynamic options panel
 * - YAPI: Select existing project or input new token
 * - Postman: Workspace and collection selection with API integration
 * - File export: Output directory and filename configuration
 *
 * @param project The IntelliJ project context
 * @param endpointCount The number of endpoints to export (shown in title)
 * @param endpoints The list of endpoints to export (used to filter available formats)
 * @see ExportDialogResult for the dialog output
 */
class ExportDialog(
    private val project: Project,
    endpointCount: Int,
    private val endpoints: List<ApiEndpoint> = emptyList()
) : DialogWrapper(project) {

    private val actionContext by lazy {
        ActionContext.forProject(project)
    }
    private val availableFormats: Array<ExportFormat> = ExportFormat.entries
        .filter { it.isAvailableFor(endpoints) }
        .toTypedArray()

    private val formatComboBox = JComboBox(availableFormats).apply {
        if (availableFormats.isNotEmpty()) {
            selectedItem = availableFormats[0]
        }
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

    // --- YAPI fields ---
    // Existing projects parsed from settings yapiTokens ("module=token" per line)
    private data class YapiProject(val projectId: String, val token: String) {
        override fun toString(): String {
            val maskedToken = if (token.length > 8) token.take(4) + "..." + token.takeLast(4) else token
            return "$projectId ($maskedToken)"
        }
    }

    private val yapiProjects = mutableListOf<YapiProject>()
    private val yapiProjectComboBox = ComboBox<String>().apply { isEnabled = true }
    private val yapiNewTokenField = JBTextField().apply { columns = 30 }
    private val yapiModeComboBox = ComboBox(arrayOf(YAPI_MODE_SELECT, YAPI_MODE_NEW_TOKEN)).apply {
        addItemListener { e ->
            if (e.stateChange == ItemEvent.SELECTED) updateYapiMode()
        }
    }
    private val yapiSelectPanel = JPanel(BorderLayout(8, 0))
    private val yapiNewTokenPanel = JPanel(BorderLayout(8, 0))

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
    private val yapiOptionsPanel: JPanel
    private val postmanOptionsPanel: JPanel
    private val httpClientOptionsPanel: JPanel

    var selectedFormat: ExportFormat = ExportFormat.MARKDOWN
        private set

    var outputConfig: OutputConfig = OutputConfig.DEFAULT
        private set

    init {
        title = "Export API Endpoints ($endpointCount endpoints)"

        fileOptionsPanel = createFileOptionsPanel()
        yapiOptionsPanel = createYapiOptionsPanel()
        postmanOptionsPanel = createPostmanOptionsPanel()
        httpClientOptionsPanel = createHttpClientOptionsPanel()

        optionsPanel.add(fileOptionsPanel, FILE_OPTIONS)
        optionsPanel.add(yapiOptionsPanel, YAPI_OPTIONS)
        optionsPanel.add(postmanOptionsPanel, POSTMAN_OPTIONS)
        optionsPanel.add(httpClientOptionsPanel, HTTP_CLIENT_OPTIONS)

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
        val settings = SettingBinder.getInstance(project).read()

        // Parse YAPI projects from yapiTokens (format: "module=token" per line)
        val yapiTokens = settings.yapiTokens
        if (!yapiTokens.isNullOrBlank()) {
            yapiTokens.lines()
                .map { it.trim() }
                .filter { it.contains("=") && !it.startsWith("#") }
                .forEach { line ->
                    val projectId = line.substringBefore("=").trim()
                    val token = line.substringAfter("=").trim()
                    if (projectId.isNotBlank() && token.isNotBlank()) {
                        yapiProjects.add(YapiProject(projectId, token))
                    }
                }
        }
        if (yapiProjects.isNotEmpty()) {
            yapiProjectComboBox.model = DefaultComboBoxModel(yapiProjects.map { it.toString() }.toTypedArray())
            yapiProjectComboBox.selectedIndex = 0
        } else {
            // No existing projects — force new token mode
            yapiModeComboBox.selectedItem = YAPI_MODE_NEW_TOKEN
            yapiModeComboBox.isEnabled = false
        }

        updateYapiMode()
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

    private fun createYapiOptionsPanel(): JPanel {
        yapiSelectPanel.add(JLabel("Project:"), BorderLayout.WEST)
        yapiSelectPanel.add(yapiProjectComboBox, BorderLayout.CENTER)

        yapiNewTokenPanel.add(JLabel("Token:"), BorderLayout.WEST)
        yapiNewTokenPanel.add(yapiNewTokenField, BorderLayout.CENTER)

        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(JPanel(BorderLayout(8, 0)).apply {
                add(JLabel("Mode:"), BorderLayout.WEST)
                add(yapiModeComboBox, BorderLayout.CENTER)
            })
            add(Box.createVerticalStrut(5))
            add(yapiSelectPanel)
            add(yapiNewTokenPanel)
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

    private fun createHttpClientOptionsPanel(): JPanel {
        return JPanel().apply {
            layout = BorderLayout()
            add(JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(Box.createVerticalStrut(20))
                add(JLabel("HTTP Client files will be created in the scratches folder.").apply {
                    alignmentX = 0.5f
                })
                add(Box.createVerticalStrut(10))
                add(JLabel("The file will be automatically opened in the editor.").apply {
                    alignmentX = 0.5f
                })
                add(Box.createVerticalStrut(20))
            }, BorderLayout.CENTER)
        }
    }

    private fun updateYapiMode() {
        val isNew = yapiModeComboBox.selectedItem == YAPI_MODE_NEW_TOKEN
        yapiSelectPanel.isVisible = !isNew
        yapiNewTokenPanel.isVisible = isNew
    }

    private fun updateOptionsPanel() {
        val format = formatComboBox.selectedItem as ExportFormat
        when (format) {
            ExportFormat.YAPI -> cardLayout.show(optionsPanel, YAPI_OPTIONS)
            ExportFormat.POSTMAN -> {
                cardLayout.show(optionsPanel, POSTMAN_OPTIONS)
                ensurePostmanDataLoaded()
            }

            ExportFormat.HTTP_CLIENT -> {
                cardLayout.show(optionsPanel, HTTP_CLIENT_OPTIONS)
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
            ExportFormat.YAPI -> {
                val isNew = yapiModeComboBox.selectedItem == YAPI_MODE_NEW_TOKEN
                val yapiOptions = if (isNew) {
                    val token = yapiNewTokenField.text.trim()
                    if (token.isNotBlank()) {
                        YapiExportOptions(selectedToken = token, useCustomProject = true)
                    } else null
                } else {
                    val idx = yapiProjectComboBox.selectedIndex
                    if (idx >= 0 && idx < yapiProjects.size) {
                        val proj = yapiProjects[idx]
                        YapiExportOptions(
                            selectedToken = proj.token,
                            useCustomProject = false
                        )
                    } else null
                }
                OutputConfig(yapiOptions = yapiOptions)
            }

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

            ExportFormat.HTTP_CLIENT -> {
                OutputConfig()
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
        private const val YAPI_OPTIONS = "YAPI_OPTIONS"
        private const val POSTMAN_OPTIONS = "POSTMAN_OPTIONS"
        private const val HTTP_CLIENT_OPTIONS = "HTTP_CLIENT_OPTIONS"

        private const val YAPI_MODE_SELECT = "Select Existing Project"
        private const val YAPI_MODE_NEW_TOKEN = "Input New Token"

        fun show(
            project: Project,
            endpointCount: Int,
            endpoints: List<ApiEndpoint> = emptyList()
        ): ExportDialogResult? {
            val dialog = ExportDialog(project, endpointCount, endpoints)
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
