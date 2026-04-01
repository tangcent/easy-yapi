package com.itangcent.easyapi.http

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.http.client.config.CookieSpecs
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.*
import org.apache.http.config.SocketConfig
import org.apache.http.conn.ssl.NoopHostnameVerifier
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.entity.ByteArrayEntity
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * HTTP client implementation using Apache HttpClient.
 *
 * Features:
 * - Connection pooling (50 max total, 20 per route)
 * - Configurable timeout
 * - Optional unsafe SSL (bypass certificate validation)
 * - Support for multipart file uploads
 * - Cookie support
 *
 * ## Usage
 * ```kotlin
 * val client = ApacheHttpClient(timeoutMs = 30000, unsafeSsl = false)
 * val response = client.execute(HttpRequest(
 *     method = "GET",
 *     url = "https://api.example.com/users"
 * ))
 * client.close()
 * ```
 *
 * @param timeoutMs Request timeout in milliseconds
 * @param unsafeSsl Whether to bypass SSL certificate validation
 * @see HttpClient for the interface
 * @see HttpClientProvider for client selection
 */
class ApacheHttpClient(
    timeoutMs: Int = 30_000,
    unsafeSsl: Boolean = false
) : HttpClient {

    private val client: CloseableHttpClient = buildClient(timeoutMs, unsafeSsl)

    override suspend fun execute(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
        val url = request.buildUrl()
        val base = try {
            when (request.method.uppercase()) {
                "GET" -> HttpGet(url)
                "POST" -> HttpPost(url)
                "PUT" -> HttpPut(url)
                "DELETE" -> HttpDelete(url)
                "PATCH" -> HttpPatch(url)
                "HEAD" -> HttpHead(url)
                "OPTIONS" -> HttpOptions(url)
                else -> HttpPost(url)
            }
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid URL: $url — ${e.message}", e)
        }
        request.headers.forEach { base.setHeader(it.name, it.value) }
        if (base is HttpEntityEnclosingRequestBase) {
            when {
                request.isMultipart() && request.formParams.isNotEmpty() -> {
                    val multipart = MultipartBodyBuilder.build(request.formParams)
                    base.entity = ByteArrayEntity(multipart.bytes, ContentType.parse(multipart.contentType))
                    base.setHeader("Content-Type", multipart.contentType)
                }
                request.formParams.isNotEmpty() -> {
                    val pairs = request.textFormParams().map { (k, v) -> BasicNameValuePair(k, v) }
                    base.entity = UrlEncodedFormEntity(pairs, Charsets.UTF_8)
                }
                request.body != null -> {
                    base.entity = StringEntity(request.body, Charsets.UTF_8)
                }
            }
        }
        client.execute(base).use { resp ->
            val code = resp.statusLine.statusCode
            val headers = resp.allHeaders
                .groupBy({ it.name }, { it.value })
            val body = resp.entity?.let { EntityUtils.toString(it, Charsets.UTF_8) }
            HttpResponse(code = code, headers = headers, body = body)
        }
    }

    override fun close() {
        runCatching { client.close() }
    }

    companion object {
        private fun buildClient(timeoutMs: Int, unsafeSsl: Boolean): CloseableHttpClient {
            val builder = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManager().apply {
                    maxTotal = 50
                    defaultMaxPerRoute = 20
                })
                .setDefaultSocketConfig(
                    SocketConfig.custom().setSoTimeout(timeoutMs).build()
                )
                .setDefaultRequestConfig(
                    RequestConfig.custom()
                        .setConnectTimeout(timeoutMs)
                        .setConnectionRequestTimeout(timeoutMs)
                        .setSocketTimeout(timeoutMs)
                        .setCookieSpec(CookieSpecs.STANDARD)
                        .build()
                )
            if (unsafeSsl) {
                val trustAll = object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                }
                val sslContext = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustAll), null) }
                builder.setSSLSocketFactory(SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE))
            }
            return builder.build()
        }
    }
}
