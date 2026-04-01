package com.itangcent.easyapi.util.storage

import com.itangcent.easyapi.util.GsonUtils
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.cache.ProjectCacheRepository
import com.itangcent.easyapi.util.storage.Storage.Companion.DEFAULT_GROUP

/**
 * SQLite-backed [Storage] that persists across sessions.
 *
 * A project-level service that stores data in a SQLite database.
 * Each group is stored as a single JSON blob keyed by the group name.
 *
 * ## Usage
 * ```kotlin
 * val localStorage = LocalStorage.getInstance(project)
 * localStorage.set("settings", "theme", "dark")
 * val theme = localStorage.get("settings", "theme")
 * ```
 *
 * @see Storage for the interface
 * @see SessionStorage for in-memory storage
 */
@Service(Service.Level.PROJECT)
class LocalStorage(project: Project) : AbstractStorage() {

    private val sqliteHelper: SqliteDataResourceHelper = run {
        val dbPath = ProjectCacheRepository.getInstance(project).resolve(".api.local.storage.v2.db")
        SqliteDataResourceHelper(dbPath)
    }

    companion object {
        private val MAP_TYPE = object : TypeToken<LinkedHashMap<String, Any?>>() {}.type

        fun getInstance(project: Project): LocalStorage =
            project.getService(LocalStorage::class.java)
    }

    override fun clear(group: String?) {
        val g = group ?: DEFAULT_GROUP
        sqliteHelper.delete(g)
    }

    override fun getCache(group: String): MutableMap<String, Any?> {
        val raw = sqliteHelper.query(group) ?: return linkedMapOf()
        return runCatching {
            GsonUtils.fromJson<LinkedHashMap<String, Any?>>(raw, MAP_TYPE)
        }.getOrDefault(linkedMapOf())
    }

    override fun onUpdate(group: String?, cache: MutableMap<String, Any?>) {
        val g = group ?: DEFAULT_GROUP
        if (cache.isEmpty()) {
            sqliteHelper.delete(g)
        } else {
            sqliteHelper.upsert(g, GsonUtils.toJson(cache))
        }
    }
}
