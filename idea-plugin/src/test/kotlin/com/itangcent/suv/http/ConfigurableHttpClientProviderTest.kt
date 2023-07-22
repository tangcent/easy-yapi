package com.itangcent.suv.http

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Test case of [ConfigurableHttpClientProvider]
 */
internal class ConfigurableHttpClientProviderTest : HttpClientProviderTest() {

    override val httpClientProviderClass get() = ConfigurableHttpClientProvider::class

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
            "com.itangcent.suv.http.ConfigurableHttpClientProvider.HttpClientWrapper",
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
        val emptyHttpResponse = httpRequest.call()

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


internal class NonConfigConfigurableHttpClientProviderTest : HttpClientProviderTest() {

    override val httpClientProviderClass get() = ConfigurableHttpClientProvider::class

    @Test
    fun `test buildHttpClient`() {
        // Build an instance of HttpClient using the provider.
        val httpClient = httpClientProvider.getHttpClient()

        // Assert that the HttpClient instance is not null.
        Assertions.assertNotNull(httpClient)
    }
}

internal class IllegalConfigConfigurableHttpClientProviderTest : HttpClientProviderTest() {

    override val httpClientProviderClass get() = ConfigurableHttpClientProvider::class

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


