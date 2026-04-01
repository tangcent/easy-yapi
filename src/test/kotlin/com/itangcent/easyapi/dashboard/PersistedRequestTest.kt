package com.itangcent.easyapi.dashboard

import org.junit.Assert.*
import org.junit.Test

class PersistedRequestTest {

    @Test
    fun testConstruction() {
        val request = PersistedRequest(
            endpointKey = "com.example.UserController.getUser",
            url = "http://localhost:8080/api/users/1",
            method = "GET",
            headers = mapOf("Authorization" to "Bearer token"),
            body = null
        )
        
        assertEquals("com.example.UserController.getUser", request.endpointKey)
        assertEquals("http://localhost:8080/api/users/1", request.url)
        assertEquals("GET", request.method)
        assertEquals(1, request.headers.size)
        assertNull(request.body)
    }

    @Test
    fun testConstructionWithDefaults() {
        val request = PersistedRequest(
            endpointKey = "test",
            url = "http://test.com",
            method = "POST"
        )
        
        assertEquals("test", request.endpointKey)
        assertEquals("http://test.com", request.url)
        assertEquals("POST", request.method)
        assertTrue(request.headers.isEmpty())
        assertNull(request.body)
    }

    @Test
    fun testConstructionWithBody() {
        val request = PersistedRequest(
            endpointKey = "test",
            url = "http://test.com",
            method = "POST",
            headers = mapOf("Content-Type" to "application/json"),
            body = "{\"name\":\"test\"}"
        )
        
        assertEquals("{\"name\":\"test\"}", request.body)
    }

    @Test
    fun testCopy() {
        val request = PersistedRequest(
            endpointKey = "test",
            url = "http://test.com",
            method = "GET"
        )
        
        val copy = request.copy(method = "POST")
        assertEquals("POST", copy.method)
        assertEquals("GET", request.method)
    }

    @Test
    fun testEquality() {
        val request1 = PersistedRequest(
            endpointKey = "test",
            url = "http://test.com",
            method = "GET"
        )
        val request2 = PersistedRequest(
            endpointKey = "test",
            url = "http://test.com",
            method = "GET"
        )
        
        assertEquals(request1, request2)
    }
}
