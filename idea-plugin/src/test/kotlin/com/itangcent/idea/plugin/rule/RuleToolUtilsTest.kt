package com.itangcent.idea.plugin.rule

import com.itangcent.common.utils.DateUtils
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.awt.HeadlessException
import java.util.regex.Pattern
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Test case of [RuleToolUtils]
 */
internal class RuleToolUtilsTest {

    @Test
    fun testIsNullOrEmpty() {
        assertTrue(RuleToolUtils.isNullOrEmpty(null))
        assertTrue(RuleToolUtils.isNullOrEmpty(""))
        assertFalse(RuleToolUtils.isNullOrEmpty(" "))
        assertFalse(RuleToolUtils.isNullOrEmpty("a"))
        assertTrue(RuleToolUtils.isNullOrEmpty(emptyArray<String>()))
        assertFalse(RuleToolUtils.isNullOrEmpty(arrayOf("a")))
        assertTrue(RuleToolUtils.isNullOrEmpty(emptyList<String>()))
        assertFalse(RuleToolUtils.isNullOrEmpty(listOf("a")))
        assertTrue(RuleToolUtils.isNullOrEmpty(emptyMap<String, String>()))
        assertFalse(RuleToolUtils.isNullOrEmpty(mapOf("a" to 1)))
        assertFalse(RuleToolUtils.isNullOrEmpty(RuleToolUtilsTestPoint(1, 2, 3)))
    }

    @Test
    fun testNotNullOrEmpty() {
        assertFalse(RuleToolUtils.notNullOrEmpty(null))
        assertFalse(RuleToolUtils.notNullOrEmpty(""))
        assertTrue(RuleToolUtils.notNullOrEmpty(" "))
        assertTrue(RuleToolUtils.notNullOrEmpty("a"))
        assertFalse(RuleToolUtils.notNullOrEmpty(emptyArray<String>()))
        assertTrue(RuleToolUtils.notNullOrEmpty(arrayOf("a")))
        assertFalse(RuleToolUtils.notNullOrEmpty(emptyList<String>()))
        assertTrue(RuleToolUtils.notNullOrEmpty(listOf("a")))
        assertFalse(RuleToolUtils.notNullOrEmpty(emptyMap<String, String>()))
        assertTrue(RuleToolUtils.notNullOrEmpty(mapOf("a" to 1)))
        assertTrue(RuleToolUtils.notNullOrEmpty(RuleToolUtilsTestPoint(1, 2, 3)))
    }

    @Test
    fun testAsArray() {
        assertNull(RuleToolUtils.asArray(null))
        assertArrayEquals(arrayOf(1), RuleToolUtils.asArray(1))
        assertArrayEquals(arrayOf(1, 2), RuleToolUtils.asArray(arrayOf(1, 2)))
        assertArrayEquals(arrayOf(1, 2), RuleToolUtils.asArray(listOf(1, 2)))
    }

    @Test
    fun testAsList() {
        assertNull(RuleToolUtils.asList(null))
        assertEquals(arrayListOf(1), RuleToolUtils.asList(1))
        assertEquals(arrayListOf(1, 2), RuleToolUtils.asList(arrayOf(1, 2)))
        assertEquals(arrayListOf(1, 2), RuleToolUtils.asList(setOf(1, 2)))
        assertEquals(arrayListOf(1, 2), RuleToolUtils.asList(listOf(1, 2)))
    }

    @Test
    fun testIntersect() {
        assertNull(RuleToolUtils.intersect(null, null))
        assertNull(RuleToolUtils.intersect(listOf(1, 2), null))
        assertNull(RuleToolUtils.intersect(null, arrayOf(1, 2)))
        assertArrayEquals(arrayOf(2, 3), RuleToolUtils.intersect(listOf(1, 2, 3), arrayOf(2, 3, 4)))
    }

    @Test
    fun testAnyIntersect() {
        assertFalse(RuleToolUtils.anyIntersect(null, null))
        assertFalse(RuleToolUtils.anyIntersect(listOf(1, 2), null))
        assertFalse(RuleToolUtils.anyIntersect(null, arrayOf(1, 2)))
        assertTrue(RuleToolUtils.anyIntersect(listOf(1, 2, 3), arrayOf(2, 3, 4)))
    }

    @Test
    fun testEqualOrIntersect() {
        assertTrue(RuleToolUtils.equalOrIntersect(null, null))
        assertFalse(RuleToolUtils.equalOrIntersect(listOf(1, 2), null))
        assertFalse(RuleToolUtils.equalOrIntersect(null, arrayOf(1, 2)))
        assertTrue(RuleToolUtils.equalOrIntersect(listOf(1, 2, 3), arrayOf(2, 3, 4)))
    }

    @Test
    fun testNewSet() {
        assertEquals(hashSetOf(1), RuleToolUtils.newSet(1))
        assertEquals(hashSetOf(1, 2), RuleToolUtils.newSet(1, 2))
    }

    @Test
    fun testNewList() {
        assertEquals(arrayListOf(1), RuleToolUtils.newList(1))
        assertEquals(arrayListOf(1, 2), RuleToolUtils.newList(1, 2))
    }

    @Test
    fun testNewMap() {
        assertEquals(LinkedHashMap<Any?, Any?>(), RuleToolUtils.newMap())
    }

    @Test
    fun testParseJson() {
        assertNull(RuleToolUtils.parseJson(null))
        assertNull(RuleToolUtils.parseJson(""))
        assertEquals(mapOf("a" to "b"), RuleToolUtils.parseJson("{\"a\": \"b\"}"))
    }

    @Test
    fun testToJson() {
        assertEquals(null, RuleToolUtils.toJson(null))
        assertEquals("{\"a\":1}", RuleToolUtils.toJson(mapOf("a" to 1)))
    }

    @Test
    fun testPrettyJson() {
        assertEquals(null, RuleToolUtils.prettyJson(null))
        assertEquals("{\n  \"a\": 1\n}", RuleToolUtils.prettyJson(mapOf("a" to 1)))
    }

    @Test
    fun testNullOrEmpty() {
        assertTrue(RuleToolUtils.nullOrEmpty(null))
        assertTrue(RuleToolUtils.nullOrEmpty(""))
        assertFalse(RuleToolUtils.nullOrEmpty(" "))
        assertFalse(RuleToolUtils.nullOrEmpty("123"))
    }

    @Test
    fun testNullOrBlank() {
        assertTrue(RuleToolUtils.nullOrBlank(null))
        assertTrue(RuleToolUtils.nullOrBlank(""))
        assertTrue(RuleToolUtils.nullOrBlank(" "))
        assertFalse(RuleToolUtils.nullOrBlank("123"))
    }

    @Test
    fun testNotNullOrBlank() {
        assertFalse(RuleToolUtils.notNullOrEmpty(null))
        assertFalse(RuleToolUtils.notNullOrEmpty(""))
        assertTrue(RuleToolUtils.notNullOrEmpty(" "))
        assertTrue(RuleToolUtils.notNullOrEmpty("123"))

    }

    @Test
    fun testHeadLine() {
        assertNull(RuleToolUtils.headLine(null))
        assertEquals(null, RuleToolUtils.headLine(""))
        assertEquals("str", RuleToolUtils.headLine("str"))
        assertEquals("first", RuleToolUtils.headLine("first\nsecond"))
        assertEquals("first", RuleToolUtils.headLine("\nfirst\nsecond"))
    }

    @Test
    fun testCapitalize() {
        assertNull(RuleToolUtils.capitalize(null))
        assertEquals("Abc", RuleToolUtils.capitalize("abc"))
        assertEquals("ABC", RuleToolUtils.capitalize("ABC"))
    }

    @Test
    fun testUncapitalize() {
        assertNull(RuleToolUtils.uncapitalize(null))
        assertEquals("abc", RuleToolUtils.uncapitalize("Abc"))
        assertEquals("abc", RuleToolUtils.uncapitalize("abc"))
    }

    @Test
    fun testSwapCase() {
        assertNull(RuleToolUtils.swapCase(null))
        assertEquals("ABC", RuleToolUtils.swapCase("abc"))
        assertEquals("AbC", RuleToolUtils.swapCase("aBc"))
    }

    @Test
    fun testUpperCase() {
        assertNull(RuleToolUtils.upperCase(null))
        assertEquals("ABC", RuleToolUtils.upperCase("abc"))
        assertEquals("ABC", RuleToolUtils.upperCase("aBc"))
    }

    @Test
    fun testLowerCase() {
        assertNull(RuleToolUtils.lowerCase(null))
        assertEquals("abc", RuleToolUtils.lowerCase("abc"))
        assertEquals("abc", RuleToolUtils.lowerCase("aBc"))
    }

    @Test
    fun testReverse() {
        assertNull(RuleToolUtils.reverse(null))
        assertEquals("cba", RuleToolUtils.reverse("abc"))
        assertEquals("cBa", RuleToolUtils.reverse("aBc"))
    }

    @Test
    fun testRepeat() {
        assertNull(RuleToolUtils.repeat(null, 10))
        assertEquals("", RuleToolUtils.repeat("", 10))
        assertEquals("abc", RuleToolUtils.repeat("abc", 1))
        assertEquals("abcabc", RuleToolUtils.repeat("abc", 2))
        assertEquals("abc,abc", RuleToolUtils.repeat("abc", ",", 2))
    }

    @Test
    fun testIsNumeric() {
        assertFalse(RuleToolUtils.isNumeric(null))
        assertFalse(RuleToolUtils.isNumeric(""))
        assertFalse(RuleToolUtils.isNumeric("  "))
        assertTrue(RuleToolUtils.isNumeric("123"))
        assertTrue(RuleToolUtils.isNumeric("\u0967\u0968\u0969"))
        assertFalse(RuleToolUtils.isNumeric("12 3"))
        assertFalse(RuleToolUtils.isNumeric("ab2c"))
        assertFalse(RuleToolUtils.isNumeric("12-3"))
        assertFalse(RuleToolUtils.isNumeric("12.3"))
        assertFalse(RuleToolUtils.isNumeric("-123"))
        assertFalse(RuleToolUtils.isNumeric("+123"))
    }

    @Test
    fun testIsAlpha() {
        assertFalse(RuleToolUtils.isAlpha(null))
        assertFalse(RuleToolUtils.isAlpha(""))
        assertFalse(RuleToolUtils.isAlpha("  "))
        assertTrue(RuleToolUtils.isAlpha("abc"))
        assertFalse(RuleToolUtils.isAlpha("ab2c"))
        assertFalse(RuleToolUtils.isAlpha("ab-c"))
    }

    @Test
    fun testSubstringBefore() {
        assertNull(RuleToolUtils.substringBefore(null, null))
        assertEquals("abc", RuleToolUtils.substringBefore("abc", null))
        assertNull(RuleToolUtils.substringBefore(null, "abc"))
        assertEquals("", RuleToolUtils.substringBefore("abc", "abc"))
        assertEquals("abcd", RuleToolUtils.substringBefore("abcdefg", "e"))
    }

    @Test
    fun testSubstringAfter() {
        assertNull(RuleToolUtils.substringAfter(null, null))
        assertEquals("", RuleToolUtils.substringAfter("abc", null))
        assertNull(RuleToolUtils.substringAfter(null, "abc"))
        assertEquals("", RuleToolUtils.substringAfter("abc", "abc"))
        assertEquals("fg", RuleToolUtils.substringAfter("abcdefg", "e"))
    }

    @Test
    fun testSubstringBeforeLast() {
        assertNull(RuleToolUtils.substringBeforeLast(null, null))
        assertEquals("abc", RuleToolUtils.substringBeforeLast("abc", null))
        assertNull(RuleToolUtils.substringBeforeLast(null, "abc"))
        assertEquals("", RuleToolUtils.substringBeforeLast("abc", "abc"))
        assertEquals("abcd", RuleToolUtils.substringBeforeLast("abcdefg", "e"))
        assertEquals("abcdefg", RuleToolUtils.substringBeforeLast("abcdefgefg", "e"))
    }

    @Test
    fun testSubstringAfterLast() {
        assertNull(RuleToolUtils.substringAfterLast(null, null))
        assertEquals("", RuleToolUtils.substringAfterLast("abc", null))
        assertNull(RuleToolUtils.substringAfterLast(null, "abc"))
        assertEquals("", RuleToolUtils.substringAfterLast("abc", "abc"))
        assertEquals("fg", RuleToolUtils.substringAfterLast("abcdefg", "e"))
        assertEquals("fg", RuleToolUtils.substringAfterLast("abcdefgefg", "e"))
    }

    @Test
    fun testSubstringBetween() {
        assertNull(RuleToolUtils.substringBetween(null, null))
        assertNull(RuleToolUtils.substringBetween("abccba", null))
        assertNull(RuleToolUtils.substringBetween(null, "abccba"))
        assertNull(RuleToolUtils.substringBetween(null, null, null))
        assertNull(RuleToolUtils.substringBetween("abccba", null, null))
        assertNull(RuleToolUtils.substringBetween(null, "abc", "abc"))
        assertEquals("bccb", RuleToolUtils.substringBetween("abccbad", "a"))
        assertEquals("cc", RuleToolUtils.substringBetween("abccbad", "b"))
        assertEquals("", RuleToolUtils.substringBetween("abccbad", "c"))
        assertEquals(null, RuleToolUtils.substringBetween("abccbad", "d"))
        assertEquals("ccb", RuleToolUtils.substringBetween("abccbad", "ab", "ad"))
    }

    @Test
    fun testSubstringsBetween() {
        assertNull(RuleToolUtils.substringsBetween(null, null, null))
        assertNull(RuleToolUtils.substringsBetween("abccba", null, null))
        assertNull(RuleToolUtils.substringsBetween(null, "abccba", "abccba"))
        assertArrayEquals(arrayOf("cc", "cca"), RuleToolUtils.substringsBetween("abccdabccaddbadb", "ab", "d"))
    }

    @Test
    fun testSplit() {
        assertNull(RuleToolUtils.split(null))
        assertArrayEquals(emptyArray(), RuleToolUtils.split(""))
        assertArrayEquals(arrayOf("abc"), RuleToolUtils.split("abc"))
        assertArrayEquals(arrayOf("abc", "def"), RuleToolUtils.split("abc\ndef"))
        assertArrayEquals(arrayOf("abc\nd", "ff", "d\ncba"), RuleToolUtils.split("abc\ndeffed\ncba", "e"))
    }

    @Test
    fun testToCamelCase() {
        assertNull(RuleToolUtils.toCamelCase(null, true))
        assertEquals("", RuleToolUtils.toCamelCase("", true))
        assertEquals(null, RuleToolUtils.toCamelCase(null, false))
        assertEquals("", RuleToolUtils.toCamelCase("", false, '*'))
        assertEquals("*", RuleToolUtils.toCamelCase("*", false))
        assertEquals("*", RuleToolUtils.toCamelCase("*", true))
        assertEquals("toCamelCase", RuleToolUtils.toCamelCase("To.Camel.Case", false, '.'))
        assertEquals("ToCamelCase", RuleToolUtils.toCamelCase(" to @ Camel case", true, '@'))
        assertEquals("toCamelCase", RuleToolUtils.toCamelCase(" @to @ Camel case", false, '@'))
    }

    @Test
    fun testCamel2Underline() {
        assertNull(RuleToolUtils.camel2Underline(null))
        assertEquals("", RuleToolUtils.camel2Underline(""))
        assertEquals("abcdefg", RuleToolUtils.camel2Underline("abcdefg"))
        assertEquals("ab_cde_fg", RuleToolUtils.camel2Underline("abCdeFg"))
        assertEquals("ab_cde_fg", RuleToolUtils.camel2Underline("AbCdeFg"))
    }

    @Test
    fun testRemovePrefix() {
        assertNull(RuleToolUtils.removePrefix(null, null))
        assertNull(RuleToolUtils.removePrefix("abc", null))
        assertNull(RuleToolUtils.removePrefix(null, "abc"))
        assertEquals("", RuleToolUtils.removePrefix("abc", "abc"))
        assertEquals("abc", RuleToolUtils.removePrefix("abc", "def"))
        assertEquals("def", RuleToolUtils.removePrefix("abcdef", "abc"))
    }

    @Test
    fun testRemoveSuffix() {
        assertNull(RuleToolUtils.removeSuffix(null, null))
        assertNull(RuleToolUtils.removeSuffix("abc", null))
        assertNull(RuleToolUtils.removeSuffix(null, "abc"))
        assertEquals("", RuleToolUtils.removeSuffix("abc", "abc"))
        assertEquals("abc", RuleToolUtils.removeSuffix("abc", "def"))
        assertEquals("abc", RuleToolUtils.removeSuffix("abcdef", "def"))
    }

    @Test
    fun testResolveProperty() {
        assertNull(RuleToolUtils.resolveProperty(null, null, null))
        assertEquals(
            ",\${y}",
            RuleToolUtils.resolveProperty("#{x},\${y}", "#", emptyMap<Any?, Any?>())
        )
        assertEquals(
            "111,\${y}", RuleToolUtils.resolveProperty("#{x},\${y}", "#", mapOf("x" to 111))
        )
        assertEquals(
            "111,\${y}", RuleToolUtils.resolveProperty("#{x},\${y}", "#", mapOf("x" to 111, "y" to 222))
        )
        assertEquals(
            ",",
            RuleToolUtils.resolveProperty("#{x},\${y}", "#$", emptyMap<Any?, Any?>())
        )
        assertEquals(
            "111,", RuleToolUtils.resolveProperty("#{x},\${y}", "#$", mapOf("x" to 111))
        )
        assertEquals(
            "111,222", RuleToolUtils.resolveProperty("#{x},\${y}", "#$", mapOf("x" to 111, "y" to 222))
        )
    }

    @Test
    fun testNow() {
        assertTrue(
            Pattern.compile("\\d\\d\\d\\d-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d").matcher(RuleToolUtils.now()).matches()
        )
        assertTrue(Pattern.compile("\\d\\d\\d\\d-\\d\\d-\\d\\d").matcher(RuleToolUtils.now("yyyy-MM-dd")).matches())

    }

    @Test
    fun testToday() {
        assertTrue(Pattern.compile("\\d\\d\\d\\d-\\d\\d-\\d\\d").matcher(RuleToolUtils.today()).matches())
    }

    @Test
    fun testFormat() {
        val date = DateUtils.parse("2020-01-01")
        assertEquals("2020-01-01 02:23:20", RuleToolUtils.format(date.time + 8600000, "yyyy-MM-dd HH:mm:ss"))
    }

    @Test
    fun testDebug() {
        assertEquals(
            "type:tool\n" +
                    "methods:\n" +
                    "bool anyIntersect(object, object)\n" +
                    "array<object> asArray(object)\n" +
                    "array<object> asList(object)\n" +
                    "string camel2Underline(string)\n" +
                    "string capitalize(string)\n" +
                    "void copy2Clipboard(string)\n" +
                    "string debug(object)\n" +
                    "bool equalOrIntersect(object, object)\n" +
                    "string format(kotlin.Long, string)\n" +
                    "string headLine(string)\n" +
                    "array<object> intersect(object, object)\n" +
                    "bool isAlpha(string)\n" +
                    "bool isNullOrEmpty(object)\n" +
                    "bool isNumeric(string)\n" +
                    "string lowerCase(string)\n" +
                    "array<object> newList(object...)\n" +
                    "map<object, object> newMap()\n" +
                    "array<object> newSet(object...)\n" +
                    "bool notNullOrBlank(string)\n" +
                    "bool notNullOrEmpty(object)\n" +
                    "bool notNullOrEmpty(string)\n" +
                    "string now()\n" +
                    "string now(string)\n" +
                    "bool nullOrBlank(string)\n" +
                    "bool nullOrEmpty(string)\n" +
                    "object parseJson(string)\n" +
                    "string prettyJson(object)\n" +
                    "string removePrefix(string, string)\n" +
                    "string removeSuffix(string, string)\n" +
                    "string repeat(string, int)\n" +
                    "string repeat(string, string, int)\n" +
                    "string resolveProperty(string, object, map<object, object>)\n" +
                    "string reverse(string)\n" +
                    "array<string> split(string)\n" +
                    "array<string> split(string, string)\n" +
                    "string substringAfter(string, string)\n" +
                    "string substringAfterLast(string, string)\n" +
                    "string substringBefore(string, string)\n" +
                    "string substringBeforeLast(string, string)\n" +
                    "string substringBetween(string, string)\n" +
                    "string substringBetween(string, string, string)\n" +
                    "array<string> substringsBetween(string, string, string)\n" +
                    "string swapCase(string)\n" +
                    "string toCamelCase(string, bool, array<char>)\n" +
                    "string toJson(object)\n" +
                    "string today()\n" +
                    "string traversal(object)\n" +
                    "string uncapitalize(string)\n" +
                    "string upperCase(string)\n", RuleToolUtils.debug(RuleToolUtils)
        )
    }

    @Test
    fun testTraversal() {
        assertEquals("null", RuleToolUtils.traversal(null))
        assertEquals("unable traversal abc", RuleToolUtils.traversal("abc"))
    }

    @Test
    fun testCopy2Clipboard() {
        assertDoesNotThrow {
            try {
                RuleToolUtils.copy2Clipboard("abc")
            } catch (e: HeadlessException) {
                //ignore HeadlessException
            }
        }
    }
}

private class RuleToolUtilsTestPoint(
    var x: Int,
    private var y: Int,
    protected var z: Int
)