package com.itangcent.easyapi.exporter.channel.postman

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWrapper
import com.itangcent.easyapi.core.threading.IdeDispatchers
import com.itangcent.easyapi.core.threading.backgroundAsync
import com.itangcent.easyapi.core.threading.swing
import com.itangcent.easyapi.exporter.channel.ApiChannel
import com.itangcent.easyapi.exporter.channel.ChannelConfig
import com.itangcent.easyapi.exporter.channel.ChannelOptionsPanel
import com.itangcent.easyapi.exporter.model.ExportContext
import com.itangcent.easyapi.exporter.model.ExportResult
import com.itangcent.easyapi.exporter.postman.*
import com.itangcent.easyapi.exporter.postman.model.PostmanCollection
import com.itangcent.easyapi.exporter.postman.model.PostmanItem
import com.itangcent.easyapi.exporter.postman.model.postmanGson
import com.itangcent.easyapi.http.HttpClientProvider
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.settings.PostmanExportMode
import com.itangcent.easyapi.settings.SettingBinder
import com.itangcent.easyapi.util.ide.ModuleHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.event.ItemEvent
import java.io.File
import javax.swing.*

/**
 * [ApiChannel] that exports API endpoints to Postman collections.
 *
 * Supports HTTP endpoints only (not gRPC). Provides a configuration panel
 * for selecting workspace/collection, and exposes a top-level IDE action.
 *
 * @see ApiChannel
 * @see PostmanFormatter
 * @see PostmanOptionsPanel
 */
class PostmanChannel : ApiChannel, IdeaLog {

    override val id: String = "postman"
    override val displayName: String = "Postman"
    override val supportsGrpc: Boolean = false
    override val exposeAsAction: Boolean = true
    override val actionText: String = "Export to Postman"

    override fun createOptionsPanel(project: Project): ChannelOptionsPanel {
        return PostmanOptionsPanel(project)
    }

    override suspend fun export(context: ExportContext): ExportResult {
        val project = context.project
        val settings = SettingBinder.getInstance(project).read()
        val token = settings.postmanToken
        val postmanConfig = context.channelConfig as? ChannelConfig.PostmanConfig

        val collectionName = postmanConfig?.collectionName ?: project.name
        val hasExplicitName = postmanConfig?.collectionName != null

        val formatter = PostmanFormatter(
            project = project,
            options = PostmanFormatOptions(
                buildExample = settings.postmanBuildExample,
                autoMergeScript = settings.autoMergeScript,
                wrapCollection = settings.wrapCollection,
                json5FormatType = runCatching { com.itangcent.easyapi.settings.PostmanJson5FormatType.valueOf(settings.postmanJson5FormatType) }.getOrDefault(
                    com.itangcent.easyapi.settings.PostmanJson5FormatType.EXAMPLE_ONLY
                ),
                appendTimestamp = !hasExplicitName
            )
        )

        val collection = formatter.format(context.endpointsToExport, collectionName)

        return if (token.isNullOrBlank()) {
            ExportResult.Success(
                count = countApiItems(collection),
                target = "Postman Collection",
                metadata = PostmanExportMetadata(
                    collectionName = collection.info.name,
                    collectionData = collection
                )
            )
        } else {
            val hasDialogSelection = postmanConfig?.collectionId != null
                    || postmanConfig?.collectionName != null
            if (hasDialogSelection) {
                uploadToPostmanWithDialogSelection(context, token, collection, settings, postmanConfig!!)
            } else {
                val exportMode = settings.postmanExportMode?.let {
                    runCatching { PostmanExportMode.valueOf(it) }.getOrNull()
                } ?: PostmanExportMode.CREATE_NEW
                uploadToPostmanWithMode(context, token, collection, settings, exportMode)
            }
        }
    }

    private suspend fun uploadToPostmanWithDialogSelection(
        context: ExportContext,
        token: String,
        collection: PostmanCollection,
        settings: com.itangcent.easyapi.settings.Settings,
        postmanConfig: ChannelConfig.PostmanConfig
    ): ExportResult {
        val project = context.project
        val workspaceId = postmanConfig.workspaceId ?: settings.postmanWorkspace
        val collectionId = postmanConfig.collectionId

        val httpClient = HttpClientProvider.getInstance(project).getClient()
        val postmanClient = PostmanApiClient(token, workspaceId = workspaceId, httpClient = httpClient)
            .asCached()

        val workspaceName = workspaceId?.let {
            postmanClient.listWorkspaces(useCache = true).find { it.id == workspaceId }?.name
        }

        val result = if (collectionId != null) {
            postmanClient.updateCollection(collectionId, collection)
        } else {
            postmanClient.uploadCollection(collection)
        }

        return if (result.success) {
            ExportResult.Success(
                count = countApiItems(collection),
                target = "Postman",
                metadata = PostmanExportMetadata(
                    workspaceName = workspaceName,
                    workspaceId = workspaceId,
                    collectionName = collection.info.name,
                    collectionId = result.collectionId ?: collectionId
                )
            )
        } else {
            ExportResult.Error(result.message ?: "Unknown error")
        }
    }

    private suspend fun uploadToPostmanWithMode(
        context: ExportContext,
        token: String,
        collection: PostmanCollection,
        settings: com.itangcent.easyapi.settings.Settings,
        exportMode: PostmanExportMode
    ): ExportResult {
        val project = context.project
        val workspaceId = settings.postmanWorkspace

        val httpClient = HttpClientProvider.getInstance(project).getClient()
        val postmanClient = PostmanApiClient(token, workspaceId = workspaceId, httpClient = httpClient)
            .asCached()

        val workspaceName = workspaceId?.let {
            postmanClient.listWorkspaces(useCache = true).find { it.id == workspaceId }?.name
        }

        when (exportMode) {
            PostmanExportMode.CREATE_NEW -> {
                val result = postmanClient.uploadCollection(collection)
                return if (result.success) {
                    ExportResult.Success(
                        count = countApiItems(collection),
                        target = "Postman",
                        metadata = PostmanExportMetadata(
                            workspaceName = workspaceName,
                            workspaceId = workspaceId,
                            collectionName = collection.info.name,
                            collectionId = result.collectionId
                        )
                    )
                } else {
                    ExportResult.Error(result.message ?: "Unknown error")
                }
            }

            PostmanExportMode.UPDATE_EXISTING -> {
                return uploadToPostmanUpdateMode(
                    context, collection, settings, postmanClient, workspaceName, workspaceId
                )
            }
        }
    }

    private suspend fun uploadToPostmanUpdateMode(
        context: ExportContext,
        collection: PostmanCollection,
        settings: com.itangcent.easyapi.settings.Settings,
        postmanClient: CachedPostmanApiClient,
        workspaceName: String?,
        workspaceId: String?
    ): ExportResult {
        val project = context.project
        val collectionHelper = PostmanCollectionHelper.getInstance(project)
        collectionHelper.resetPromptedModules()

        val endpointsByModule = withContext(IdeDispatchers.ReadAction) {
            context.endpointsToExport.groupBy { endpoint ->
                endpoint.sourceClass?.let { ModuleHelper.resolveModule(it)?.name } ?: "default"
            }
        }

        if (endpointsByModule.size == 1) {
            val module = endpointsByModule.keys.first()
            val collectionId = collectionHelper.getCollectionIdForModule(module)

            return if (collectionId != null) {
                val result = postmanClient.updateCollection(collectionId, collection)
                if (result.success) {
                    ExportResult.Success(
                        count = countApiItems(collection),
                        target = "Postman",
                        metadata = PostmanExportMetadata(
                            workspaceName = workspaceName,
                            workspaceId = workspaceId,
                            collectionName = collection.info.name,
                            collectionId = result.collectionId ?: collectionId
                        )
                    )
                } else {
                    ExportResult.Error(result.message ?: "Unknown error")
                }
            } else {
                val result = postmanClient.uploadCollection(collection)
                if (result.success) {
                    ExportResult.Success(
                        count = countApiItems(collection),
                        target = "Postman",
                        metadata = PostmanExportMetadata(
                            workspaceName = workspaceName,
                            workspaceId = workspaceId,
                            collectionName = collection.info.name,
                            collectionId = result.collectionId
                        )
                    )
                } else {
                    ExportResult.Error(result.message ?: "Unknown error")
                }
            }
        }

        val results = mutableListOf<ExportResult>()
        for ((module, endpoints) in endpointsByModule) {
            val collectionId = collectionHelper.getCollectionIdForModule(module)
            val moduleCollection = createModuleCollection(endpoints, collection.info.name, context, settings)

            val result = if (collectionId != null) {
                postmanClient.updateCollection(collectionId, moduleCollection)
            } else {
                postmanClient.uploadCollection(moduleCollection)
            }

            if (result.success) {
                results.add(
                    ExportResult.Success(
                        count = countApiItems(moduleCollection),
                        target = "Postman",
                        metadata = PostmanExportMetadata(
                            workspaceName = workspaceName,
                            workspaceId = workspaceId,
                            collectionName = moduleCollection.info.name,
                            collectionId = result.collectionId ?: collectionId
                        )
                    )
                )
            } else {
                results.add(ExportResult.Error("Failed to export module '$module': ${result.message}"))
            }
        }

        val successCount = results.count { it is ExportResult.Success }
        return if (successCount == results.size) {
            ExportResult.Success(
                count = results.sumOf { (it as ExportResult.Success).count },
                target = "Postman",
                metadata = PostmanExportMetadata(
                    workspaceName = workspaceName,
                    workspaceId = workspaceId,
                    collectionName = collection.info.name,
                    collectionId = null
                )
            )
        } else if (successCount > 0) {
            ExportResult.Success(
                count = results.sumOf { (it as? ExportResult.Success)?.count ?: 0 },
                target = "Postman",
                metadata = PostmanExportMetadata(
                    workspaceName = workspaceName,
                    workspaceId = workspaceId,
                    collectionName = collection.info.name,
                    collectionId = null
                )
            )
        } else {
            results.first { it is ExportResult.Error } as ExportResult.Error
        }
    }

    private suspend fun createModuleCollection(
        endpoints: List<com.itangcent.easyapi.exporter.model.ApiEndpoint>,
        baseName: String,
        context: ExportContext,
        settings: com.itangcent.easyapi.settings.Settings
    ): PostmanCollection {
        val project = context.project
        val formatter = PostmanFormatter(
            project = project,
            options = PostmanFormatOptions(
                buildExample = settings.postmanBuildExample,
                autoMergeScript = settings.autoMergeScript,
                wrapCollection = settings.wrapCollection,
                json5FormatType = runCatching { com.itangcent.easyapi.settings.PostmanJson5FormatType.valueOf(settings.postmanJson5FormatType) }.getOrDefault(
                    com.itangcent.easyapi.settings.PostmanJson5FormatType.EXAMPLE_ONLY
                ),
                appendTimestamp = false
            )
        )
        return formatter.format(endpoints, baseName)
    }

    override suspend fun handleResult(
        project: Project,
        result: ExportResult.Success,
        config: ChannelConfig
    ): Boolean {
        val metadata = result.metadata as? PostmanExportMetadata ?: return false

        if (metadata.collectionData != null) {
            return handleFileExport(project, result, metadata, config)
        }

        val details = metadata.formatDisplay()
        val message = buildString {
            append("Successfully exported ${result.count} endpoints to Postman")
            if (!details.isNullOrBlank()) {
                append("\n$details")
            }
        }
        swing {
            Messages.showInfoMessage(project, message, "Export API")
        }
        return true
    }

    private suspend fun handleFileExport(
        project: Project,
        result: ExportResult.Success,
        metadata: PostmanExportMetadata,
        config: ChannelConfig
    ): Boolean {
        val fileConfig = config as? ChannelConfig.FileConfig
        val targetFile = resolveTargetFile(project, fileConfig, "postman_collection.json")
            ?: throw CancellationException("User cancelled file selection")

        val gson = postmanGson()
        val content = gson.toJson(metadata.collectionData)

        withContext(IdeDispatchers.Background) {
            targetFile.writeText(content)
        }

        swing {
            Messages.showInfoMessage(
                project,
                "Successfully exported ${result.count} endpoints to ${targetFile.absolutePath}",
                "Export API"
            )
        }
        return true
    }

    private suspend fun resolveTargetFile(
        project: Project,
        fileConfig: ChannelConfig.FileConfig?,
        defaultFileName: String
    ): File? {
        val outputDir = fileConfig?.outputDir
        val fileName = fileConfig?.fileName
        if (!outputDir.isNullOrBlank()) {
            val dir = File(outputDir)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val name = if (!fileName.isNullOrBlank()) "$fileName.json" else defaultFileName
            return File(dir, name)
        }
        return selectTargetFile(project, defaultFileName)
    }

    private suspend fun selectTargetFile(project: Project, defaultFileName: String): File? {
        return swing {
            val descriptor = FileSaverDescriptor(
                "Save Postman Collection",
                "Choose where to save the Postman collection file"
            )
            val saver = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
            val wrapper: VirtualFileWrapper? = saver.save(null as VirtualFile?, defaultFileName)
            wrapper?.file
        }
    }

    private fun countApiItems(collection: PostmanCollection): Int {
        fun count(items: List<PostmanItem>): Int = items.sumOf { item ->
            if (item.request != null) 1 else count(item.item)
        }
        return count(collection.item)
    }
}

private class PostmanCollectionItem(val name: String, val id: String, val uid: String? = null) {
    override fun toString(): String = name
}

private class PostmanWorkspaceItem(val name: String, val id: String) {
    override fun toString(): String = name
}

/**
 * Configuration panel for [PostmanChannel].
 *
 * Allows the user to select a Postman workspace and collection,
 * or choose to create a new collection.
 */
class PostmanOptionsPanel(private val project: Project) : ChannelOptionsPanel, IdeaLog {

    private val postmanWorkspaces = mutableListOf<PostmanWorkspaceItem>()
    private val postmanCollectionItems = mutableListOf<PostmanCollectionItem>()
    private val postmanWorkspaceComboBox = ComboBox<String>().apply { isEditable = false }
    private val postmanCollectionComboBox = ComboBox<String>().apply { isEditable = true }
    private val postmanRefreshButton = JButton("Refresh").apply {
        addActionListener { refreshPostmanData() }
    }
    private val errorLabel = JLabel("").apply { foreground = java.awt.Color.RED }

    private var selectedPostmanCollection: PostmanCollectionItem? = null
    private var postmanDataLoaded = false
    private var postmanClient: CachedPostmanApiClient? = null

    override val component: JComponent = JPanel().apply {
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
        add(Box.createVerticalStrut(5))
        add(errorLabel)
    }

    override fun onShown() {
        if (!postmanDataLoaded) {
            postmanDataLoaded = true
            loadPostmanDataFromApi()
        }
    }

    override fun buildConfig(): ChannelConfig.PostmanConfig {
        val wsIdx = postmanWorkspaceComboBox.selectedIndex
        val ws = if (wsIdx >= 0 && wsIdx < postmanWorkspaces.size) postmanWorkspaces[wsIdx] else null

        val collectionText = ((postmanCollectionComboBox.editor.editorComponent as? JTextField)?.text
            ?: postmanCollectionComboBox.selectedItem?.toString())?.trim()
            ?.removePrefix("(New): ")?.trim().orEmpty()

        val remembered = selectedPostmanCollection
        val isUpdate = remembered != null && collectionText == remembered.name

        return ChannelConfig.PostmanConfig(
            workspaceId = ws?.id,
            workspaceName = ws?.name,
            collectionId = if (isUpdate) (remembered.uid ?: remembered.id) else null,
            collectionName = collectionText.ifEmpty { null },
            isUpdate = isUpdate
        )
    }

    private fun refreshPostmanData() {
        postmanDataLoaded = false
        errorLabel.text = ""
        onShown()
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
                    errorLabel.text = ""

                    if (postmanWorkspaces.isNotEmpty()) {
                        postmanWorkspaceComboBox.model = DefaultComboBoxModel(
                            postmanWorkspaces.map { it.toString() }.toTypedArray()
                        )
                        postmanWorkspaceComboBox.selectedIndex = 0
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
            } catch (e: Exception) {
                LOG.warn("Failed to load Postman workspaces", e)
                swing(ModalityState.any()) {
                    postmanWorkspaceComboBox.model = DefaultComboBoxModel(arrayOf("Failed to load"))
                    populateCollectionCombo(emptyList())
                    errorLabel.text = "Failed to load workspaces — check your API token in Settings"
                }
            }
        }
    }

    private suspend fun loadCollectionsForWorkspace(client: CachedPostmanApiClient, workspaceId: String) {
        try {
            val collections = client.listCollections(workspaceId, useCache = true)
            swing(ModalityState.any()) {
                populateCollectionCombo(collections.map { PostmanCollectionItem(it.name, it.id, it.uid) })
                errorLabel.text = ""
            }
        } catch (e: Exception) {
            LOG.warn("Failed to load Postman collections", e)
            swing(ModalityState.any()) {
                populateCollectionCombo(emptyList())
                errorLabel.text = "Failed to load collections — check your API token in Settings"
            }
        }
    }

    private fun populateCollectionCombo(collections: List<PostmanCollectionItem>) {
        postmanCollectionItems.clear()
        postmanCollectionItems.addAll(collections)
        selectedPostmanCollection = null

        val inferredName = defaultNewCollectionName()
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

    init {
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
    }
}
