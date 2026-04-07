package com.itangcent.easyapi.util

import org.junit.Assert.*
import org.junit.Test

class RuleToolUtilsTest {

    // ── isNullOrEmpty / notNullOrEmpty ────────────────────────────────

    @Test
    fun testIsNullOrEmpty() {
        assertTrue(RuleToolUtils.isNullOrEmpty(null))
        assertTrue(RuleToolUtils.isNullOrEmpty(""))
        assertTrue(RuleToolUtils.isNullOrEmpty(emptyList<Any>()))
        assertTrue(RuleToolUtils.isNullOrEmpty(emptyMap<Any, Any>()))
        assertTrue(RuleToolUtils.isNullOrEmpty(emptyArray<Any>()))
        assertFalse(RuleToolUtils.isNullOrEmpty("hello"))
        assertFalse(RuleToolUtils.isNullOrEmpty(listOf(1)))
        assertFalse(RuleToolUtils.isNullOrEmpty(mapOf("a" to 1)))
        assertFalse(RuleToolUtils.isNullOrEmpty(arrayOf("x")))
        assertFalse(RuleToolUtils.isNullOrEmpty(42))
    }

    @Test
    fun testNotNullOrEmpty() {
        assertFalse(RuleToolUtils.notNullOrEmpty(null))
        assertFalse(RuleToolUtils.notNullOrEmpty(""))
        assertTrue(RuleToolUtils.notNullOrEmpty("hello"))
        assertTrue(RuleToolUtils.notNullOrEmpty(listOf(1)))
    }

    // ── asArray / asList ──────────────────────────────────────────────

    @Test
    fun testAsArray() {
        assertNull(RuleToolUtils.asArray(null))
        val arr = arrayOf(1, 2, 3)
        assertSame(arr, RuleToolUtils.asArray(arr))
        assertArrayEquals(arrayOf(1, 2, 3), RuleToolUtils.asArray(listOf(1, 2, 3)))
        assertArrayEquals(arrayOf("x"), RuleToolUtils.asArray("x"))
    }

    @Test
    fun testAsList() {
        assertNull(RuleToolUtils.asList(null))
        assertEquals(listOf(1, 2, 3), RuleToolUtils.asList(listOf(1, 2, 3)))
        assertEquals(listOf(1, 2, 3), RuleToolUtils.asList(arrayOf(1, 2, 3)))
        assertEquals(listOf("x"), RuleToolUtils.asList("x"))
        assertEquals(listOf(1, 2), RuleToolUtils.asList(setOf(1, 2)))
    }

    // ── intersect / anyIntersect / equalOrIntersect ───────────────────

    @Test
    fun testIntersect() {
        val result = RuleToolUtils.intersect(listOf(1, 2, 3), listOf(2, 3, 4))
        assertNotNull(result)
        assertTrue(result!!.contains(2))
        assertTrue(result.contains(3))
        assertNull(RuleToolUtils.intersect(listOf(1, 2), listOf(3, 4)))
        assertNull(RuleToolUtils.intersect(null, listOf(1)))
        assertNull(RuleToolUtils.intersect(listOf(1), null))
    }

    @Test
    fun testAnyIntersect() {
        assertTrue(RuleToolUtils.anyIntersect(listOf(1, 2), listOf(2, 3)))
        assertFalse(RuleToolUtils.anyIntersect(listOf(1, 2), listOf(3, 4)))
        assertFalse(RuleToolUtils.anyIntersect(null, listOf(1)))
        assertFalse(RuleToolUtils.anyIntersect(listOf(1), null))
    }

    @Test
    fun testEqualOrIntersect() {
        assertTrue(RuleToolUtils.equalOrIntersect("a", "a"))
        assertTrue(RuleToolUtils.equalOrIntersect(listOf("a"), listOf("a", "b")))
        assertFalse(RuleToolUtils.equalOrIntersect("a", "b"))
        assertFalse(RuleToolUtils.equalOrIntersect(listOf("a"), listOf("b")))
    }

    // ── collections ───────────────────────────────────────────────────

    @Test
    fun testNewSet() {
        val set = RuleToolUtils.newSet(1, 2, 3)
        assertEquals(3, set.size)
        assertTrue(set.contains(1))
    }

    @Test
    fun testNewList() {
        val list = RuleToolUtils.newList(1, 2, 3)
        assertEquals(listOf(1, 2, 3), list)
    }

    @Test
    fun testNewMap() {
        val map = RuleToolUtils.newMap()
        assertTrue(map.isEmpty())
    }

    // ── json ──────────────────────────────────────────────────────────

    @Test
    fun testParseJson() {
        val result = RuleToolUtils.parseJson("{\"key\":\"value\"}")
        assertNotNull(result)
        assertNull(RuleToolUtils.parseJson(null))
        assertNull(RuleToolUtils.parseJson(""))
    }

    @Test
    fun testToJson() {
        assertEquals("{\"key\":\"value\"}", RuleToolUtils.toJson(mapOf("key" to "value")))
        assertNull(RuleToolUtils.toJson(null))
    }

    @Test
    fun testPrettyJson() {
        val result = RuleToolUtils.prettyJson(mapOf("key" to "value"))
        assertNotNull(result)
        assertTrue(result!!.contains("\n"))
        assertNull(RuleToolUtils.prettyJson(null))
    }

    // ── string ────────────────────────────────────────────────────────

    @Test
    fun testNullOrEmpty() {
        assertTrue(RuleToolUtils.nullOrEmpty(null))
        assertTrue(RuleToolUtils.nullOrEmpty(""))
        assertFalse(RuleToolUtils.nullOrEmpty("a"))
    }

    @Test
    fun testNullOrBlank() {
        assertTrue(RuleToolUtils.nullOrBlank(null))
        assertTrue(RuleToolUtils.nullOrBlank(""))
        assertTrue(RuleToolUtils.nullOrBlank("   "))
        assertFalse(RuleToolUtils.nullOrBlank("a"))
    }

    @Test
    fun testNotNullOrEmptyStr() {
        assertFalse(RuleToolUtils.notNullOrEmpty(null as String?))
        assertFalse(RuleToolUtils.notNullOrEmpty(""))
        assertTrue(RuleToolUtils.notNullOrEmpty("a"))
    }

    @Test
    fun testNotNullOrBlank() {
        assertFalse(RuleToolUtils.notNullOrBlank(null))
        assertFalse(RuleToolUtils.notNullOrBlank("   "))
        assertTrue(RuleToolUtils.notNullOrBlank("a"))
    }

    @Test
    fun testHeadLine() {
        assertEquals("first", RuleToolUtils.headLine("first\nsecond"))
        assertEquals("only", RuleToolUtils.headLine("only"))
        assertNull(RuleToolUtils.headLine(null))
        assertNull(RuleToolUtils.headLine(""))
        assertNull(RuleToolUtils.headLine("\nsecond"))
    }

    @Test
    fun testCapitalize() {
        assertEquals("Hello", RuleToolUtils.capitalize("hello"))
        assertEquals("Hello", RuleToolUtils.capitalize("Hello"))
        assertEquals("A", RuleToolUtils.capitalize("a"))
        assertNull(RuleToolUtils.capitalize(null))
    }

    @Test
    fun testUncapitalize() {
        assertEquals("hello", RuleToolUtils.uncapitalize("Hello"))
        assertEquals("hello", RuleToolUtils.uncapitalize("hello"))
        assertNull(RuleToolUtils.uncapitalize(null))
    }

    @Test
    fun testSwapCase() {
        assertEquals("hELLO", RuleToolUtils.swapCase("Hello"))
        assertEquals("HELLO", RuleToolUtils.swapCase("hello"))
        assertEquals("hello", RuleToolUtils.swapCase("HELLO"))
        assertNull(RuleToolUtils.swapCase(null))
    }

    @Test
    fun testUpperCase() {
        assertEquals("HELLO", RuleToolUtils.upperCase("hello"))
        assertNull(RuleToolUtils.upperCase(null))
    }

    @Test
    fun testLowerCase() {
        assertEquals("hello", RuleToolUtils.lowerCase("HELLO"))
        assertNull(RuleToolUtils.lowerCase(null))
    }

    @Test
    fun testReverse() {
        assertEquals("olleh", RuleToolUtils.reverse("hello"))
        assertEquals("", RuleToolUtils.reverse(""))
        assertNull(RuleToolUtils.reverse(null))
    }

    @Test
    fun testRepeat() {
        assertEquals("aaa", RuleToolUtils.repeat("a", 3))
        // repeat <= 0 returns str itself (not empty)
        assertEquals("a", RuleToolUtils.repeat("a", 0))
        assertEquals("a", RuleToolUtils.repeat("a", -1))
        // null str returns ""
        assertEquals("", RuleToolUtils.repeat(null, 3))
    }

    @Test
    fun testRepeatWithSeparator() {
        assertEquals("a,a,a", RuleToolUtils.repeat("a", ",", 3))
        assertEquals("", RuleToolUtils.repeat("a", ",", 0))
        assertNull(RuleToolUtils.repeat(null, ",", 3))
    }

    @Test
    fun testIsNumeric() {
        assertTrue(RuleToolUtils.isNumeric("123"))
        assertFalse(RuleToolUtils.isNumeric("12a"))
        assertFalse(RuleToolUtils.isNumeric(""))
        assertFalse(RuleToolUtils.isNumeric(null))
    }

    @Test
    fun testIsAlpha() {
        assertTrue(RuleToolUtils.isAlpha("abc"))
        assertFalse(RuleToolUtils.isAlpha("ab1"))
        assertFalse(RuleToolUtils.isAlpha(""))
        assertFalse(RuleToolUtils.isAlpha(null))
    }

    @Test
    fun testSubstringBefore() {
        assertEquals("hello", RuleToolUtils.substringBefore("hello.world", "."))
        assertEquals("hello.world", RuleToolUtils.substringBefore("hello.world", "x"))
        assertNull(RuleToolUtils.substringBefore(null, "."))
        assertEquals("hello.world", RuleToolUtils.substringBefore("hello.world", null))
    }

    @Test
    fun testSubstringAfter() {
        assertEquals("world", RuleToolUtils.substringAfter("hello.world", "."))
        assertEquals("", RuleToolUtils.substringAfter("hello.world", "x"))
        assertNull(RuleToolUtils.substringAfter(null, "."))
        assertEquals("", RuleToolUtils.substringAfter("hello.world", null))
    }

    @Test
    fun testSubstringBeforeLast() {
        assertEquals("hello.world", RuleToolUtils.substringBeforeLast("hello.world.end", "."))
        assertNull(RuleToolUtils.substringBeforeLast(null, "."))
        assertEquals("hello.world.end", RuleToolUtils.substringBeforeLast("hello.world.end", null))
    }

    @Test
    fun testSubstringAfterLast() {
        assertEquals("end", RuleToolUtils.substringAfterLast("hello.world.end", "."))
        assertNull(RuleToolUtils.substringAfterLast(null, "."))
        assertEquals("", RuleToolUtils.substringAfterLast("hello.world.end", null))
    }

    @Test
    fun testSubstringBetween() {
        assertEquals("world", RuleToolUtils.substringBetween("hello[world]end", "[", "]"))
        assertEquals("world", RuleToolUtils.substringBetween("hello*world*end", "*"))
        assertNull(RuleToolUtils.substringBetween(null, "[", "]"))
        assertNull(RuleToolUtils.substringBetween("hello", null, "]"))
        assertNull(RuleToolUtils.substringBetween("hello", "[", null))
        assertNull(RuleToolUtils.substringBetween("hello", "[", "]"))
    }

    @Test
    fun testSubstringsBetween() {
        val result = RuleToolUtils.substringsBetween("a[1]b[2]c[3]", "[", "]")
        assertNotNull(result)
        assertArrayEquals(arrayOf("1", "2", "3"), result)
        assertNull(RuleToolUtils.substringsBetween(null, "[", "]"))
        assertNull(RuleToolUtils.substringsBetween("abc", null, "]"))
        assertNull(RuleToolUtils.substringsBetween("abc", "[", null))
    }

    @Test
    fun testSplit() {
        assertArrayEquals(arrayOf("a", "b", "c"), RuleToolUtils.split("a b c"))
        assertArrayEquals(arrayOf("a", "b", "c"), RuleToolUtils.split("  a  b  c  "))
        assertNull(RuleToolUtils.split(null))
    }

    @Test
    fun testSplitWithSeparator() {
        assertArrayEquals(arrayOf("a", "b", "c"), RuleToolUtils.split("a,b,c", ","))
        assertArrayEquals(arrayOf("a", "b", "c"), RuleToolUtils.split("a.b.c", "."))
        assertNull(RuleToolUtils.split(null, ","))
    }

    @Test
    fun testToCamelCase() {
        assertEquals("HelloWorld", RuleToolUtils.toCamelCase("hello world", true))
        assertEquals("helloWorld", RuleToolUtils.toCamelCase("hello world", false))
        assertEquals("HelloWorld", RuleToolUtils.toCamelCase("hello_world", true, '_'))
        assertNull(RuleToolUtils.toCamelCase(null, true))
        assertEquals("", RuleToolUtils.toCamelCase("", true))
    }

    @Test
    fun testCamel2Underline() {
        assertEquals("hello_world", RuleToolUtils.camel2Underline("helloWorld"))
        assertEquals("my_class_name", RuleToolUtils.camel2Underline("myClassName"))
        assertEquals("hello", RuleToolUtils.camel2Underline("hello"))
        assertNull(RuleToolUtils.camel2Underline(null))
    }

    @Test
    fun testRemovePrefix() {
        assertEquals("World", RuleToolUtils.removePrefix("HelloWorld", "Hello"))
        assertEquals("HelloWorld", RuleToolUtils.removePrefix("HelloWorld", "Bye"))
        assertNull(RuleToolUtils.removePrefix(null, "Hello"))
        assertNull(RuleToolUtils.removePrefix("Hello", null))
    }

    @Test
    fun testRemoveSuffix() {
        assertEquals("Hello", RuleToolUtils.removeSuffix("HelloWorld", "World"))
        assertEquals("HelloWorld", RuleToolUtils.removeSuffix("HelloWorld", "Bye"))
        assertNull(RuleToolUtils.removeSuffix(null, "World"))
        assertNull(RuleToolUtils.removeSuffix("Hello", null))
    }

    // ── time & date ───────────────────────────────────────────────────

    @Test
    fun testNow() {
        val now = RuleToolUtils.now()
        assertNotNull(now)
        assertTrue(now.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")))
    }

    @Test
    fun testNowWithPattern() {
        val now = RuleToolUtils.now("yyyy/MM/dd")
        assertNotNull(now)
        assertTrue(now.matches(Regex("\\d{4}/\\d{2}/\\d{2}")))
    }

    @Test
    fun testNowWithNullPattern() {
        val now = RuleToolUtils.now(null)
        assertTrue(now.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")))
    }

    @Test
    fun testToday() {
        val today = RuleToolUtils.today()
        assertTrue(today.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
    }

    @Test
    fun testFormat() {
        val result = RuleToolUtils.format(0L, "yyyy-MM-dd")
        assertNotNull(result)
        assertTrue(result!!.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
    }

    @Test
    fun testFormatWithNullPattern() {
        val result = RuleToolUtils.format(0L, null)
        assertNotNull(result)
        assertTrue(result!!.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")))
    }

    // ── debug ─────────────────────────────────────────────────────────

    @Test
    fun testDebugNull() {
        assertEquals("debug object is null", RuleToolUtils.debug(null))
    }

    @Test
    fun testDebugObject() {
        val result = RuleToolUtils.debug("hello")
        assertNotNull(result)
        assertTrue(result.contains("type:"))
    }

    @Test
    fun testDebugDataClass() {
        data class Point(val x: Int, val y: Int)
        val result = RuleToolUtils.debug(Point(1, 2))
        assertNotNull(result)
        // debug output contains type information
        assertTrue(result.isNotEmpty())
    }
}
