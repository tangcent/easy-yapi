package com.itangcent.easyapi.exporter.channel.postman

import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.*
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.UIUtil
import com.itangcent.easyapi.cache.AppCacheRepository
import com.itangcent.easyapi.exporter.channel.postman.PostmanApiClient
import com.itangcent.easyapi.exporter.channel.postman.Workspace
import com.itangcent.easyapi.exporter.channel.postman.asCached
import com.itangcent.easyapi.http.ApacheHttpClient
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.settings.PostmanExportMode
import com.itangcent.easyapi.settings.PostmanJson5FormatType
import com.itangcent.easyapi.settings.ui.SettingsPanel
import com.itangcent.easyapi.util.json.GsonUtils
import java.awt.*
import javax.swing.*
import kotlin.concurrent.thread

class PostmanSettingsPanel : SettingsPanel<PostmanSettings> {
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

    override fun resetFrom(settings: PostmanSettings?) {
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

    override fun applyTo(settings: PostmanSettings) {
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

    override fun isModified(settings: PostmanSettings?): Boolean {
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
