package com.itangcent.easyapi.http

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.cache.ProjectCacheRepository
import com.itangcent.easyapi.util.GsonUtils

/**
 * Persists HTTP cookies to the project cache.
 *
 * A project-level service that stores cookies in a JSON file.
 * Used for maintaining session state across plugin restarts.
 *
 * ## Usage
 * ```kotlin
 * val helper = CookiePersistenceHelper.getInstance(project)
 *
 * // Load saved cookies
 * val cookies = helper.loadCookies()
 *
 * // Save cookies
 * helper.saveCookies(newCookies)
 * ```
 *
 * @see HttpCookie for the cookie model
 * @see ProjectCacheRepository for the underlying storage
 */
@Service(Service.Level.PROJECT)
class CookiePersistenceHelper(project: Project) {

    private val projectCacheRepository = ProjectCacheRepository.getInstance(project)

    companion object {
        private const val CACHE_FILE = ".cookies.v1.0.json"

        fun getInstance(project: Project): CookiePersistenceHelper =
            project.getService(CookiePersistenceHelper::class.java)
    }

    fun loadCookies(): List<HttpCookie> {
        val raw = projectCacheRepository.read(CACHE_FILE) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return runCatching { GsonUtils.fromJson<List<HttpCookie>>(raw) }.getOrNull().orEmpty()
    }

    fun saveCookies(cookies: List<HttpCookie>) {
        val json = GsonUtils.toJson(cookies)
        projectCacheRepository.write(CACHE_FILE, json)
    }
}
