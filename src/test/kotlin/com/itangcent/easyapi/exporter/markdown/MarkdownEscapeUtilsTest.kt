package com.itangcent.easyapi.exporter.markdown

import org.junit.Assert.*
import org.junit.Test

class MarkdownEscapeUtilsTest {

    @Test
    fun testEscapeNullReturnsEmpty() {
        assertEquals("", MarkdownEscapeUtils.escape(null))
    }

    @Test
    fun testEscapeBlankReturnsEmpty() {
        assertEquals("", MarkdownEscapeUtils.escape(""))
        assertEquals("", MarkdownEscapeUtils.escape("   "))
    }

    @Test
    fun testEscapePlainStringUnchanged() {
        assertEquals("hello world", MarkdownEscapeUtils.escape("hello world"))
    }

    @Test
    fun testEscapeNewlineToBr() {
        assertEquals("line1<br>line2", MarkdownEscapeUtils.escape("line1\nline2"))
    }

    @Test
    fun testEscapePipeToBackslashPipe() {
        assertEquals("a\\|b", MarkdownEscapeUtils.escape("a|b"))
    }

    @Test
    fun testEscapeMultipleNewlines() {
        assertEquals("a<br><br>b", MarkdownEscapeUtils.escape("a\n\nb"))
    }

    @Test
    fun testEscapeMixedNewlinesAndPipes() {
        assertEquals("a<br>\\|b", MarkdownEscapeUtils.escape("a\n|b"))
    }
}
