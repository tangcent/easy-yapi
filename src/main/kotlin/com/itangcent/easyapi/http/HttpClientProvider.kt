package com.itangcent.easyapi.http

import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.core.context.ActionContext
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.rule.engine.RuleEngine
import com.itangcent.easyapi.settings.SettingBinder
import com.itangcent.easyapi.settings.HttpClientType

/**
 * Creates [HttpClient] instances based on the user's configured HTTP client type.
 * Supports switching between Apache HttpClient and IntelliJ HttpRequests (OkHttp slot).
 *
 * This is the single entry point for obtaining an [HttpClient].
 * Every client returned is wrapped with [HttpClientScriptInterceptor].
 *
 * This class is action-scoped — prefer [getInstance] to reuse the instance
 * already bound in the [ActionContext]'s [OperationScope].
 */
class HttpClientProvider(private val actionContext: ActionContext) {

    @Volatile
    private var cached: Pair<String, HttpClient>? = null

    /**
     * Returns an [HttpClient] wrapped with [HttpClientScriptInterceptor].
     *
     * Settings (`httpClient`, `httpTimeOut`, `unsafeSsl`) are resolved from the
     * [ActionContext]'s [EasyApiSettings] when available. Callers may override any
     * individual setting for specific use cases.
     *
     * A [RuleEngine] is built from [actionContext] when a [ConfigReader] is available
     * in its scope, enabling `http.call.before` / `http.call.after` rule evaluation
     * around each request. Otherwise the interceptor is a pass-through.
     */
    fun getClient(
        httpClient: String? = null,
        httpTimeOut: Int? = null,
        unsafeSsl: Boolean? = null
    ): HttpClient {
        val settings = actionContext.instanceOrNull(SettingBinder::class)?.read()
        val resolvedHttpClient = httpClient ?: settings?.httpClient ?: HttpClientType.APACHE.value
        val resolvedHttpTimeOutSec = httpTimeOut ?: settings?.httpTimeOut ?: 30
        val resolvedHttpTimeOutMs = resolvedHttpTimeOutSec * 1000
        val resolvedUnsafeSsl = unsafeSsl ?: settings?.unsafeSsl ?: false

        val ruleEngine = if (actionContext.instanceOrNull(ConfigReader::class) != null) {
            RuleEngine.getInstance(actionContext)
        } else {
            null
        }
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
        /**
         * Returns the [HttpClientProvider] from the [ActionContext]'s scope.
         * The scope will auto-create and cache the instance if not explicitly bound.
         */
        fun getInstance(actionContext: ActionContext): HttpClientProvider =
            actionContext.instance(HttpClientProvider::class)
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
