package com.itangcent.easyapi.channel.httpclient

import org.junit.Assert.*
import org.junit.Test

/**
 * Pure-JUnit tests for [HttpClientChannel] static properties.
 *
 * The export path (host dialog, scratch-file creation) is exercised by the
 * orchestrator integration tests; here we only assert the channel's
 * out-of-the-box enablement declaration.
 */
class HttpClientChannelTest {

    @Test
    fun testHttpClientChannelIsDefaultOff() {
        // Req 1.4: HttpClientChannel ships disabled by default — users opt in via Settings.
        assertFalse(HttpClientChannel().enabledByDefault)
    }

    @Test
    fun testHttpClientChannelIdAndDisplayName() {
        val channel = HttpClientChannel()
        assertEquals("http-client", channel.id)
        assertEquals("HTTP Client", channel.displayName)
    }
}
