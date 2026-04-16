package com.itangcent.easyapi.ide.dialog

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.itangcent.easyapi.core.threading.backgroundAsync
import com.itangcent.easyapi.core.threading.swing
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.ExportFormat
import com.itangcent.easyapi.exporter.model.OutputConfig
import com.itangcent.easyapi.exporter.model.PostmanExportOptions
import com.itangcent.easyapi.exporter.model.YapiExportOptions
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.exporter.model.path
import com.itangcent.easyapi.exporter.postman.CachedPostmanApiClient
import com.itangcent.easyapi.exporter.postman.PostmanApiClient
import com.itangcent.easyapi.exporter.postman.asCached
import com.itangcent.easyapi.http.HttpClientProvider
import com.itangcent.easyapi.settings.SettingBinder
import java.awt.*
import java.awt.event.ItemEvent
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer

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

    private var selectedPostmanCollection: PostmanCollectionItem? = null
    private var postmanDataLoaded = false
    private var postmanClient: CachedPostmanApiClient? = null

    private val preferencesPersistence = ExportDialogPreferencesPersistence(project)

    private val cardLayout = CardLayout()
    private val optionsPanel = JPanel(cardLayout)

    private val fileOptionsPanel: JPanel
    private val yapiOptionsPanel: JPanel
    private val postmanOptionsPanel: JPanel
    private val httpClientOptionsPanel: JPanel

    // --- Endpoint table ---
    private val endpointTableModel = EndpointTableModel(endpoints)
    private val endpointTable = JBTable(endpointTableModel)
    private val selectAllBtn = JButton("Select All")
    private val deselectAllBtn = JButton("Deselect All")

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

        selectAllBtn.addActionListener { endpointTableModel.selectAll() }
        deselectAllBtn.addActionListener { endpointTableModel.deselectAll() }

        init()
        updateOptionsPanel()
        setupEndpointTable()
    }

    private fun setupEndpointTable() {
        endpointTable.autoResizeMode = JBTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS

        val selectCol = endpointTable.columnModel.getColumn(COL_SELECT)
        selectCol.maxWidth = 50
        selectCol.minWidth = 40
        selectCol.preferredWidth = 40
        selectCol.resizable = false
        selectCol.cellRenderer = CheckboxRenderer()
        selectCol.cellEditor = CheckboxEditor()

        val methodCol = endpointTable.columnModel.getColumn(COL_METHOD)
        methodCol.maxWidth = 80
        methodCol.minWidth = 60
        methodCol.preferredWidth = 70
        methodCol.resizable = false
        methodCol.cellRenderer = MethodCellRenderer()

        val pathCol = endpointTable.columnModel.getColumn(COL_PATH)
        pathCol.preferredWidth = 250
        pathCol.minWidth = 100

        val nameCol = endpointTable.columnModel.getColumn(COL_NAME)
        nameCol.preferredWidth = 180
        nameCol.minWidth = 80
    }

    private fun loadDefaultValues() {
        val savedPrefs = preferencesPersistence.load()

        savedPrefs.lastExportFormat?.let { formatName ->
            val format = availableFormats.find { it.name == formatName }
            if (format != null) {
                formatComboBox.selectedItem = format
            }
        }

        savedPrefs.lastOutputDir?.takeIf { it.isNotBlank() }?.let { dir ->
            outputDirField.text = dir
        }

        savedPrefs.lastFileName?.takeIf { it.isNotBlank() }?.let { name ->
            fileNameField.text = name
        }

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

            val savedYapiToken = savedPrefs.lastYapiToken?.takeIf { it.isNotBlank() }
            val matchedIdx = if (savedYapiToken != null) {
                yapiProjects.indexOfFirst { it.token == savedYapiToken }
            } else -1

            if (matchedIdx >= 0) {
                yapiProjectComboBox.selectedIndex = matchedIdx
            } else {
                yapiProjectComboBox.selectedIndex = 0
            }
        } else {
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

        val httpClient = HttpClientProvider.getInstance(project).getClient()
        val client = PostmanApiClient(token, httpClient = httpClient).asCached()
        postmanClient = client

        backgroundAsync {
            try {
                val workspaces = client.listWorkspaces(useCache = true)
                val workspaceItems = workspaces.map { PostmanWorkspaceItem(it.name, it.id) }

                swing(ModalityState.any()) {
                    postmanWorkspaces.clear()
                    postmanWorkspaces.addAll(workspaceItems)

                    if (postmanWorkspaces.isNotEmpty()) {
                        postmanWorkspaceComboBox.model = DefaultComboBoxModel(
                            postmanWorkspaces.map { it.toString() }.toTypedArray()
                        )
                        val savedWs = preferencesPersistence.load().lastPostmanWorkspaceId
                        val savedIdx = postmanWorkspaces.indexOfFirst { it.id == savedWs }
                        postmanWorkspaceComboBox.selectedIndex = if (savedIdx >= 0) savedIdx else 0
                    } else {
                        postmanWorkspaceComboBox.model = DefaultComboBoxModel(arrayOf("No workspaces found"))
                        populateCollectionCombo(emptyList())
                    }
                }

                if (workspaceItems.isNotEmpty()) {
                    var wsIdx = -1
                    swing(ModalityState.any()) { wsIdx = postmanWorkspaceComboBox.selectedIndex }
                    if (wsIdx >= 0 && wsIdx < postmanWorkspaces.size) {
                        loadCollectionsForWorkspace(client, postmanWorkspaces[wsIdx].id)
                    }
                }
            } catch (_: Exception) {
                swing(ModalityState.any()) {
                    postmanWorkspaceComboBox.model = DefaultComboBoxModel(arrayOf("Failed to load"))
                    populateCollectionCombo(emptyList())
                }
            }
        }
    }

    private suspend fun loadCollectionsForWorkspace(client: CachedPostmanApiClient, workspaceId: String) {
        try {
            val collections = client.listCollections(workspaceId, useCache = true)
            swing(ModalityState.any()) {
                populateCollectionCombo(collections.map { PostmanCollectionItem(it.name, it.id, it.uid) })
            }
        } catch (_: Exception) {
            swing(ModalityState.any()) {
                populateCollectionCombo(emptyList())
            }
        }
    }

    private fun populateCollectionCombo(collections: List<PostmanCollectionItem>) {
        postmanCollectionItems.clear()
        postmanCollectionItems.addAll(collections)
        selectedPostmanCollection = null

        val savedPrefs = preferencesPersistence.load()
        val savedCollectionId = savedPrefs.lastPostmanCollectionId
        val savedCollectionName = savedPrefs.lastPostmanCollectionName

        val savedIdx = if (savedCollectionId != null) {
            collections.indexOfFirst { (it.uid ?: it.id) == savedCollectionId }
        } else -1

        val inferredName = defaultNewCollectionName()
        val matchIdx = if (savedIdx >= 0) {
            savedIdx
        } else {
            collections.indexOfFirst { it.name == inferredName }
                .takeIf { it >= 0 }
                ?: collections.indexOfFirst { it.name.startsWith("$inferredName-") }
        }

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
            val textToUse = savedCollectionName?.takeIf { it.isNotBlank() } ?: inferredName
            (postmanCollectionComboBox.editor.editorComponent as? JTextField)?.text = textToUse
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
            add(Box.createVerticalStrut(10))
            add(createEndpointPanel())
        }
    }

    private fun createFormatPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            add(JLabel("Export Format:"), BorderLayout.WEST)
            add(formatComboBox, BorderLayout.CENTER)
        }
    }

    private fun createEndpointPanel(): JPanel {
        val headerPanel = JPanel(BorderLayout()).apply {
            add(JLabel("API Endpoints:"), BorderLayout.WEST)
            add(JPanel().apply {
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                add(selectAllBtn)
                add(Box.createHorizontalStrut(4))
                add(deselectAllBtn)
            }, BorderLayout.EAST)
        }

        val scrollPane = JScrollPane(endpointTable).apply {
            preferredSize = Dimension(0, 200)
            minimumSize = Dimension(0, 120)
        }

        return JPanel(BorderLayout()).apply {
            add(headerPanel, BorderLayout.NORTH)
            add(scrollPane, BorderLayout.CENTER)
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

                val remembered = selectedPostmanCollection
                val isUpdate = remembered != null && collectionText == remembered.name

                val postmanOptions = PostmanExportOptions(
                    selectedWorkspaceId = ws?.id,
                    selectedWorkspaceName = ws?.name,
                    selectedCollectionId = if (isUpdate) (remembered.uid ?: remembered.id) else null,
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

        saveDialogState()

        super.doOKAction()
    }

    private fun saveDialogState() {
        val prefs = ExportDialogPreferences(
            lastExportFormat = selectedFormat.name,
            lastOutputDir = if (selectedFormat == ExportFormat.MARKDOWN || selectedFormat == ExportFormat.CURL) {
                outputConfig.outputDir
            } else null,
            lastFileName = if (selectedFormat == ExportFormat.MARKDOWN || selectedFormat == ExportFormat.CURL) {
                outputConfig.fileName
            } else null,
            lastPostmanWorkspaceId = if (selectedFormat == ExportFormat.POSTMAN) {
                val wsIdx = postmanWorkspaceComboBox.selectedIndex
                if (wsIdx >= 0 && wsIdx < postmanWorkspaces.size) {
                    postmanWorkspaces[wsIdx].id
                } else null
            } else null,
            lastPostmanWorkspaceName = if (selectedFormat == ExportFormat.POSTMAN) {
                val wsIdx = postmanWorkspaceComboBox.selectedIndex
                if (wsIdx >= 0 && wsIdx < postmanWorkspaces.size) {
                    postmanWorkspaces[wsIdx].name
                } else null
            } else null,
            lastPostmanCollectionId = if (selectedFormat == ExportFormat.POSTMAN) {
                selectedPostmanCollection?.let { it.uid ?: it.id }
            } else null,
            lastPostmanCollectionName = if (selectedFormat == ExportFormat.POSTMAN) {
                val collectionText = ((postmanCollectionComboBox.editor.editorComponent as? JTextField)?.text
                    ?: postmanCollectionComboBox.selectedItem?.toString())?.trim()
                    ?.removePrefix("(New): ")?.trim().orEmpty()
                selectedPostmanCollection?.name ?: collectionText.takeIf { it.isNotBlank() }
            } else null,
            lastYapiToken = if (selectedFormat == ExportFormat.YAPI) {
                outputConfig.yapiOptions?.selectedToken
            } else null
        )

        preferencesPersistence.save(prefs)
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
                val selectedEndpoints = dialog.endpointTableModel.getSelectedEndpoints()
                ExportDialogResult(
                    format = dialog.selectedFormat,
                    outputConfig = dialog.outputConfig,
                    selectedEndpoints = selectedEndpoints
                )
            } else {
                null
            }
        }
    }
}

data class ExportDialogResult(
    val format: ExportFormat,
    val outputConfig: OutputConfig,
    val selectedEndpoints: List<EndpointSelection> = emptyList()
)

data class EndpointSelection(
    val endpoint: ApiEndpoint
)

private const val COL_SELECT = 0
private const val COL_METHOD = 1
private const val COL_PATH = 2
private const val COL_NAME = 3

private class EndpointTableModel(
    endpoints: List<ApiEndpoint>
) : AbstractTableModel() {

    data class Row(
        val endpoint: ApiEndpoint,
        var selected: Boolean = true
    )

    val rows = endpoints.map { Row(it, true) }

    override fun getRowCount(): Int = rows.size

    override fun getColumnCount(): Int = 4

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? = when (columnIndex) {
        COL_SELECT -> rows[rowIndex].selected
        COL_METHOD -> rows[rowIndex].endpoint.httpMetadata?.method?.name
            ?: rows[rowIndex].endpoint.metadata.protocol

        COL_PATH -> rows[rowIndex].endpoint.path
        COL_NAME -> rows[rowIndex].endpoint.name ?: ""
        else -> null
    }

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        if (columnIndex == COL_SELECT) {
            rows[rowIndex].selected = aValue as Boolean
        }
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean =
        columnIndex == COL_SELECT

    override fun getColumnClass(columnIndex: Int): Class<*> = when (columnIndex) {
        COL_SELECT -> Boolean::class.java
        else -> String::class.java
    }

    override fun getColumnName(column: Int): String = when (column) {
        COL_SELECT -> ""
        COL_METHOD -> "Method"
        COL_PATH -> "Path"
        COL_NAME -> "Name"
        else -> ""
    }

    fun selectAll() {
        rows.forEach { it.selected = true }
        fireTableDataChanged()
    }

    fun deselectAll() {
        rows.forEach { it.selected = false }
        fireTableDataChanged()
    }

    fun getSelectedEndpoints(): List<EndpointSelection> {
        return rows.filter { it.selected }.map { EndpointSelection(it.endpoint) }
    }
}

private class MethodCellRenderer : DefaultTableCellRenderer() {

    override fun getTableCellRendererComponent(
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        val c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
        if (c is JLabel && value is String) {
            c.text = value.padEnd(6)
            if (!isSelected) {
                c.foreground = getMethodColor(value)
            }
        }
        return c
    }

    private fun getMethodColor(method: String): Color = when (method) {
        "GET" -> Color(0x61affe)
        "POST" -> Color(0x49cc90)
        "PUT" -> Color(0xfca130)
        "DELETE" -> Color(0xf93e3e)
        "PATCH" -> Color(0x50e3c2)
        "HEAD" -> Color(0x9012fe)
        "OPTIONS" -> Color(0x0d5aa7)
        "gRPC" -> Color(0x8B5CF6)
        else -> Color(0x999999)
    }
}

private class CheckboxRenderer : JCheckBox(), TableCellRenderer {

    init {
        horizontalAlignment = SwingConstants.CENTER
        isOpaque = true
    }

    override fun getTableCellRendererComponent(
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        this.isSelected = value as? Boolean ?: false
        if (isSelected) {
            foreground = table?.selectionForeground
            background = table?.selectionBackground
        } else {
            foreground = table?.foreground
            background = table?.background
        }
        return this
    }
}

private class CheckboxEditor : DefaultCellEditor(JCheckBox().apply {
    horizontalAlignment = SwingConstants.CENTER
})
