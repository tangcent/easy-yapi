package com.itangcent.easyapi.channel.curl

import com.itangcent.easyapi.channel.spi.ChannelConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain-JUnit tests for [CurlConfig] and [CurlFormatOptions].
 */
class CurlConfigTest {

    @Test
    fun `CurlConfig is a ChannelConfig subclass`() {
        val config: ChannelConfig = CurlConfig()
        assertNotNull(config)
    }

    @Test
    fun `CurlConfig defaults are null for outputDir and fileName`() {
        val config = CurlConfig()
        assertNull(config.outputDir)
        assertNull(config.fileName)
    }

    @Test
    fun `CurlConfig default options are CurlFormatOptions defaults`() {
        val config = CurlConfig()
        assertEquals(CurlFormatOptions(), config.options)
    }

    @Test
    fun `CurlConfig preserves custom outputDir and fileName`() {
        val config = CurlConfig(outputDir = "/tmp/output", fileName = "my-curls")
        assertEquals("/tmp/output", config.outputDir)
        assertEquals("my-curls", config.fileName)
    }

    @Test
    fun `CurlConfig preserves custom options`() {
        val options = CurlFormatOptions(
            includeComments = false,
            prettyPrintBody = true,
            multiLineFormat = true,
            longFlags = true,
            includeResponseExample = true,
        )
        val config = CurlConfig(options = options)
        assertEquals(options, config.options)
    }

    @Test
    fun `CurlFormatOptions defaults preserve backward-compatible output`() {
        // CurlFormatOptions defaults are independent of CurlSettings defaults.
        // CurlFormatOptions() is used as a fallback when no config is provided,
        // so it must preserve the pre-enhancement output.
        val opts = CurlFormatOptions()
        assertTrue(opts.includeComments)
        assertFalse(opts.prettyPrintBody)
        assertFalse(opts.multiLineFormat)
        assertFalse(opts.longFlags)
        assertFalse(opts.includeResponseExample)
    }

    @Test
    fun `CurlFormatOptions copy produces independent instance`() {
        val original = CurlFormatOptions()
        val modified = original.copy(longFlags = true)
        assertFalse(original.longFlags)
        assertTrue(modified.longFlags)
        // other fields unchanged
        assertEquals(original.includeComments, modified.includeComments)
        assertEquals(original.prettyPrintBody, modified.prettyPrintBody)
    }
}
