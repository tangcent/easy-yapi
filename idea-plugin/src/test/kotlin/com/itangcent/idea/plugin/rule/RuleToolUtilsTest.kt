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

    private val ruleToolUtils = RuleToolUtils()

    @Test
    fun testIsNullOrEmpty() {
        assertTrue(ruleToolUtils.isNullOrEmpty(null))
        assertTrue(ruleToolUtils.isNullOrEmpty(""))
        assertFalse(ruleToolUtils.isNullOrEmpty(" "))
        assertFalse(ruleToolUtils.isNullOrEmpty("a"))
        assertTrue(ruleToolUtils.isNullOrEmpty(emptyArray<String>()))
        assertFalse(ruleToolUtils.isNullOrEmpty(arrayOf("a")))
        assertTrue(ruleToolUtils.isNullOrEmpty(emptyList<String>()))
        assertFalse(ruleToolUtils.isNullOrEmpty(listOf("a")))
        assertTrue(ruleToolUtils.isNullOrEmpty(emptyMap<String, String>()))
        assertFalse(ruleToolUtils.isNullOrEmpty(mapOf("a" to 1)))
        assertFalse(ruleToolUtils.isNullOrEmpty(RuleToolUtilsTestPoint(1, 2, 3)))
    }

    @Test
    fun testNotNullOrEmpty() {
        assertFalse(ruleToolUtils.notNullOrEmpty(null))
        assertFalse(ruleToolUtils.notNullOrEmpty(""))
        assertTrue(ruleToolUtils.notNullOrEmpty(" "))
        assertTrue(ruleToolUtils.notNullOrEmpty("a"))
        assertFalse(ruleToolUtils.notNullOrEmpty(emptyArray<String>()))
        assertTrue(ruleToolUtils.notNullOrEmpty(arrayOf("a")))
        assertFalse(ruleToolUtils.notNullOrEmpty(emptyList<String>()))
        assertTrue(ruleToolUtils.notNullOrEmpty(listOf("a")))
        assertFalse(ruleToolUtils.notNullOrEmpty(emptyMap<String, String>()))
        assertTrue(ruleToolUtils.notNullOrEmpty(mapOf("a" to 1)))
        assertTrue(ruleToolUtils.notNullOrEmpty(RuleToolUtilsTestPoint(1, 2, 3)))
    }

    @Test
    fun testAsArray() {
        assertNull(ruleToolUtils.asArray(null))
        assertArrayEquals(arrayOf(1), ruleToolUtils.asArray(1))
        assertArrayEquals(arrayOf(1, 2), ruleToolUtils.asArray(arrayOf(1, 2)))
        assertArrayEquals(arrayOf(1, 2), ruleToolUtils.asArray(listOf(1, 2)))
    }

    @Test
    fun testAsList() {
        assertNull(ruleToolUtils.asList(null))
        assertEquals(arrayListOf(1), ruleToolUtils.asList(1))
        assertEquals(arrayListOf(1, 2), ruleToolUtils.asList(arrayOf(1, 2)))
        assertEquals(arrayListOf(1, 2), ruleToolUtils.asList(setOf(1, 2)))
        assertEquals(arrayListOf(1, 2), ruleToolUtils.asList(listOf(1, 2)))
    }

    @Test
    fun testIntersect() {
        assertNull(ruleToolUtils.intersect(null, null))
        assertNull(ruleToolUtils.intersect(listOf(1, 2), null))
        assertNull(ruleToolUtils.intersect(null, arrayOf(1, 2)))
        assertArrayEquals(arrayOf(2, 3), ruleToolUtils.intersect(listOf(1, 2, 3), arrayOf(2, 3, 4)))
    }

    @Test
    fun testAnyIntersect() {
        assertFalse(ruleToolUtils.anyIntersect(null, null))
        assertFalse(ruleToolUtils.anyIntersect(listOf(1, 2), null))
        assertFalse(ruleToolUtils.anyIntersect(null, arrayOf(1, 2)))
        assertTrue(ruleToolUtils.anyIntersect(listOf(1, 2, 3), arrayOf(2, 3, 4)))
    }

    @Test
    fun testEqualOrIntersect() {
        assertTrue(ruleToolUtils.equalOrIntersect(null, null))
        assertFalse(ruleToolUtils.equalOrIntersect(listOf(1, 2), null))
        assertFalse(ruleToolUtils.equalOrIntersect(null, arrayOf(1, 2)))
        assertTrue(ruleToolUtils.equalOrIntersect(listOf(1, 2, 3), arrayOf(2, 3, 4)))
    }

    @Test
    fun testNewSet() {
        assertEquals(hashSetOf(1), ruleToolUtils.newSet(1))
        assertEquals(hashSetOf(1, 2), ruleToolUtils.newSet(1, 2))
    }

    @Test
    fun testNewList() {
        assertEquals(arrayListOf(1), ruleToolUtils.newList(1))
        assertEquals(arrayListOf(1, 2), ruleToolUtils.newList(1, 2))
    }

    @Test
    fun testNewMap() {
        assertEquals(LinkedHashMap<Any?, Any?>(), ruleToolUtils.newMap())
    }

    @Test
    fun testParseJson() {
        assertNull(ruleToolUtils.parseJson(null))
        assertNull(ruleToolUtils.parseJson(""))
        assertEquals(mapOf("a" to "b"), ruleToolUtils.parseJson("{\"a\": \"b\"}"))
    }

    @Test
    fun testToJson() {
        assertEquals(null, ruleToolUtils.toJson(null))
        assertEquals("{\"a\":1}", ruleToolUtils.toJson(mapOf("a" to 1)))
    }

    @Test
    fun testPrettyJson() {
        assertEquals(null, ruleToolUtils.prettyJson(null))
        assertEquals("{\n  \"a\": 1\n}", ruleToolUtils.prettyJson(mapOf("a" to 1)))
    }

    @Test
    fun testNullOrEmpty() {
        assertTrue(ruleToolUtils.nullOrEmpty(null))
        assertTrue(ruleToolUtils.nullOrEmpty(""))
        assertFalse(ruleToolUtils.nullOrEmpty(" "))
        assertFalse(ruleToolUtils.nullOrEmpty("123"))
    }

    @Test
    fun testNullOrBlank() {
        assertTrue(ruleToolUtils.nullOrBlank(null))
        assertTrue(ruleToolUtils.nullOrBlank(""))
        assertTrue(ruleToolUtils.nullOrBlank(" "))
        assertFalse(ruleToolUtils.nullOrBlank("123"))
    }

    @Test
    fun testNotNullOrBlank() {
        assertFalse(ruleToolUtils.notNullOrEmpty(null))
        assertFalse(ruleToolUtils.notNullOrEmpty(""))
        assertTrue(ruleToolUtils.notNullOrEmpty(" "))
        assertTrue(ruleToolUtils.notNullOrEmpty("123"))

    }

    @Test
    fun testHeadLine() {
        assertNull(ruleToolUtils.headLine(null))
        assertEquals(null, ruleToolUtils.headLine(""))
        assertEquals("str", ruleToolUtils.headLine("str"))
        assertEquals("first", ruleToolUtils.headLine("first\nsecond"))
        assertEquals("first", ruleToolUtils.headLine("\nfirst\nsecond"))
    }

    @Test
    fun testCapitalize() {
        assertNull(ruleToolUtils.capitalize(null))
        assertEquals("Abc", ruleToolUtils.capitalize("abc"))
        assertEquals("ABC", ruleToolUtils.capitalize("ABC"))
    }

    @Test
    fun testUncapitalize() {
        assertNull(ruleToolUtils.uncapitalize(null))
        assertEquals("abc", ruleToolUtils.uncapitalize("Abc"))
        assertEquals("abc", ruleToolUtils.uncapitalize("abc"))
    }

    @Test
    fun testSwapCase() {
        assertNull(ruleToolUtils.swapCase(null))
        assertEquals("ABC", ruleToolUtils.swapCase("abc"))
        assertEquals("AbC", ruleToolUtils.swapCase("aBc"))
    }

    @Test
    fun testUpperCase() {
        assertNull(ruleToolUtils.upperCase(null))
        assertEquals("ABC", ruleToolUtils.upperCase("abc"))
        assertEquals("ABC", ruleToolUtils.upperCase("aBc"))
    }

    @Test
    fun testLowerCase() {
        assertNull(ruleToolUtils.lowerCase(null))
        assertEquals("abc", ruleToolUtils.lowerCase("abc"))
        assertEquals("abc", ruleToolUtils.lowerCase("aBc"))
    }

    @Test
    fun testReverse() {
        assertNull(ruleToolUtils.reverse(null))
        assertEquals("cba", ruleToolUtils.reverse("abc"))
        assertEquals("cBa", ruleToolUtils.reverse("aBc"))
    }

    @Test
    fun testRepeat() {
        assertNull(ruleToolUtils.repeat(null, 10))
        assertEquals("", ruleToolUtils.repeat("", 10))
        assertEquals("abc", ruleToolUtils.repeat("abc", 1))
        assertEquals("abcabc", ruleToolUtils.repeat("abc", 2))
        assertEquals("abc,abc", ruleToolUtils.repeat("abc", ",", 2))
    }

    @Test
    fun testIsNumeric() {
        assertFalse(ruleToolUtils.isNumeric(null))
        assertFalse(ruleToolUtils.isNumeric(""))
        assertFalse(ruleToolUtils.isNumeric("  "))
        assertTrue(ruleToolUtils.isNumeric("123"))
        assertTrue(ruleToolUtils.isNumeric("\u0967\u0968\u0969"))
        assertFalse(ruleToolUtils.isNumeric("12 3"))
        assertFalse(ruleToolUtils.isNumeric("ab2c"))
        assertFalse(ruleToolUtils.isNumeric("12-3"))
        assertFalse(ruleToolUtils.isNumeric("12.3"))
        assertFalse(ruleToolUtils.isNumeric("-123"))
        assertFalse(ruleToolUtils.isNumeric("+123"))
    }

    @Test
    fun testIsAlpha() {
        assertFalse(ruleToolUtils.isAlpha(null))
        assertFalse(ruleToolUtils.isAlpha(""))
        assertFalse(ruleToolUtils.isAlpha("  "))
        assertTrue(ruleToolUtils.isAlpha("abc"))
        assertFalse(ruleToolUtils.isAlpha("ab2c"))
        assertFalse(ruleToolUtils.isAlpha("ab-c"))
    }

    @Test
    fun testSubstringBefore() {
        assertNull(ruleToolUtils.substringBefore(null, null))
        assertEquals("abc", ruleToolUtils.substringBefore("abc", null))
        assertNull(ruleToolUtils.substringBefore(null, "abc"))
        assertEquals("", ruleToolUtils.substringBefore("abc", "abc"))
        assertEquals("abcd", ruleToolUtils.substringBefore("abcdefg", "e"))
    }

    @Test
    fun testSubstringAfter() {
        assertNull(ruleToolUtils.substringAfter(null, null))
        assertEquals("", ruleToolUtils.substringAfter("abc", null))
        assertNull(ruleToolUtils.substringAfter(null, "abc"))
        assertEquals("", ruleToolUtils.substringAfter("abc", "abc"))
        assertEquals("fg", ruleToolUtils.substringAfter("abcdefg", "e"))
    }

    @Test
    fun testSubstringBeforeLast() {
        assertNull(ruleToolUtils.substringBeforeLast(null, null))
        assertEquals("abc", ruleToolUtils.substringBeforeLast("abc", null))
        assertNull(ruleToolUtils.substringBeforeLast(null, "abc"))
        assertEquals("", ruleToolUtils.substringBeforeLast("abc", "abc"))
        assertEquals("abcd", ruleToolUtils.substringBeforeLast("abcdefg", "e"))
        assertEquals("abcdefg", ruleToolUtils.substringBeforeLast("abcdefgefg", "e"))
    }

    @Test
    fun testSubstringAfterLast() {
        assertNull(ruleToolUtils.substringAfterLast(null, null))
        assertEquals("", ruleToolUtils.substringAfterLast("abc", null))
        assertNull(ruleToolUtils.substringAfterLast(null, "abc"))
        assertEquals("", ruleToolUtils.substringAfterLast("abc", "abc"))
        assertEquals("fg", ruleToolUtils.substringAfterLast("abcdefg", "e"))
        assertEquals("fg", ruleToolUtils.substringAfterLast("abcdefgefg", "e"))
    }

    @Test
    fun testSubstringBetween() {
        assertNull(ruleToolUtils.substringBetween(null, null))
        assertNull(ruleToolUtils.substringBetween("abccba", null))
        assertNull(ruleToolUtils.substringBetween(null, "abccba"))
        assertNull(ruleToolUtils.substringBetween(null, null, null))
        assertNull(ruleToolUtils.substringBetween("abccba", null, null))
        assertNull(ruleToolUtils.substringBetween(null, "abc", "abc"))
        assertEquals("bccb", ruleToolUtils.substringBetween("abccbad", "a"))
        assertEquals("cc", ruleToolUtils.substringBetween("abccbad", "b"))
        assertEquals("", ruleToolUtils.substringBetween("abccbad", "c"))
        assertEquals(null, ruleToolUtils.substringBetween("abccbad", "d"))
        assertEquals("ccb", ruleToolUtils.substringBetween("abccbad", "ab", "ad"))
    }

    @Test
    fun testSubstringsBetween() {
        assertNull(ruleToolUtils.substringsBetween(null, null, null))
        assertNull(ruleToolUtils.substringsBetween("abccba", null, null))
        assertNull(ruleToolUtils.substringsBetween(null, "abccba", "abccba"))
        assertArrayEquals(arrayOf("cc", "cca"), ruleToolUtils.substringsBetween("abccdabccaddbadb", "ab", "d"))
    }

    @Test
    fun testSplit() {
        assertNull(ruleToolUtils.split(null))
        assertArrayEquals(emptyArray(), ruleToolUtils.split(""))
        assertArrayEquals(arrayOf("abc"), ruleToolUtils.split("abc"))
        assertArrayEquals(arrayOf("abc", "def"), ruleToolUtils.split("abc\ndef"))
        assertArrayEquals(arrayOf("abc\nd", "ff", "d\ncba"), ruleToolUtils.split("abc\ndeffed\ncba", "e"))
    }

    @Test
    fun testToCamelCase() {
        assertNull(ruleToolUtils.toCamelCase(null, true))
        assertEquals("", ruleToolUtils.toCamelCase("", true))
        assertEquals(null, ruleToolUtils.toCamelCase(null, false))
        assertEquals("", ruleToolUtils.toCamelCase("", false, '*'))
        assertEquals("*", ruleToolUtils.toCamelCase("*", false))
        assertEquals("*", ruleToolUtils.toCamelCase("*", true))
        assertEquals("toCamelCase", ruleToolUtils.toCamelCase("To.Camel.Case", false, '.'))
        assertEquals("ToCamelCase", ruleToolUtils.toCamelCase(" to @ Camel case", true, '@'))
        assertEquals("toCamelCase", ruleToolUtils.toCamelCase(" @to @ Camel case", false, '@'))
    }

    @Test
    fun testCamel2Underline() {
        assertNull(ruleToolUtils.camel2Underline(null))
        assertEquals("", ruleToolUtils.camel2Underline(""))
        assertEquals("abcdefg", ruleToolUtils.camel2Underline("abcdefg"))
        assertEquals("ab_cde_fg", ruleToolUtils.camel2Underline("abCdeFg"))
        assertEquals("ab_cde_fg", ruleToolUtils.camel2Underline("AbCdeFg"))
    }

    @Test
    fun testRemovePrefix() {
        assertNull(ruleToolUtils.removePrefix(null, null))
        assertNull(ruleToolUtils.removePrefix("abc", null))
        assertNull(ruleToolUtils.removePrefix(null, "abc"))
        assertEquals("", ruleToolUtils.removePrefix("abc", "abc"))
        assertEquals("abc", ruleToolUtils.removePrefix("abc", "def"))
        assertEquals("def", ruleToolUtils.removePrefix("abcdef", "abc"))
    }

    @Test
    fun testRemoveSuffix() {
        assertNull(ruleToolUtils.removeSuffix(null, null))
        assertNull(ruleToolUtils.removeSuffix("abc", null))
        assertNull(ruleToolUtils.removeSuffix(null, "abc"))
        assertEquals("", ruleToolUtils.removeSuffix("abc", "abc"))
        assertEquals("abc", ruleToolUtils.removeSuffix("abc", "def"))
        assertEquals("abc", ruleToolUtils.removeSuffix("abcdef", "def"))
    }

    @Test
    fun testNow() {
        assertTrue(Pattern.compile("\\d\\d\\d\\d-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d").matcher(ruleToolUtils.now()).matches())
        assertTrue(Pattern.compile("\\d\\d\\d\\d-\\d\\d-\\d\\d").matcher(ruleToolUtils.now("yyyy-MM-dd")).matches())

    }

    @Test
    fun testToday() {
        assertTrue(Pattern.compile("\\d\\d\\d\\d-\\d\\d-\\d\\d").matcher(ruleToolUtils.today()).matches())
    }

    @Test
    fun testFormat() {
        val date = DateUtils.parse("2020-01-01")
        assertEquals("2020-01-01 02:23:20", ruleToolUtils.format(date.time + 8600000, "yyyy-MM-dd HH:mm:ss"))
    }

    @Test
    fun testDebug() {
        assertEquals("type:tool$n" +
                "methods:$n" +
                "bool anyIntersect(object, object)$n" +
                "array<object> asArray(object)$n" +
                "array<object> asList(object)$n" +
                "string camel2Underline(string)$n" +
                "string capitalize(string)$n" +
                "void copy2Clipboard(string)$n" +
                "string debug(object)$n" +
                "bool equalOrIntersect(object, object)$n" +
                "string format(kotlin.Long, string)$n" +
                "string headLine(string)$n" +
                "array<object> intersect(object, object)$n" +
                "bool isAlpha(string)$n" +
                "bool isNullOrEmpty(object)$n" +
                "bool isNumeric(string)$n" +
                "string lowerCase(string)$n" +
                "array<object> newList(object...)$n" +
                "map<object, object> newMap()$n" +
                "array<object> newSet(object...)$n" +
                "bool notNullOrBlank(string)$n" +
                "bool notNullOrEmpty(object)$n" +
                "bool notNullOrEmpty(string)$n" +
                "string now()$n" +
                "string now(string)$n" +
                "bool nullOrBlank(string)$n" +
                "bool nullOrEmpty(string)$n" +
                "object parseJson(string)$n" +
                "string prettyJson(object)$n" +
                "string removePrefix(string, string)$n" +
                "string removeSuffix(string, string)$n" +
                "string repeat(string, int)$n" +
                "string repeat(string, string, int)$n" +
                "string reverse(string)$n" +
                "array<string> split(string)$n" +
                "array<string> split(string, string)$n" +
                "string substringAfter(string, string)$n" +
                "string substringAfterLast(string, string)$n" +
                "string substringBefore(string, string)$n" +
                "string substringBeforeLast(string, string)$n" +
                "string substringBetween(string, string)$n" +
                "string substringBetween(string, string, string)$n" +
                "array<string> substringsBetween(string, string, string)$n" +
                "string swapCase(string)$n" +
                "string toCamelCase(string, bool, array<char>)$n" +
                "string toJson(object)$n" +
                "string today()$n" +
                "string traversal(object)$n" +
                "string uncapitalize(string)$n" +
                "string upperCase(string)$n", ruleToolUtils.debug(ruleToolUtils))
    }

    @Test
    fun testTraversal() {
        assertEquals("null", ruleToolUtils.traversal(null))
        assertEquals("unable traversal abc", ruleToolUtils.traversal("abc"))
    }

    @Test
    fun testCopy2Clipboard() {
        assertDoesNotThrow {
            try {
                ruleToolUtils.copy2Clipboard("abc")
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

val n = System.getProperty("line.separator")