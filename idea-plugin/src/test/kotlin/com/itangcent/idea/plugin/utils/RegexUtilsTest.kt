package com.itangcent.idea.plugin.utils

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Test case of [RegexUtils]
 */
class RegexUtilsTest {

    @Test
    fun testGet() {
        assertEquals("123", REGEX_UTILS.getGroup0("\\d+", "abc123efg"))
        assertEquals("123", REGEX_UTILS.getGroup1("(\\d+)", "abc123efg"))
        assertEquals(null, REGEX_UTILS.getGroup1("\\d[a-z]\\d+", "abc123efg"))
        assertEquals(null, REGEX_UTILS.get(null, "abc123efg", 1))
        assertEquals(null, REGEX_UTILS.get("(\\d+)", null, 1))
        assertEquals("efg", REGEX_UTILS.get("(\\d+)(.*?)$", "abc123efg", 2))
        assertEquals(arrayListOf("123", "efg", "456"), REGEX_UTILS.getAllGroups("(\\d+)(.*?)(\\d+)\$", "abc123efg456"))
        assertEquals(emptyList<String>(), REGEX_UTILS.getAllGroups("(\\d+F\\d+)\$", "abc123efg456"))
        assertEquals(null, REGEX_UTILS.getAllGroups(null, "abc123efg456"))
        assertEquals(null, REGEX_UTILS.getAllGroups("(\\d+F\\d+)\$", null))
    }

    @Test
    fun testFind() {
        assertEquals(listOf("c123", "g456"), REGEX_UTILS.findAllGroup0("[a-z]\\d+", "abc123efg456"))
        assertEquals(listOf("123", "456"), REGEX_UTILS.findAllGroup1("[a-z](\\d+)", "abc123efg456"))
        assertEquals(listOf("c", "g"), REGEX_UTILS.findAll("([a-z])(\\d+)", "abc123efg456", 1))
    }

    @Test
    fun testExtract() {
        assertEquals("abcxefgx", REGEX_UTILS.extract("\\d+", "abc123efg456", "x"))
        assertEquals("ab123-cef456-g", REGEX_UTILS.extract("([a-z])(\\d+)", "abc123efg456", "$2-$1"))
        assertThrows<IndexOutOfBoundsException> { REGEX_UTILS.extract("[a-z]\\d+", "abc123efg456", "$2-$1") }
        assertEquals(null, REGEX_UTILS.extract(null, "abc123efg456", "$2-$1"))
        assertEquals(null, REGEX_UTILS.extract("([a-z])(\\d+)", null, "$2-$1"))
        assertEquals(null, REGEX_UTILS.extract("([a-z])(\\d+)", "abc123efg456", null))
        assertEquals("ab123-cef456-g", REGEX_UTILS.replaceAll("abc123efg456", "([a-z])(\\d+)", "$2-$1"))
    }

    @Test
    fun testDel() {
        assertEquals("abcefg", REGEX_UTILS.delAll("\\d+", "abc123efg456"))
        assertEquals("123456", REGEX_UTILS.delAll("[a-z]", "abc123efg456"))
        assertEquals("", REGEX_UTILS.delAll("[a-z]", ""))
        assertEquals("abc123efg456", REGEX_UTILS.delAll("", "abc123efg456"))
        assertEquals("123efg456", REGEX_UTILS.delBefore("\\d", "abc123efg456"))
        assertEquals("", REGEX_UTILS.delBefore("\\d", ""))
        assertEquals("abc123efg456", REGEX_UTILS.delBefore("\\d[a-z]\\d", "abc123efg456"))
        assertEquals("bc123efg456", REGEX_UTILS.delFirst("[a-z]", "abc123efg456"))
        assertEquals("", REGEX_UTILS.delFirst("[a-z]", ""))
        assertEquals("123efg456", REGEX_UTILS.delFirst("[a-z]+", "abc123efg456"))
    }

    @Test
    fun testCount() {
        assertEquals(2, REGEX_UTILS.count("\\d+", "abc123efg456"))
        assertEquals(6, REGEX_UTILS.count("\\d", "abc123efg456"))
        assertEquals(0, REGEX_UTILS.count(null, "abc123efg456"))
        assertEquals(0, REGEX_UTILS.count("\\d", null))
    }

    @Test
    fun testContains() {
        assertTrue(REGEX_UTILS.contains("\\d+", "abc123efg456"))
        assertTrue(REGEX_UTILS.contains("\\d", "abc123efg456"))
        assertTrue(REGEX_UTILS.contains("[a-z]\\d", "abc123efg456"))
        assertFalse(REGEX_UTILS.contains("\\d[a-z]\\d", "abc123efg456"))
        assertFalse(REGEX_UTILS.contains(null, "abc123efg456"))
        assertFalse(REGEX_UTILS.contains("\\d", null))
    }

    @Test
    fun testIsMatch() {
        assertFalse(REGEX_UTILS.isMatch("\\d+", "abc123efg456"))
        assertFalse(REGEX_UTILS.isMatch("\\d", "abc123efg456"))
        assertFalse(REGEX_UTILS.isMatch("[a-z]\\d", "abc123efg456"))
        assertTrue(REGEX_UTILS.isMatch("[a-z]+\\d+[a-z]+\\d+", "abc123efg456"))
        assertFalse(REGEX_UTILS.isMatch("\\d[a-z]\\d", "abc123efg456"))
        assertFalse(REGEX_UTILS.isMatch(null, "abc123efg456"))
        assertFalse(REGEX_UTILS.isMatch("\\d", null))
    }

    @Test
    fun testEscape() {
        assertEquals(null, REGEX_UTILS.escape(null))
        assertEquals("", REGEX_UTILS.escape(""))
        assertEquals("abc123efg456", REGEX_UTILS.escape("abc123efg456"))
        assertEquals("\\\$\\(a\\)bc123efg456", REGEX_UTILS.escape("\$(a)bc123efg456"))
        assertEquals("\\\$\\(\\)\\*\\+\\.\\[\\]\\?\\\\\\\\\\^\\{\\}\\|", REGEX_UTILS.escape("\$()*+.[]?\\\\^{}|"))
    }
}

private val REGEX_UTILS = RegexUtils();
