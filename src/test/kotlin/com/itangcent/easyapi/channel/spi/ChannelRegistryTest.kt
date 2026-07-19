package com.itangcent.easyapi.channel.spi

import com.itangcent.easyapi.core.export.*
import org.junit.Assert.*
import org.junit.Test

class ChannelRegistryTest {

    private class StubChannel(
        override val id: String,
        override val displayName: String,
        override val supportsHttp: Boolean = true,
        override val supportsGrpc: Boolean = false,
        override val exposeAsAction: Boolean = false,
        override val actionText: String? = null,
        override val enabledByDefault: Boolean = true
    ) : Channel {

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

    // --- Task 1.1: Channel.enabledByDefault ---

    @Test
    fun testEnabledByDefaultDefaultsToTrue() {
        // A StubChannel that does not override enabledByDefault resolves to true.
        val channel = StubChannel("test", "Test")
        assertTrue(channel.enabledByDefault)
    }

    @Test
    fun testEnabledByDefaultCanBeOverridden() {
        // A StubChannel that overrides enabledByDefault = false resolves to false.
        val channel = StubChannel("test", "Test", enabledByDefault = false)
        assertFalse(channel.enabledByDefault)
    }

    // --- Task 1.3: ChannelRegistry.resolveEnabled (pure resolution rule) ---
    // Exercises the full truth table of the resolution rule without a Project.
    // Integrated filtering (getActionChannels/getAvailableChannels/channelsForSettings
    // via isEnabled) is covered by ExportOrchestratorTest's IDE-fixture tests.

    private val defaultOn = StubChannel("default-on", "Default On", enabledByDefault = true)
    private val defaultOff = StubChannel("default-off", "Default Off", enabledByDefault = false)
    private val empty = emptyArray<String>()

    @Test
    fun testResolveEnabled_defaultOnNoPreference_isEnabled() {
        assertTrue(ChannelRegistry.resolveEnabled(defaultOn, empty, empty))
    }

    @Test
    fun testResolveEnabled_defaultOnInDisabled_isDisabled() {
        assertFalse(ChannelRegistry.resolveEnabled(defaultOn, empty, arrayOf("default-on")))
    }

    @Test
    fun testResolveEnabled_defaultOnInEnabled_isEnabled() {
        assertTrue(ChannelRegistry.resolveEnabled(defaultOn, arrayOf("default-on"), empty))
    }

    @Test
    fun testResolveEnabled_defaultOffNoPreference_isDisabled() {
        assertFalse(ChannelRegistry.resolveEnabled(defaultOff, empty, empty))
    }

    @Test
    fun testResolveEnabled_defaultOffInEnabled_isEnabled() {
        assertTrue(ChannelRegistry.resolveEnabled(defaultOff, arrayOf("default-off"), empty))
    }

    @Test
    fun testResolveEnabled_defaultOffInDisabled_isDisabled() {
        // Redundant but explicit — default-off in disabledIds stays disabled.
        assertFalse(ChannelRegistry.resolveEnabled(defaultOff, empty, arrayOf("default-off")))
    }

    @Test
    fun testResolveEnabled_idInBothEnabledAndDisabled_explicitOnWins() {
        // Design "Normalization on save": when a channel's id is in both sets,
        // explicit-on wins (the `in enabledIds` short-circuit returns true).
        assertTrue(ChannelRegistry.resolveEnabled(defaultOn, arrayOf("default-on"), arrayOf("default-on")))
        assertTrue(ChannelRegistry.resolveEnabled(defaultOff, arrayOf("default-off"), arrayOf("default-off")))
    }

    @Test
    fun testResolveEnabled_fallsBackToEnabledByDefaultWhenPreferenceAbsent() {
        // Req 2.6: absent preference (empty arrays) falls back to enabledByDefault.
        // This mirrors the fallback path taken when settings storage is unreadable.
        assertTrue(ChannelRegistry.resolveEnabled(defaultOn, empty, empty))
        assertFalse(ChannelRegistry.resolveEnabled(defaultOff, empty, empty))
    }

    // --- Task 1.3: filtered query logic ---
    // The registry query methods need a Project (covered by ExportOrchestratorTest).
    // Here we replicate the exact filtering they apply (&& resolveEnabled) to verify
    // the rule excludes disabled channels from each enumeration shape.

    @Test
    fun testFilteredQueryLogic_actionChannelsExcludesDisabled() {
        val channels = listOf(
            StubChannel("on-action", "On Action", exposeAsAction = true, enabledByDefault = true),
            StubChannel("off-action", "Off Action", exposeAsAction = true, enabledByDefault = false),
            StubChannel("non-action", "Non Action", exposeAsAction = false, enabledByDefault = true)
        )
        val enabledIds = emptyArray<String>()
        val disabledIds = emptyArray<String>()

        // Mirrors getActionChannels(): exposeAsAction && isEnabled
        val actionChannels = channels.filter { it.exposeAsAction && ChannelRegistry.resolveEnabled(it, enabledIds, disabledIds) }
        assertEquals(1, actionChannels.size)
        assertEquals("on-action", actionChannels[0].id)
    }

    @Test
    fun testFilteredQueryLogic_availableChannelsExcludesDisabledEvenWhenAvailable() {
        val channels = listOf(
            StubChannel("on", "On", supportsHttp = true, enabledByDefault = true),
            StubChannel("off", "Off", supportsHttp = true, enabledByDefault = false)
        )
        val httpEndpoints = listOf(httpEndpoint())
        val enabledIds = emptyArray<String>()
        val disabledIds = emptyArray<String>()

        // Mirrors getAvailableChannels(): isAvailableFor && isEnabled. The "off"
        // channel is available for httpEndpoints but disabled, so it is excluded.
        val available = channels.filter { it.isAvailableFor(httpEndpoints) && ChannelRegistry.resolveEnabled(it, enabledIds, disabledIds) }
        assertEquals(1, available.size)
        assertEquals("on", available[0].id)
    }

    @Test
    fun testFilteredQueryLogic_channelsForSettingsExcludesDisabled() {
        val channels = listOf(
            StubChannel("on", "On", enabledByDefault = true),
            StubChannel("off", "Off", enabledByDefault = false)
        )
        val enabledIds = emptyArray<String>()
        val disabledIds = emptyArray<String>()

        // Mirrors channelsForSettings(): filter isEnabled, then sortBy settingsTabOrder.
        val forSettings = channels
            .filter { ChannelRegistry.resolveEnabled(it, enabledIds, disabledIds) }
            .sortedBy { it.settingsTabOrder }
        assertEquals(1, forSettings.size)
        assertEquals("on", forSettings[0].id)
    }

    @Test
    fun testFilteredQueryLogic_allChannelsAndGetChannelRemainUnfiltered() {
        // Req 4.5: allChannels()/getChannel(id) are pure registry primitives — they
        // do NOT consult enablement. Here we assert the lookup-by-id logic itself
        // ignores enabledByDefault (a disabled channel is still found by id).
        val channels = listOf(
            StubChannel("on", "On", enabledByDefault = true),
            StubChannel("off", "Off", enabledByDefault = false)
        )
        // getChannel equivalent (unfiltered lookup)
        assertEquals("off", channels.firstOrNull { it.id == "off" }?.id)
        assertEquals("on", channels.firstOrNull { it.id == "on" }?.id)
        assertNull(channels.firstOrNull { it.id == "missing" })
    }
}
