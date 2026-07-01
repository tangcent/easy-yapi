package com.itangcent.easyapi.settings.ui

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.icons.AllIcons
import com.intellij.ui.CheckBoxList
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.*
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.FormBuilder
import com.intellij.ui.JBIntSpinner
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.UIUtil
import com.itangcent.easyapi.ai.AiApiKeyStore
import com.itangcent.easyapi.ai.AIService
import com.itangcent.easyapi.ai.AIServiceFactory
import com.itangcent.easyapi.ai.AiProvider
import com.itangcent.easyapi.ai.AiSettings
import com.itangcent.easyapi.ai.TokenSizeUtils
import com.itangcent.easyapi.ai.credentials.CredentialScanner
import com.itangcent.easyapi.ai.credentials.DefaultCredentialScanner
import com.itangcent.easyapi.ai.credentials.DetectionResult
import com.itangcent.easyapi.cache.AppCacheRepository
import com.itangcent.easyapi.cache.ProjectCacheRepository
import com.itangcent.easyapi.core.threading.backgroundAsync
import com.itangcent.easyapi.core.threading.swingAsync
import com.itangcent.easyapi.exporter.postman.PostmanApiClient
import com.itangcent.easyapi.exporter.postman.Workspace
import com.itangcent.easyapi.exporter.postman.asCached
import com.itangcent.easyapi.repository.DefaultRepositories
import com.itangcent.easyapi.repository.RepositoryConfig
import com.itangcent.easyapi.repository.RepositoryType
import com.itangcent.easyapi.exporter.model.PathSelector
import com.itangcent.easyapi.http.ApacheHttpClient
import com.itangcent.easyapi.extension.ExtensionConfigRegistry
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.util.json.GsonUtils
import com.itangcent.easyapi.util.text.ByteSizeUtil
import com.itangcent.easyapi.settings.HttpClientType
import com.itangcent.easyapi.settings.PostmanExportMode
import com.itangcent.easyapi.settings.PostmanJson5FormatType
import com.itangcent.easyapi.settings.Settings
import com.itangcent.easyapi.settings.YapiExportMode
import java.awt.*
import java.io.File
import javax.swing.*
import javax.swing.border.TitledBorder
import kotlin.concurrent.thread

/**
 * Interface for settings UI panels.
 *
 * Provides a contract for panels that display and edit plugin settings.
 * Each panel handles a specific category of settings.
 */
interface SettingsPanel {
    /** The UI component for this panel */
    val component: JComponent

    /**
     * Resets the panel UI to reflect the given settings.
     *
     * @param settings The settings to display
     */
    fun resetFrom(settings: Settings?)

    /**
     * Applies the panel UI values to the given settings.
     *
     * @param settings The settings to modify
     */
    fun applyTo(settings: Settings)

    /**
     * Checks if the panel has unsaved changes.
     *
     * @param settings The current settings
     * @return true if the panel has modifications
     */
    fun isModified(settings: Settings?): Boolean
}

/**
 * General settings panel for basic plugin configuration.
 *
 * Provides UI for:
 * - Framework support toggles (Feign, JAX-RS, Actuator)
 * - Logging level selection
 * - Output charset and demo settings
 * - Cache management
 */
class GeneralSettingsPanel(private val project: com.intellij.openapi.project.Project) : SettingsPanel {
    private val feignEnable = JBCheckBox("Enable Feign client support").apply {
        toolTipText = "Enable parsing of Feign client interfaces as API endpoints"
    }
    private val jaxrsEnable = JBCheckBox("Enable JAX-RS support", true).apply {
        toolTipText = "Enable parsing of JAX-RS annotations (@Path, @GET, etc.) as API endpoints"
    }
    private val actuatorEnable = JBCheckBox("Enable Spring Actuator support").apply {
        toolTipText = "Enable export of Spring Boot Actuator endpoints (e.g., /health, /metrics)"
    }
    private val autoScanEnabled = JBCheckBox("Enable automatic API scanning on file changes", true).apply {
        toolTipText = "Automatically re-scan APIs when source files are modified"
    }
    private val concurrentScanEnabled = JBCheckBox("Enable concurrent API scanning (experimental)", false).apply {
        toolTipText = "Use multiple threads for API scanning (may improve performance but is experimental)"
    }
    private val gutterIconEnabled = JBCheckBox("Show gutter icon on API methods", true).apply {
        toolTipText =
            "Show a gutter icon on API methods for quick navigation to the API Dashboard. Disable if it conflicts with other plugins."
    }
    private val switchNotice = JBCheckBox("Show notification on settings switch", true).apply {
        toolTipText = "Show a notification when switching between different setting profiles"
    }

    private val logLevelCombo = ComboBox(CommonSettingsHelper.VerbosityLevel.values())
    private val outputCharsetCombo = ComboBox(arrayOf("UTF-8", "GBK", "ISO-8859-1"))
    private val outputDemoCheckBox = JBCheckBox("Output demo in markdown", true).apply {
        toolTipText = "Include example/demo values in generated markdown documentation"
    }

    private val projectCacheSizeLabel = JBLabel("0 B")
    private val globalCacheSizeLabel = JBLabel("0 B")
    private val clearProjectCacheButton = JButton("Clear")
    private val clearGlobalCacheButton = JButton("Clear")

    private val cachePanel: JPanel

    private val repositoryTableModel = ListTableModel<RepositoryConfig>(
        arrayOf(
            object : ColumnInfo<RepositoryConfig, String>("Type") {
                override fun valueOf(item: RepositoryConfig?): String? = item?.displayName()
            },
            object : ColumnInfo<RepositoryConfig, String>("Path") {
                override fun valueOf(item: RepositoryConfig?): String? = item?.path
            },
            object : ColumnInfo<RepositoryConfig, Boolean>("Enable") {
                override fun valueOf(item: RepositoryConfig?): Boolean = item?.enabled ?: true
                override fun getColumnClass(): Class<*> = java.lang.Boolean::class.java
                override fun isCellEditable(item: RepositoryConfig?): Boolean = true
                override fun setValue(item: RepositoryConfig?, value: Boolean) {
                    item?.enabled = value
                }
            }
        ),
        mutableListOf()
    )

    private val repositoryTable = TableView(repositoryTableModel)

    init {
        val projectRow = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            add(JLabel("Project Cache:"))
            add(projectCacheSizeLabel)
            add(clearProjectCacheButton)
        }
        val globalRow = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            add(JLabel("Global Cache:"))
            add(globalCacheSizeLabel)
            add(clearGlobalCacheButton)
        }
        cachePanel = JPanel(GridLayout(0, 1, 0, 2)).apply {
            add(projectRow)
            add(globalRow)
        }

        clearProjectCacheButton.addActionListener {
            ProjectCacheRepository.getInstance(project).clear()
            refreshCacheSizes()
            Messages.showInfoMessage("Project cache cleared.", "Clear Cache")
        }

        clearGlobalCacheButton.addActionListener {
            AppCacheRepository.getInstance().clear()
            refreshCacheSizes()
            Messages.showInfoMessage("Global cache cleared.", "Clear Cache")
        }

        repositoryTable.setShowGrid(false)
        repositoryTable.intercellSpacing = Dimension(0, 0)
        repositoryTable.columnModel.getColumn(0).preferredWidth = 120
        repositoryTable.columnModel.getColumn(1).preferredWidth = 350
        repositoryTable.columnModel.getColumn(2).preferredWidth = 60
    }

    private fun refreshCacheSizes() {
        projectCacheSizeLabel.text = "..."
        globalCacheSizeLabel.text = "..."
        thread {
            LOG.info("refreshCacheSizes: project=${project.name}@${project.basePath}")
            var projectSize: Long = -1L
            try {
                val repo = ProjectCacheRepository.getInstance(project)
                projectSize = repo.cacheSize()
            } catch (e: Exception) {
                LOG.warn("Failed to get project cache size", e)
            }

            val globalSize = try {
                AppCacheRepository.getInstance().cacheSize()
            } catch (e: Exception) {
                LOG.warn("Failed to get app cache size", e)
                -1L
            }

            LOG.info("Cache refresh: projectSize=$projectSize, globalSize=$globalSize")

            SwingUtilities.invokeLater {
                projectCacheSizeLabel.text = when {
                    projectSize < 0 -> "N/A"
                    else -> ByteSizeUtil.format(projectSize)
                }
                projectCacheSizeLabel.toolTipText = null
                globalCacheSizeLabel.text = if (globalSize < 0) "N/A" else ByteSizeUtil.format(globalSize)
            }
        }
    }

    private fun createRepositoryPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Repositories",
                TitledBorder.LEFT,
                TitledBorder.TOP
            )
            val toolbarDecorator = ToolbarDecorator.createDecorator(repositoryTable)
                .setAddAction {
                    showAddRepositoryDialog()
                }
                .setRemoveAction {
                    val selected = repositoryTable.selectedRow
                    if (selected >= 0) {
                        repositoryTableModel.removeRow(selected)
                    }
                }
                .setEditAction {
                    val selected = repositoryTable.selectedRow
                    if (selected >= 0) {
                        val config = repositoryTableModel.getItem(selected)
                        showEditRepositoryDialog(config)
                    }
                }
                .disableUpDownActions()
            add(toolbarDecorator.createPanel(), BorderLayout.CENTER)
        }
    }

    private fun showAddRepositoryDialog() {
        val dialog = AddRepositoryDialog()
        if (dialog.showAndGet()) {
            repositoryTableModel.addRow(dialog.config)
        }
    }

    private fun showEditRepositoryDialog(config: RepositoryConfig) {
        val dialog = EditRepositoryDialog(config)
        if (dialog.showAndGet()) {
            repositoryTableModel.fireTableDataChanged()
        }
    }

    private inner class AddRepositoryDialog : DialogWrapper(false) {
        private val typeCombo = JComboBox(arrayOf("Maven Local", "Gradle Cache", "Custom"))
        private val pathField = JTextField(40)
        private val browseButton = JButton("Browse...")

        lateinit var config: RepositoryConfig

        init {
            title = "Add Repository"
            browseButton.addActionListener {
                val fileChooser = JFileChooser()
                fileChooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                fileChooser.isMultiSelectionEnabled = false
                if (fileChooser.showOpenDialog(contentPane) == JFileChooser.APPROVE_OPTION) {
                    pathField.text = fileChooser.selectedFile.absolutePath
                }
            }
            typeCombo.addActionListener {
                updatePathField()
            }
            updatePathField()
            init()
        }

        private fun updatePathField() {
            val isCustom = typeCombo.selectedItem == "Custom"
            pathField.isEnabled = isCustom
            browseButton.isEnabled = isCustom

            if (!isCustom) {
                val path = when (typeCombo.selectedItem) {
                    "Maven Local" -> DefaultRepositories.MAVEN_LOCAL.toString()
                    "Gradle Cache" -> DefaultRepositories.GRADLE_CACHE.toString()
                    else -> ""
                }
                pathField.text = path
            }
        }

        override fun createCenterPanel(): JComponent {
            return JPanel(GridLayout(0, 2, 4, 4)).apply {
                add(JLabel("Type:"))
                add(typeCombo)
                add(JLabel("Path:"))
                val pathPanel = JPanel(BorderLayout()).apply {
                    add(pathField, BorderLayout.CENTER)
                    add(browseButton, BorderLayout.EAST)
                }
                add(pathPanel)
                preferredSize = Dimension(500, preferredSize.height)
            }
        }

        override fun doOKAction() {
            val path = pathField.text.trim()
            if (path.isEmpty()) {
                return
            }
            val type = when (typeCombo.selectedItem) {
                "Maven Local" -> RepositoryType.MAVEN_LOCAL
                "Gradle Cache" -> RepositoryType.GRADLE_CACHE
                else -> RepositoryType.CUSTOM
            }
            config = RepositoryConfig(type, path)
            super.doOKAction()
        }
    }

    private inner class EditRepositoryDialog(private val config: RepositoryConfig) : DialogWrapper(false) {
        private val pathField = JTextField(40)
        private val browseButton = JButton("Browse...")

        init {
            title = "Edit Repository: ${config.displayName()}"
            pathField.text = config.path
            pathField.isEnabled = config.type == RepositoryType.CUSTOM
            browseButton.isEnabled = config.type == RepositoryType.CUSTOM
            browseButton.addActionListener {
                val fileChooser = JFileChooser()
                fileChooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                fileChooser.selectedFile = File(config.path)
                if (fileChooser.showOpenDialog(contentPane) == JFileChooser.APPROVE_OPTION) {
                    pathField.text = fileChooser.selectedFile.absolutePath
                }
            }
            init()
        }

        override fun createCenterPanel(): JComponent {
            return JPanel(GridLayout(0, 2, 4, 4)).apply {
                add(JLabel("Type:"))
                add(JLabel(config.displayName()))
                add(JLabel("Path:"))
                val pathPanel = JPanel(BorderLayout()).apply {
                    add(pathField, BorderLayout.CENTER)
                    add(browseButton, BorderLayout.EAST)
                }
                add(pathPanel)
                preferredSize = Dimension(500, preferredSize.height)
            }
        }

        override fun doOKAction() {
            if (config.type == RepositoryType.CUSTOM) {
                val path = pathField.text.trim()
                if (path.isEmpty()) {
                    return
                }
                config.path = path
            }
            super.doOKAction()
        }
    }

    override val component: JComponent = FormBuilder.createFormBuilder()
        .addComponent(
            createTitledPanel(
                "Framework Support", listOf(
                    feignEnable, jaxrsEnable, actuatorEnable
                )
            )
        )
        .addComponent(autoScanEnabled)
        .addComponent(concurrentScanEnabled)
        .addComponent(gutterIconEnabled)
        .addComponent(switchNotice)
        .addLabeledComponent("Log Level:", logLevelCombo)
        .addLabeledComponent("Output Charset:", outputCharsetCombo)
        .addComponent(outputDemoCheckBox)
        .addComponent(createTitledPanel("Cache Management", listOf(cachePanel)))
        .addComponent(createRepositoryPanel())
        .addComponentFillVertically(JPanel(), 0)
        .panel

    override fun resetFrom(settings: Settings?) {
        feignEnable.isSelected = settings?.feignEnable ?: false
        jaxrsEnable.isSelected = settings?.jaxrsEnable ?: true
        actuatorEnable.isSelected = settings?.actuatorEnable ?: false
        autoScanEnabled.isSelected = settings?.autoScanEnabled ?: true
        concurrentScanEnabled.isSelected = settings?.concurrentScanEnabled ?: false
        gutterIconEnabled.isSelected = settings?.gutterIconEnabled ?: true
        switchNotice.isSelected = settings?.switchNotice ?: true
        logLevelCombo.selectedItem = CommonSettingsHelper.VerbosityLevel.toLevel(settings?.logLevel ?: 0)
        outputCharsetCombo.selectedItem = settings?.outputCharset ?: "UTF-8"
        outputDemoCheckBox.isSelected = settings?.outputDemo ?: true
        refreshCacheSizes()

        val userRepos = settings?.grpcRepositories?.mapNotNull { RepositoryConfig.parse(it) }
        repositoryTableModel.items = if (!userRepos.isNullOrEmpty()) {
            userRepos.toMutableList()
        } else {
            DefaultRepositories.detectFromEnvironment().toMutableList()
        }
    }

    override fun applyTo(settings: Settings) {
        settings.feignEnable = feignEnable.isSelected
        settings.jaxrsEnable = jaxrsEnable.isSelected
        settings.actuatorEnable = actuatorEnable.isSelected
        settings.autoScanEnabled = autoScanEnabled.isSelected
        settings.concurrentScanEnabled = concurrentScanEnabled.isSelected
        settings.gutterIconEnabled = gutterIconEnabled.isSelected
        settings.switchNotice = switchNotice.isSelected
        settings.logLevel = (logLevelCombo.selectedItem as? CommonSettingsHelper.VerbosityLevel)?.level ?: 0
        settings.outputCharset = outputCharsetCombo.selectedItem?.toString() ?: "UTF-8"
        settings.outputDemo = outputDemoCheckBox.isSelected

        val repos = repositoryTableModel.items.map { RepositoryConfig.serialize(it) }
        settings.grpcRepositories = repos.toTypedArray()
    }

    override fun isModified(settings: Settings?): Boolean {
        val s = settings ?: return false
        val currentRepos = repositoryTableModel.items.map { RepositoryConfig.serialize(it) }.toTypedArray()
        return feignEnable.isSelected != s.feignEnable ||
                jaxrsEnable.isSelected != s.jaxrsEnable ||
                actuatorEnable.isSelected != s.actuatorEnable ||
                autoScanEnabled.isSelected != s.autoScanEnabled ||
                concurrentScanEnabled.isSelected != s.concurrentScanEnabled ||
                gutterIconEnabled.isSelected != s.gutterIconEnabled ||
                switchNotice.isSelected != s.switchNotice ||
                (logLevelCombo.selectedItem as? CommonSettingsHelper.VerbosityLevel)?.level != s.logLevel ||
                outputCharsetCombo.selectedItem?.toString() != s.outputCharset ||
                outputDemoCheckBox.isSelected != s.outputDemo ||
                !currentRepos.contentEquals(s.grpcRepositories)
    }

    companion object : IdeaLog
}

object CommonSettingsHelper {
    enum class VerbosityLevel(val level: Int, val displayName: String) {
        SILENT(100, "Silent"),
        ERROR(40, "Error"),
        WARN(30, "Warning"),
        INFO(20, "Info"),
        DEBUG(10, "Debug"),
        TRACE(0, "Trace");

        override fun toString(): String = displayName

        companion object {
            fun toLevel(level: Int): VerbosityLevel {
                return values().minByOrNull { kotlin.math.abs(it.level - level) } ?: TRACE
            }
        }
    }
}

class PostmanSettingsPanel : SettingsPanel {
    private val postmanToken = JBPasswordField().apply { columns = 30 }
    private val postmanWorkspace = ComboBox<String>().apply { isEditable = true }
    private val fetchWorkspacesButton = JButton("Fetch")
    private val postmanExportModeCombo = ComboBox(PostmanExportMode.values())
    private val postmanBuildExample = JBCheckBox("Build example", true).apply {
        toolTipText = "Generate example request/response bodies in Postman collections"
    }
    private val wrapCollection = JBCheckBox("Wrap collection").apply {
        toolTipText = "Wrap exported endpoints in a Postman collection folder instead of exporting directly"
    }
    private val autoMergeScript = JBCheckBox("Auto merge script").apply {
        toolTipText = "Automatically merge pre-request and test scripts when updating existing Postman collections"
    }
    private val postmanJson5FormatTypeCombo = ComboBox(PostmanJson5FormatType.values())
    private val postmanCollectionsField = JBTextArea(5, 40)

    // Cache fetched workspaces: id -> name
    private var fetchedWorkspaces: List<Pair<String, String>> = emptyList()

    init {
        fetchWorkspacesButton.addActionListener { fetchWorkspaces() }
    }

    override val component: JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent("Postman Token:", createTokenPanel())
        .addLabeledComponent("Workspace:", createWorkspacePanel())
        .addLabeledComponent("Export Mode:", postmanExportModeCombo)
        .addComponent(postmanBuildExample)
        .addComponent(wrapCollection)
        .addComponent(autoMergeScript)
        .addLabeledComponent("JSON5 Format Type:", postmanJson5FormatTypeCombo)
        .addLabeledComponent("Collections (module:collectionId per line):", JScrollPane(postmanCollectionsField))
        .addComponentFillVertically(JPanel(), 0)
        .panel

    private fun createTokenPanel(): JPanel {
        return JPanel(BorderLayout(4, 0)).apply {
            add(postmanToken, BorderLayout.CENTER)
            val helpLabel = JLabel("(Get token from Postman Integrations Dashboard)")
            helpLabel.foreground = UIUtil.getInactiveTextColor()
            add(helpLabel, BorderLayout.EAST)
        }
    }

    private fun createWorkspacePanel(): JPanel {
        return JPanel(BorderLayout(4, 0)).apply {
            add(postmanWorkspace, BorderLayout.CENTER)
            add(fetchWorkspacesButton, BorderLayout.EAST)
        }
    }

    private fun fetchWorkspaces() {
        val token = String(postmanToken.password).trim()
        if (token.isBlank()) {
            Messages.showWarningDialog("Please enter a Postman token first.", "Fetch Workspaces")
            return
        }
        fetchWorkspacesButton.isEnabled = false
        fetchWorkspacesButton.text = "..."
        val savedSelection = postmanWorkspace.selectedItem as? String
        thread {
            var errorMsg: String? = null
            val workspaces = try {
                LOG.info("fetchWorkspaces: creating ApacheHttpClient...")
                val httpClient = ApacheHttpClient()
                LOG.info("fetchWorkspaces: creating CachedPostmanApiClient with token length=${token.length}")
                val postmanClient = PostmanApiClient(
                    apiKey = token,
                    httpClient = httpClient
                ).asCached()
                LOG.info("fetchWorkspaces: calling listWorkspaces...")
                val result = kotlinx.coroutines.runBlocking {
                    postmanClient.listWorkspaces(useCache = false)
                }
                LOG.info("fetchWorkspaces: listWorkspaces returned ${result.size} items")
                result.map { it.id to it.name }
            } catch (e: Exception) {
                LOG.warn("fetchWorkspaces: exception: ${e.javaClass.name}: ${e.message}", e)
                errorMsg = "${e.javaClass.simpleName}: ${e.message}"
                emptyList()
            }
            LOG.info("Postman workspaces fetch: found ${workspaces.size} workspaces")
            SwingUtilities.invokeLater {
                fetchedWorkspaces = workspaces
                val model = DefaultComboBoxModel<String>()
                workspaces.forEach { (id, name) -> model.addElement("$name ($id)") }
                postmanWorkspace.model = model
                if (savedSelection != null) {
                    for (i in 0 until model.size) {
                        if (model.getElementAt(i).contains(savedSelection)) {
                            postmanWorkspace.selectedIndex = i
                            break
                        }
                    }
                }
                fetchWorkspacesButton.isEnabled = true
                fetchWorkspacesButton.text = "Fetch"
                if (workspaces.isEmpty()) {
                    val detail = if (errorMsg != null) "\n\nError: $errorMsg" else ""
                    Messages.showInfoMessage("No workspaces found. Check your token.$detail", "Fetch Workspaces")
                }
            }
        }
    }

    override fun resetFrom(settings: Settings?) {
        postmanToken.text = settings?.postmanToken ?: ""

        val currentWorkspace = settings?.postmanWorkspace ?: ""
        val cachedWorkspaces = loadCachedWorkspaces()

        val model = DefaultComboBoxModel<String>()
        if (cachedWorkspaces.isNotEmpty()) {
            cachedWorkspaces.forEach { (id, name) -> model.addElement("$name ($id)") }
        }
        val existingElements = (0 until model.size).map { model.getElementAt(it) }
        if (currentWorkspace.isNotBlank() && !existingElements.any { it.contains(currentWorkspace) }) {
            model.addElement(currentWorkspace)
        }
        if (model.size == 0) {
            model.addElement("")
        }
        postmanWorkspace.model = model
        if (currentWorkspace.isNotBlank()) {
            for (i in 0 until model.size) {
                if (model.getElementAt(i).contains(currentWorkspace)) {
                    postmanWorkspace.selectedIndex = i
                    break
                }
            }
        }

        postmanExportModeCombo.selectedItem = settings?.postmanExportMode?.let {
            runCatching { PostmanExportMode.valueOf(it) }.getOrNull()
        } ?: PostmanExportMode.CREATE_NEW
        postmanBuildExample.isSelected = settings?.postmanBuildExample ?: true
        wrapCollection.isSelected = settings?.wrapCollection ?: false
        autoMergeScript.isSelected = settings?.autoMergeScript ?: false
        postmanJson5FormatTypeCombo.selectedItem = settings?.postmanJson5FormatType?.let {
            runCatching { PostmanJson5FormatType.valueOf(it) }.getOrNull()
        } ?: PostmanJson5FormatType.EXAMPLE_ONLY
        postmanCollectionsField.text = settings?.postmanCollections ?: ""
    }

    private fun loadCachedWorkspaces(): List<Pair<String, String>> {
        return try {
            val cached = AppCacheRepository.getInstance().read("postman/workspaces.json")
            if (cached != null) {
                val workspaces = GsonUtils.fromJson<Array<Workspace>>(cached)
                workspaces.map { ws -> ws.id to ws.name }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            LOG.warn("Failed to load cached workspaces", e)
            emptyList()
        }
    }

    override fun applyTo(settings: Settings) {
        settings.postmanToken = String(postmanToken.password).takeIf { it.isNotBlank() }
        settings.postmanWorkspace = extractWorkspaceId((postmanWorkspace.selectedItem as? String).orEmpty())
        settings.postmanExportMode = (postmanExportModeCombo.selectedItem as? PostmanExportMode)?.name
        settings.postmanBuildExample = postmanBuildExample.isSelected
        settings.wrapCollection = wrapCollection.isSelected
        settings.autoMergeScript = autoMergeScript.isSelected
        settings.postmanJson5FormatType = (postmanJson5FormatTypeCombo.selectedItem as? PostmanJson5FormatType)?.name
            ?: PostmanJson5FormatType.EXAMPLE_ONLY.name
        settings.postmanCollections = postmanCollectionsField.text.takeIf { it.isNotBlank() }
    }

    /**
     * Extracts workspace ID from display format "name (id)" or returns raw value.
     */
    private fun extractWorkspaceId(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null
        // Match "name (id)" format
        val match = Regex(".*\\((.+)\\)$").find(trimmed)
        return match?.groupValues?.get(1)?.trim() ?: trimmed
    }

    override fun isModified(settings: Settings?): Boolean {
        val s = settings ?: return false
        return String(postmanToken.password) != (s.postmanToken ?: "") ||
                (extractWorkspaceId((postmanWorkspace.selectedItem as? String).orEmpty()) ?: "") != (s.postmanWorkspace
            ?: "") ||
                postmanExportModeCombo.selectedItem?.toString() != s.postmanExportMode ||
                postmanBuildExample.isSelected != s.postmanBuildExample ||
                wrapCollection.isSelected != s.wrapCollection ||
                autoMergeScript.isSelected != s.autoMergeScript ||
                postmanJson5FormatTypeCombo.selectedItem?.toString() != s.postmanJson5FormatType ||
                postmanCollectionsField.text != (s.postmanCollections ?: "")
    }

    companion object : IdeaLog
}

class YapiSettingsPanel : SettingsPanel {
    private val yapiServer = JBTextField()
    private val yapiTokens = JBTextArea(5, 40)
    private val enableUrlTemplating = JBCheckBox("Enable URL templating", true)
    private val switchNotice = JBCheckBox("Switch notice", true)
    private val yapiExportModeCombo = ComboBox(YapiExportMode.entries.toTypedArray())
    private val yapiReqBodyJson5 = JBCheckBox("Request body JSON5")
    private val yapiResBodyJson5 = JBCheckBox("Response body JSON5")

    override val component: JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent("Yapi Server:", yapiServer)
        .addLabeledComponent("Tokens (module=token per line):", JScrollPane(yapiTokens))
        .addComponent(enableUrlTemplating)
        .addComponent(switchNotice)
        .addLabeledComponent("Export Mode:", yapiExportModeCombo)
        .addComponent(yapiReqBodyJson5)
        .addComponent(yapiResBodyJson5)
        .addComponentFillVertically(JPanel(), 0)
        .panel

    override fun resetFrom(settings: Settings?) {
        yapiServer.text = settings?.yapiServer ?: ""
        yapiTokens.text = settings?.yapiTokens ?: ""
        enableUrlTemplating.isSelected = settings?.enableUrlTemplating ?: true
        switchNotice.isSelected = settings?.switchNotice ?: true
        yapiExportModeCombo.selectedItem = settings?.yapiExportMode?.let {
            runCatching { YapiExportMode.valueOf(it) }.getOrNull()
        } ?: YapiExportMode.ALWAYS_UPDATE
        yapiReqBodyJson5.isSelected = settings?.yapiReqBodyJson5 ?: false
        yapiResBodyJson5.isSelected = settings?.yapiResBodyJson5 ?: false
    }

    override fun applyTo(settings: Settings) {
        settings.yapiServer = yapiServer.text.takeIf { it.isNotBlank() }
        settings.yapiTokens = yapiTokens.text.takeIf { it.isNotBlank() }
        settings.enableUrlTemplating = enableUrlTemplating.isSelected
        settings.switchNotice = switchNotice.isSelected
        settings.yapiExportMode =
            (yapiExportModeCombo.selectedItem as? YapiExportMode)?.name ?: YapiExportMode.ALWAYS_UPDATE.name
        settings.yapiReqBodyJson5 = yapiReqBodyJson5.isSelected
        settings.yapiResBodyJson5 = yapiResBodyJson5.isSelected
    }

    override fun isModified(settings: Settings?): Boolean {
        val s = settings ?: return false
        return yapiServer.text != (s.yapiServer ?: "") ||
                yapiTokens.text != (s.yapiTokens ?: "") ||
                enableUrlTemplating.isSelected != s.enableUrlTemplating ||
                switchNotice.isSelected != s.switchNotice ||
                yapiExportModeCombo.selectedItem?.toString() != s.yapiExportMode ||
                yapiReqBodyJson5.isSelected != s.yapiReqBodyJson5 ||
                yapiResBodyJson5.isSelected != s.yapiResBodyJson5
    }
}

class HttpSettingsPanel : SettingsPanel {
    private val httpClientCombo = ComboBox(HttpClientType.values().map { it.value }.toTypedArray())
    private val httpTimeout = JBTextField("30")
    private val unsafeSsl = JBCheckBox("Allow unsafe SSL").apply {
        toolTipText = "Allow HTTPS connections to servers with untrusted or self-signed SSL certificates"
    }

    override val component: JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent("HTTP Client:", httpClientCombo)
        .addLabeledComponent("Timeout (seconds):", httpTimeout)
        .addComponent(unsafeSsl)
        .addComponentFillVertically(JPanel(), 0)
        .panel

    override fun resetFrom(settings: Settings?) {
        httpClientCombo.selectedItem = settings?.httpClient ?: HttpClientType.APACHE.value
        httpTimeout.text = settings?.httpTimeOut?.toString() ?: "30"
        unsafeSsl.isSelected = settings?.unsafeSsl ?: false
    }

    override fun applyTo(settings: Settings) {
        settings.httpClient = httpClientCombo.selectedItem?.toString() ?: HttpClientType.APACHE.value
        settings.httpTimeOut = httpTimeout.text.toIntOrNull() ?: 30
        settings.unsafeSsl = unsafeSsl.isSelected
    }

    override fun isModified(settings: Settings?): Boolean {
        val s = settings ?: return false
        return httpClientCombo.selectedItem?.toString() != s.httpClient ||
                httpTimeout.text != s.httpTimeOut.toString() ||
                unsafeSsl.isSelected != s.unsafeSsl
    }
}

class IntelligentSettingsPanel : SettingsPanel {
    private val queryExpanded = JBCheckBox("Query expanded", true).apply {
        toolTipText = "Expand query parameters into individual fields in the exported API documentation"
    }
    private val formExpanded = JBCheckBox("Form expanded", true).apply {
        toolTipText = "Expand form parameters into individual fields in the exported API documentation"
    }
    private val inferReturnMain = JBCheckBox("Infer return main type from wrapper", true).apply {
        toolTipText = "Automatically detect the actual data type inside generic response wrappers (e.g., Result<T>)"
    }
    private val enableUrlTemplating = JBCheckBox("Enable URL templating (RFC 6570)", true).apply {
        toolTipText = "Use RFC 6570 URI template syntax for path variables in exported URLs (e.g., /users/{id})"
    }
    private val pathMultiCombo = ComboBox(PathSelector.values().map { it.name }.toTypedArray())
    private val enumFieldAutoInferEnabled =
        JBCheckBox("Auto-infer enum value field for ambiguous references", false).apply {
            toolTipText = buildString {
                append("When enabled, auto-infer the enum value field for ambiguous references: ")
                append("enum-typed fields with a single instance field, or @see references without a specific field. ")
                append("Explicit references (@see Enum#field, @JsonValue, enum.use.custom) always work regardless of this setting.")
            }
        }

    override val component: JComponent = FormBuilder.createFormBuilder()
        .addComponent(queryExpanded)
        .addComponent(formExpanded)
        .addComponent(inferReturnMain)
        .addComponent(enableUrlTemplating)
        .addLabeledComponent("Path multi-select strategy:", pathMultiCombo)
        .addComponent(enumFieldAutoInferEnabled)
        .addComponentFillVertically(JPanel(), 0)
        .panel

    override fun resetFrom(settings: Settings?) {
        queryExpanded.isSelected = settings?.queryExpanded ?: true
        formExpanded.isSelected = settings?.formExpanded ?: true
        inferReturnMain.isSelected = settings?.inferReturnMain ?: true
        enableUrlTemplating.isSelected = settings?.enableUrlTemplating ?: true
        pathMultiCombo.selectedItem = settings?.pathMulti ?: "ALL"
        enumFieldAutoInferEnabled.isSelected = settings?.enumFieldAutoInferEnabled ?: false
    }

    override fun applyTo(settings: Settings) {
        settings.queryExpanded = queryExpanded.isSelected
        settings.formExpanded = formExpanded.isSelected
        settings.inferReturnMain = inferReturnMain.isSelected
        settings.enableUrlTemplating = enableUrlTemplating.isSelected
        settings.pathMulti = pathMultiCombo.selectedItem?.toString() ?: "ALL"
        settings.enumFieldAutoInferEnabled = enumFieldAutoInferEnabled.isSelected
    }

    override fun isModified(settings: Settings?): Boolean {
        val s = settings ?: return false
        return queryExpanded.isSelected != s.queryExpanded ||
                formExpanded.isSelected != s.formExpanded ||
                inferReturnMain.isSelected != s.inferReturnMain ||
                enableUrlTemplating.isSelected != s.enableUrlTemplating ||
                pathMultiCombo.selectedItem?.toString() != s.pathMulti ||
                enumFieldAutoInferEnabled.isSelected != s.enumFieldAutoInferEnabled
    }
}

class ExtensionConfigPanel : SettingsPanel {
    private val extensionList = CheckBoxList<String>()
    private val preview = JBTextArea()

    override val component: JComponent = JSplitPane(JSplitPane.HORIZONTAL_SPLIT).apply {
        val left = JPanel(BorderLayout())
        left.add(JScrollPane(extensionList), BorderLayout.CENTER)
        val right = JPanel(BorderLayout())
        preview.isEditable = false
        right.add(JScrollPane(preview), BorderLayout.CENTER)
        leftComponent = left
        rightComponent = right
        resizeWeight = 0.45
    }

    init {
        val allCodes = ExtensionConfigRegistry.allExtensions().map { it.code }
        extensionList.setItems(allCodes) { it }
        extensionList.setCheckBoxListListener { _, _ -> refreshPreview() }
        extensionList.addListSelectionListener { refreshPreview() }
    }

    override fun resetFrom(settings: Settings?) {
        val selected = ExtensionConfigRegistry.stringToCodes(settings?.extensionConfigs ?: "").toSet()
        ExtensionConfigRegistry.allExtensions().forEachIndexed { index, extension ->
            val isSelected =
                selected.contains(extension.code) || (extension.defaultEnabled && !selected.contains("-${extension.code}"))
            extensionList.setItemSelected(extension.code, isSelected)
        }
        refreshPreview()
    }

    override fun applyTo(settings: Settings) {
        settings.extensionConfigs = ExtensionConfigRegistry.codesToString(selectedCodes().toTypedArray())
    }

    override fun isModified(settings: Settings?): Boolean {
        val s = settings ?: return false
        val currentSelected = selectedCodes().toSet()
        val savedSelected = ExtensionConfigRegistry.stringToCodes(s.extensionConfigs ?: "").toSet()
        val defaultEnabled = ExtensionConfigRegistry.allExtensions()
            .filter { it.defaultEnabled }
            .map { it.code }
            .toSet()
        val effectiveSaved = savedSelected + defaultEnabled
        return currentSelected != effectiveSaved
    }

    private fun selectedCodes(): List<String> {
        return ExtensionConfigRegistry.allExtensions().mapNotNull { extension ->
            if (extensionList.isItemSelected(extension.code)) extension.code else null
        }
    }

    private fun refreshPreview() {
        val selectedIndex = extensionList.selectedIndex
        if (selectedIndex >= 0) {
            val allExtensions = ExtensionConfigRegistry.allExtensions()
            if (selectedIndex < allExtensions.size) {
                val extension = allExtensions[selectedIndex]
                val sb = StringBuilder()
                sb.appendLine("# Code: ${extension.code}")
                sb.appendLine("# Description: ${extension.description}")
                if (extension.onClass != null) {
                    sb.appendLine("# Condition: on-class ${extension.onClass}")
                }
                sb.appendLine("# Default: ${if (extension.defaultEnabled) "enabled" else "disabled"}")
                sb.appendLine()
                if (extension.content.isNotBlank()) {
                    sb.append(extension.content)
                } else {
                    sb.append("# (no content)")
                }
                preview.text = sb.toString()
                return
            }
        }
        preview.text = "# Select an extension to preview"
    }
}

class RemoteConfigPanel : SettingsPanel {
    private val list = CheckBoxList<String>()
    private val preview = JBTextArea()
    private val add = JButton("Add")
    private val remove = JButton("Remove")
    private val refresh = JButton("Refresh")
    private var remoteItems: MutableList<Pair<Boolean, String>> = mutableListOf()

    override val component: JComponent = JPanel(BorderLayout()).apply {
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(add)
            add(remove)
            add(refresh)
        }
        add(toolbar, BorderLayout.NORTH)
        val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT).apply {
            leftComponent = JScrollPane(list)
            preview.isEditable = false
            rightComponent = JScrollPane(preview)
            resizeWeight = 0.45
        }
        add(split, BorderLayout.CENTER)
    }

    init {
        list.setCheckBoxListListener { index, value ->
            if (index in remoteItems.indices) remoteItems[index] = value to remoteItems[index].second
            refreshPreview()
        }
        list.addListSelectionListener { refreshPreview() }
        add.addActionListener {
            val url =
                Messages.showInputDialog("Input remote config url", "Remote Config", Messages.getInformationIcon())
            if (!url.isNullOrBlank()) {
                remoteItems.add(true to url.trim())
                refreshList()
            }
        }
        remove.addActionListener {
            val selected = list.selectedIndices.sortedDescending()
            selected.forEach { index ->
                if (index in remoteItems.indices) remoteItems.removeAt(index)
            }
            refreshList()
        }
        refresh.addActionListener { refreshPreview(force = true) }
    }

    override fun resetFrom(settings: Settings?) {
        val raw = settings?.remoteConfig ?: emptyArray()
        remoteItems = raw.map {
            val clean = it.trim()
            if (clean.startsWith("!")) false to clean.removePrefix("!").trim() else true to clean
        }.filter { it.second.isNotBlank() }.toMutableList()
        refreshList()
    }

    override fun applyTo(settings: Settings) {
        settings.remoteConfig = remoteItems.map { if (it.first) it.second else "!${it.second}" }.toTypedArray()
    }

    override fun isModified(settings: Settings?): Boolean {
        val s = settings ?: return false
        val current = remoteItems.map { if (it.first) it.second else "!${it.second}" }
        return current != s.remoteConfig.toList()
    }

    private fun refreshList() {
        list.setItems(remoteItems.map { it.second }) { it }
        remoteItems.forEach { item -> list.setItemSelected(item.second, item.first) }
        refreshPreview()
    }

    private fun refreshPreview(force: Boolean = false) {
        val index = list.selectedIndex
        if (index !in remoteItems.indices) {
            preview.text = ""
            return
        }
        val target = remoteItems[index].second
        if (!force && target == preview.getClientProperty("url")) return
        preview.putClientProperty("url", target)
        preview.text = "Loading..."
        thread {
            val content =
                runCatching { java.net.URI(target).toURL().readText() }.getOrElse { "Load failed: ${it.message}" }
            SwingUtilities.invokeLater {
                if (list.selectedIndex == index) {
                    preview.text = content
                }
            }
        }
    }
}

/**
 * AI Assistant configuration section embedded in the Other tab.
 *
 * Form fields:
 * - Provider combo (pre-fills base URL + model on change if user hasn't edited)
 * - Base URL, API Key (PasswordSafe), Model
 * - Request Timeout, Max Requests spinners
 * - "Test Connection" button — builds `AiSettings` from
 * on-screen fields, calls `AIServiceFactory.create(settings).testConnection()`
 * on `backgroundAsync`, surfaces result via `NotificationUtils`)
 *
 * The API key round-trips through [PasswordSafe] directly, not through
 * [Settings]. All other fields are backed by [Settings] and tracked via
 * [resetFrom]/[applyTo]/[isModified].
 */
class AiAssistantSection : SettingsPanel {

    private val providerCombo = ComboBox(AiProvider.values().map { it.displayName }.toTypedArray()).apply {
        toolTipText =
            "Pick your LLM provider. Use 'Custom (OpenAI-compatible)' for a LiteLLM proxy, LM Studio, or vLLM."
    }
    private val baseUrlField = JBTextField().apply {
        columns = 28
        toolTipText = "API base URL — auto-filled from the provider; editable."
    }
    private val apiKeyField = JBPasswordField().apply {
        columns = 28
        toolTipText =
            "Stored securely in PasswordSafe; never written to settings XML. Optional for providers that don't require a key."
    }

    /**
     * Default masking character for [apiKeyField], captured once so the eye
     * toggle can restore it after revealing (the LaF's echo char, not a
     * hard-coded bullet).
     */
    private val apiKeyEchoChar: Char = apiKeyField.echoChar

    /**
     * Toggle that reveals [apiKeyField]'s value by clearing its echo char, and
     * re-masks by restoring it. Holding the key visible is a deliberate user
     * action; the default is masked.
     */
    private val revealApiKeyButton: JButton = JButton().apply {
        icon = AllIcons.Actions.Preview
        toolTipText = "Show API key"
        isFocusable = false
        margin = Insets(0, 2, 0, 2)
        addActionListener {
            val revealed = apiKeyField.echoChar == 0.toChar()
            if (revealed) {
                apiKeyField.echoChar = apiKeyEchoChar
                icon = AllIcons.Actions.Preview
                toolTipText = "Show API key"
            } else {
                apiKeyField.echoChar = 0.toChar()
                icon = AllIcons.Actions.PreviewDetails
                toolTipText = "Hide API key"
            }
        }
    }
    private val modelField = JBTextField().apply {
        columns = 22
        toolTipText = "Model name — auto-filled from the provider; editable."
    }
    private val timeoutSpinner = JBIntSpinner(60, 5, 300).apply {
        toolTipText = "LLM request timeout in seconds (default 60)."
    }
    private val maxAgentStepsSpinner = JBIntSpinner(100, 1, 1000).apply {
        toolTipText =
            "The maximum number of requests to allow per-turn when using an agent. When the limit is reached, will ask to confirm to continue."
    }

    /**
     * Dropdown option for the Context Window combo. [tokens] is the stored
     * value; [label] is shown in the UI. The presets themselves live in
     * [TokenSizeUtils].
     */
    data class ContextWindowOption(val tokens: Int, val label: String) {
        override fun toString(): String = label
    }

    private val contextWindowOptions: Array<ContextWindowOption> =
        TokenSizeUtils.presets
            .map { label -> ContextWindowOption(TokenSizeUtils.parse(label), label) }
            .toTypedArray()

    private val contextWindowCombo = ComboBox(contextWindowOptions).apply {
        isEditable = true
        toolTipText =
            "Model context window in tokens. Used to derive how much conversation history the agent keeps."
    }
    private val testConnectionButton = JButton("Test Connection").apply {
        toolTipText = "Send a tiny request to verify the provider, key, and model."
    }
    private val autoDetectButton = JButton("Auto-detect").apply {
        toolTipText = "Scan env vars, CLI tool configs, and local servers for AI credentials."
    }

    /**
     * Inline status label. The Settings dialog is
     * modal, so `NotificationUtils` balloons are suppressed — Test Connection /
     * Auto-detect feedback is shown here instead.
     */
    private val statusLabel = JBLabel(" ").apply {
        foreground = UIUtil.getContextHelpForeground()
    }

    private var userEditedBaseUrl = false
    private var userEditedModel = false
    private var userEditedApiKey = false

    /**
     * When true, the user manually changed the Context Window spinner, so
     * provider-switch auto-fill no longer touches it. Reset to false in
     * `resetFrom` after the spinner is programmatically set.
     */
    private var userEditedContextWindow = false

    /**
     * When true, document listeners on baseUrl/model/apiKey fields are
     * suppressed so programmatic updates (e.g. `preFillFromHit`,
     * `resetFrom`) don't mark the field as user-edited.
     */
    private var suppressUserEditedListeners = false

    override val component: JComponent = FormBuilder.createFormBuilder()
        .addComponent(
            createTitledPanel(
                "AI Assistant", listOf(
            compactRow("Provider:", providerCombo),
            compactRow("Base URL:", baseUrlField),
            compactRow("API Key:", apiKeyField, revealApiKeyButton),
            compactRow("Model:", modelField),
            compactRow("Request Timeout (sec):", timeoutSpinner),
            compactRow("Max Requests:", maxAgentStepsSpinner),
            compactRow("Context Window:", contextWindowCombo),
            JPanel(FlowLayout(FlowLayout.LEFT, 6, 2)).apply {
                add(testConnectionButton)
                add(autoDetectButton)
            },
            JPanel(FlowLayout(FlowLayout.LEFT, 6, 2)).apply { add(statusLabel) }
        )))
        .panel

    init {
        testConnectionButton.addActionListener { onTestConnectionClicked() }

        providerCombo.addActionListener {
            val provider = currentProvider()
            preFillProviderDefaults(provider)
            updateContextWindowTooltip(provider)
        }

        baseUrlField.document.addDocumentListener(simpleDocListener {
            if (!suppressUserEditedListeners) userEditedBaseUrl = true
        })
        modelField.document.addDocumentListener(simpleDocListener {
            if (!suppressUserEditedListeners) userEditedModel = true
        })
        apiKeyField.document.addDocumentListener(simpleDocListener {
            if (!suppressUserEditedListeners) userEditedApiKey = true
        })
        contextWindowCombo.addActionListener {
            if (!suppressUserEditedListeners) {
                userEditedContextWindow = true
            }
        }
        // Also mark as edited when the user types a custom value in the editor.
        (contextWindowCombo.editor.editorComponent as? JTextField)?.document?.addDocumentListener(
            simpleDocListener { if (!suppressUserEditedListeners) userEditedContextWindow = true }
        )

        autoDetectButton.addActionListener { onAutoDetectClicked() }
    }

    /**
     * Reflects the provider's default context window in the combo tooltip so a
     * user who hasn't touched the field can see the provider's typical value.
     */
    private fun updateContextWindowTooltip(provider: AiProvider) {
        val eff = provider.contextWindow
        contextWindowCombo.toolTipText =
            "Model context window in tokens. ${provider.displayName} default is $eff. " +
                    "Used to derive how much conversation history the agent keeps."
    }

    /**
     * Pre-fills base URL, model, and context window from the [provider]'s
     * defaults — but only for fields the user hasn't manually edited. A real
     * custom edit is sticky and survives provider switches; programmatic fills
     * (here, or from `reset`/auto-detect) don't count as edits.
     *
     * Edit-tracking is suppressed around the writes so the fields' own document
     * listeners don't latch `userEdited*` to `true` on a provider switch — that
     * would otherwise make the pre-fill stick only on the *first* switch.
     *
     * The API key is always cleared on switch: a key is specific to a provider,
     * so reusing it against a different one would just produce a 401. The user
     * can re-enter it (or use Auto-detect). Recovery: cancelling the dialog
     * abandons the clear, since PasswordSafe is only written on Apply.
     */
    private fun preFillProviderDefaults(provider: AiProvider) {
        val previous = suppressUserEditedListeners
        suppressUserEditedListeners = true
        try {
            if (!userEditedBaseUrl) {
                baseUrlField.text = provider.defaultBaseUrl ?: ""
            }
            if (!userEditedModel) {
                modelField.text = provider.defaultModel ?: ""
            }
            // Auto-set the context window to the provider's default when the
            // user hasn't manually edited it. This makes the effective token
            // budget visible in the UI.
            if (!userEditedContextWindow) {
                setContextWindowValue(provider.contextWindow)
            }
            // Always clear: a key is provider-specific. Latching userEditedApiKey
            // would freeze it empty, so we DON'T set that flag here — the field's
            // own listener does, but only on a real keystroke (the empty string
            // write is suppressed above).
            apiKeyField.text = ""
        } finally {
            suppressUserEditedListeners = previous
        }
    }

    /**
     * Sets the context window combo to [tokens]. If [tokens] matches a preset,
     * the preset is selected; otherwise the raw number is placed in the
     * editable editor. Listeners are suppressed so programmatic sets don't
     * mark the field as user-edited.
     */
    private fun setContextWindowValue(tokens: Int) {
        val previous = suppressUserEditedListeners
        suppressUserEditedListeners = true
        try {
            val match = contextWindowOptions.firstOrNull { it.tokens == tokens }
            if (match != null) {
                contextWindowCombo.selectedItem = match
            } else {
                contextWindowCombo.selectedItem = tokens.toString()
            }
        } finally {
            suppressUserEditedListeners = previous
        }
    }

    /**
     * Reads the current context window value from the combo. Handles preset
     * selections, raw integer strings, and "8k"/"1m" style shorthand via
     * [TokenSizeUtils.parse].
     */
    private fun contextWindowValue(): Int {
        val item = contextWindowCombo.selectedItem ?: return 0
        return when (item) {
            is ContextWindowOption -> item.tokens
            is Number -> item.toInt()
            is String -> TokenSizeUtils.parse(item)
            else -> 0
        }
    }

    // -------------------------------------------------------------------------
    // Test Connection
    // -------------------------------------------------------------------------

    /**
     * Factory seam for the AI service. Production uses [AIServiceFactory.create];
     * tests override this to inject a fake.
     */
    internal var aiServiceFactory: (AiSettings) -> AIService =
        { settings -> AIServiceFactory.create(settings) }

    /**
     * Result handler seam (mirrors [detectHandler]).
     *
     * Production is `null` — the handler posts a notification. Tests override
     * to capture the [Result] without going through the notification system.
     */
    internal var testConnectionResultHandler: ((Result<String>) -> Unit)? = null

    private fun onTestConnectionClicked() {
        // Build AiSettings from the on-screen fields (not from persisted settings).
        val settings = AiSettings(
            provider = currentProvider(),
            baseUrl = baseUrlField.text.trim(),
            apiKey = String(apiKeyField.password),
            model = modelField.text.trim(),
            requestTimeoutSec = (timeoutSpinner.value as Number).toInt(),
            maxRequests = (maxAgentStepsSpinner.value as Number).toInt()
        )
        testConnectionButton.isEnabled = false
        val previousLabel = testConnectionButton.text
        testConnectionButton.text = "Testing…"
        setStatus("Testing connection…", ok = true)

        backgroundAsync {
            val result = runCatching { aiServiceFactory(settings).testConnection() }
                .getOrElse { Result.failure(it) }
            // The Settings dialog is modal, so a plain `swingAsync` (which uses
            // ModalityState.nonModal()) would be deferred until the dialog closes.
            // Use ModalityState.any() so the result surfaces while the dialog is open.
            swingAsync(ModalityState.any()) {
                testConnectionButton.isEnabled = true
                testConnectionButton.text = previousLabel
                if (testConnectionResultHandler != null) {
                    testConnectionResultHandler?.invoke(result)
                } else {
                    result.fold(
                        onSuccess = { msg ->
                            setStatus("Connection OK: $msg", ok = true)
                        },
                        onFailure = { err ->
                            setStatus("Connection failed: ${err.message}", ok = false)
                            // Modal dialog is visible over the (modal) settings dialog,
                            // unlike a balloon notification.
                            Messages.showErrorDialog(
                                component,
                                err.message ?: "Unknown error",
                                "EasyApi AI — Connection Failed"
                            )
                        }
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Auto-detect
    // -------------------------------------------------------------------------

    /**
     * Scan result handler seam.
     *
     * Tests override this to capture the [DetectionResult] without launching
     * the real background coroutine. Production wires [DefaultCredentialScanner]
     * and posts notifications / pre-fills fields on the Swing thread.
     */
    internal var detectHandler: ((DetectionResult) -> Unit)? = null

    /** Scanner instance seam (overridable in tests). */
    internal var credentialScanner: CredentialScanner = DefaultCredentialScanner()

    private fun onAutoDetectClicked() {
        // Disable + relabel while the scan runs.
        autoDetectButton.isEnabled = false
        val previousLabel = autoDetectButton.text
        autoDetectButton.text = "Detecting…"

        backgroundAsync {
            val result = runCatching { credentialScanner.scan() }
                .getOrElse {
                    // ModalityState.any(): see onTestConnectionClicked for rationale.
                    swingAsync(ModalityState.any()) {
                        setStatus("Auto-detect failed: ${it.message}", ok = false)
                    }
                    DetectionResult.Miss
                }
            // ModalityState.any(): see onTestConnectionClicked for rationale.
            swingAsync(ModalityState.any()) {
                autoDetectButton.isEnabled = true
                autoDetectButton.text = previousLabel
                if (detectHandler != null) {
                    detectHandler?.invoke(result)
                } else {
                    applyDetectionResult(result)
                }
            }
        }
    }

    /**
     * Pre-fills form fields from a successful scan and surfaces the result in
     * the inline status label (balloons are suppressed over the modal
     * settings dialog).
     */
    internal fun applyDetectionResult(result: DetectionResult) {
        when (result) {
            is DetectionResult.Miss -> {
                setStatus(
                    "No local AI credentials found. Enter your API key manually.",
                    ok = true
                )
            }

            is DetectionResult.Hit -> {
                preFillFromHit(result)
                setStatus(
                    "Detected ${result.provider.displayName} from ${result.sourceLabel}. Click Apply to save.",
                    ok = true
                )
            }

            is DetectionResult.MultipleFound -> {
                preFillFromHit(result.primary)
                val others = result.others.joinToString(", ") { "${it.provider.displayName} (${it.sourceLabel})" }
                setStatus(
                    "Detected ${result.primary.provider.displayName} from ${result.primary.sourceLabel}. " +
                            "Also found: $others. Apply to save, or switch provider manually.",
                    ok = true
                )
            }
        }
    }

    private fun preFillFromHit(hit: DetectionResult.Hit) {
        suppressUserEditedListeners = true
        try {
            providerCombo.selectedIndex = hit.provider.ordinal
            // Respect user-edited fields.
            if (!userEditedApiKey && !hit.apiKey.isNullOrBlank()) {
                apiKeyField.text = hit.apiKey
            }
            if (!userEditedBaseUrl && !hit.baseUrl.isNullOrBlank()) {
                baseUrlField.text = hit.baseUrl
            } else if (!userEditedBaseUrl) {
                baseUrlField.text = hit.provider.defaultBaseUrl ?: ""
            }
            if (!userEditedModel) {
                modelField.text = hit.model ?: hit.provider.defaultModel ?: ""
            }
        } finally {
            suppressUserEditedListeners = false
        }
    }

    private fun currentProvider(): AiProvider =
        AiProvider.values().getOrElse(providerCombo.selectedIndex) { AiProvider.OPENAI }

    override fun resetFrom(settings: Settings?) {
        val s = settings ?: return
        val provider = runCatching { AiProvider.valueOf(s.aiProvider) }.getOrDefault(AiProvider.OPENAI)
        providerCombo.selectedIndex = provider.ordinal
        // Show exactly what's in settings — do NOT pre-fill from provider defaults here.
        // Pre-fill only happens on provider combo change (see init block).
        baseUrlField.text = s.aiBaseUrl
        modelField.text = s.aiModel
        timeoutSpinner.value = s.aiRequestTimeoutSec.coerceIn(5, 300)
        maxAgentStepsSpinner.value = s.aiMaxRequests.coerceIn(1, 1000)
        setContextWindowValue(s.aiContextWindow)
        updateContextWindowTooltip(provider)
        // API key from PasswordSafe
        apiKeyField.text = AiApiKeyStore.loadApiKey()
        // Reset edit flags AFTER writing fields — the document listeners above would
        // have set them to true.
        userEditedBaseUrl = false
        userEditedModel = false
        userEditedApiKey = false
        userEditedContextWindow = false
    }

    override fun applyTo(settings: Settings) {
        val provider = currentProvider()
        settings.aiProvider = provider.name
        settings.aiBaseUrl = baseUrlField.text.trim()
        settings.aiModel = modelField.text.trim()
        settings.aiRequestTimeoutSec = (timeoutSpinner.value as Number).toInt()
        settings.aiMaxRequests = (maxAgentStepsSpinner.value as Number).toInt()
        settings.aiContextWindow = contextWindowValue()
        // API key to PasswordSafe
        val key = String(apiKeyField.password)
        AiApiKeyStore.saveApiKey(key)
    }

    override fun isModified(settings: Settings?): Boolean {
        val s = settings ?: return false
        val provider = currentProvider()
        if (provider.name != s.aiProvider) return true
        if (baseUrlField.text.trim() != s.aiBaseUrl) return true
        if (modelField.text.trim() != s.aiModel) return true
        if ((timeoutSpinner.value as Number).toInt() != s.aiRequestTimeoutSec) return true
        if ((maxAgentStepsSpinner.value as Number).toInt() != s.aiMaxRequests) return true
        if (contextWindowValue() != s.aiContextWindow) return true
        // Password field — compare against PasswordSafe
        val storedKey = AiApiKeyStore.loadApiKey()
        if (String(apiKeyField.password) != storedKey) return true
        return false
    }

    private fun compactRow(label: String, field: JComponent, vararg extras: JComponent): JComponent {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, 6, 2))
        if (label.isNotEmpty()) {
            val l = JLabel(label)
            l.preferredSize = Dimension(150, l.preferredSize.height)
            panel.add(l)
        }
        panel.add(field)
        extras.forEach { panel.add(it) }
        return panel
    }

    /**
     * Updates the inline status label. [ok] = true → neutral/positive
     * colour; false → error colour.
     */
    private fun setStatus(text: String, ok: Boolean) {
        statusLabel.text = text
        statusLabel.foreground = if (ok) UIUtil.getContextHelpForeground() else com.intellij.ui.JBColor.RED
    }

    /** Test-only: the current inline status text. */
    internal fun statusTextForTest(): String = statusLabel.text

    private fun simpleDocListener(onChange: () -> Unit) = object : javax.swing.event.DocumentListener {
        override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = onChange()
        override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = onChange()
        override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = onChange()
    }

    // --- Test helpers (used by AiAssistantSectionTest) ---

    internal fun selectProvider(provider: AiProvider) {
        providerCombo.selectedIndex = provider.ordinal
        // In headless test environments, JComboBox may not fire ActionEvent.
        // Perform the same pre-fill the action listener does.
        preFillProviderDefaults(provider)
        updateContextWindowTooltip(provider)
    }

    internal fun setBaseUrl(url: String) {
        baseUrlField.text = url
    }

    /** Simulates a user typing the API key (marks the field as edited). */
    internal fun setApiKey(key: String) {
        apiKeyField.text = key
    }

    internal fun setModel(model: String) {
        modelField.text = model
    }

    internal fun setTimeoutSec(sec: Int) {
        timeoutSpinner.value = sec
    }

    internal fun setMaxRequests(steps: Int) {
        maxAgentStepsSpinner.value = steps
    }

    internal fun setContextWindow(tokens: Int) {
        // Simulates a user edit: mark as edited, then set the combo value.
        userEditedContextWindow = true
        setContextWindowValue(tokens)
    }

    // --- Auto-detect test helpers (used by AiAssistantSectionAutoDetectTest) ---

    /** Returns the current provider selection (test-only). */
    internal fun currentProviderForTest(): AiProvider = currentProvider()

    /** Returns the current API key field text (test-only). */
    internal fun apiKeyText(): String = String(apiKeyField.password)

    /** Whether the API key is currently shown in clear text (test-only). */
    internal fun isApiKeyRevealedForTest(): Boolean = apiKeyField.echoChar == 0.toChar()

    /** Simulates clicking the reveal toggle (test-only). */
    internal fun toggleRevealApiKeyForTest() {
        revealApiKeyButton.doClick()
    }

    /** Returns the current base URL text (test-only). */
    internal fun baseUrlText(): String = baseUrlField.text

    /** Returns the current model text (test-only). */
    internal fun modelText(): String = modelField.text

    /** Returns the auto-detect button's label (test-only). */
    internal fun autoDetectButtonLabel(): String = autoDetectButton.text

    /** Returns whether the auto-detect button is enabled (test-only). */
    internal fun isAutoDetectButtonEnabled(): Boolean = autoDetectButton.isEnabled

    /** Returns the test-connection button's label (test-only). */
    internal fun testConnectionButtonLabel(): String = testConnectionButton.text

    /** Returns whether the test-connection button is enabled (test-only). */
    internal fun isTestConnectionButtonEnabled(): Boolean = testConnectionButton.isEnabled

    /** Triggers the Test Connection action directly (test-only). */
    internal fun triggerTestConnectionForTest() {
        onTestConnectionClicked()
    }

    /** Invokes [applyDetectionResult] directly, bypassing the background scan. */
    internal fun applyDetectionResultForTest(result: DetectionResult) {
        applyDetectionResult(result)
    }
}

class OtherSettingsPanel : SettingsPanel {
    private val importButton = JButton("Import Settings")
    private val exportButton = JButton("Export Settings")
    private var currentSettings: Settings? = null

    override val component: JComponent = JPanel(BorderLayout()).apply {
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        buttonPanel.add(importButton)
        buttonPanel.add(exportButton)
        add(buttonPanel, BorderLayout.NORTH)

        val content = JPanel(GridLayout(0, 1, 0, 10))
        val infoPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder("Info")
            val infoText = JBTextArea().apply {
                text = """
                    |EasyYapi Plugin Settings
                    |
                    |Import/Export your settings as JSON file.
                    |
                    |Version: 3.0.0.212.0
                """.trimMargin()
                isEditable = false
                rows = 10
            }
            add(JScrollPane(infoText), BorderLayout.CENTER)
        }
        content.add(infoPanel)
        add(content, BorderLayout.CENTER)
    }

    init {
        importButton.addActionListener {
            val settings = currentSettings ?: return@addActionListener
            val chooser = JFileChooser()
            chooser.dialogTitle = "Import Settings"
            chooser.fileSelectionMode = JFileChooser.FILES_ONLY
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                val file = chooser.selectedFile ?: return@addActionListener
                runCatching {
                    val imported = GsonUtils.fromJson<Settings>(file.readText())
                    applyImported(settings, imported)
                    resetFrom(settings)
                }.onFailure {
                    Messages.showErrorDialog("Import failed: ${it.message}", "EasyApi Settings")
                }
            }
        }
        exportButton.addActionListener {
            val settings = currentSettings ?: return@addActionListener
            val chooser = JFileChooser()
            chooser.dialogTitle = "Export Settings"
            chooser.fileSelectionMode = JFileChooser.FILES_ONLY
            if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                val file = chooser.selectedFile ?: return@addActionListener
                runCatching {
                    file.writeText(GsonUtils.toJson(settings))
                }.onFailure {
                    Messages.showErrorDialog("Export failed: ${it.message}", "EasyApi Settings")
                }
            }
        }
    }

    override fun resetFrom(settings: Settings?) {
        currentSettings = settings
    }

    override fun applyTo(settings: Settings) {
        // No mutable Other-specific state remaining; AI settings live in the
        // dedicated AI tab.
    }

    override fun isModified(settings: Settings?): Boolean {
        return false
    }

    private fun applyImported(settings: Settings, imported: Settings) {
        settings.feignEnable = imported.feignEnable
        settings.jaxrsEnable = imported.jaxrsEnable
        settings.actuatorEnable = imported.actuatorEnable
        settings.postmanToken = imported.postmanToken
        settings.postmanWorkspace = imported.postmanWorkspace
        settings.postmanExportMode = imported.postmanExportMode
        settings.postmanCollections = imported.postmanCollections
        settings.postmanBuildExample = imported.postmanBuildExample
        settings.wrapCollection = imported.wrapCollection
        settings.autoMergeScript = imported.autoMergeScript
        settings.postmanJson5FormatType = imported.postmanJson5FormatType
        settings.queryExpanded = imported.queryExpanded
        settings.formExpanded = imported.formExpanded
        settings.yapiServer = imported.yapiServer
        settings.yapiTokens = imported.yapiTokens
        settings.enableUrlTemplating = imported.enableUrlTemplating
        settings.switchNotice = imported.switchNotice
        settings.yapiExportMode = imported.yapiExportMode
        settings.yapiReqBodyJson5 = imported.yapiReqBodyJson5
        settings.yapiResBodyJson5 = imported.yapiResBodyJson5
        settings.httpTimeOut = imported.httpTimeOut
        settings.unsafeSsl = imported.unsafeSsl
        settings.httpClient = imported.httpClient
        settings.extensionConfigs = imported.extensionConfigs
        settings.logLevel = imported.logLevel
        settings.outputDemo = imported.outputDemo
        settings.outputCharset = imported.outputCharset
        settings.builtInConfig = imported.builtInConfig
        settings.remoteConfig = imported.remoteConfig
    }
}

private fun createTitledPanel(title: String, components: List<JComponent>): JPanel {
    return JPanel(BorderLayout()).apply {
        border = BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            title,
            TitledBorder.LEFT,
            TitledBorder.TOP
        )
        val inner = JPanel(GridLayout(0, 1, 0, 2))
        components.forEach { inner.add(it) }
        add(inner, BorderLayout.CENTER)
    }
}
