package com.itangcent.easyapi.util

import org.junit.Assert.*
import org.junit.Test

class StringKitKtTest {

    @Test
    fun testAppend_bothNonBlank() {
        assertEquals("hello\nworld", "hello".append("world"))
    }

    @Test
    fun testAppend_customSeparator() {
        assertEquals("hello world", "hello".append("world", " "))
        assertEquals("hello,world", "hello".append("world", ","))
    }

    @Test
    fun testAppend_thisNull() {
        assertEquals("world", null.append("world"))
    }

    @Test
    fun testAppend_thisBlank() {
        assertEquals("world", "".append("world"))
        assertEquals("world", "   ".append("world"))
    }

    @Test
    fun testAppend_otherNull() {
        assertEquals("hello", "hello".append(null))
    }

    @Test
    fun testAppend_otherBlank() {
        assertEquals("hello", "hello".append(""))
        assertEquals("hello", "hello".append("   "))
    }

    @Test
    fun testAppend_bothNull() {
        assertEquals("", null.append(null))
    }

    @Test
    fun testAppend_bothBlank() {
        // When both are blank, returns "this" (the first blank string)
        assertEquals("", "".append(""))
        assertEquals("   ", "   ".append("   "))
    }

    @Test
    fun testAppend_multiLine() {
        val result = "line1".append("line2").append("line3")
        assertEquals("line1\nline2\nline3", result)
    }

    // --- appendWithDedup ---

    @Test
    fun testAppendWithDedup_noDuplicates() {
        assertEquals("hello\nworld", "hello".appendWithDedup("world"))
    }

    @Test
    fun testAppendWithDedup_removesDuplicateLine() {
        assertEquals("hello\nworld", "hello\nworld".appendWithDedup("world"))
    }

    @Test
    fun testAppendWithDedup_removesFirstDuplicateOnly() {
        assertEquals("hello\nworld", "hello".appendWithDedup("hello\nworld"))
    }

    @Test
    fun testAppendWithDedup_multipleDuplicates() {
        val origin = "line1\nline2\nline3"
        val additional = "line1\nline2\nline4"
        assertEquals("line1\nline2\nline3\nline4", origin.appendWithDedup(additional))
    }

    @Test
    fun testAppendWithDedup_additionalAllDuplicates() {
        assertEquals("hello\nworld", "hello\nworld".appendWithDedup("hello\nworld"))
    }

    @Test
    fun testAppendWithDedup_thisNull() {
        assertEquals("world", null.appendWithDedup("world"))
    }

    @Test
    fun testAppendWithDedup_thisBlank() {
        assertEquals("world", "".appendWithDedup("world"))
    }

    @Test
    fun testAppendWithDedup_additionalNull() {
        assertEquals("hello", "hello".appendWithDedup(null))
    }

    @Test
    fun testAppendWithDedup_additionalBlank() {
        assertEquals("hello", "hello".appendWithDedup(""))
    }

    @Test
    fun testAppendWithDedup_bothNull() {
        assertEquals("", null.appendWithDedup(null))
    }

    @Test
    fun testAppendWithDedup_customSeparator() {
        assertEquals("hello world", "hello".appendWithDedup("world", " "))
    }

    @Test
    fun testAppendWithDedup_realWorldScenario() {
        val docComment = "User API\nProvides user management"
        val ruleDoc = "User API\nProvides user management\nAdditional info"
        assertEquals("User API\nProvides user management\nAdditional info", docComment.appendWithDedup(ruleDoc))
    }

    @Test
    fun testAppendWithDedup_keepsSingleEmptyLine() {
        val origin = "hello"
        val additional = "hello\n\nworld"
        assertEquals("hello\nworld", origin.appendWithDedup(additional))
    }

    @Test
    fun testAppendWithDedup_mergesConsecutiveEmptyLines() {
        val origin = "hello"
        val additional = "hello\n\n\nworld"
        assertEquals("hello\nworld", origin.appendWithDedup(additional))
    }

    @Test
    fun testAppendWithDedup_mergesMultipleConsecutiveEmptyLines() {
        val origin = "line1"
        val additional = "line1\n\n\n\n\nline2"
        assertEquals("line1\nline2", origin.appendWithDedup(additional))
    }

    @Test
    fun testAppendWithDedup_keepsEmptyLineBetweenContent() {
        val origin = "line1"
        val additional = "line1\n\nline2\n\nline3"
        assertEquals("line1\nline2\n\nline3", origin.appendWithDedup(additional))
    }

    @Test
    fun testAppendWithDedup_emptyLineAfterDedup() {
        val origin = "hello\nworld"
        val additional = "hello\n\nworld\nextra"
        assertEquals("hello\nworld\nextra", origin.appendWithDedup(additional))
    }

    @Test
    fun testAppendWithDedup_preservesEmptyLineWithinAdditional() {
        val origin = "line1"
        val additional = "extra\n\nline2"
        assertEquals("line1\nextra\n\nline2", origin.appendWithDedup(additional))
    }
}
