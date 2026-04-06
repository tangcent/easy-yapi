package com.itangcent.easyapi.exporter.postman

import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.HttpMetadata
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.httpMetadata
import org.junit.Assert.*
import org.junit.Test

class PostmanEndpointContextTest {

    @Test
    fun testConstruction() {
        val endpoint = ApiEndpoint(
            name = "Get User",
            metadata = HttpMetadata(path = "/api/users/{id}", method = HttpMethod.GET)
        )
        val response = PostmanResponseData(name = "Success", statusCode = 200)
        val context = PostmanEndpointContext(
            endpoint = endpoint,
            responses = listOf(response),
            preRequestScript = "console.log('test')",
            testScript = "pm.test('test', () => {})"
        )
        
        assertEquals("Get User", context.endpoint.name)
        assertEquals(1, context.responses.size)
        assertEquals("console.log('test')", context.preRequestScript)
        assertEquals("pm.test('test', () => {})", context.testScript)
    }

    @Test
    fun testConstructionWithDefaults() {
        val endpoint = ApiEndpoint(metadata = HttpMetadata(path = "/test", method = HttpMethod.GET))
        val context = PostmanEndpointContext(endpoint = endpoint)
        
        assertEquals("/test", context.endpoint.httpMetadata?.path)
        assertTrue(context.responses.isEmpty())
        assertNull(context.preRequestScript)
        assertNull(context.testScript)
        assertNull(context.psiElement)
        assertNull(context.psiClass)
    }

    @Test
    fun testCopy() {
        val endpoint = ApiEndpoint(metadata = HttpMetadata(path = "/test", method = HttpMethod.POST))
        val context = PostmanEndpointContext(endpoint = endpoint)
        
        val copy = context.copy(preRequestScript = "new script")
        assertEquals("new script", copy.preRequestScript)
    }

    @Test
    fun testEquality() {
        val endpoint = ApiEndpoint(metadata = HttpMetadata(path = "/test", method = HttpMethod.GET))
        val context1 = PostmanEndpointContext(endpoint = endpoint)
        val context2 = PostmanEndpointContext(endpoint = endpoint)
        
        assertEquals(context1, context2)
    }
}
