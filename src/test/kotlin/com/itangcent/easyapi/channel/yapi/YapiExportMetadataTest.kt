package com.itangcent.easyapi.channel.yapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [YapiExportMetadata.notificationLinks] decides which links a completion notification offers and
 * in which order — see the method's KDoc for the policy. Pure computation, no project needed.
 */
class YapiExportMetadataTest {

    private val cartLink = "用户服务" to "http://y/project/42/interface/api/cat_34"

    @Test
    fun `a single exported api leads the notification`() {
        val metadata = YapiExportMetadata(
            cartLinks = mapOf(cartLink),
            apiLinks = mapOf("轻量刷新 token" to "http://y/project/42/interface/api/333")
        )

        assertEquals(
            listOf("轻量刷新 token" to "http://y/project/42/interface/api/333", cartLink),
            metadata.notificationLinks()
        )
    }

    @Test
    fun `a batch export links to the categories only`() {
        val metadata = YapiExportMetadata(
            cartLinks = mapOf(cartLink),
            apiLinks = mapOf(
                "API A" to "http://y/project/42/interface/api/1",
                "API B" to "http://y/project/42/interface/api/2"
            )
        )

        assertEquals(
            "多端点导出不应把气球变成一堆逐 API 链接",
            listOf(cartLink),
            metadata.notificationLinks()
        )
    }

    @Test
    fun `an export that revealed no api id links to the categories only`() {
        // A YAPI fork may report success without echoing data._id — see ApiUploadResult.
        val metadata = YapiExportMetadata(cartLinks = mapOf(cartLink))

        assertEquals(listOf(cartLink), metadata.notificationLinks())
    }

    @Test
    fun `a batch that revealed a single api id links like a single export`() {
        // The signal is "exactly one API link", which a wider export can also produce when only
        // one of its APIs revealed an id — a lone link is not a wall of links.
        val metadata = YapiExportMetadata(
            cartLinks = mapOf(cartLink),
            apiLinks = mapOf("API A" to "http://y/project/42/interface/api/1")
        )

        assertEquals(
            listOf("API A" to "http://y/project/42/interface/api/1", cartLink),
            metadata.notificationLinks()
        )
    }

    @Test
    fun `a long api name is capped for the notification action`() {
        // A balloon clips a long action label mid-word, so it is capped with an ellipsis.
        val longName = "轻量刷新 token 的接口名称".repeat(5)
        assertTrue("fixture must exceed the cap", longName.length > 32)
        val metadata = YapiExportMetadata(
            cartLinks = mapOf(cartLink),
            apiLinks = mapOf(longName to "http://y/project/42/interface/api/333")
        )

        val links = metadata.notificationLinks()

        assertEquals(32, links[0].first.length)
        assertTrue(links[0].first.endsWith("…"))
        assertTrue(
            "formatDisplay 应保留完整名称",
            metadata.formatDisplay().contains(longName)
        )
    }

    @Test
    fun `formatDisplay keeps cart and api links`() {
        val metadata = YapiExportMetadata(
            cartLinks = mapOf(cartLink),
            apiLinks = mapOf("轻量刷新 token" to "http://y/project/42/interface/api/333")
        )

        assertEquals(
            "用户服务: http://y/project/42/interface/api/cat_34\n" +
                "轻量刷新 token: http://y/project/42/interface/api/333",
            metadata.formatDisplay()
        )
    }
}
