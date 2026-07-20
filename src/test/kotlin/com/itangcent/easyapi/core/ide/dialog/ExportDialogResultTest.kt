package com.itangcent.easyapi.core.ide.dialog

import com.itangcent.easyapi.channel.spi.ChannelConfig
import com.itangcent.easyapi.core.export.*
import org.junit.Assert.*
import org.junit.Test

class ExportDialogResultTest {

    @Test
    fun testProperties() {
        val result = ExportDialogResult(channelId = "markdown")
        assertEquals("markdown", result.channelId)
        assertEquals(ChannelConfig.Empty, result.channelConfig)
        assertTrue(result.selectedEndpoints.isEmpty())
    }

    @Test
    fun testEquality() {
        val r1 = ExportDialogResult(channelId = "markdown")
        val r2 = ExportDialogResult(channelId = "markdown")
        assertEquals(r1, r2)
    }

    @Test
    fun testInequality_differentChannelId() {
        val r1 = ExportDialogResult(channelId = "markdown")
        val r2 = ExportDialogResult(channelId = "postman")
        assertNotEquals(r1, r2)
    }

    @Test
    fun testCopy() {
        val original = ExportDialogResult(channelId = "markdown")
        val copy = original.copy(channelId = "curl")
        assertEquals("curl", copy.channelId)
    }

    @Test
    fun testWithSelectedEndpoints() {
        val endpoint = ApiEndpoint(
            name = "Get User",
            metadata = httpMetadata(path = "/api/users", method = HttpMethod.GET)
        )
        val selection = EndpointSelection(endpoint)
        val result = ExportDialogResult(
            channelId = "markdown",
            selectedEndpoints = listOf(selection)
        )

        assertEquals(1, result.selectedEndpoints.size)
        assertSame(endpoint, result.selectedEndpoints[0].endpoint)
    }

    @Test
    fun testEndpointSelectionProperties() {
        val endpoint = ApiEndpoint(
            name = "Create User",
            metadata = httpMetadata(path = "/api/users", method = HttpMethod.POST)
        )
        val selection = EndpointSelection(endpoint)
        assertSame(endpoint, selection.endpoint)
    }

    @Test
    fun testEndpointSelectionEquality() {
        val endpoint = ApiEndpoint(
            name = "Get User",
            metadata = httpMetadata(path = "/api/users", method = HttpMethod.GET)
        )
        val s1 = EndpointSelection(endpoint)
        val s2 = EndpointSelection(endpoint)
        assertEquals(s1, s2)
    }
}
