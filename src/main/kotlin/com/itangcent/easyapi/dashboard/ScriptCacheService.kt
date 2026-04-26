package com.itangcent.easyapi.dashboard

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.cache.ProjectCacheRepository
import com.itangcent.easyapi.util.GsonUtils
import com.itangcent.easyapi.util.storage.DbBeanBinder
import com.itangcent.easyapi.util.storage.SqliteDataResourceHelper

@Service(Service.Level.PROJECT)
class ScriptCacheService(private val project: Project) {

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
        binder.save(scope.key, cache)
    }

    fun load(scope: ScriptScope): ScriptCache? {
        return binder.load(scope.key)
    }

    fun delete(scope: ScriptScope) {
        binder.delete(scope.key)
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
