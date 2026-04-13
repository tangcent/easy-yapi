package com.itangcent.easyapi.http

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.core.event.EventBus
import com.itangcent.easyapi.core.event.EventKeys
import com.itangcent.easyapi.http.HttpClientProvider.Companion.getInstance
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.rule.engine.RuleEngine
import com.itangcent.easyapi.settings.HttpClientType
import com.itangcent.easyapi.settings.SettingBinder

/**
 * Creates [HttpClient] instances based on the user's configured HTTP client type.
 * Supports switching between Apache HttpClient and IntelliJ HttpRequests (OkHttp slot).
 *
 * This is the single entry point for obtaining an [HttpClient].
 * Every client returned is wrapped with [HttpClientScriptInterceptor].
 *
 * This class is action-scoped — prefer [getInstance] to reuse the instance
 */
@Service(Service.Level.PROJECT)
class HttpClientProvider(private val project: Project) {

    @Volatile
    private var cached: Pair<String, HttpClient>? = null

    init {
        EventBus.getInstance(project).register(EventKeys.ON_COMPLETED) {
            dispose()
        }
    }

    fun getClient(
        httpClient: String? = null,
        httpTimeOut: Int? = null,
        unsafeSsl: Boolean? = null
    ): HttpClient {
        val settings = SettingBinder.getInstance(project).read()
        val resolvedHttpClient = httpClient ?: settings.httpClient ?: HttpClientType.APACHE.value
        val resolvedHttpTimeOutSec = httpTimeOut ?: settings.httpTimeOut ?: 30
        val resolvedHttpTimeOutMs = resolvedHttpTimeOutSec * 1000
        val resolvedUnsafeSsl = unsafeSsl ?: settings.unsafeSsl ?: false

        val ruleEngine = RuleEngine.getInstance(project)
        val raw = getRawClient(resolvedHttpClient, resolvedHttpTimeOutMs, resolvedUnsafeSsl)
        return HttpClientScriptInterceptor(raw.logging(), ruleEngine)
    }

    fun dispose() {
        synchronized(this) {
            cached?.second?.close()
            cached = null
        }
    }

    private fun getRawClient(httpClient: String, httpTimeOut: Int, unsafeSsl: Boolean): HttpClient {
        val key = "$httpClient|$httpTimeOut|$unsafeSsl"
        cached?.let { (k, p) -> if (k == key) return p }
        synchronized(this) {
            cached?.let { (k, p) -> if (k == key) return p }
            cached?.second?.close()
            val client = createClient(httpClient, httpTimeOut, unsafeSsl)
            cached = key to client
            return client
        }
    }

    private fun createClient(httpClient: String, httpTimeOut: Int, unsafeSsl: Boolean): HttpClient {
        return when (httpClient) {
            HttpClientType.DEFAULT.value -> IntelliJHttpClient(httpTimeOut)
            else -> ApacheHttpClient(httpTimeOut, unsafeSsl)
        }
    }

    companion object {
        fun getInstance(project: Project): HttpClientProvider = project.service()
    }
}

fun HttpClient.logging() = LoggingHttpClient(this)

class LoggingHttpClient(private val delegate: HttpClient) : HttpClient by delegate {
    companion object : IdeaLog

    override suspend fun execute(request: HttpRequest): HttpResponse {
        val start = System.currentTimeMillis()
        LOG.info("--> ${request.method} ${request.buildUrl()}")
        try {
            val response = delegate.execute(request)
            val elapsed = System.currentTimeMillis() - start
            LOG.info(
                "<-- ${request.method} ${request.buildUrl()}: ${response.code} (${elapsed}ms):\n-------\n" +
                        "${response.body}\n" +
                        "-------"
            )
            return response
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - start
            LOG.info("<-- FAILED (${elapsed}ms) ${e.message}", e)
            throw e
        }
    }
}
