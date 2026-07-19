package com.itangcent.easyapi.core.export

import com.itangcent.easyapi.channel.spi.ChannelConfig
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

        // Use ChannelConfig.FileConfig (the SPI base class) instead of a concrete
        // channel-specific subclass — the core/export package must not import
        // channel.<id>.* per the DAG rule (Decision CO3).
        val modified = context.withChannel("postman", ChannelConfig.FileConfig(outputDir = "Test"))
        assertEquals("postman", modified.channelId)
        assertTrue(modified.channelConfig is ChannelConfig.FileConfig)
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
