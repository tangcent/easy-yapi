package com.itangcent.easyapi.exporter.channel

import com.itangcent.easyapi.exporter.channel.curl.CurlChannel
import com.itangcent.easyapi.exporter.channel.markdown.MarkdownChannel
import com.itangcent.easyapi.exporter.channel.postman.PostmanChannel
import com.itangcent.easyapi.exporter.model.*
import org.junit.Assert.*
import org.junit.Test

class ChannelTest {

    private class TestChannel(
        override val id: String = "test",
        override val displayName: String = "Test Channel",
        override val supportsHttp: Boolean = true,
        override val supportsGrpc: Boolean = false,
        override val exposeAsAction: Boolean = false,
        override val actionText: String? = null
    ) : Channel {

        override fun createOptionsPanel(project: com.intellij.openapi.project.Project): ChannelOptionsPanel? = null

        override suspend fun export(context: ExportContext): ExportResult =
            ExportResult.Success(context.endpointsToExport.size, "test-output")
    }

    private class HttpOnlyChannel : Channel {
        override val id = "http-only"
        override val displayName = "HTTP Only"
        override val supportsHttp = true
        override val supportsGrpc = false
        override fun createOptionsPanel(project: com.intellij.openapi.project.Project) = null
        override suspend fun export(context: ExportContext) = ExportResult.Success(0, "")
    }

    private class GrpcOnlyChannel : Channel {
        override val id = "grpc-only"
        override val displayName = "gRPC Only"
        override val supportsHttp = false
        override val supportsGrpc = true
        override fun createOptionsPanel(project: com.intellij.openapi.project.Project) = null
        override suspend fun export(context: ExportContext) = ExportResult.Success(0, "")
    }

    private class DualProtocolChannel : Channel {
        override val id = "dual"
        override val displayName = "Dual Protocol"
        override val supportsHttp = true
        override val supportsGrpc = true
        override fun createOptionsPanel(project: com.intellij.openapi.project.Project) = null
        override suspend fun export(context: ExportContext) = ExportResult.Success(0, "")
    }

    private class NoProtocolChannel : Channel {
        override val id = "none"
        override val displayName = "No Protocol"
        override val supportsHttp = false
        override val supportsGrpc = false
        override fun createOptionsPanel(project: com.intellij.openapi.project.Project) = null
        override suspend fun export(context: ExportContext) = ExportResult.Success(0, "")
    }

    private class ActionChannel(
        override val exposeAsAction: Boolean = true,
        override val actionText: String? = "Quick Export"
    ) : Channel {
        override val id = "action-channel"
        override val displayName = "Action Channel"
        override fun createOptionsPanel(project: com.intellij.openapi.project.Project) = null
        override suspend fun export(context: ExportContext) = ExportResult.Success(0, "")
    }

    private class HandleResultChannel : Channel {
        override val id = "handle-result"
        override val displayName = "Handle Result Channel"
        override fun createOptionsPanel(project: com.intellij.openapi.project.Project) = null
        override suspend fun export(context: ExportContext) = ExportResult.Success(1, "test")
        override suspend fun handleResult(
            project: com.intellij.openapi.project.Project,
            result: ExportResult.Success,
            config: ChannelConfig
        ): Boolean = true
    }

    private fun httpEndpoint(name: String = "test") = ApiEndpoint(
        name = name,
        metadata = httpMetadata(path = "/api/test", method = HttpMethod.GET)
    )

    private fun grpcEndpoint(name: String = "test") = ApiEndpoint(
        name = name,
        metadata = GrpcMetadata(
            path = "/test.Service/Method",
            serviceName = "Service",
            methodName = "Method",
            packageName = "test",
            streamingType = GrpcStreamingType.UNARY
        )
    )

    @Test
    fun testDefaultProperties() {
        val channel = TestChannel()
        assertEquals("test", channel.id)
        assertEquals("Test Channel", channel.displayName)
        assertTrue(channel.supportsHttp)
        assertFalse(channel.supportsGrpc)
        assertFalse(channel.exposeAsAction)
        assertNull(channel.actionText)
    }

    @Test
    fun testCustomProperties() {
        val channel = TestChannel(
            id = "custom",
            displayName = "Custom Channel",
            supportsHttp = false,
            supportsGrpc = true,
            exposeAsAction = true,
            actionText = "Quick"
        )
        assertEquals("custom", channel.id)
        assertEquals("Custom Channel", channel.displayName)
        assertFalse(channel.supportsHttp)
        assertTrue(channel.supportsGrpc)
        assertTrue(channel.exposeAsAction)
        assertEquals("Quick", channel.actionText)
    }

    @Test
    fun testIsAvailableFor_emptyEndpoints_returnsTrue() {
        val channel = TestChannel()
        assertTrue(channel.isAvailableFor(emptyList()))
    }

    @Test
    fun testIsAvailableFor_httpOnlyChannel_withHttpEndpoints() {
        val channel = HttpOnlyChannel()
        assertTrue(channel.isAvailableFor(listOf(httpEndpoint())))
    }

    @Test
    fun testIsAvailableFor_httpOnlyChannel_withGrpcEndpoints() {
        val channel = HttpOnlyChannel()
        assertFalse(channel.isAvailableFor(listOf(grpcEndpoint())))
    }

    @Test
    fun testIsAvailableFor_grpcOnlyChannel_withGrpcEndpoints() {
        val channel = GrpcOnlyChannel()
        assertTrue(channel.isAvailableFor(listOf(grpcEndpoint())))
    }

    @Test
    fun testIsAvailableFor_grpcOnlyChannel_withHttpEndpoints() {
        val channel = GrpcOnlyChannel()
        assertFalse(channel.isAvailableFor(listOf(httpEndpoint())))
    }

    @Test
    fun testIsAvailableFor_dualChannel_withHttpEndpoints() {
        val channel = DualProtocolChannel()
        assertTrue(channel.isAvailableFor(listOf(httpEndpoint())))
    }

    @Test
    fun testIsAvailableFor_dualChannel_withGrpcEndpoints() {
        val channel = DualProtocolChannel()
        assertTrue(channel.isAvailableFor(listOf(grpcEndpoint())))
    }

    @Test
    fun testIsAvailableFor_dualChannel_withMixedEndpoints() {
        val channel = DualProtocolChannel()
        assertTrue(channel.isAvailableFor(listOf(httpEndpoint(), grpcEndpoint())))
    }

    @Test
    fun testIsAvailableFor_noProtocolChannel_alwaysFalse() {
        val noProtocol = NoProtocolChannel()
        assertFalse(noProtocol.isAvailableFor(listOf(httpEndpoint())))
        assertFalse(noProtocol.isAvailableFor(listOf(grpcEndpoint())))
        assertFalse(noProtocol.isAvailableFor(listOf(httpEndpoint(), grpcEndpoint())))
    }

    @Test
    fun testIsAvailableFor_mixedEndpoints_httpOnlyChannel() {
        val channel = HttpOnlyChannel()
        assertTrue(channel.isAvailableFor(listOf(httpEndpoint(), grpcEndpoint())))
    }

    @Test
    fun testIsAvailableFor_mixedEndpoints_grpcOnlyChannel() {
        val channel = GrpcOnlyChannel()
        assertTrue(channel.isAvailableFor(listOf(httpEndpoint(), grpcEndpoint())))
    }

    @Test
    fun testDefaultHandleResult_returnsFalse() {
        val channel = TestChannel()
        val project = org.mockito.Mockito.mock(com.intellij.openapi.project.Project::class.java)
        val result = ExportResult.Success(1, "test")
        kotlinx.coroutines.runBlocking {
            assertFalse(channel.handleResult(project, result, ChannelConfig.Empty))
        }
    }

    @Test
    fun testOverriddenHandleResult_returnsTrue() {
        val channel = HandleResultChannel()
        val project = org.mockito.Mockito.mock(com.intellij.openapi.project.Project::class.java)
        val result = ExportResult.Success(1, "test")
        kotlinx.coroutines.runBlocking {
            assertTrue(channel.handleResult(project, result, ChannelConfig.Empty))
        }
    }

    @Test
    fun testActionChannelProperties() {
        val channel = ActionChannel()
        assertTrue(channel.exposeAsAction)
        assertEquals("Quick Export", channel.actionText)
    }

    @Test
    fun testActionChannelWithNullActionText() {
        val channel = ActionChannel(actionText = null)
        assertTrue(channel.exposeAsAction)
        assertNull(channel.actionText)
    }

    // --- Task 2.1: default-on shipping channels inherit enabledByDefault = true ---

    @Test
    fun testMarkdownChannelIsDefaultOn() {
        // Req 1.5: MarkdownChannel inherits the default (enabledByDefault = true).
        assertTrue(MarkdownChannel().enabledByDefault)
    }

    @Test
    fun testPostmanChannelIsDefaultOn() {
        // Req 1.5: PostmanChannel inherits the default (enabledByDefault = true).
        assertTrue(PostmanChannel().enabledByDefault)
    }

    @Test
    fun testCurlChannelIsDefaultOn() {
        // Req 1.5: CurlChannel inherits the default (enabledByDefault = true).
        assertTrue(CurlChannel().enabledByDefault)
    }
}
