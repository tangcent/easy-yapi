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
import com.itangcent.easyapi.ai.AiRuntimeConfig
import com.itangcent.easyapi.ai.TokenSizeUtils
import com.itangcent.easyapi.ai.credentials.CredentialScanner
import com.itangcent.easyapi.ai.credentials.DefaultCredentialScanner
import com.itangcent.easyapi.ai.credentials.DetectionResult
import com.itangcent.easyapi.cache.AppCacheRepository
import com.itangcent.easyapi.cache.ProjectCacheRepository
import com.itangcent.easyapi.core.threading.backgroundAsync
import com.itangcent.easyapi.core.threading.swingAsync
import com.itangcent.easyapi.exporter.channel.postman.PostmanApiClient
import com.itangcent.easyapi.exporter.channel.postman.Workspace
import com.itangcent.easyapi.exporter.channel.postman.asCached
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
import com.itangcent.easyapi.settings.SettingBinder
import com.itangcent.easyapi.settings.PostmanExportMode
import com.itangcent.easyapi.settings.PostmanJson5FormatType
import com.itangcent.easyapi.settings.Settings
import com.itangcent.easyapi.settings.module.AiSettings
import com.itangcent.easyapi.settings.module.EnvironmentSettings
import com.itangcent.easyapi.settings.module.GeneralSettings
import com.itangcent.easyapi.settings.module.GrpcSettings
import com.itangcent.easyapi.settings.module.HttpSettings
import com.itangcent.easyapi.settings.module.ParsingOutputSettings
import com.itangcent.easyapi.settings.module.RuleFileSettings
import com.itangcent.easyapi.settings.settings
import com.itangcent.easyapi.settings.update
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.awt.*
import java.io.File
import javax.swing.*
import kotlin.concurrent.thread

/**
 * Interface for settings UI panels.
 *
 * Provides a contract for panels that display and edit plugin settings.
 * Each panel handles a specific category of settings, typed by the
 * [Settings] it reads from / writes to.
 *
 * @param T the settings module type this panel binds to
 */
interface SettingsPanel<T : Settings> {
    /** The UI component for this panel */
    val component: JComponent

    /**
     * Resets the panel UI to reflect the given settings.
     *
     * @param settings The settings to display
     */
    fun resetFrom(settings: T?)

    /**
     * Applies the panel UI values to the given settings.
     *
     * @param settings The settings to modify
     */
    fun applyTo(settings: T)

    /**
     * Checks if the panel has unsaved changes.
     *
     * @param settings The current settings
     * @return true if the panel has modifications
     */
    fun isModified(settings: T?): Boolean
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
class GeneralSettingsPanel(private val project: com.intellij.openapi.project.Project) : SettingsPanel<GeneralSettings> {
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
        return SettingsUiKit.titledPanel("Repositories", toolbarDecorator.createPanel())
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
            SettingsUiKit.titledPanel(
                "Framework Support", listOf(
                    feignEnable, jaxrsEnable, actuatorEnable
                )
            )
        )
        .addComponent(
            SettingsUiKit.titledPanel(
                "Scanning", listOf(
                    autoScanEnabled, concurrentScanEnabled
                )
            )
        )
        .addComponent(
            SettingsUiKit.titledPanel(
                "Editor", listOf(
                    gutterIconEnabled, switchNotice
                )
            )
        )
        .addComponent(
            SettingsUiKit.titledPanel(
                "Output", listOf(
                    SettingsUiKit.labeledRow("Output Charset:", outputCharsetCombo)
                )
            )
        )
        .addComponent(
            SettingsUiKit.titledPanel(
                "Diagnostics", listOf(
                    SettingsUiKit.labeledRow("Log Level:", logLevelCombo)
                )
            )
        )
        .addComponent(SettingsUiKit.titledPanel("Cache Management", cachePanel))
        .addComponent(createRepositoryPanel())
        .addComponentFillVertically(JPanel(), 0)
        .panel

    override fun resetFrom(settings: GeneralSettings?) {
        feignEnable.isSelected = settings?.feignEnable ?: false
        jaxrsEnable.isSelected = settings?.jaxrsEnable ?: true
        actuatorEnable.isSelected = settings?.actuatorEnable ?: false
        autoScanEnabled.isSelected = settings?.autoScanEnabled ?: true
        concurrentScanEnabled.isSelected = settings?.concurrentScanEnabled ?: false
        gutterIconEnabled.isSelected = settings?.gutterIconEnabled ?: true
        switchNotice.isSelected = settings?.switchNotice ?: true
        logLevelCombo.selectedItem = CommonSettingsHelper.VerbosityLevel.toLevel(settings?.logLevel ?: 0)
        outputCharsetCombo.selectedItem = settings?.outputCharset ?: "UTF-8"
        refreshCacheSizes()
    }

    /**
     * Resets the repositories table from [GrpcSettings.grpcRepositories].
     * Called separately by the configurable because `grpcRepositories` belongs
     * to [GrpcSettings], not [GeneralSettings].
     */
    fun resetRepositoriesFrom(grpcSettings: GrpcSettings?) {
        val userRepos = grpcSettings?.grpcRepositories?.mapNotNull { RepositoryConfig.parse(it) }
        repositoryTableModel.items = if (!userRepos.isNullOrEmpty()) {
            userRepos.toMutableList()
        } else {
            DefaultRepositories.detectFromEnvironment().toMutableList()
        }
    }

    override fun applyTo(settings: GeneralSettings) {
        settings.feignEnable = feignEnable.isSelected
        settings.jaxrsEnable = jaxrsEnable.isSelected
        settings.actuatorEnable = actuatorEnable.isSelected
        settings.autoScanEnabled = autoScanEnabled.isSelected
        settings.concurrentScanEnabled = concurrentScanEnabled.isSelected
        settings.gutterIconEnabled = gutterIconEnabled.isSelected
        settings.switchNotice = switchNotice.isSelected
        settings.logLevel = (logLevelCombo.selectedItem as? CommonSettingsHelper.VerbosityLevel)?.level ?: 0
        settings.outputCharset = outputCharsetCombo.selectedItem?.toString() ?: "UTF-8"
    }

    /**
     * Applies the repositories table to [GrpcSettings.grpcRepositories].
     * Called separately by the configurable because `grpcRepositories` belongs
     * to [GrpcSettings], not [GeneralSettings].
     */
    fun applyRepositoriesTo(grpcSettings: GrpcSettings) {
        val repos = repositoryTableModel.items.map { RepositoryConfig.serialize(it) }
        grpcSettings.grpcRepositories = repos.toTypedArray()
    }

    override fun isModified(settings: GeneralSettings?): Boolean {
        val s = settings ?: return false
        return feignEnable.isSelected != s.feignEnable ||
                jaxrsEnable.isSelected != s.jaxrsEnable ||
                actuatorEnable.isSelected != s.actuatorEnable ||
                autoScanEnabled.isSelected != s.autoScanEnabled ||
                concurrentScanEnabled.isSelected != s.concurrentScanEnabled ||
                gutterIconEnabled.isSelected != s.gutterIconEnabled ||
                switchNotice.isSelected != s.switchNotice ||
                (logLevelCombo.selectedItem as? CommonSettingsHelper.VerbosityLevel)?.level != s.logLevel ||
                outputCharsetCombo.selectedItem?.toString() != s.outputCharset
    }

    /**
     * Checks if the repositories table has been modified relative to
     * [GrpcSettings.grpcRepositories].
     * Called separately by the configurable because `grpcRepositories` belongs
     * to [GrpcSettings], not [GeneralSettings].
     */
    fun isRepositoriesModified(grpcSettings: GrpcSettings?): Boolean {
        val currentRepos = repositoryTableModel.items.map { RepositoryConfig.serialize(it) }.toTypedArray()
        return !currentRepos.contentEquals(grpcSettings?.grpcRepositories ?: emptyArray())
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

class HttpSettingsPanel : SettingsPanel<HttpSettings> {
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

    override fun resetFrom(settings: HttpSettings?) {
        httpClientCombo.selectedItem = settings?.httpClient ?: HttpClientType.APACHE.value
        httpTimeout.text = settings?.httpTimeOut?.toString() ?: "30"
        unsafeSsl.isSelected = settings?.unsafeSsl ?: false
    }

    override fun applyTo(settings: HttpSettings) {
        settings.httpClient = httpClientCombo.selectedItem?.toString() ?: HttpClientType.APACHE.value
        settings.httpTimeOut = httpTimeout.text.toIntOrNull() ?: 30
        settings.unsafeSsl = unsafeSsl.isSelected
    }

    override fun isModified(settings: HttpSettings?): Boolean {
        val s = settings ?: return false
        return httpClientCombo.selectedItem?.toString() != s.httpClient ||
                httpTimeout.text != s.httpTimeOut.toString() ||
                unsafeSsl.isSelected != s.unsafeSsl
    }
}

class ParsingOutputSettingsPanel : SettingsPanel<ParsingOutputSettings> {
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
        .addComponent(
            SettingsUiKit.titledPanel(
                "Inference", listOf(
                    inferReturnMain, enumFieldAutoInferEnabled
                )
            )
        )
        .addComponent(
            SettingsUiKit.titledPanel(
                "Output Format", listOf(
                    queryExpanded, formExpanded, enableUrlTemplating
                )
            )
        )
        .addLabeledComponent("Path multi-select strategy:", pathMultiCombo)
        .addComponentFillVertically(JPanel(), 0)
        .panel

    override fun resetFrom(settings: ParsingOutputSettings?) {
        queryExpanded.isSelected = settings?.queryExpanded ?: true
        formExpanded.isSelected = settings?.formExpanded ?: true
        inferReturnMain.isSelected = settings?.inferReturnMain ?: true
        enableUrlTemplating.isSelected = settings?.enableUrlTemplating ?: true
        pathMultiCombo.selectedItem = settings?.pathMulti ?: "ALL"
        enumFieldAutoInferEnabled.isSelected = settings?.enumFieldAutoInferEnabled ?: false
    }

    override fun applyTo(settings: ParsingOutputSettings) {
        settings.queryExpanded = queryExpanded.isSelected
        settings.formExpanded = formExpanded.isSelected
        settings.inferReturnMain = inferReturnMain.isSelected
        settings.enableUrlTemplating = enableUrlTemplating.isSelected
        settings.pathMulti = pathMultiCombo.selectedItem?.toString() ?: "ALL"
        settings.enumFieldAutoInferEnabled = enumFieldAutoInferEnabled.isSelected
    }

    override fun isModified(settings: ParsingOutputSettings?): Boolean {
        val s = settings ?: return false
        return queryExpanded.isSelected != s.queryExpanded ||
                formExpanded.isSelected != s.formExpanded ||
                inferReturnMain.isSelected != s.inferReturnMain ||
                enableUrlTemplating.isSelected != s.enableUrlTemplating ||
                pathMultiCombo.selectedItem?.toString() != s.pathMulti ||
                enumFieldAutoInferEnabled.isSelected != s.enumFieldAutoInferEnabled
    }
}

class ExtensionConfigPanel : SettingsPanel<RuleFileSettings> {
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

    override fun resetFrom(settings: RuleFileSettings?) {
        val selected = ExtensionConfigRegistry.stringToCodes(settings?.extensionConfigs ?: "").toSet()
        ExtensionConfigRegistry.allExtensions().forEachIndexed { index, extension ->
            val isSelected =
                selected.contains(extension.code) || (extension.defaultEnabled && !selected.contains("-${extension.code}"))
            extensionList.setItemSelected(extension.code, isSelected)
        }
        refreshPreview()
    }

    override fun applyTo(settings: RuleFileSettings) {
        settings.extensionConfigs = ExtensionConfigRegistry.codesToString(selectedCodes().toTypedArray())
    }

    override fun isModified(settings: RuleFileSettings?): Boolean {
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

class RemoteConfigPanel : SettingsPanel<RuleFileSettings> {
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

    override fun resetFrom(settings: RuleFileSettings?) {
        val raw = settings?.remoteConfig ?: emptyArray()
        remoteItems = raw.map {
            val clean = it.trim()
            if (clean.startsWith("!")) false to clean.removePrefix("!").trim() else true to clean
        }.filter { it.second.isNotBlank() }.toMutableList()
        refreshList()
    }

    override fun applyTo(settings: RuleFileSettings) {
        settings.remoteConfig = remoteItems.map { if (it.first) it.second else "!${it.second}" }.toTypedArray()
    }

    override fun isModified(settings: RuleFileSettings?): Boolean {
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
 * - "Test Connection" button — builds `AiRuntimeConfig` from
 * on-screen fields, calls `AIServiceFactory.create(settings).testConnection()`
 * on `backgroundAsync`, surfaces result via `NotificationUtils`)
 *
 * The API key round-trips through [PasswordSafe] directly, not through
 * [Settings]. All other fields are backed by [Settings] and tracked via
 * [resetFrom]/[applyTo]/[isModified].
 */
class AiAssistantSection : SettingsPanel<AiSettings> {

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
            SettingsUiKit.titledPanel(
                "AI Assistant", listOf(
            SettingsUiKit.labeledRow("Provider:", providerCombo),
            SettingsUiKit.labeledRow("Base URL:", baseUrlField),
            SettingsUiKit.labeledRow("API Key:", apiKeyField, revealApiKeyButton),
            SettingsUiKit.labeledRow("Model:", modelField),
            SettingsUiKit.labeledRow("Request Timeout (sec):", timeoutSpinner),
            SettingsUiKit.labeledRow("Max Requests:", maxAgentStepsSpinner),
            SettingsUiKit.labeledRow("Context Window:", contextWindowCombo),
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
    internal var aiServiceFactory: (AiRuntimeConfig) -> AIService =
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
        val settings = AiRuntimeConfig(
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

    override fun resetFrom(settings: AiSettings?) {
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

    override fun applyTo(settings: AiSettings) {
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

    override fun isModified(settings: AiSettings?): Boolean {
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

class BackupSettingsPanel(private val project: com.intellij.openapi.project.Project) : SettingsPanel<Settings> {
    private val importButton = JButton("Import Settings")
    private val exportButton = JButton("Export Settings")

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
                    |easyapi Plugin Settings
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
            val chooser = JFileChooser()
            chooser.dialogTitle = "Import Settings"
            chooser.fileSelectionMode = JFileChooser.FILES_ONLY
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                val file = chooser.selectedFile ?: return@addActionListener
                runCatching {
                    applyImported(file.readText())
                }.onFailure {
                    Messages.showErrorDialog("Import failed: ${it.message}", "EasyApi Settings")
                }
            }
        }
        exportButton.addActionListener {
            val chooser = JFileChooser()
            chooser.dialogTitle = "Export Settings"
            chooser.fileSelectionMode = JFileChooser.FILES_ONLY
            if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                val file = chooser.selectedFile ?: return@addActionListener
                runCatching {
                    file.writeText(exportSettings())
                }.onFailure {
                    Messages.showErrorDialog("Export failed: ${it.message}", "EasyApi Settings")
                }
            }
        }
    }

    override fun resetFrom(settings: Settings?) {
        // No mutable Other-specific state; import/export operates directly on modules.
    }

    override fun applyTo(settings: Settings) {
        // No mutable Other-specific state remaining; AI settings live in the
        // dedicated AI tab.
    }

    override fun isModified(settings: Settings?): Boolean {
        return false
    }

    /**
     * Imports settings from a JSON file (flat format compatible with the
     * legacy `Settings` god-object) and distributes fields across modules.
     */
    private fun applyImported(json: String) {
        val obj = JsonParser.parseString(json)?.takeIf { it.isJsonObject }?.asJsonObject ?: return
        val binder = SettingBinder.getInstance(project)

        binder.update(com.itangcent.easyapi.settings.module.GeneralSettings::class) {
            obj.get("feignEnable")?.asBoolean?.let { feignEnable = it }
            obj.get("jaxrsEnable")?.asBoolean?.let { jaxrsEnable = it }
            obj.get("actuatorEnable")?.asBoolean?.let { actuatorEnable = it }
            obj.get("autoScanEnabled")?.asBoolean?.let { autoScanEnabled = it }
            obj.get("concurrentScanEnabled")?.asBoolean?.let { concurrentScanEnabled = it }
            obj.get("gutterIconEnabled")?.asBoolean?.let { gutterIconEnabled = it }
            obj.get("switchNotice")?.asBoolean?.let { switchNotice = it }
            obj.get("logLevel")?.asInt?.let { logLevel = it }
            obj.get("outputCharset")?.asString?.let { outputCharset = it }
        }

        binder.update(com.itangcent.easyapi.settings.module.HttpSettings::class) {
            obj.get("httpTimeOut")?.asInt?.let { httpTimeOut = it }
            obj.get("unsafeSsl")?.asBoolean?.let { unsafeSsl = it }
            obj.get("httpClient")?.asString?.let { httpClient = it }
        }

        binder.update(com.itangcent.easyapi.settings.module.ParsingOutputSettings::class) {
            obj.get("queryExpanded")?.asBoolean?.let { queryExpanded = it }
            obj.get("formExpanded")?.asBoolean?.let { formExpanded = it }
            obj.get("inferReturnMain")?.asBoolean?.let { inferReturnMain = it }
            obj.get("enableUrlTemplating")?.asBoolean?.let { enableUrlTemplating = it }
            obj.get("pathMulti")?.asString?.let { pathMulti = it }
            obj.get("enumFieldAutoInferEnabled")?.asBoolean?.let { enumFieldAutoInferEnabled = it }
        }

        binder.update(com.itangcent.easyapi.settings.module.EnvironmentSettings::class) {
            obj.get("globalEnvironments")?.asString?.let { globalEnvironments = it }
        }

        binder.update(com.itangcent.easyapi.settings.module.RuleFileSettings::class) {
            obj.get("extensionConfigs")?.asString?.let { extensionConfigs = it }
            obj.get("builtInConfig")?.asString?.let { builtInConfig = it }
            obj.get("remoteConfig")?.takeIf { it.isJsonArray }?.asJsonArray?.map { it.asString }
                ?.toTypedArray()?.let { remoteConfig = it }
            obj.get("disabledGlobalRuleFiles")?.takeIf { it.isJsonArray }?.asJsonArray?.map { it.asString }
                ?.toTypedArray()?.let { disabledGlobalRuleFiles = it }
        }

        binder.update(com.itangcent.easyapi.exporter.channel.postman.PostmanSettings::class) {
            obj.get("postmanToken")?.asString?.let { postmanToken = it }
            obj.get("postmanWorkspace")?.asString?.let { postmanWorkspace = it }
            obj.get("postmanExportMode")?.asString?.let { postmanExportMode = it }
            obj.get("postmanCollections")?.asString?.let { postmanCollections = it }
            obj.get("postmanBuildExample")?.asBoolean?.let { postmanBuildExample = it }
            obj.get("wrapCollection")?.asBoolean?.let { wrapCollection = it }
            obj.get("autoMergeScript")?.asBoolean?.let { autoMergeScript = it }
            obj.get("postmanJson5FormatType")?.asString?.let { postmanJson5FormatType = it }
        }
    }

    /**
     * Exports all module settings as a flat JSON object (compatible with the
     * legacy `Settings` god-object format).
     */
    private fun exportSettings(): String {
        val obj = JsonObject()
        val binder = SettingBinder.getInstance(project)

        val general = binder.read(com.itangcent.easyapi.settings.module.GeneralSettings::class)
        obj.addProperty("feignEnable", general.feignEnable)
        obj.addProperty("jaxrsEnable", general.jaxrsEnable)
        obj.addProperty("actuatorEnable", general.actuatorEnable)
        obj.addProperty("autoScanEnabled", general.autoScanEnabled)
        obj.addProperty("concurrentScanEnabled", general.concurrentScanEnabled)
        obj.addProperty("gutterIconEnabled", general.gutterIconEnabled)
        obj.addProperty("switchNotice", general.switchNotice)
        obj.addProperty("logLevel", general.logLevel)
        obj.addProperty("outputCharset", general.outputCharset)

        val http = binder.read(com.itangcent.easyapi.settings.module.HttpSettings::class)
        obj.addProperty("httpTimeOut", http.httpTimeOut)
        obj.addProperty("unsafeSsl", http.unsafeSsl)
        obj.addProperty("httpClient", http.httpClient)

        val parsingOutput = binder.read(com.itangcent.easyapi.settings.module.ParsingOutputSettings::class)
        obj.addProperty("queryExpanded", parsingOutput.queryExpanded)
        obj.addProperty("formExpanded", parsingOutput.formExpanded)
        obj.addProperty("inferReturnMain", parsingOutput.inferReturnMain)
        obj.addProperty("enableUrlTemplating", parsingOutput.enableUrlTemplating)
        obj.addProperty("pathMulti", parsingOutput.pathMulti)
        obj.addProperty("enumFieldAutoInferEnabled", parsingOutput.enumFieldAutoInferEnabled)

        val environment = binder.read(com.itangcent.easyapi.settings.module.EnvironmentSettings::class)
        obj.addProperty("globalEnvironments", environment.globalEnvironments)

        val ruleFile = binder.read(com.itangcent.easyapi.settings.module.RuleFileSettings::class)
        obj.addProperty("extensionConfigs", ruleFile.extensionConfigs)
        obj.addProperty("builtInConfig", ruleFile.builtInConfig)
        obj.add("remoteConfig", GsonUtils.GSON.toJsonTree(ruleFile.remoteConfig))
        obj.add("disabledGlobalRuleFiles", GsonUtils.GSON.toJsonTree(ruleFile.disabledGlobalRuleFiles))

        val postman = binder.read(com.itangcent.easyapi.exporter.channel.postman.PostmanSettings::class)
        obj.addProperty("postmanToken", postman.postmanToken)
        obj.addProperty("postmanWorkspace", postman.postmanWorkspace)
        obj.addProperty("postmanExportMode", postman.postmanExportMode)
        obj.addProperty("postmanCollections", postman.postmanCollections)
        obj.addProperty("postmanBuildExample", postman.postmanBuildExample)
        obj.addProperty("wrapCollection", postman.wrapCollection)
        obj.addProperty("autoMergeScript", postman.autoMergeScript)
        obj.addProperty("postmanJson5FormatType", postman.postmanJson5FormatType)

        return GsonUtils.toJson(obj)
    }
}
