package com.itangcent.easyapi.exporter.channel.hoppscotch

import com.itangcent.easyapi.exporter.channel.Channel
import com.itangcent.easyapi.exporter.channel.ChannelRegistry
import com.itangcent.easyapi.exporter.channel.hoppscotch.HoppscotchExportMetadata
import com.itangcent.easyapi.exporter.channel.hoppscotch.model.HoppCollection
import com.itangcent.easyapi.exporter.channel.hoppscotch.model.HoppRESTRequest
import com.itangcent.easyapi.exporter.model.ExportResult
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class HoppscotchChannelTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var channel: HoppscotchChannel

    override fun setUp() {
        super.setUp()
        channel = HoppscotchChannel()
    }

    fun testChannelProperties() {
        assertEquals("hoppscotch", channel.id)
        assertEquals("Hoppscotch (Beta)", channel.displayName)
        assertFalse(channel.supportsGrpc)
        assertTrue(channel.exposeAsAction)
        assertEquals("Export to Hoppscotch (Beta)", channel.actionText)
    }

    fun testChannelRegistered() {
        val registry = ChannelRegistry.getInstance(project)
        val channels = registry.getAvailableChannels(emptyList())
        val hoppscotchChannel = channels.find { it.id == "hoppscotch" }
        assertNotNull("Hoppscotch channel should be registered", hoppscotchChannel)
        assertEquals("Hoppscotch (Beta)", hoppscotchChannel!!.displayName)
    }

    fun testCreateOptionsPanelReturnsNonNull() {
        val panel = channel.createOptionsPanel(project)
        assertNotNull("Options panel should not be null", panel)
    }

    fun testCountRequests() {
        val collection = HoppCollection(
            name = "Root",
            requests = listOf(
                HoppRESTRequest(name = "R1", method = "GET", endpoint = "/r1"),
                HoppRESTRequest(name = "R2", method = "POST", endpoint = "/r2")
            ),
            folders = listOf(
                HoppCollection(
                    name = "Sub",
                    requests = listOf(
                        HoppRESTRequest(name = "R3", method = "PUT", endpoint = "/r3")
                    )
                )
            )
        )
        val method = HoppscotchChannel::class.java.getDeclaredMethod("countRequests", HoppCollection::class.java)
        method.isAccessible = true
        val count = method.invoke(channel, collection) as Int
        assertEquals(3, count)
    }
}
