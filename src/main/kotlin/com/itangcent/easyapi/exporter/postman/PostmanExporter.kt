package com.itangcent.easyapi.exporter.postman

import com.intellij.openapi.components.Service
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWrapper
import com.itangcent.easyapi.core.context.ActionContext
import com.itangcent.easyapi.core.threading.IdeDispatchers
import com.itangcent.easyapi.core.threading.swing
import com.itangcent.easyapi.exporter.ApiExporter
import com.itangcent.easyapi.exporter.model.ExportContext
import com.itangcent.easyapi.exporter.model.ExportFormat
import com.itangcent.easyapi.exporter.model.ExportResult
import com.itangcent.easyapi.exporter.postman.model.PostmanCollection
import com.itangcent.easyapi.exporter.postman.model.PostmanItem
import com.itangcent.easyapi.http.HttpClientProvider
import com.itangcent.easyapi.settings.PostmanExportMode
import com.itangcent.easyapi.settings.SettingBinder
import com.itangcent.easyapi.exporter.postman.model.postmanGson
import com.itangcent.easyapi.util.ide.ModuleHelper
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Exports API endpoints to Postman collections.
 *
 * Supports two export modes:
 * 1. **File Export** - Saves collection as JSON file (when no API token configured)
 * 2. **API Export** - Uploads directly to Postman (when API token configured)
 *
 * ## Export Modes
 * - **CREATE_NEW** - Always creates a new collection
 * - **UPDATE_EXISTING** - Updates existing collections by module
 *
 * ## Features
 * - Automatic collection naming with timestamps
 * - Module-based collection organization
 * - Pre-request and test script support
 * - Workspace selection support
 *
 * @see PostmanFormatter for collection formatting
 * @see PostmanApiClient for API communication
 */
@Service(Service.Level.PROJECT)
class PostmanExporter(private val project: Project) : ApiExporter {

    override val format: ExportFormat = ExportFormat.POSTMAN

    companion object {
        fun getInstance(project: Project): PostmanExporter {
            return project.getService(PostmanExporter::class.java)
        }
    }

    override suspend fun export(context: ExportContext): ExportResult {
        val settings = SettingBinder.getInstance(project).read()
        val token = settings.postmanToken
        val postmanOptions = context.outputConfig.postmanOptions

        val collectionName = postmanOptions?.selectedCollectionName ?: context.project.name
        val hasExplicitName = postmanOptions?.selectedCollectionName != null

        val formatter = PostmanFormatter(
            actionContext = context.actionContext ?: ActionContext.forProject(project),
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

        val collection = formatter.format(
            context.endpointsToExport,
            collectionName
        )

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
            val hasDialogSelection = postmanOptions?.selectedCollectionId != null
                    || postmanOptions?.selectedCollectionName != null
            if (hasDialogSelection) {
                uploadToPostmanWithDialogSelection(context, token, collection, settings)
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
        settings: com.itangcent.easyapi.settings.Settings
    ): ExportResult {
        val postmanOptions = context.outputConfig.postmanOptions!!
        val workspaceId = postmanOptions.selectedWorkspaceId ?: settings.postmanWorkspace
        val collectionId = postmanOptions.selectedCollectionId

        val httpClient = HttpClientProvider.getInstance(
            context.actionContext ?: ActionContext.forProject(project)
        ).getClient()
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
        val workspaceId = settings.postmanWorkspace

        val httpClient = HttpClientProvider.getInstance(
            context.actionContext ?: ActionContext.forProject(project)
        ).getClient()
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
                    context,
                    collection,
                    settings,
                    postmanClient,
                    workspaceName,
                    workspaceId
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
        val totalCount = results.size
        return if (successCount == totalCount) {
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
        val formatter = PostmanFormatter(
            actionContext = context.actionContext ?: ActionContext.forProject(project),
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

    override suspend fun handleExportResult(project: Project, result: ExportResult.Success): Boolean {
        val metadata = result.metadata as? PostmanExportMetadata ?: return false

        if (metadata.collectionData != null) {
            return handleFileExport(project, result, metadata)
        }

        val details = metadata.formatDisplay()
        val message = buildString {
            append("Successfully exported ${result.count} endpoints to Postman")
            if (!details.isNullOrBlank()) {
                append("\n$details")
            }
        }
        Messages.showInfoMessage(project, message, "Export API")
        return true
    }

    private suspend fun handleFileExport(
        project: Project,
        result: ExportResult.Success,
        metadata: PostmanExportMetadata
    ): Boolean {
        val targetFile = selectTargetFile(project) ?: return false

        val gson = postmanGson()
        val content = gson.toJson(metadata.collectionData)

        withContext(IdeDispatchers.Background) {
            targetFile.writeText(content)
        }

        swing {
            showSuccessMessage(project, result, targetFile.absolutePath)
        }
        return true
    }

    private suspend fun selectTargetFile(project: Project): File? {
        return swing {
            val descriptor = FileSaverDescriptor(
                "Save Postman Collection",
                "Choose where to save the Postman collection file"
            )
            val saver = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)

            val wrapper: VirtualFileWrapper? = saver.save(null as VirtualFile?, "postman_collection.json")
            wrapper?.file
        }
    }

    private fun showSuccessMessage(project: Project, result: ExportResult.Success, target: String) {
        val message = buildString {
            append("Successfully exported ${result.count} endpoints to $target")
            result.metadata?.formatDisplay()?.let { append(" $it") }
        }
        Messages.showInfoMessage(
            project,
            message,
            "Export API"
        )
    }

    private fun countApiItems(collection: PostmanCollection): Int {
        fun count(items: List<PostmanItem>): Int = items.sumOf { item ->
            if (item.request != null) 1 else count(item.item)
        }
        return count(collection.item)
    }
}
