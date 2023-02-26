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
        assertEquals("123", RegexUtils.getGroup0("\\d+", "abc123efg"))
        assertEquals("123", RegexUtils.getGroup1("(\\d+)", "abc123efg"))
        assertEquals(null, RegexUtils.getGroup1("\\d[a-z]\\d+", "abc123efg"))
        assertEquals(null, RegexUtils.get(null, "abc123efg", 1))
        assertEquals(null, RegexUtils.get("(\\d+)", null, 1))
        assertEquals("efg", RegexUtils.get("(\\d+)(.*?)$", "abc123efg", 2))
        assertEquals(arrayListOf("123", "efg", "456"), RegexUtils.getAllGroups("(\\d+)(.*?)(\\d+)\$", "abc123efg456"))
        assertEquals(emptyList<String>(), RegexUtils.getAllGroups("(\\d+F\\d+)\$", "abc123efg456"))
        assertEquals(null, RegexUtils.getAllGroups(null, "abc123efg456"))
        assertEquals(null, RegexUtils.getAllGroups("(\\d+F\\d+)\$", null))
    }

    @Test
    fun testFind() {
        assertEquals(listOf("c123", "g456"), RegexUtils.findAllGroup0("[a-z]\\d+", "abc123efg456"))
        assertEquals(listOf("123", "456"), RegexUtils.findAllGroup1("[a-z](\\d+)", "abc123efg456"))
        assertEquals(listOf("c", "g"), RegexUtils.findAll("([a-z])(\\d+)", "abc123efg456", 1))
    }

    @Test
    fun testExtract() {
        assertEquals("abcxefgx", RegexUtils.extract("\\d+", "abc123efg456", "x"))
        assertEquals("ab123-cef456-g", RegexUtils.extract("([a-z])(\\d+)", "abc123efg456", "$2-$1"))
        assertThrows<IndexOutOfBoundsException> { RegexUtils.extract("[a-z]\\d+", "abc123efg456", "$2-$1") }
        assertEquals(null, RegexUtils.extract(null, "abc123efg456", "$2-$1"))
        assertEquals(null, RegexUtils.extract("([a-z])(\\d+)", null, "$2-$1"))
        assertEquals(null, RegexUtils.extract("([a-z])(\\d+)", "abc123efg456", null))
        assertEquals("ab123-cef456-g", RegexUtils.replaceAll("abc123efg456", "([a-z])(\\d+)", "$2-$1"))
    }

    @Test
    fun testDel() {
        assertEquals("abcefg", RegexUtils.delAll("\\d+", "abc123efg456"))
        assertEquals("123456", RegexUtils.delAll("[a-z]", "abc123efg456"))
        assertEquals("", RegexUtils.delAll("[a-z]", ""))
        assertEquals("abc123efg456", RegexUtils.delAll("", "abc123efg456"))
        assertEquals("123efg456", RegexUtils.delBefore("\\d", "abc123efg456"))
        assertEquals("", RegexUtils.delBefore("\\d", ""))
        assertEquals("abc123efg456", RegexUtils.delBefore("\\d[a-z]\\d", "abc123efg456"))
        assertEquals("bc123efg456", RegexUtils.delFirst("[a-z]", "abc123efg456"))
        assertEquals("", RegexUtils.delFirst("[a-z]", ""))
        assertEquals("123efg456", RegexUtils.delFirst("[a-z]+", "abc123efg456"))
    }

    @Test
    fun testCount() {
        assertEquals(2, RegexUtils.count("\\d+", "abc123efg456"))
        assertEquals(6, RegexUtils.count("\\d", "abc123efg456"))
        assertEquals(0, RegexUtils.count(null, "abc123efg456"))
        assertEquals(0, RegexUtils.count("\\d", null))
    }

    @Test
    fun testContains() {
        assertTrue(RegexUtils.contains("\\d+", "abc123efg456"))
        assertTrue(RegexUtils.contains("\\d", "abc123efg456"))
        assertTrue(RegexUtils.contains("[a-z]\\d", "abc123efg456"))
        assertFalse(RegexUtils.contains("\\d[a-z]\\d", "abc123efg456"))
        assertFalse(RegexUtils.contains(null, "abc123efg456"))
        assertFalse(RegexUtils.contains("\\d", null))
    }

    @Test
    fun testIsMatch() {
        assertFalse(RegexUtils.isMatch("\\d+", "abc123efg456"))
        assertFalse(RegexUtils.isMatch("\\d", "abc123efg456"))
        assertFalse(RegexUtils.isMatch("[a-z]\\d", "abc123efg456"))
        assertTrue(RegexUtils.isMatch("[a-z]+\\d+[a-z]+\\d+", "abc123efg456"))
        assertFalse(RegexUtils.isMatch("\\d[a-z]\\d", "abc123efg456"))
        assertFalse(RegexUtils.isMatch(null, "abc123efg456"))
        assertFalse(RegexUtils.isMatch("\\d", null))
    }

    @Test
    fun testEscape() {
        assertEquals(null, RegexUtils.escape(null))
        assertEquals("", RegexUtils.escape(""))
        assertEquals("abc123efg456", RegexUtils.escape("abc123efg456"))
        assertEquals("\\\$\\(a\\)bc123efg456", RegexUtils.escape("\$(a)bc123efg456"))
        assertEquals("\\\$\\(\\)\\*\\+\\.\\[\\]\\?\\\\\\\\\\^\\{\\}\\|", RegexUtils.escape("\$()*+.[]?\\\\^{}|"))
    }
}
