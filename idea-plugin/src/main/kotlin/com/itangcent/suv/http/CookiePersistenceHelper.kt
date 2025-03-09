package com.itangcent.suv.http

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.common.logger.Log
import com.itangcent.common.logger.traceError
import com.itangcent.common.utils.GsonUtils
import com.itangcent.http.BasicCookie
import com.itangcent.http.CookieStore
import com.itangcent.http.json
import com.itangcent.idea.plugin.api.cache.ProjectCacheRepository

/**
 * A utility class that helps persist cookies to file and load them back.
 * This class provides methods to save and load cookies from a CookieStore to persistent storage.
 *
 * @author tangcent
 */
@Singleton
class CookiePersistenceHelper {

    @Inject
    private lateinit var projectCacheRepository: ProjectCacheRepository

    companion object : Log() {
        private const val CACHE_FILE = ".cookies.v1.0.json"
    }

    /**
     * Reads cookies from persistent storage and loads them into the provided CookieStore.
     * Any expired cookies in storage will be skipped.
     *
     * @param cookieStore the CookieStore to load cookies into
     */
    fun loadCookiesInto(cookieStore: CookieStore) {
        try {
            val cookieFile = projectCacheRepository.getOrCreateFile(CACHE_FILE)
            val cookiesJson = cookieFile.readText()
            if (cookiesJson.isBlank()) return

            val cookiesList = GsonUtils.fromJson<List<String>>(cookiesJson)
            cookiesList?.forEach { cookieJson ->
                val cookie = BasicCookie.fromJson(cookieJson)
                cookieStore.addCookie(cookie)
            }
        } catch (e: Exception) {
            // If file doesn't exist or there's an error reading it, just log and return
            LOG.traceError("cookies is invalid", e)
            return
        }
    }

    /**
     * Stores all cookies from the provided CookieStore into persistent storage.
     * Only non-expired cookies will be saved.
     *
     * @param cookieStore the CookieStore to save cookies from
     */
    fun storeCookiesFrom(cookieStore: CookieStore) {
        val cookieFile = projectCacheRepository.getOrCreateFile(CACHE_FILE)
        val cookies = cookieStore.cookies()
        val cookiesJson = cookies.map { it.json() }
        cookieFile.writeText(GsonUtils.toJson(cookiesJson))
    }
}