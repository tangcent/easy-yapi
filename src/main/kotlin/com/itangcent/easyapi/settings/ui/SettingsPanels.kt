package com.itangcent.easyapi.settings.ui

import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.CheckBoxList
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.*
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.UIUtil
import com.itangcent.easyapi.cache.AppCacheRepository
import com.itangcent.easyapi.cache.ProjectCacheRepository
import com.itangcent.easyapi.exporter.postman.PostmanApiClient
import com.itangcent.easyapi.exporter.postman.Workspace
import com.itangcent.easyapi.exporter.postman.asCached
import com.itangcent.easyapi.repository.DefaultRepositories
import com.itangcent.easyapi.repository.RepositoryConfig
import com.itangcent.easyapi.repository.RepositoryType
import com.itangcent.easyapi.http.ApacheHttpClient
import com.itangcent.easyapi.extension.ExtensionConfigRegistry
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.util.GsonUtils
import com.itangcent.easyapi.settings.HttpClientType
import com.itangcent.easyapi.settings.MarkdownFormatType
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
 * - Markdown format selection
 * - Cache management
 */
class GeneralSettingsPanel(private val project: com.intellij.openapi.project.Project) : SettingsPanel {
    private val feignEnable = JBCheckBox("Enable Feign client support")
    private val jaxrsEnable = JBCheckBox("Enable JAX-RS support", true)
    private val actuatorEnable = JBCheckBox("Enable Spring Actuator support")
    private val autoScanEnabled = JBCheckBox("Enable automatic API scanning on file changes", true)

    private val logLevelCombo = ComboBox(CommonSettingsHelper.VerbosityLevel.values())
    private val outputCharsetCombo = ComboBox(arrayOf("UTF-8", "GBK", "ISO-8859-1"))
    private val outputDemoCheckBox = JBCheckBox("Output demo in markdown", true)
    private val markdownFormatTypeCombo = ComboBox(MarkdownFormatType.values())

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
                -1L
            }

            LOG.info("Cache refresh: projectSize=$projectSize, globalSize=$globalSize")

            SwingUtilities.invokeLater {
                projectCacheSizeLabel.text = when {
                    projectSize < 0 -> "N/A"
                    else -> formatFileSize(projectSize)
                }
                projectCacheSizeLabel.toolTipText = null
                globalCacheSizeLabel.text = if (globalSize < 0) "N/A" else formatFileSize(globalSize)
            }
        }
    }

    /**
     * Formats a file size in bytes to human-readable format.
     */
    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
            size < 1024 * 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0))
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
        .addLabeledComponent("Log Level:", logLevelCombo)
        .addLabeledComponent("Output Charset:", outputCharsetCombo)
        .addComponent(outputDemoCheckBox)
        .addLabeledComponent("Markdown Format:", markdownFormatTypeCombo)
        .addComponent(createTitledPanel("Cache Management", listOf(cachePanel)))
        .addComponent(createRepositoryPanel())
        .addComponentFillVertically(JPanel(), 0)
        .panel

    override fun resetFrom(settings: Settings?) {
        feignEnable.isSelected = settings?.feignEnable ?: false
        jaxrsEnable.isSelected = settings?.jaxrsEnable ?: true
        actuatorEnable.isSelected = settings?.actuatorEnable ?: false
        autoScanEnabled.isSelected = settings?.autoScanEnabled ?: true
        logLevelCombo.selectedItem = CommonSettingsHelper.VerbosityLevel.toLevel(settings?.logLevel ?: 50)
        outputCharsetCombo.selectedItem = settings?.outputCharset ?: "UTF-8"
        outputDemoCheckBox.isSelected = settings?.outputDemo ?: true
        markdownFormatTypeCombo.selectedItem = settings?.markdownFormatType?.let {
            runCatching { MarkdownFormatType.valueOf(it) }.getOrNull()
        } ?: MarkdownFormatType.SIMPLE
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
        settings.logLevel = (logLevelCombo.selectedItem as? CommonSettingsHelper.VerbosityLevel)?.level ?: 50
        settings.outputCharset = outputCharsetCombo.selectedItem?.toString() ?: "UTF-8"
        settings.outputDemo = outputDemoCheckBox.isSelected
        settings.markdownFormatType =
            (markdownFormatTypeCombo.selectedItem as? MarkdownFormatType)?.name ?: MarkdownFormatType.SIMPLE.name

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
                (logLevelCombo.selectedItem as? CommonSettingsHelper.VerbosityLevel)?.level != s.logLevel ||
                outputCharsetCombo.selectedItem?.toString() != s.outputCharset ||
                outputDemoCheckBox.isSelected != s.outputDemo ||
                markdownFormatTypeCombo.selectedItem?.toString() != s.markdownFormatType ||
                !currentRepos.contentEquals(s.grpcRepositories)
    }

    companion object : IdeaLog
}

object CommonSettingsHelper {
    enum class VerbosityLevel(val level: Int, val displayName: String) {
        SILENT(0, "Silent"),
        ERROR(10, "Error"),
        WARN(20, "Warning"),
        INFO(30, "Info"),
        DEBUG(40, "Debug"),
        TRACE(50, "Trace");

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
    private val postmanBuildExample = JBCheckBox("Build example", true)
    private val wrapCollection = JBCheckBox("Wrap collection")
    private val autoMergeScript = JBCheckBox("Auto merge script")
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
    private val unsafeSsl = JBCheckBox("Allow unsafe SSL")

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
    private val queryExpanded = JBCheckBox("Query expanded", true)
    private val formExpanded = JBCheckBox("Form expanded", true)

    override val component: JComponent = FormBuilder.createFormBuilder()
        .addComponent(queryExpanded)
        .addComponent(formExpanded)
        .addComponentFillVertically(JPanel(), 0)
        .panel

    override fun resetFrom(settings: Settings?) {
        queryExpanded.isSelected = settings?.queryExpanded ?: true
        formExpanded.isSelected = settings?.formExpanded ?: true
    }

    override fun applyTo(settings: Settings) {
        settings.queryExpanded = queryExpanded.isSelected
        settings.formExpanded = formExpanded.isSelected
    }

    override fun isModified(settings: Settings?): Boolean {
        val s = settings ?: return false
        return queryExpanded.isSelected != s.queryExpanded ||
                formExpanded.isSelected != s.formExpanded
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
        remoteItems = if (raw.isEmpty()) mutableListOf(true to DEFAULT_REMOTE_URL) else raw.map {
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
            val content = runCatching { java.net.URI(target).toURL().readText() }.getOrElse { "Load failed: ${it.message}" }
            SwingUtilities.invokeLater {
                if (list.selectedIndex == index) {
                    preview.text = content
                }
            }
        }
    }

    companion object {
        private const val DEFAULT_REMOTE_URL =
            "https://raw.githubusercontent.com/tangcent/easy-yapi/master/.default.remote.easy.api.config"
    }
}

class BuiltInConfigPanel : SettingsPanel {
    private val editor = JBTextArea()
    override val component: JComponent = JPanel(BorderLayout()).apply {
        add(JScrollPane(editor), BorderLayout.CENTER)
    }

    override fun resetFrom(settings: Settings?) {
        editor.text = settings?.builtInConfig?.takeIf { it.isNotBlank() } ?: defaultBuiltInConfig()
    }

    override fun applyTo(settings: Settings) {
        val content = editor.text
        settings.builtInConfig = if (content == defaultBuiltInConfig()) "" else content
    }

    override fun isModified(settings: Settings?): Boolean {
        val s = settings ?: return false
        val current = editor.text
        val stored = s.builtInConfig?.takeIf { it.isNotBlank() } ?: defaultBuiltInConfig()
        return current != stored
    }

    private fun defaultBuiltInConfig(): String {
        return javaClass.classLoader.getResourceAsStream("config/builtin.easyapi.config")
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: ""
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
        add(infoPanel, BorderLayout.CENTER)
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
    }

    override fun isModified(settings: Settings?): Boolean = false

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
        settings.markdownFormatType = imported.markdownFormatType
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
