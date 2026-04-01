package com.itangcent.easyapi.http

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

/**
 * Simple HTTP client implementation using Java's URLConnection.
 *
 * A lightweight alternative that uses only JDK classes.
 * Suitable for simple use cases where Apache HttpClient is not needed.
 *
 * Features:
 * - No external dependencies
 * - Fixed timeouts (15s connect, 30s read)
 * - Support for multipart file uploads
 *
 * ## Note
 * For production use, prefer [ApacheHttpClient] or [IntelliJHttpClient]
 * which offer better performance and features.
 *
 * @see HttpClient for the interface
 * @see ApacheHttpClient for the recommended implementation
 */
object UrlConnectionHttpClient : HttpClient {
    override suspend fun execute(request: HttpRequest): HttpResponse {
        return withContext(Dispatchers.IO) {
            val url = request.buildUrl()
            val conn = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = request.method
                connectTimeout = 15_000
                readTimeout = 30_000
                for (kv in request.headers) {
                    setRequestProperty(kv.name, kv.value)
                }
                if (request.formParams.isNotEmpty() || request.body != null) {
                    doOutput = true
                }
            }
            when {
                request.isMultipart() && request.formParams.isNotEmpty() -> {
                    val multipart = MultipartBodyBuilder.build(request.formParams)
                    conn.setRequestProperty("Content-Type", multipart.contentType)
                    conn.outputStream.use { it.write(multipart.bytes) }
                }

                request.formParams.isNotEmpty() -> {
                    val formBody = request.textFormParams().entries.joinToString("&") {
                        "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
                    }
                    if (conn.getRequestProperty("Content-Type") == null) {
                        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    }
                    conn.outputStream.use { it.write(formBody.toByteArray(Charsets.UTF_8)) }
                }

                request.body != null -> {
                    conn.outputStream.use { it.write(request.body.toByteArray(Charsets.UTF_8)) }
                }
            }
            val code = conn.responseCode
            val input = if (code in 200..299) conn.inputStream else conn.errorStream
            val responseBody = input?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            HttpResponse(code = code, headers = conn.headerFields.filterKeys { !it.isNullOrEmpty() }, body = responseBody)
        }
    }

    override fun close() {}
}
