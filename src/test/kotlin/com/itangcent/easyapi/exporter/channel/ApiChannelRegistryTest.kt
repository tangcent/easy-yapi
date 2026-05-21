package com.itangcent.easyapi.exporter.channel

import com.itangcent.easyapi.exporter.model.*
import org.junit.Assert.*
import org.junit.Test

class ApiChannelRegistryTest {

    private class StubChannel(
        override val id: String,
        override val displayName: String,
        override val supportsHttp: Boolean = true,
        override val supportsGrpc: Boolean = false,
        override val exposeAsAction: Boolean = false,
        override val actionText: String? = null
    ) : ApiChannel {

        override fun createOptionsPanel(project: com.intellij.openapi.project.Project): ChannelOptionsPanel? = null

        override suspend fun export(context: ExportContext): ExportResult =
            ExportResult.Success(0, "")
    }

    private fun httpEndpoint() = ApiEndpoint(
        name = "test",
        metadata = httpMetadata(path = "/api/test", method = HttpMethod.GET)
    )

    private fun grpcEndpoint() = ApiEndpoint(
        name = "test",
        metadata = GrpcMetadata(
            path = "/test.Service/Method",
            serviceName = "Service",
            methodName = "Method",
            packageName = "test",
            streamingType = GrpcStreamingType.UNARY
        )
    )

    @Test
    fun testIsAvailableFor_withEmptyEndpoints() {
        val httpChannel = StubChannel("http", "HTTP Channel", supportsHttp = true, supportsGrpc = false)
        assertTrue(httpChannel.isAvailableFor(emptyList()))
    }

    @Test
    fun testIsAvailableFor_filtersByProtocol() {
        val httpChannel = StubChannel("http", "HTTP Channel", supportsHttp = true, supportsGrpc = false)
        val grpcChannel = StubChannel("grpc", "gRPC Channel", supportsHttp = false, supportsGrpc = true)
        val dualChannel = StubChannel("dual", "Dual Channel", supportsHttp = true, supportsGrpc = true)

        val httpEndpoints = listOf(httpEndpoint())
        val grpcEndpoints = listOf(grpcEndpoint())
        val mixedEndpoints = listOf(httpEndpoint(), grpcEndpoint())

        assertTrue(httpChannel.isAvailableFor(httpEndpoints))
        assertFalse(httpChannel.isAvailableFor(grpcEndpoints))
        assertTrue(httpChannel.isAvailableFor(mixedEndpoints))

        assertFalse(grpcChannel.isAvailableFor(httpEndpoints))
        assertTrue(grpcChannel.isAvailableFor(grpcEndpoints))
        assertTrue(grpcChannel.isAvailableFor(mixedEndpoints))

        assertTrue(dualChannel.isAvailableFor(httpEndpoints))
        assertTrue(dualChannel.isAvailableFor(grpcEndpoints))
        assertTrue(dualChannel.isAvailableFor(mixedEndpoints))
    }

    @Test
    fun testChannelFilteringByAvailability() {
        val channels = listOf(
            StubChannel("http", "HTTP Channel", supportsHttp = true, supportsGrpc = false),
            StubChannel("grpc", "gRPC Channel", supportsHttp = false, supportsGrpc = true),
            StubChannel("dual", "Dual Channel", supportsHttp = true, supportsGrpc = true)
        )

        val httpEndpoints = listOf(httpEndpoint())
        val available = channels.filter { it.isAvailableFor(httpEndpoints) }
        assertEquals(2, available.size)
        assertTrue(available.any { it.id == "http" })
        assertTrue(available.any { it.id == "dual" })
    }

    @Test
    fun testChannelFilteringByAction() {
        val channels = listOf(
            StubChannel("regular", "Regular", exposeAsAction = false),
            StubChannel("action1", "Action 1", exposeAsAction = true, actionText = "Quick Export"),
            StubChannel("action2", "Action 2", exposeAsAction = true, actionText = null)
        )

        val actionChannels = channels.filter { it.exposeAsAction }
        assertEquals(2, actionChannels.size)
        assertTrue(actionChannels.any { it.id == "action1" })
        assertTrue(actionChannels.any { it.id == "action2" })
    }

    @Test
    fun testChannelLookupById() {
        val channels = listOf(
            StubChannel("markdown", "Markdown"),
            StubChannel("postman", "Postman"),
            StubChannel("curl", "cURL")
        )

        assertEquals("postman", channels.firstOrNull { it.id == "postman" }?.id)
        assertNull(channels.firstOrNull { it.id == "nonexistent" })
    }

    @Test
    fun testChannelLookupWithDuplicateIds() {
        val channels = listOf(
            StubChannel("dup", "First"),
            StubChannel("dup", "Second"),
            StubChannel("unique", "Unique")
        )

        val found = channels.firstOrNull { it.id == "dup" }
        assertNotNull(found)
        assertEquals("First", found?.displayName)
    }
}
