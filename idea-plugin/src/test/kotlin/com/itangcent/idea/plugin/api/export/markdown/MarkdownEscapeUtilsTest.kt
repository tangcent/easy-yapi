package com.itangcent.idea.plugin.api.export.markdown

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class MarkdownEscapeUtilsTest {

    @Test
    fun testEscapeNull() {
        assertEquals("", MarkdownEscapeUtils.escape(null))
    }

    @Test
    fun testEscapeBlank() {
        assertEquals("", MarkdownEscapeUtils.escape(""))
        assertEquals("", MarkdownEscapeUtils.escape("   "))
    }

    @Test
    fun testEscapeNewline() {
        assertEquals("Hello<br>World", MarkdownEscapeUtils.escape("Hello\nWorld"))
        assertEquals("Line1<br>Line2<br>Line3", MarkdownEscapeUtils.escape("Line1\nLine2\nLine3"))
    }

    @Test
    fun testEscapePipe() {
        assertEquals("Column1 \\| Column2", MarkdownEscapeUtils.escape("Column1 | Column2"))
    }

    @Test
    fun testEscapeCombined() {
        assertEquals("Header \\| Description<br>Content \\| Details", 
            MarkdownEscapeUtils.escape("Header | Description\nContent | Details"))
    }

    @Test
    fun testEscapeNoSpecialChars() {
        assertEquals("Plain text", MarkdownEscapeUtils.escape("Plain text"))
    }
} 