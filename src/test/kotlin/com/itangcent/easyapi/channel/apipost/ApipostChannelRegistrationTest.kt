package com.itangcent.easyapi.channel.apipost

import com.itangcent.easyapi.channel.spi.ChannelRegistry
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

/**
 * Integration smoke test: [ApipostChannel] is registered in `plugin.xml` and
 * discoverable via [ChannelRegistry].
 *
 * Mirrors `OpenApiChannelRegistrationTest`. Because
 * `ApipostChannel.enabledByDefault == false`, the enabling-filtered surfaces
 * (`getActionChannels`, `channelsForSettings`) are deliberately not asserted
 * here — the unfiltered `getChannel()` path is used instead, and the
 * "disabled by default" fact is asserted explicitly.
 */
class ApipostChannelRegistrationTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testApipostChannelIsRegisteredViaPluginXml() {
        val channel = ChannelRegistry.getInstance(project).getChannel("apipost")
        assertNotNull(
            "ApipostChannel should be registered via plugin.xml and discoverable via ChannelRegistry",
            channel,
        )
        assertEquals("apipost", channel?.id)
        assertEquals("ApiPost (Beta)", channel?.displayName)
    }

    fun testApipostChannelAppearsInAllChannelsList() {
        val channels = ChannelRegistry.getInstance(project).allChannels()
        assertNotNull(
            "ApipostChannel should appear in ChannelRegistry.allChannels()",
            channels.find { it is ApipostChannel },
        )
    }

    fun testApipostChannelDisabledByDefault() {
        val channel = ChannelRegistry.getInstance(project).getChannel("apipost")!!
        assertFalse(
            "ApipostChannel should be disabled by default (beta, opt-in)",
            ChannelRegistry.getInstance(project).isEnabled(channel),
        )
    }

    fun testApipostChannelExposedAsAction() {
        val channel = ChannelRegistry.getInstance(project).getChannel("apipost")!!
        assertTrue("ApipostChannel should have exposeAsAction=true", channel.exposeAsAction)
        assertEquals("Export to ApiPost", channel.actionText)
    }

    fun testApipostChannelContributesSettingsType() {
        val channel = ChannelRegistry.getInstance(project).getChannel("apipost")!!
        assertEquals(
            "ApipostChannel should expose ApipostSettings as its settings type",
            ApipostSettings::class,
            channel.settingsType,
        )
    }

    fun testApipostChannelExposesPanels() {
        val channel = ChannelRegistry.getInstance(project).getChannel("apipost")!!
        assertTrue(
            "createOptionsPanel should return an ApipostOptionsPanel",
            channel.createOptionsPanel(project) is ApipostOptionsPanel,
        )
        assertTrue(
            "createSettingsPanel should return an ApipostSettingsPanel",
            channel.createSettingsPanel(project) is ApipostSettingsPanel,
        )
    }

    fun testApipostChannelContributesConfigFileAndRuleKeys() {
        val channel = ChannelRegistry.getInstance(project).getChannel("apipost")!!
        assertEquals(listOf("apipost"), channel.configFiles())
        assertEquals("ApipostChannel should contribute 6 rule keys", 6, channel.ruleKeys().size)
    }

    fun testApipostChannelIsHttpOnly() {
        val channel = ChannelRegistry.getInstance(project).getChannel("apipost")!!
        assertFalse("ApipostChannel must not claim gRPC support", channel.supportsGrpc)
        assertTrue("ApipostChannel supports HTTP", channel.supportsHttp)
        assertTrue("Empty endpoint list is trivially available", channel.isAvailableFor(emptyList()))
    }
}
