package com.itangcent.easyapi.ide.dialog

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.cache.ProjectCacheRepository
import com.itangcent.easyapi.util.GsonUtils

/**
 * Data class representing export dialog preferences.
 * 
 * @property lastExportFormat The last selected export format
 * @property lastOutputDir The last output directory
 * @property lastFileName The last file name
 * @property lastPostmanWorkspaceId The last selected Postman workspace ID
 * @property lastPostmanWorkspaceName The last selected Postman workspace name
 * @property lastPostmanCollectionId The last selected Postman collection ID
 * @property lastPostmanCollectionName The last selected Postman collection name
 * @property lastYapiToken The last selected YAPI project token
 */
data class ExportDialogPreferences(
    val lastExportFormat: String? = null,
    val lastOutputDir: String? = null,
    val lastFileName: String? = null,
    val lastPostmanWorkspaceId: String? = null,
    val lastPostmanWorkspaceName: String? = null,
    val lastPostmanCollectionId: String? = null,
    val lastPostmanCollectionName: String? = null,
    val lastYapiToken: String? = null
)

/**
 * Handles persistence of export dialog preferences.
 * 
 * This class provides functionality to save and load export dialog preferences
 * to/from a JSON file in the project cache directory. Useful for preserving
 * user's last used export options across IDE sessions.
 * 
 * @param project The IntelliJ project context
 */
class ExportDialogPreferencesPersistence(project: Project) {
    private val repo = ProjectCacheRepository.getInstance(project)
    private val key = "export_dialog_preferences.json"

    /**
     * Loads the export dialog preferences.
     * 
     * @return The preferences, or default preferences if none found
     */
    fun load(): ExportDialogPreferences {
        val raw = repo.read(key) ?: return ExportDialogPreferences()
        return runCatching { GsonUtils.fromJson<ExportDialogPreferences>(raw) }.getOrNull()
            ?: ExportDialogPreferences()
    }

    /**
     * Saves the export dialog preferences.
     * 
     * @param preferences The preferences to save
     */
    fun save(preferences: ExportDialogPreferences) {
        repo.write(key, GsonUtils.toJson(preferences))
    }

    /**
     * Clears the export dialog preferences.
     */
    fun reset() {
        repo.delete(key)
    }
}
