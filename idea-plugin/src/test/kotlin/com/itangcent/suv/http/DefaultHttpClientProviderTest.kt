package com.itangcent.suv.http

import com.itangcent.common.utils.DateUtils
import com.itangcent.http.ApacheCookie
import com.itangcent.http.ApacheHttpClient
import com.itangcent.http.asApacheCookie
import com.itangcent.idea.plugin.settings.HttpClientType
import com.itangcent.intellij.context.ActionContextBuilder
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Test case of [HttpClientProvider]
 */
internal abstract class DefaultHttpClientProviderTest : HttpClientProviderTest() {

    override fun customConfig(): String {
        return "http.call.before=groovy:logger.info(\"call:\"+request.url())\nhttp.call.after=groovy:logger.info(\"response:\"+response.string())\nhttp.timeOut=3"
    }

    @Test
    fun `test buildHttpClient`() {
        // Build an instance of HttpClient using the provider.
        val httpClient = httpClientProvider.getHttpClient()

        // Assert that the HttpClient instance is not null.
        Assertions.assertNotNull(httpClient)

        // Assert that the HttpClient instance is an instance of HttpClientWrapper.
        Assertions.assertEquals(
            "com.itangcent.suv.http.HttpClientScriptInterceptor.HttpClientWrapper",
            httpClient::class.qualifiedName
        )
    }

    @Test
    fun `test callForbiddenRequest`() {

        // Build an instance of HttpClient using the provider.
        val httpClient = httpClientProvider.getHttpClient()

        // Create an instance of HttpRequest using the HttpClient instance.
        val httpRequest = httpClient.request()
            .method("GET")
            .url("http://forbidden.com")

        // Send the request and receive the response.
        httpRequest.call().use { emptyHttpResponse ->
            // Assert that the response code is 404.
            assertEquals(404, emptyHttpResponse.code())

            // Assert that the response headers are null.
            assertNull(emptyHttpResponse.headers())

            // Assert that the response headers for a specific name are null.
            assertNull(emptyHttpResponse.headers("Content-Type"))

            // Assert that the response string is null.
            assertNull(emptyHttpResponse.string())

            // Assert that the response string with a specific charset is null.
            assertNull(emptyHttpResponse.string(Charsets.UTF_8))

            // Assert that the response stream is empty.
            assertTrue(emptyHttpResponse.stream().readBytes().isEmpty())

            // Assert that the response content type is null.
            assertNull(emptyHttpResponse.contentType())

            // Assert that the response bytes are null.
            assertNull(emptyHttpResponse.bytes())

            // Assert that the response does not contain a specific header.
            Assertions.assertFalse(emptyHttpResponse.containsHeader("Content-Type"))

            // Assert that the first header for a specific name is null.
            assertNull(emptyHttpResponse.firstHeader("Content-Type"))

            // Assert that the last header for a specific name is null.
            assertNull(emptyHttpResponse.lastHeader("Content-Type"))

            // Assert that the response request is the same as the sample request.
            assertEquals(httpRequest, emptyHttpResponse.request())

        }
    }
}

internal class ApacheHttpClientProviderTest : DefaultHttpClientProviderTest() {
    override fun setUp() {
        settings.httpClient = HttpClientType.APACHE.value
    }

    @Test
    fun `the httpClient should be ApacheHttpClient`() {
        val httpClient = httpClientProvider.getHttpClient()
        assertTrue(httpClient is HttpClientScriptInterceptor.HttpClientWrapper)
        assertTrue(httpClient.delegate is ApacheHttpClient)
    }

    @Test
    fun testApacheCookies() {
        val httpClient = httpClientProvider.getHttpClient()
        val cookieStore = httpClient.cookieStore()
        cookieStore.clear()

        val token = cookieStore.newCookie()
        token.setName("token")
        token.setValue("111111")
        token.setExpiryDate(DateUtils.parse("2021-01-01").time)
        token.setDomain("github.com")
        token.setPorts(intArrayOf(9999))
        token.setComment("for auth")
        token.setCommentURL("http://www.apache.org/licenses/LICENSE-2.0")
        token.setSecure(false)
        token.setPath("/")
        token.setVersion(100)
        token.setExpiryDate(DateUtils.parse("2099-01-01").time)

        var apacheCookie = token.asApacheCookie()

        val packageApacheCookie = ApacheCookie(apacheCookie)
        assertEquals("token", packageApacheCookie.getName())
        assertEquals("111111", packageApacheCookie.getValue())
        assertEquals("github.com", packageApacheCookie.getDomain())
        assertEquals("for auth", packageApacheCookie.getComment())
        assertEquals("http://www.apache.org/licenses/LICENSE-2.0", packageApacheCookie.getCommentURL())
        assertEquals("/", packageApacheCookie.getPath())
        assertEquals(100, packageApacheCookie.getVersion())
        assertContentEquals(intArrayOf(9999), packageApacheCookie.getPorts())
        assertEquals(DateUtils.parse("2099-01-01").time, packageApacheCookie.getExpiryDate())
        assertTrue(packageApacheCookie.isPersistent())

        token.setPorts(null)
        apacheCookie = token.asApacheCookie()
        assertNull(apacheCookie.commentURL)
        assertTrue(apacheCookie.isPersistent)
    }
}

internal class UnsafeSslApacheHttpClientProviderTest : DefaultHttpClientProviderTest() {
    override fun setUp() {
        settings.httpClient = HttpClientType.APACHE.value
        settings.unsafeSsl = true
    }

    @Test
    fun `the httpClient should be ApacheHttpClient`() {
        val httpClient = httpClientProvider.getHttpClient()
        assertTrue(httpClient is HttpClientScriptInterceptor.HttpClientWrapper)
        assertTrue(httpClient.delegate is ApacheHttpClient)
    }
}

internal class OkHttpClientProviderTest : DefaultHttpClientProviderTest() {
    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        settings.httpClient = HttpClientType.OKHTTP.value
    }

    @Test
    fun `the httpClient should be OkHttpClient`() {
        val httpClient = httpClientProvider.getHttpClient()
        assertTrue(httpClient is HttpClientScriptInterceptor.HttpClientWrapper)
        assertTrue(httpClient.delegate is OkHttpClient)
    }
}

internal class UnsafeSslOkHttpClientProviderTest : DefaultHttpClientProviderTest() {
    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        settings.httpClient = HttpClientType.OKHTTP.value
        settings.unsafeSsl = true
    }

    @Test
    fun `the httpClient should be OkHttpClient`() {
        val httpClient = httpClientProvider.getHttpClient()
        assertTrue(httpClient is HttpClientScriptInterceptor.HttpClientWrapper)
        assertTrue(httpClient.delegate is OkHttpClient)
    }
}

internal class IllegalHttpClientProviderTest : DefaultHttpClientProviderTest() {
    override fun setUp() {
        settings.httpClient = "fake"
    }

    @Test
    fun `assert the httpClient should default to Apache`() {
        val httpClient = httpClientProvider.getHttpClient()
        assertTrue(httpClient is HttpClientScriptInterceptor.HttpClientWrapper)
        assertTrue(httpClient.delegate is ApacheHttpClient)
    }
}

internal class NonConfigConfigurableHttpClientProviderTest : HttpClientProviderTest() {

    @Test
    fun `test buildHttpClient`() {
        // Build an instance of HttpClient using the provider.
        val httpClient = httpClientProvider.getHttpClient()

        // Assert that the HttpClient instance is not null.
        Assertions.assertNotNull(httpClient)
    }
}

internal class IllegalConfigConfigurableHttpClientProviderTest : HttpClientProviderTest() {
    override fun customConfig(): String {
        return "http.timeOut=illegal"
    }

    @Test
    fun `test buildHttpClient`() {
        // Build an instance of HttpClient using the provider.
        val httpClient = httpClientProvider.getHttpClient()

        // Assert that the HttpClient instance is not null.
        Assertions.assertNotNull(httpClient)
    }
}


