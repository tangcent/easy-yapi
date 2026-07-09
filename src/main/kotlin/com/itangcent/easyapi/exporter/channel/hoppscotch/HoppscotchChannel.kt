package com.itangcent.easyapi.exporter.channel.hoppscotch

import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWrapper
import com.itangcent.easyapi.core.threading.IdeDispatchers
import com.itangcent.easyapi.core.threading.swing
import com.itangcent.easyapi.exporter.channel.Channel
import com.itangcent.easyapi.exporter.channel.ChannelConfig
import com.itangcent.easyapi.exporter.channel.ChannelOptionsPanel
import com.itangcent.easyapi.exporter.channel.hoppscotch.*
import com.itangcent.easyapi.exporter.channel.hoppscotch.model.HoppCollection
import com.itangcent.easyapi.exporter.channel.hoppscotch.model.hoppscotchGson
import com.itangcent.easyapi.exporter.model.ExportContext
import com.itangcent.easyapi.exporter.model.ExportResult
import com.itangcent.easyapi.rule.RuleKey
import com.itangcent.easyapi.http.HttpClientProvider
import com.itangcent.easyapi.ide.support.NotificationUtils
import com.itangcent.easyapi.logging.IdeaConsoleProvider
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.settings.SettingBinder
import com.itangcent.easyapi.settings.settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.reflect.KClass

/**
 * [Channel] implementation for exporting API endpoints to Hoppscotch.
 *
 * Supports two export modes:
 * 1. **File export** — when no Hoppscotch access token is configured, serializes the
 *    collection to a JSON file using [hoppscotchGson]
 * 2. **Cloud upload** — when an access token is available, uploads the collection to
 *    Hoppscotch via the GraphQL API. Supports both creating new collections and
 *    updating existing ones (create-first-then-delete strategy).
 *
 * ## Cloud upload flow
 * - On 401 errors, automatically attempts token refresh via [HoppscotchAuthService.refreshToken]
 * - If refresh fails, prompts the user to re-authenticate
 * - Progress is reported via [IdeaConsoleProvider]
 * - GraphQL errors are translated to user-friendly messages via [formatGraphQLError]
 *
 * @see HoppscotchFormatter for the ApiEndpoint → HoppCollection conversion
 * @see HoppscotchOptionsPanel for the dual-mode options UI
 * @see HoppscotchApiClient for the GraphQL API client
 */
class HoppscotchChannel : Channel, IdeaLog {

    override val id: String = "hoppscotch"
    override val displayName: String = "Hoppscotch (Beta)"
    override val supportsGrpc: Boolean = false
    override val exposeAsAction: Boolean = true
    override val actionText: String = "Export to Hoppscotch (Beta)"
    override val settingsType: KClass<out com.itangcent.easyapi.settings.Settings> = HoppscotchSettings::class

    override fun createOptionsPanel(project: Project): ChannelOptionsPanel {
        return HoppscotchOptionsPanel(project)
    }

    override fun createSettingsPanel(project: Project): com.itangcent.easyapi.settings.ui.SettingsPanel<*>? =
        HoppscotchSettingsPanel(project)

    override fun configFiles(): List<String> = emptyList()

    override fun ruleKeys(): List<RuleKey<*>> = RuleKey.collectFrom(HoppscotchRuleKeys)

    override suspend fun export(context: ExportContext): ExportResult {
        val project = context.project
        val hoppscotchSettings = project.settings<HoppscotchSettings>()
        val token = hoppscotchSettings.hoppscotchToken
        val collectionName = project.name

        val formatter = HoppscotchFormatter(project)
        val collection = formatter.format(context.endpointsToExport, collectionName)

        if (token.isNullOrBlank()) {
            return ExportResult.Success(
                count = countRequests(collection),
                target = "Hoppscotch Collection",
                metadata = HoppscotchExportMetadata(
                    collectionName = collection.name,
                    collectionData = collection
                )
            )
        }

        val hoppscotchConfig = context.channelConfig as? HoppscotchConfig
        return uploadToHoppscotch(project, token, collection, hoppscotchSettings, hoppscotchConfig)
    }

    private suspend fun uploadToHoppscotch(
        project: Project,
        token: String,
        collection: HoppCollection,
        settings: HoppscotchSettings,
        hoppscotchConfig: HoppscotchConfig?
    ): ExportResult {
        val console = IdeaConsoleProvider.getInstance(project).getConsole()
        val serverUrl = settings.hoppscotchServerUrl?.takeIf { it.isNotBlank() } ?: "https://hoppscotch.io"
        val backendUrl = settings.hoppscotchBackendUrl?.takeIf { it.isNotBlank() }
        val httpClient = HttpClientProvider.getInstance(project).getClient()
        val client = HoppscotchApiClient(token, serverUrl, backendUrl, httpClient).asCached()

        val collectionId = hoppscotchConfig?.collectionId
        val isUpdate = hoppscotchConfig?.isUpdate == true && collectionId != null

        if (isUpdate) {
            console.info("Updating Hoppscotch collection \"${hoppscotchConfig.collectionName}\"...")
            val confirmed = swing {
                val choice = Messages.showYesNoDialog(
                    project,
                    "Update existing collection \"${hoppscotchConfig.collectionName}\"?\n" +
                            "This will create a new collection and delete the old one.\n" +
                            "Note: The collection ID will change and shared links will break.",
                    "Confirm Update",
                    Messages.getQuestionIcon()
                )
                choice == Messages.YES
            }
            if (!confirmed) {
                console.info("Update cancelled by user")
                return ExportResult.Error("Update cancelled by user")
            }

            console.info("Uploading collection to Hoppscotch...")
            val result = try {
                client.updateCollection(collectionId, collection)
            } catch (e: HoppscotchAuthException) {
                return handleAuthErrorWithRefresh(project, token, collection, serverUrl, backendUrl, httpClient, hoppscotchConfig, console)
            }
            return if (result.success) {
                console.info("Collection updated successfully (new ID: ${result.collectionId})")
                ExportResult.Success(
                    count = countRequests(collection),
                    target = "Hoppscotch",
                    metadata = HoppscotchExportMetadata(
                        collectionName = collection.name,
                        collectionId = result.collectionId ?: collectionId
                    )
                )
            } else {
                val userMessage = formatGraphQLError(result.message)
                console.warn("Upload failed: $userMessage")
                ExportResult.Error(userMessage)
            }
        }

        console.info("Uploading collection \"${collection.name}\" to Hoppscotch...")
        val result = try {
            client.uploadCollection(collection)
        } catch (e: HoppscotchAuthException) {
            return handleAuthErrorWithRefresh(project, token, collection, serverUrl, backendUrl, httpClient, hoppscotchConfig, console)
        }
        return if (result.success) {
            console.info("Collection uploaded successfully (ID: ${result.collectionId})")
            ExportResult.Success(
                count = countRequests(collection),
                target = "Hoppscotch",
                metadata = HoppscotchExportMetadata(
                    collectionName = collection.name,
                    collectionId = result.collectionId
                )
            )
        } else {
            val userMessage = formatGraphQLError(result.message)
            console.warn("Upload failed: $userMessage")
            ExportResult.Error(userMessage)
        }
    }

    private suspend fun handleAuthErrorWithRefresh(
        project: Project,
        token: String,
        collection: HoppCollection,
        serverUrl: String,
        backendUrl: String?,
        httpClient: com.itangcent.easyapi.http.HttpClient,
        hoppscotchConfig: HoppscotchConfig?,
        console: com.itangcent.easyapi.logging.IdeaConsole = IdeaConsoleProvider.getInstance(project).getConsole()
    ): ExportResult {
        console.info("Token expired, attempting refresh...")
        LOG.info("Hoppscotch token expired, attempting refresh...")
        val authService = HoppscotchAuthService.getInstance(project)
        val refreshed = authService.refreshToken()
        if (refreshed) {
            console.info("Token refreshed successfully, retrying upload...")
            LOG.info("Token refreshed successfully, retrying upload...")
            val newSettings = SettingBinder.getInstance(project).read(HoppscotchSettings::class)
            val newToken = newSettings.hoppscotchToken ?: return handleAuthError(
                project, HoppscotchAuthException("Token lost after refresh"), console
            )
            val newClient = HoppscotchApiClient(newToken, serverUrl, backendUrl, httpClient).asCached()
            val collectionId = hoppscotchConfig?.collectionId
            val isUpdate = hoppscotchConfig?.isUpdate == true && collectionId != null

            return try {
                val result = if (isUpdate) {
                    newClient.updateCollection(collectionId!!, collection)
                } else {
                    newClient.uploadCollection(collection)
                }
                if (result.success) {
                    console.info("Collection uploaded successfully after token refresh")
                    ExportResult.Success(
                        count = countRequests(collection),
                        target = "Hoppscotch",
                        metadata = HoppscotchExportMetadata(
                            collectionName = collection.name,
                            collectionId = result.collectionId ?: collectionId
                        )
                    )
                } else {
                    ExportResult.Error(formatGraphQLError(result.message))
                }
            } catch (e: HoppscotchAuthException) {
                handleAuthError(project, e, console)
            }
        }
        console.warn("Token refresh failed. Please login again.")
        return handleAuthError(project, HoppscotchAuthException("Token refresh failed"), console)
    }

    private suspend fun handleAuthError(
        project: Project,
        e: HoppscotchAuthException,
        console: com.itangcent.easyapi.logging.IdeaConsole = IdeaConsoleProvider.getInstance(project).getConsole()
    ): ExportResult {
        LOG.warn("Hoppscotch authentication error: ${e.message}")
        console.error("Authentication failed: ${e.message}")
        swing {
            NotificationUtils.notifyWarning(
                project,
                "Hoppscotch Authentication Failed",
                "Your Hoppscotch token has expired. Please login again via Settings > Hoppscotch."
            )
        }
        return ExportResult.Error("Authentication failed: ${e.message}. Please login again.")
    }

    private fun formatGraphQLError(message: String?): String {
        if (message.isNullOrBlank()) return "Upload failed with an unknown error"
        return when {
            message.contains("UNAUTHENTICATED", ignoreCase = true) ->
                "Authentication failed. Please login again via Settings > Hoppscotch."
            message.contains("FORBIDDEN", ignoreCase = true) ->
                "Permission denied. You may not have access to this collection or team."
            message.contains("NOT_FOUND", ignoreCase = true) ->
                "The specified collection was not found on the server."
            message.contains("already exists", ignoreCase = true) ->
                "A collection with this name already exists. Try updating instead."
            message.contains("rate limit", ignoreCase = true) ->
                "Rate limit exceeded. Please wait a moment and try again."
            else -> "Upload failed: $message"
        }
    }

    override suspend fun handleResult(
        project: Project,
        result: ExportResult.Success,
        config: ChannelConfig
    ): Boolean {
        val metadata = result.metadata as? HoppscotchExportMetadata ?: return false

        if (metadata.collectionData != null) {
            return handleFileExport(project, result, metadata, config)
        }

        val details = metadata.formatDisplay()
        val message = buildString {
            append("Successfully exported ${result.count} endpoints to Hoppscotch")
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
        metadata: HoppscotchExportMetadata,
        config: ChannelConfig
    ): Boolean {
        val fileConfig = config as? ChannelConfig.FileConfig
        val targetFile = resolveTargetFile(project, fileConfig, "hoppscotch_collection.json")
            ?: throw CancellationException("User cancelled file selection")

        val gson = hoppscotchGson()
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
                "Save Hoppscotch Collection",
                "Choose where to save the Hoppscotch collection file"
            )
            val saver = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
            val wrapper: VirtualFileWrapper? = saver.save(null as VirtualFile?, defaultFileName)
            wrapper?.file
        }
    }

    private fun countRequests(collection: HoppCollection): Int {
        fun count(coll: HoppCollection): Int =
            coll.requests.size + coll.folders.sumOf { count(it) }
        return count(collection)
    }
}
