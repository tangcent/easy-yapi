package com.itangcent.easyapi.channel.curl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain-JUnit tests for [CurlSettings] defaults and [CurlRenderMode] parsing.
 *
 * Verifies the user-requested defaults:
 *  - renderMode → ALWAYS_ASK (most discoverable)
 *  - prettyPrintBody → true (more readable)
 *  - copyFromEdited → false (safe default — use original endpoint)
 *  - includeComments → true (preserve section dividers)
 *  - multiLineFormat / longFlags / includeResponseExample → false
 */
class CurlSettingsTest {

    @Test
    fun `default renderMode is ALWAYS_ASK`() {
        val s = CurlSettings()
        assertEquals(CurlRenderMode.ALWAYS_ASK.name, s.renderMode)
        assertEquals(CurlRenderMode.ALWAYS_ASK, s.renderModeEnum())
    }

    @Test
    fun `default prettyPrintBody is true`() {
        val s = CurlSettings()
        assertTrue("prettyPrintBody should default to true", s.prettyPrintBody)
    }

    @Test
    fun `default copyFromEdited is false`() {
        val s = CurlSettings()
        assertFalse("copyFromEdited should default to false", s.copyFromEdited)
    }

    @Test
    fun `default includeComments is true`() {
        val s = CurlSettings()
        assertTrue(s.includeComments)
    }

    @Test
    fun `default multiLineFormat longFlags includeResponseExample are false`() {
        val s = CurlSettings()
        assertFalse(s.multiLineFormat)
        assertFalse(s.longFlags)
        assertFalse(s.includeResponseExample)
    }

    @Test
    fun `renderModeEnum parses valid stored value`() {
        val s = CurlSettings(renderMode = "ALWAYS_RENDER")
        assertEquals(CurlRenderMode.ALWAYS_RENDER, s.renderModeEnum())
    }

    @Test
    fun `renderModeEnum falls back to NEVER_RENDER for invalid stored value`() {
        val s = CurlSettings(renderMode = "INVALID_MODE")
        assertEquals(CurlRenderMode.NEVER_RENDER, s.renderModeEnum())
    }

    @Test
    fun `renderModeEnum falls back to NEVER_RENDER for null-like stored value`() {
        val s = CurlSettings(renderMode = "")
        assertEquals(CurlRenderMode.NEVER_RENDER, s.renderModeEnum())
    }

    // ===== toFormatOptions tests =====

    @Test
    fun `toFormatOptions defaults match CurlSettings defaults`() {
        // The persisted defaults (see class KDoc) must flow through to the
        // default CurlFormatOptions produced by toFormatOptions. If this breaks,
        // the Dashboard "Copy as cURL" default output changes silently.
        val s = CurlSettings()
        val opts = s.toFormatOptions()
        assertEquals(s.includeComments, opts.includeComments)
        assertEquals(s.prettyPrintBody, opts.prettyPrintBody)
        assertEquals(s.multiLineFormat, opts.multiLineFormat)
        assertEquals(s.longFlags, opts.longFlags)
        assertEquals(s.includeResponseExample, opts.includeResponseExample)
    }

    @Test
    fun `toFormatOptions maps every format field`() {
        val s = CurlSettings(
            includeComments = false,
            prettyPrintBody = false,
            multiLineFormat = true,
            longFlags = true,
            includeResponseExample = true,
        )
        val opts = s.toFormatOptions()
        assertFalse(opts.includeComments)
        assertFalse(opts.prettyPrintBody)
        assertTrue(opts.multiLineFormat)
        assertTrue(opts.longFlags)
        assertTrue(opts.includeResponseExample)
    }

    @Test
    fun `toFormatOptions is unaffected by non-format settings`() {
        // renderMode and copyFromEdited are NOT format options — they must not
        // leak into CurlFormatOptions. This pins the exclusion documented in the
        // toFormatOptions KDoc.
        val withRender = CurlSettings(renderMode = CurlRenderMode.ALWAYS_RENDER.name, copyFromEdited = true)
        val withoutRender = CurlSettings(renderMode = CurlRenderMode.NEVER_RENDER.name, copyFromEdited = false)
        assertEquals(withRender.toFormatOptions(), withoutRender.toFormatOptions())
    }
}

/**
 * Plain-JUnit tests for [CurlRenderMode] enum.
 */
class CurlRenderModeTest {

    @Test
    fun `fromStored returns the enum for valid name`() {
        assertEquals(CurlRenderMode.NEVER_RENDER, CurlRenderMode.fromStored("NEVER_RENDER"))
        assertEquals(CurlRenderMode.ALWAYS_RENDER, CurlRenderMode.fromStored("ALWAYS_RENDER"))
        assertEquals(CurlRenderMode.ALWAYS_ASK, CurlRenderMode.fromStored("ALWAYS_ASK"))
    }

    @Test
    fun `fromStored returns NEVER_RENDER for null`() {
        assertEquals(CurlRenderMode.NEVER_RENDER, CurlRenderMode.fromStored(null))
    }

    @Test
    fun `fromStored returns NEVER_RENDER for invalid string`() {
        assertEquals(CurlRenderMode.NEVER_RENDER, CurlRenderMode.fromStored("foobar"))
        assertEquals(CurlRenderMode.NEVER_RENDER, CurlRenderMode.fromStored(""))
    }

    @Test
    fun `fromStored is case-sensitive`() {
        // valueOf is case-sensitive; invalid casing falls back to NEVER_RENDER
        assertEquals(CurlRenderMode.NEVER_RENDER, CurlRenderMode.fromStored("always_ask"))
    }

    @Test
    fun `each mode has a non-empty desc`() {
        // desc is used by the settings panel combo — must be non-empty and unique.
        val descs = CurlRenderMode.values().map { it.desc }
        descs.forEach { d ->
            assertTrue("desc should be non-empty: $d", d.isNotEmpty())
        }
        assertEquals("descs should be unique", descs.size, descs.toSet().size)
    }

    @Test
    fun `desc matches expected human-readable labels`() {
        // Pin the labels so the settings panel ordering stays stable.
        assertEquals("keep placeholders", CurlRenderMode.NEVER_RENDER.desc)
        assertEquals("resolve with active environment", CurlRenderMode.ALWAYS_RENDER.desc)
        assertEquals("ask each export", CurlRenderMode.ALWAYS_ASK.desc)
    }
}
