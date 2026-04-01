package com.itangcent.easyapi.cache

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.itangcent.easyapi.http.HttpCookie

/**
 * Default implementation of [HttpContextCacheHelper].
 *
 * Persists HTTP context data using [ProjectCacheRepository]:
 * - Host names stored in `http_hosts.txt`
 * - Cookies stored in `http_cookies.json`
 *
 * ## Features
 * - Host history with deduplication and max limit
 * - Cookie management with automatic updates
 * - Interactive host selection dialog
 *
 * @see HttpContextCacheHelper for the interface
 * @see ProjectCacheRepository for storage
 */
@Service(Service.Level.PROJECT)
class DefaultHttpContextCacheHelper(
    project: Project
) : HttpContextCacheHelper {

    private val projectCacheRepository: ProjectCacheRepository = ProjectCacheRepository.getInstance(project)

    companion object {
        private const val HOSTS_KEY = "http_hosts.txt"
        private const val COOKIES_KEY = "http_cookies.json"
        private const val MAX_HOST_HISTORY = 10
        private const val DEFAULT_HOST = "http://localhost:8080"

        fun getInstance(project: Project): DefaultHttpContextCacheHelper =
            project.getService(DefaultHttpContextCacheHelper::class.java)
    }

    override fun getHosts(): List<String> {
        val raw = projectCacheRepository.read(HOSTS_KEY) ?: return listOf(DEFAULT_HOST)
        val hosts = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return hosts.ifEmpty { listOf(DEFAULT_HOST) }
    }

    override fun addHost(host: String) {
        val normalized = host.trim()
        if (normalized.isEmpty()) return
        val current = getHosts().toMutableList()
        current.removeAll { it.equals(normalized, ignoreCase = true) }
        current.add(0, normalized)
        val capped = current.take(MAX_HOST_HISTORY)
        projectCacheRepository.write(HOSTS_KEY, capped.joinToString("\n"))
    }

    override fun getCookies(): List<HttpCookie> {
        val raw = projectCacheRepository.read(COOKIES_KEY) ?: return emptyList()
        return runCatching {
            com.itangcent.easyapi.util.GsonUtils.fromJson<List<HttpCookie>>(raw)
        }.getOrNull().orEmpty()
    }

    override fun addCookies(cookies: List<HttpCookie>) {
        val existing = getCookies().toMutableList()
        cookies.forEach { newCookie ->
            existing.removeAll { it.name == newCookie.name && it.domain == newCookie.domain }
            existing.add(newCookie)
        }
        val json = com.itangcent.easyapi.util.GsonUtils.toJson(existing)
        projectCacheRepository.write(COOKIES_KEY, json)
    }

    override fun selectHost(message: String?): String {
        val hosts = getHosts()
        val selectedHost = Messages.showEditableChooseDialog(
            message ?: "Select Host",
            "Host",
            Messages.getInformationIcon(),
            hosts.toTypedArray(),
            hosts.first(),
            null
        ) ?: return DEFAULT_HOST
        addHost(selectedHost)
        return selectedHost
    }
}
