package com.itangcent.easyapi.settings.ui

import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.CheckBoxList
import com.intellij.ui.components.*
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.UIUtil
import com.itangcent.easyapi.cache.AppCacheRepository
import com.itangcent.easyapi.cache.ProjectCacheRepository
import com.itangcent.easyapi.exporter.postman.PostmanApiClient
import com.itangcent.easyapi.exporter.postman.Workspace
import com.itangcent.easyapi.exporter.postman.asCached
import com.itangcent.easyapi.http.ApacheHttpClient
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.recommend.RecommendPresetRegistry
import com.itangcent.easyapi.util.GsonUtils
import com.itangcent.easyapi.settings.HttpClientType
import com.itangcent.easyapi.settings.MarkdownFormatType
import com.itangcent.easyapi.settings.PostmanExportMode
import com.itangcent.easyapi.settings.PostmanJson5FormatType
import com.itangcent.easyapi.settings.Settings
import java.awt.*
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

    private val logLevelCombo = ComboBox(CommonSettingsHelper.VerbosityLevel.values())
    private val outputCharsetCombo = ComboBox(arrayOf("UTF-8", "GBK", "ISO-8859-1"))
    private val outputDemoCheckBox = JBCheckBox("Output demo in markdown", true)
    private val markdownFormatTypeCombo = ComboBox(MarkdownFormatType.values())

    private val projectCacheSizeLabel = JBLabel("0 B")
    private val globalCacheSizeLabel = JBLabel("0 B")
    private val clearProjectCacheButton = JButton("Clear")
    private val clearGlobalCacheButton = JButton("Clear")

    private val cachePanel: JPanel

    init {
        // Build a compact cache panel similar to "Framework Support" style
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

    override val component: JComponent = FormBuilder.createFormBuilder()
        .addComponent(
            createTitledPanel(
                "Framework Support", listOf(
                    feignEnable, jaxrsEnable, actuatorEnable
                )
            )
        )
        .addLabeledComponent("Log Level:", logLevelCombo)
        .addLabeledComponent("Output Charset:", outputCharsetCombo)
        .addComponent(outputDemoCheckBox)
        .addLabeledComponent("Markdown Format:", markdownFormatTypeCombo)
        .addComponent(createTitledPanel("Cache Management", listOf(cachePanel)))
        .addComponentFillVertically(JPanel(), 0)
        .panel

    override fun resetFrom(settings: Settings?) {
        feignEnable.isSelected = settings?.feignEnable ?: false
        jaxrsEnable.isSelected = settings?.jaxrsEnable ?: true
        actuatorEnable.isSelected = settings?.actuatorEnable ?: false
        logLevelCombo.selectedItem = CommonSettingsHelper.VerbosityLevel.toLevel(settings?.logLevel ?: 50)
        outputCharsetCombo.selectedItem = settings?.outputCharset ?: "UTF-8"
        outputDemoCheckBox.isSelected = settings?.outputDemo ?: true
        markdownFormatTypeCombo.selectedItem = settings?.markdownFormatType?.let {
            runCatching { MarkdownFormatType.valueOf(it) }.getOrNull()
        } ?: MarkdownFormatType.SIMPLE
        refreshCacheSizes()
    }

    override fun applyTo(settings: Settings) {
        settings.feignEnable = feignEnable.isSelected
        settings.jaxrsEnable = jaxrsEnable.isSelected
        settings.actuatorEnable = actuatorEnable.isSelected
        settings.logLevel = (logLevelCombo.selectedItem as? CommonSettingsHelper.VerbosityLevel)?.level ?: 50
        settings.outputCharset = outputCharsetCombo.selectedItem?.toString() ?: "UTF-8"
        settings.outputDemo = outputDemoCheckBox.isSelected
        settings.markdownFormatType =
            (markdownFormatTypeCombo.selectedItem as? MarkdownFormatType)?.name ?: MarkdownFormatType.SIMPLE.name
    }

    override fun isModified(settings: Settings?): Boolean {
        val s = settings ?: return false
        return feignEnable.isSelected != s.feignEnable ||
                jaxrsEnable.isSelected != s.jaxrsEnable ||
                actuatorEnable.isSelected != s.actuatorEnable ||
                (logLevelCombo.selectedItem as? CommonSettingsHelper.VerbosityLevel)?.level != s.logLevel ||
                outputCharsetCombo.selectedItem?.toString() != s.outputCharset ||
                outputDemoCheckBox.isSelected != s.outputDemo ||
                markdownFormatTypeCombo.selectedItem?.toString() != s.markdownFormatType
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

class RecommendConfigPanel : SettingsPanel {
    private val presetList = CheckBoxList<String>()
    private val preview = JBTextArea()

    override val component: JComponent = JSplitPane(JSplitPane.HORIZONTAL_SPLIT).apply {
        val left = JPanel(BorderLayout())
        left.add(JScrollPane(presetList), BorderLayout.CENTER)
        val right = JPanel(BorderLayout())
        preview.isEditable = false
        right.add(JScrollPane(preview), BorderLayout.CENTER)
        leftComponent = left
        rightComponent = right
        resizeWeight = 0.45
    }

    init {
        val allCodes = RecommendPresetRegistry.allPresets().map { it.code }
        presetList.setItems(allCodes) { it }
        presetList.setCheckBoxListListener { _, _ -> refreshPreview() }
    }

    override fun resetFrom(settings: Settings?) {
        val selected = settings?.recommendConfigs
            ?.split(',', ';', '\n')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()
        RecommendPresetRegistry.allPresets().forEachIndexed { index, preset ->
            val isSelected =
                selected.contains(preset.code) || (preset.defaultEnabled && !selected.contains("-${preset.code}"))
            presetList.setItemSelected(preset.code, isSelected)
        }
        refreshPreview()
    }

    override fun applyTo(settings: Settings) {
        settings.recommendConfigs = selectedCodes().joinToString(",").takeIf { it.isNotBlank() } ?: ""
    }

    override fun isModified(settings: Settings?): Boolean {
        val s = settings ?: return false
        val currentSelected = selectedCodes().toSet()
        val savedSelected = s.recommendConfigs
            ?.split(',', ';', '\n')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()
        val defaultEnabled = RecommendPresetRegistry.allPresets()
            .filter { it.defaultEnabled }
            .map { it.code }
            .toSet()
        val effectiveSaved = savedSelected + defaultEnabled
        return currentSelected != effectiveSaved
    }

    private fun selectedCodes(): List<String> {
        return RecommendPresetRegistry.allPresets().mapNotNull { preset ->
            if (presetList.isItemSelected(preset.code)) preset.code else null
        }
    }

    private fun refreshPreview() {
        val lines = selectedCodes().mapNotNull { code ->
            RecommendPresetRegistry.getPreset(code)?.let { preset ->
                if (preset.content.isBlank()) "# $code" else "# $code\n" + preset.content
            }
        }
        preview.text = lines.joinToString("\n\n")
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
            "https://raw.githubusercontent.com/tangcent/easy-api/master/.default.remote.easy.api.config"
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
        settings.httpTimeOut = imported.httpTimeOut
        settings.unsafeSsl = imported.unsafeSsl
        settings.httpClient = imported.httpClient
        settings.recommendConfigs = imported.recommendConfigs
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
