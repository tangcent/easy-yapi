package com.itangcent.easyapi.exporter.postman

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.itangcent.easyapi.core.threading.IdeDispatchers
import com.itangcent.easyapi.core.threading.swing
import com.itangcent.easyapi.http.HttpClientProvider
import com.itangcent.easyapi.settings.SettingBinder
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Properties

/**
 * Manages Postman collection associations for project modules.
 *
 * Provides functionality to:
 * - Store and retrieve collection IDs per module
 * - Prompt users to select collections
 * - Persist collection mappings in settings
 *
 * Used when exporting in UPDATE_EXISTING mode to maintain
 * consistent collection IDs across exports.
 *
 * @see PostmanExporter for usage in export workflow
 */
@Service(Service.Level.PROJECT)
class PostmanCollectionHelper(private val project: Project) {

    private val promptedModules = mutableSetOf<String>()

    private val settingBinder: SettingBinder by lazy { SettingBinder.getInstance(project) }

    companion object {
        fun getInstance(project: Project): PostmanCollectionHelper = project.service()
    }

    suspend fun getCollectionIdForModule(module: String, prompt: Boolean = true): String? {
        val settings = settingBinder.read()

        val savedCollectionId = getCollectionId(settings.postmanCollections, module)
        if (savedCollectionId != null) {
            return savedCollectionId
        }

        if (prompt && promptedModules.add(module)) {
            return promptForCollection(settings.postmanToken, settings.postmanWorkspace, module)
        }

        return null
    }

    private suspend fun promptForCollection(token: String?, workspaceId: String?, module: String): String? {
        if (token.isNullOrBlank()) {
            return null
        }

        val httpClient = HttpClientProvider.getInstance(project).getClient()
        val postmanClient = PostmanApiClient(token, workspaceId = workspaceId, httpClient = httpClient).asCached()

        return swing {
            val collections = try {
                postmanClient.listCollections(workspaceId ?: "", useCache = true)
            } catch (e: Exception) {
                emptyList()
            }

            if (collections.isEmpty()) {
                null
            } else {
                val options = collections.map { it.name }.toTypedArray()
                val selected = Messages.showEditableChooseDialog(
                    "Select a collection to save APIs in [$module] to",
                    "Postman Collection",
                    Messages.getInformationIcon(),
                    options,
                    options.firstOrNull(),
                    null
                )
                if (selected != null) {
                    val selectedCollection = collections.find { it.name == selected }
                    if (selectedCollection != null) {
                        setCollectionId(module, selectedCollection.id)
                        selectedCollection.id
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        }
    }

    private fun getCollectionId(postmanCollections: String?, module: String): String? {
        val properties = Properties()
        postmanCollections?.byteInputStream()?.let { properties.load(it) }
        return properties.getProperty(module)
    }

    private fun setCollectionId(module: String, collectionId: String) {
        val settings = settingBinder.read()
        val properties = Properties()
        settings.postmanCollections?.byteInputStream()?.let { properties.load(it) }
        properties[module] = collectionId
        val output = ByteArrayOutputStream()
        properties.store(output, "")
        val newCollections = output.toString().removePropertiesComments()
        val updatedSettings = settings.copy(postmanCollections = newCollections)
        settingBinder.save(updatedSettings)
    }

    fun readCollections(): Map<String, String> {
        val settings = settingBinder.read()
        val properties = Properties()
        settings.postmanCollections?.byteInputStream()?.let { properties.load(it) }
        return properties.entries.associate { (it.key as String) to (it.value as String) }
    }

    fun removeCollectionId(module: String) {
        val settings = settingBinder.read()
        val properties = Properties()
        settings.postmanCollections?.byteInputStream()?.let { properties.load(it) }
        properties.remove(module)
        val output = ByteArrayOutputStream()
        properties.store(output, "")
        val newCollections = output.toString().removePropertiesComments()
        val updatedSettings = settings.copy(postmanCollections = newCollections)
        settingBinder.save(updatedSettings)
    }

    fun resetPromptedModules() {
        promptedModules.clear()
    }

    private fun String.removePropertiesComments(): String {
        var ret = this
        while (ret.startsWith("#") && ret.contains('\n')) {
            if (ret.substringBefore('\n').contains('=')) {
                break
            }
            ret = ret.substringAfter('\n')
        }
        return ret
    }
}
