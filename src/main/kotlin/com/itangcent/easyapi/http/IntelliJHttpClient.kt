package com.itangcent.easyapi.http

import com.intellij.util.io.HttpRequests
import com.intellij.util.io.RequestBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URLEncoder

/**
 * HTTP client implementation using IntelliJ's built-in HTTP infrastructure.
 *
 * Uses IntelliJ's [HttpRequests] API which:
 * - Respects IDE proxy settings
 * - Uses IntelliJ's certificate trust store
 * - Integrates with IDE network features
 *
 * This is the "Default" client type in settings.
 *
 * ## Usage
 * ```kotlin
 * val client = IntelliJHttpClient(timeoutMs = 30000)
 * val response = client.execute(HttpRequest(
 *     method = "GET",
 *     url = "https://api.example.com/users"
 * ))
 * ```
 *
 * @param timeoutMs Request timeout in milliseconds
 * @see HttpClient for the interface
 * @see ApacheHttpClient for the alternative implementation
 */
class IntelliJHttpClient(
    private val timeoutMs: Int = 30_000
) : HttpClient {

    override suspend fun execute(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
        val url = request.buildUrl()
        val builder: RequestBuilder = if (request.method.uppercase() == "GET") {
            HttpRequests.request(url)
        } else {
            HttpRequests.post(url, null)
        }
        builder
            .connectTimeout(timeoutMs)
            .readTimeout(timeoutMs)
            .tuner { conn ->
                if (conn is HttpURLConnection) {
                    conn.requestMethod = request.method.uppercase()
                }
                request.headers.forEach { conn.setRequestProperty(it.name, it.value) }
            }

        builder.connect { req ->
            val conn = req.connection as HttpURLConnection
            if (request.method.uppercase() != "GET") {
                when {
                    request.isMultipart() && request.formParams.isNotEmpty() -> {
                        conn.doOutput = true
                        val multipart = MultipartBodyBuilder.build(request.formParams)
                        conn.setRequestProperty("Content-Type", multipart.contentType)
                        conn.outputStream.use { it.write(multipart.bytes) }
                    }
                    request.formParams.isNotEmpty() -> {
                        conn.doOutput = true
                        val formBody = request.textFormParams().entries.joinToString("&") {
                            "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
                        }
                        if (conn.getRequestProperty("Content-Type") == null) {
                            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                        }
                        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(formBody) }
                    }
                    request.body != null -> {
                        conn.doOutput = true
                        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(request.body) }
                    }
                }
            }
            val code = conn.responseCode
            val input = if (code in 200..299) conn.inputStream else conn.errorStream
            val responseBody = input?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            val headers = conn.headerFields.filterKeys { it != null }.mapValues { (_, v) -> v }
            HttpResponse(code = code, headers = headers, body = responseBody)
        }
    }

    override fun close() {}
}
