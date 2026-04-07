package com.itangcent.easyapi.util

import org.junit.Assert.*
import org.junit.Test

class RegexUtilsTest {

    @Test
    fun testGetGroup0() {
        assertEquals("user:john", RegexUtils.getGroup0("user:\\w+", "user:john"))
        assertEquals("123", RegexUtils.getGroup0("\\d+", "abc123def"))
        assertNull(RegexUtils.getGroup0("xyz", "abc"))
        assertNull(RegexUtils.getGroup0("", "abc"))
        // getGroup0 delegates to get() which handles null
        assertNull(RegexUtils.get("abc", null, 0))
    }

    @Test
    fun testGetGroup1() {
        assertEquals("john", RegexUtils.getGroup1("user:(\\w+)", "user:john"))
        assertEquals("123", RegexUtils.getGroup1("id=(\\d+)", "id=123"))
        assertNull(RegexUtils.getGroup1("(\\d+)", "abc"))
        assertNull(RegexUtils.getGroup1("", "abc"))
        // getGroup1 delegates to get() which handles null
        assertNull(RegexUtils.get("abc", null, 1))
    }

    @Test
    fun testGet() {
        assertEquals("user:john", RegexUtils.get("user:(\\w+)", "user:john", 0))
        assertEquals("john", RegexUtils.get("user:(\\w+)", "user:john", 1))
        assertNull(RegexUtils.get("(\\d+)", "abc", 1))
        assertNull(RegexUtils.get(null, "abc", 0))
        assertNull(RegexUtils.get("abc", null, 0))
    }

    @Test
    fun testGetAllGroups() {
        val result = RegexUtils.getAllGroups("(\\w+):(\\w+)", "user:john")
        assertEquals(listOf("user", "john"), result)

        val multiGroup = RegexUtils.getAllGroups("(\\d+)-(\\d+)-(\\d+)", "2024-01-15")
        assertEquals(listOf("2024", "01", "15"), multiGroup)

        assertEquals(emptyList<String>(), RegexUtils.getAllGroups("(\\d+)", "abc"))
        assertNull(RegexUtils.getAllGroups(null, "abc"))
        assertNull(RegexUtils.getAllGroups("abc", null))
    }

    @Test
    fun testExtract() {
        assertEquals("john", RegexUtils.extract("user:(\\w+)", "user:john", "$1"))
        assertEquals("name=john", RegexUtils.extract("user:(\\w+)", "user:john", "name=$1"))
        assertEquals("abc123def", RegexUtils.extract("(\\d+)", "abc123def", "$0"))
        assertNull(RegexUtils.extract(null, "abc", "$1"))
        assertNull(RegexUtils.extract("abc", null, "$1"))
        assertNull(RegexUtils.extract("abc", "def", null))
    }

    @Test
    fun testExtractMultipleMatches() {
        val result = RegexUtils.extract("(\\d+)", "a1b2c3", "[$1]")
        assertEquals("a[1]b[2]c[3]", result)
    }

    @Test
    fun testDelFirst() {
        assertEquals("bcdef", RegexUtils.delFirst("a", "abcdef"))
        // replaceFirst only removes the first match
        assertEquals("abcdef456", RegexUtils.delFirst("\\d+", "abc123def456"))
        assertEquals("abc", RegexUtils.delFirst("xyz", "abc"))
        assertEquals("abc", RegexUtils.delFirst("", "abc"))
    }

    @Test
    fun testDelAll() {
        assertEquals("bcdef", RegexUtils.delAll("a", "aabcadef"))
        assertEquals("abcdef", RegexUtils.delAll("\\d+", "abc123def456"))
        assertEquals("abc", RegexUtils.delAll("xyz", "abc"))
        assertEquals("abc", RegexUtils.delAll("", "abc"))
    }

    @Test
    fun testDelBefore() {
        // delBefore returns content.substring(matcher.end() - 1)
        // For "c" in "abcdef": match ends at index 3, so substring(2) = "cdef"
        assertEquals("cdef", RegexUtils.delBefore("c", "abcdef"))
        // For "\\d" in "abc123def": first digit "1" ends at index 4, so substring(3) = "123def"
        assertEquals("123def", RegexUtils.delBefore("\\d", "abc123def"))
        assertEquals("abc", RegexUtils.delBefore("xyz", "abc"))
    }

    @Test
    fun testFindAllGroup0() {
        assertEquals(listOf("123", "456"), RegexUtils.findAllGroup0("\\d+", "abc123def456"))
        assertEquals(listOf("a", "b", "c"), RegexUtils.findAllGroup0("[a-c]", "abcdef"))
        assertEquals(emptyList<String>(), RegexUtils.findAllGroup0("\\d+", "abc"))
        assertEquals(emptyList<String>(), RegexUtils.findAllGroup0("", "abc"))
    }

    @Test
    fun testFindAllGroup1() {
        assertEquals(listOf("john", "jane"), RegexUtils.findAllGroup1("user:(\\w+)", "user:john user:jane"))
        assertEquals(listOf("123", "456"), RegexUtils.findAllGroup1("id=(\\d+)", "id=123 id=456"))
        assertEquals(emptyList<String>(), RegexUtils.findAllGroup1("(\\d+)", "abc"))
    }

    @Test
    fun testFindAll() {
        assertEquals(listOf("123", "456"), RegexUtils.findAll("\\d+", "abc123def456", 0))
        assertEquals(listOf("john", "jane"), RegexUtils.findAll("user:(\\w+)", "user:john user:jane", 1))
        assertEquals(emptyList<String>(), RegexUtils.findAll("", "abc"))
        assertEquals(emptyList<String>(), RegexUtils.findAll("\\d+", ""))
    }

    @Test
    fun testReplaceAll() {
        assertEquals("john", RegexUtils.replaceAll("user:john", "user:(\\w+)", "$1"))
        assertEquals("name=john", RegexUtils.replaceAll("user:john", "user:(\\w+)", "name=$1"))
    }

    @Test
    fun testCount() {
        assertEquals(2, RegexUtils.count("\\d+", "abc123def456"))
        assertEquals(3, RegexUtils.count("a", "banana"))
        assertEquals(0, RegexUtils.count("\\d+", "abc"))
        assertEquals(0, RegexUtils.count(null, "abc"))
        assertEquals(0, RegexUtils.count("abc", null))
    }

    @Test
    fun testContains() {
        assertTrue(RegexUtils.contains("\\d+", "abc123"))
        assertTrue(RegexUtils.contains("user", "username"))
        assertFalse(RegexUtils.contains("\\d+", "abc"))
        assertFalse(RegexUtils.contains(null, "abc"))
        assertFalse(RegexUtils.contains("abc", null))
    }

    @Test
    fun testIsMatch() {
        assertTrue(RegexUtils.isMatch("\\d+", "123"))
        assertTrue(RegexUtils.isMatch("\\w+", "abc"))
        assertFalse(RegexUtils.isMatch("\\d+", "abc123"))
        assertFalse(RegexUtils.isMatch("\\d+", "abc"))
        assertFalse(RegexUtils.isMatch(null, "abc"))
        assertFalse(RegexUtils.isMatch("abc", null))
    }

    @Test
    fun testEscape() {
        assertEquals("\\\\", RegexUtils.escape("\\"))
        assertEquals("\\$", RegexUtils.escape("$"))
        assertEquals("\\(\\)", RegexUtils.escape("()"))
        assertEquals("\\*\\+", RegexUtils.escape("*+"))
        assertEquals("\\.\\[\\]", RegexUtils.escape(".[]"))
        assertEquals("\\?\\^", RegexUtils.escape("?^"))
        assertEquals("\\{\\}\\|", RegexUtils.escape("{}|"))
        assertEquals("abc", RegexUtils.escape("abc"))
        assertNull(RegexUtils.escape(null))
        assertEquals("", RegexUtils.escape(""))
    }

    @Test
    fun testEscapeAllSpecialChars() {
        val special = "\\$()*.[]?^{}|+"
        val escaped = RegexUtils.escape(special)
        // Each special char gets a backslash prepended
        assertNotNull(escaped)
        assertTrue(escaped!!.startsWith("\\\\"))
        assertTrue(escaped.contains("\\$"))
        assertTrue(escaped.contains("\\("))
    }

    @Test
    fun testPatternCaching() {
        val regex = "\\d+"
        val result1 = RegexUtils.getGroup0(regex, "123")
        val result2 = RegexUtils.getGroup0(regex, "456")
        assertEquals("123", result1)
        assertEquals("456", result2)
    }

    @Test
    fun testDotallFlag() {
        val result = RegexUtils.getGroup0("a.*b", "a\nb")
        assertEquals("a\nb", result)
    }

    @Test
    fun testResolveTemplateWithMultipleGroups() {
        val result = RegexUtils.extract("(\\w+):(\\w+)", "user:john", "$2-$1")
        assertEquals("john-user", result)
    }

    @Test
    fun testResolveTemplateWithNoGroups() {
        val result = RegexUtils.extract("\\d+", "abc123def", "num")
        assertEquals("abcnumdef", result)
    }

    @Test
    fun testResolveTemplateWithInvalidGroup() {
        // Group 2 doesn't exist for a single-group pattern, so $2 is kept as-is
        val result = RegexUtils.extract("(\\w+)", "abc", "\$1-end")
        assertEquals("abc-end", result)
    }
}
