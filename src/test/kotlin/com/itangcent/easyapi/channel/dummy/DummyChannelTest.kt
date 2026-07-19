package com.itangcent.easyapi.channel.dummy

import com.itangcent.easyapi.channel.spi.Channel
import com.itangcent.easyapi.channel.spi.ChannelRegistry
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

/**
 * V2 extensibility proof.
 *
 * This test registers the [DummyChannel] via the `channel` extension point
 * **programmatically**, then asserts it is discoverable through the platform
 * registry. No production `plugin.xml` or any other easy-api core/shared
 * source file is edited to add the dummy channel — the stub artifacts live
 * entirely under `src/test/` and register through the platform's extension
 * point. This proves the Channel Author Contract.
 *
 * With the collapsed EP (Task 9 / Phase 4), a single `channel` registration
 * covers both export and settings surfaces.
 */
class DummyChannelTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testDummyChannelIsDiscoverableViaChannelEP() {
        // The channel EP is project-scoped (area="IDEA_PROJECT").
        project.extensionArea
            .getExtensionPoint<Channel>("com.itangcent.idea.plugin.easy-yapi.channel")
            .registerExtension(DummyChannel(), testRootDisposable)

        val channels = ChannelRegistry.getInstance(project).allChannels()
        val found = channels.find { it.id == "dummy" }
        assertNotNull("DummyChannel should be discoverable via the channel EP", found)
        assertEquals("Dummy", found?.displayName)
    }

    fun testDummyChannelSettingsSurfaceIsDiscoverable() {
        // The single channel EP now also covers the former channelSettings surface.
        project.extensionArea
            .getExtensionPoint<Channel>("com.itangcent.idea.plugin.easy-yapi.channel")
            .registerExtension(DummyChannel(), testRootDisposable)

        val channel = ChannelRegistry.getInstance(project).getChannel("dummy")
        assertNotNull("DummyChannel should be discoverable via the channel EP", channel)
        assertEquals("dummy", channel?.id)
        assertNull("Dummy channel has no settings panel", channel?.createSettingsPanel(project))
        assertTrue("Dummy channel contributes no config files", channel?.configFiles().isNullOrEmpty() ?: true)
    }
}
