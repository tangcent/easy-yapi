package com.itangcent.easyapi.rule.context

import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.HttpMetadata
import com.itangcent.easyapi.exporter.model.HttpMethod
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ScriptApiEndpointTest {

    private lateinit var endpoint: ApiEndpoint
    private lateinit var scriptEndpoint: ScriptApiEndpoint

    @Before
    fun setUp() {
        val metadata = HttpMetadata(
            method = HttpMethod.GET,
            path = "/api/users"
        )
        endpoint = ApiEndpoint(name = "testEndpoint", metadata = metadata)
        scriptEndpoint = ScriptApiEndpoint(endpoint)
    }

    @Test
    fun testName() {
        assertEquals("testEndpoint", scriptEndpoint.name())
    }

    @Test
    fun testPath() {
        assertEquals("/api/users", scriptEndpoint.path())
    }

    @Test
    fun testMethod() {
        assertEquals("GET", scriptEndpoint.method())
    }

    @Test
    fun testSetPath() {
        scriptEndpoint.setPath("/api/v2/users")
        assertEquals("/api/v2/users", scriptEndpoint.path())
    }

    @Test
    fun testSetMethod() {
        scriptEndpoint.setMethod("POST")
        assertEquals("POST", scriptEndpoint.method())
    }

    @Test
    fun testSetMethodCaseInsensitive() {
        scriptEndpoint.setMethod("post")
        assertEquals("POST", scriptEndpoint.method())
    }

    @Test
    fun testDescription() {
        assertNull("Description should be null initially", scriptEndpoint.description())
        scriptEndpoint.setDescription("A test endpoint")
        assertEquals("A test endpoint", scriptEndpoint.description())
    }

    @Test
    fun testAppendDesc() {
        scriptEndpoint.setDescription("Base")
        scriptEndpoint.appendDesc(" extra")
        assertTrue("Should append description", scriptEndpoint.description()!!.contains("extra"))
    }

    @Test
    fun testToString() {
        assertNotNull("toString should not be null", scriptEndpoint.toString())
    }
}
