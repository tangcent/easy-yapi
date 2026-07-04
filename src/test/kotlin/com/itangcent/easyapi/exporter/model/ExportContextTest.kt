package com.itangcent.easyapi.exporter.model

import com.itangcent.easyapi.exporter.channel.ChannelConfig
import com.itangcent.easyapi.exporter.channel.postman.PostmanConfig
import org.junit.Assert.*
import org.junit.Test

class ExportContextTest {

    @Test
    fun testHasSelection() {
        val endpoint1 = ApiEndpoint(
            name = "getUser",
            metadata = httpMetadata(path = "/api/users/{id}", method = HttpMethod.GET)
        )
        val endpoint2 = ApiEndpoint(
            name = "createUser",
            metadata = httpMetadata(path = "/api/users", method = HttpMethod.POST)
        )

        val contextWithoutSelection = ExportContext(
            project = mockProject(),
            endpoints = listOf(endpoint1, endpoint2)
        )
        assertFalse(contextWithoutSelection.hasSelection)

        val contextWithSelection = ExportContext(
            project = mockProject(),
            endpoints = listOf(endpoint1, endpoint2),
            selectedEndpoints = listOf(endpoint1)
        )
        assertTrue(contextWithSelection.hasSelection)
    }

    @Test
    fun testEndpointsToExport() {
        val endpoint1 = ApiEndpoint(
            name = "getUser",
            metadata = httpMetadata(path = "/api/users/{id}", method = HttpMethod.GET)
        )
        val endpoint2 = ApiEndpoint(
            name = "createUser",
            metadata = httpMetadata(path = "/api/users", method = HttpMethod.POST)
        )

        val contextWithoutSelection = ExportContext(
            project = mockProject(),
            endpoints = listOf(endpoint1, endpoint2)
        )
        assertEquals(2, contextWithoutSelection.endpointsToExport.size)

        val contextWithSelection = ExportContext(
            project = mockProject(),
            endpoints = listOf(endpoint1, endpoint2),
            selectedEndpoints = listOf(endpoint1)
        )
        assertEquals(1, contextWithSelection.endpointsToExport.size)
        assertEquals("getUser", contextWithSelection.endpointsToExport[0].name)
    }

    @Test
    fun testWithSelectedEndpoints() {
        val endpoint1 = ApiEndpoint(
            name = "getUser",
            metadata = httpMetadata(path = "/api/users/{id}", method = HttpMethod.GET)
        )
        val endpoint2 = ApiEndpoint(
            name = "createUser",
            metadata = httpMetadata(path = "/api/users", method = HttpMethod.POST)
        )

        val original = ExportContext(
            project = mockProject(),
            endpoints = listOf(endpoint1, endpoint2)
        )

        val modified = original.withSelectedEndpoints(listOf(endpoint2))

        assertFalse(original.hasSelection)
        assertTrue(modified.hasSelection)
        assertEquals(1, modified.selectedEndpoints.size)
    }

    @Test
    fun testWithChannel() {
        val context = ExportContext(
            project = mockProject(),
            endpoints = emptyList()
        )

        assertEquals("markdown", context.channelId)
        assertEquals(ChannelConfig.Empty, context.channelConfig)

        val modified = context.withChannel("postman", PostmanConfig(collectionName = "Test"))
        assertEquals("postman", modified.channelId)
        assertTrue(modified.channelConfig is PostmanConfig)
    }

    @Test
    fun testChannelConfigDefault() {
        val context = ExportContext(
            project = mockProject(),
            endpoints = emptyList()
        )
        assertEquals(ChannelConfig.Empty, context.channelConfig)
    }

    private fun mockProject(): com.intellij.openapi.project.Project {
        return org.mockito.Mockito.mock(com.intellij.openapi.project.Project::class.java)
    }
}
