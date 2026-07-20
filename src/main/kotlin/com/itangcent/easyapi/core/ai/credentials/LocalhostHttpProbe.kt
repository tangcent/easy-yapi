package com.itangcent.easyapi.core.ai.credentials

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.HttpURLConnection
import java.net.URL
import kotlin.time.Duration.Companion.milliseconds

/**
 * HEAD-probe abstraction for the credential scanner.
 *
 * Used to detect running local servers (Ollama on 11434, LiteLLM on 4000)
 * without recovering any key — those providers need no API key.
 *
 * Note: the SAM has no default for [timeoutMs] because Kotlin forbids
 * defaults on functional-interface abstract methods. Callers pass the
 * timeout explicitly; the default impl uses 500ms.
 */
fun interface LocalhostHttpProbe {

    /**
     * Returns `true` if a HEAD request to [url] completes with any 2xx/3xx
     * status within [timeoutMs]; `false` otherwise (unreachable, refused,
     * timeout, non-2xx). Never throws.
     */
    suspend fun head(url: String, timeoutMs: Long): Boolean
}

/**
 * Default implementation using `HttpURLConnection`.
 *
 * Only `localhost` / `127.0.0.1` URLs are accepted (the scanner never probes
 * remote hosts). Anything else returns `false` without a network call.
 */
object DefaultLocalhostHttpProbe : LocalhostHttpProbe {

    override suspend fun head(url: String, timeoutMs: Long): Boolean {
        // Guard: never probe remote hosts from the scanner.
        if (!isLocalhost(url)) return false
        return withContext(Dispatchers.IO) {
            withTimeout(timeoutMs.milliseconds) {
                runCatching {
                    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                        requestMethod = "HEAD"
                        connectTimeout = timeoutMs.toInt().coerceAtLeast(1)
                        readTimeout = timeoutMs.toInt().coerceAtLeast(1)
                        instanceFollowRedirects = true
                    }
                    try {
                        val code = conn.responseCode
                        code in 200..399
                    } finally {
                        conn.disconnect()
                    }
                }.getOrDefault(false)
            }
        }
    }

    private fun isLocalhost(url: String): Boolean {
        val host = runCatching { URL(url).host }.getOrNull() ?: return false
        return host == "localhost" || host == "127.0.0.1" || host == "::1"
    }
}
