package com.itangcent.easyapi.core.script

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.cache.ProjectCacheRepository
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.util.json.GsonUtils
import com.itangcent.easyapi.core.util.storage.DbBeanBinder
import com.itangcent.easyapi.core.util.storage.SqliteDataResourceHelper

@Service(Service.Level.PROJECT)
class ScriptCacheService(private val project: Project) : IdeaLog {

    private val sqliteHelper: SqliteDataResourceHelper by lazy {
        val cacheFile = ProjectCacheRepository.getInstance(project).resolve("script_cache.db")
        SqliteDataResourceHelper(cacheFile)
    }

    private val binder: DbBeanBinder<ScriptCache> by lazy {
        DbBeanBinder(
            sqliteHelper,
            "script",
            { GsonUtils.toJson(it) },
            { GsonUtils.fromJson(it) }
        )
    }

    fun save(scope: ScriptScope, cache: ScriptCache) {
        runCatching { binder.save(scope.key, cache) }
            .onFailure { LOG.warn("Failed to save script cache for scope=${scope.key}", it) }
    }

    fun load(scope: ScriptScope): ScriptCache? {
        return runCatching { binder.load(scope.key) }
            .onFailure { LOG.warn("Failed to load script cache for scope=${scope.key}", it) }
            .getOrNull()
    }

    fun delete(scope: ScriptScope) {
        runCatching { binder.delete(scope.key) }
            .onFailure { LOG.warn("Failed to delete script cache for scope=${scope.key}", it) }
    }

    fun resolveScripts(scopes: List<ScriptScope>): ResolvedScripts {
        val preScripts = mutableListOf<String>()
        val postScripts = mutableListOf<String>()
        for (scope in scopes) {
            val cache = load(scope) ?: continue
            cache.preRequestScript?.takeIf { it.isNotBlank() }?.let { preScripts.add(it) }
            cache.postResponseScript?.takeIf { it.isNotBlank() }?.let { postScripts.add(it) }
        }
        return ResolvedScripts(
            preRequestScript = preScripts.takeIf { it.isNotEmpty() }?.joinToString("\n\n"),
            postResponseScript = postScripts.takeIf { it.isNotEmpty() }?.joinToString("\n\n")
        )
    }

    companion object {
        fun getInstance(project: Project): ScriptCacheService =
            project.getService(ScriptCacheService::class.java)
    }
}

data class ResolvedScripts(
    val preRequestScript: String? = null,
    val postResponseScript: String? = null
)
