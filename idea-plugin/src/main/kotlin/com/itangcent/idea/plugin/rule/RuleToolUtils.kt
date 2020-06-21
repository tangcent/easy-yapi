package com.itangcent.idea.plugin.rule

import com.itangcent.annotation.script.ScriptIgnore
import com.itangcent.annotation.script.ScriptReturn
import com.itangcent.annotation.script.ScriptTypeName
import com.itangcent.annotation.script.ScriptUnIgnore
import com.itangcent.common.kit.headLine
import com.itangcent.common.utils.GsonUtils
import com.itangcent.common.utils.KV
import com.itangcent.common.utils.notNullOrBlank
import com.itangcent.common.utils.notNullOrEmpty
import com.itangcent.intellij.util.ToolUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.time.DateFormatUtils
import java.util.*
import java.util.regex.Pattern
import kotlin.collections.ArrayList
import kotlin.collections.HashSet
import kotlin.reflect.*
import kotlin.reflect.full.allSuperclasses
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions

/**
 * <p>Util operations for rule.</p>
 * For rules only.
 * @see StandardJdkRuleParser
 */
@ScriptTypeName("tool")
class RuleToolUtils {

    //region collections

    fun newSet(vararg items: Any): Set<*> {
        return HashSet(Arrays.asList(*items))
    }

    fun newList(vararg items: Any): List<*> {
        return ArrayList(Arrays.asList(*items))
    }

    fun newMap(): Map<*, *> {
        return LinkedHashMap<Any, Any>()
    }

    //endregion

    //region json

    fun parseJson(json: String?): Any? {
        if (json.isNullOrEmpty()) {
            return null
        }
        return GsonUtils.fromJson(json, Any::class)
    }

    fun toJson(obj: Any?): String? {
        if (obj == null) {
            return null
        }
        return GsonUtils.toJson(obj)
    }

    fun prettyJson(obj: Any?): String? {
        if (obj == null) {
            return null
        }
        return GsonUtils.prettyJson(obj)
    }

    //endregion

    //region string

    fun nullOrEmpty(str: String?): Boolean {
        return str.isNullOrEmpty()
    }

    fun nullOrBlank(str: String?): Boolean {
        return str.isNullOrBlank()
    }

    fun notNullOrEmpty(str: String?): Boolean {
        return str.notNullOrEmpty()
    }

    fun notNullOrBlank(str: String?): Boolean {
        return str.notNullOrBlank()
    }

    /**
     * <p>find the first line in a string</p>
     *
     * <pre>
     * tool.headLine(null)  = null
     * tool.headLine("")    = null
     * tool.headLine("cat") = "cat"
     * tool.headLine("cat\r\n") = "cat"
     * tool.headLine("cat\r\ntest") = "cat"
     * tool.headLine("cat\ntest") = "cat"
     * </pre>
     *
     * @param str the String to find headLine
     * @return the headLine String, {@code null} if null String input
     */
    fun headLine(str: String?): String? {
        return str?.headLine()
    }

    /**
     * <p>Capitalizes a String changing the first character to title case as
     * per {@link Character#toTitleCase(int)}. No other characters are changed.</p>
     *
     * <p>For a word based algorithm, see {@link org.apache.commons.lang3.text.WordUtils#capitalize(String)}.
     * A {@code null} input String returns {@code null}.</p>
     *
     * <pre>
     * tool.capitalize(null)  = null
     * tool.capitalize("")    = ""
     * tool.capitalize("cat") = "Cat"
     * tool.capitalize("cAt") = "CAt"
     * tool.capitalize("'cat'") = "'cat'"
     * </pre>
     *
     * @param str the String to capitalize, may be null
     * @return the capitalized String, {@code null} if null String input
     * @see org.apache.commons.lang3.text.WordUtils#capitalize(String)
     * @see #uncapitalize(String)
     * @since 2.0
     */
    fun capitalize(str: String?): String {
        return StringUtils.capitalize(str)
    }

    /**
     * <p>Uncapitalizes a String, changing the first character to lower case as
     * per {@link Character#toLowerCase(int)}. No other characters are changed.</p>
     *
     * <p>For a word based algorithm, see {@link org.apache.commons.lang3.text.WordUtils#uncapitalize(String)}.
     * A {@code null} input String returns {@code null}.</p>
     *
     * <pre>
     * tool.uncapitalize(null)  = null
     * tool.uncapitalize("")    = ""
     * tool.uncapitalize("cat") = "cat"
     * tool.uncapitalize("Cat") = "cat"
     * tool.uncapitalize("CAT") = "cAT"
     * </pre>
     *
     * @param str the String to uncapitalize, may be null
     * @return the uncapitalized String, {@code null} if null String input
     * @see org.apache.commons.lang3.text.WordUtils#uncapitalize(String)
     * @see #capitalize(String)
     * @since 2.0
     */
    fun uncapitalize(str: String?): String? {
        return StringUtils.uncapitalize(str)
    }

    /**
     * <p>Swaps the case of a String changing upper and title case to
     * lower case, and lower case to upper case.</p>
     *
     * <ul>
     *  <li>Upper case character converts to Lower case</li>
     *  <li>Title case character converts to Lower case</li>
     *  <li>Lower case character converts to Upper case</li>
     * </ul>
     *
     * <p>For a word based algorithm, see {@link org.apache.commons.lang3.text.WordUtils#swapCase(String)}.
     * A {@code null} input String returns {@code null}.</p>
     *
     * <pre>
     * tool.swapCase(null)                 = null
     * tool.swapCase("")                   = ""
     * tool.swapCase("The dog has a BONE") = "tHE DOG HAS A bone"
     * </pre>
     *
     * <p>NOTE: This method changed in Lang version 2.0.
     * It no longer performs a word based algorithm.
     * If you only use ASCII, you will notice no change.
     * That functionality is available in org.apache.commons.lang3.text.WordUtils.</p>
     *
     * @param str  the String to swap case, may be null
     * @return the changed String, {@code null} if null String input
     */
    fun swapCase(str: String?): String? {
        return StringUtils.swapCase(str)
    }

    /**
     * <p>Converts a String to upper case as per {@link String#toUpperCase()}.</p>
     *
     * <p>A {@code null} input String returns {@code null}.</p>
     *
     * <pre>
     * tool.upperCase(null)  = null
     * tool.upperCase("")    = ""
     * tool.upperCase("aBc") = "ABC"
     * </pre>
     *
     * <p><strong>Note:</strong> As described in the documentation for {@link String#toUpperCase()},
     * the result of this method is affected by the current locale.
     * For platform-independent case transformations, the method {@link #lowerCase(String, Locale)}
     * should be used with a specific locale (e.g. {@link Locale#ENGLISH}).</p>
     *
     * @param str  the String to upper case, may be null
     * @return the upper cased String, {@code null} if null String input
     */
    fun upperCase(str: String?): String? {
        return StringUtils.upperCase(str)
    }

    /**
     * <p>Converts a String to lower case as per {@link String#toLowerCase()}.</p>
     *
     * <p>A {@code null} input String returns {@code null}.</p>
     *
     * <pre>
     * tool.lowerCase(null)  = null
     * tool.lowerCase("")    = ""
     * tool.lowerCase("aBc") = "abc"
     * </pre>
     *
     * <p><strong>Note:</strong> As described in the documentation for {@link String#toLowerCase()},
     * the result of this method is affected by the current locale.
     * For platform-independent case transformations, the method {@link #lowerCase(String, Locale)}
     * should be used with a specific locale (e.g. {@link Locale#ENGLISH}).</p>
     *
     * @param str  the String to lower case, may be null
     * @return the lower cased String, {@code null} if null String input
     */
    fun lowerCase(str: String?): String? {
        return StringUtils.lowerCase(str)
    }

    /**
     * <p>Reverses a String as per {@link StringBuilder#reverse()}.</p>
     *
     * <p>A {@code null} String returns {@code null}.</p>
     *
     * <pre>
     * tool.reverse(null)  = null
     * tool.reverse("")    = ""
     * tool.reverse("bat") = "tab"
     * </pre>
     *
     * @param str  the String to reverse, may be null
     * @return the reversed String, {@code null} if null String input
     */
    fun reverse(str: String?): String? {
        return StringUtils.reverse(str)
    }

    /**
     * <p>Repeat a String {@code repeat} times to form a
     * new String.</p>
     *
     * <pre>
     * tool.repeat(null, 2) = null
     * tool.repeat("", 0)   = ""
     * tool.repeat("", 2)   = ""
     * tool.repeat("a", 3)  = "aaa"
     * tool.repeat("ab", 2) = "abab"
     * tool.repeat("a", -2) = ""
     * </pre>
     *
     * @param str  the String to repeat, may be null
     * @param repeat  number of times to repeat str, negative treated as zero
     * @return a new String consisting of the original String repeated,
     *  {@code null} if null String input
     */
    fun repeat(str: String?, repeat: Int): String? {
        return StringUtils.repeat(str, repeat)
    }

    /**
     * <p>Repeat a String {@code repeat} times to form a
     * new String, with a String separator injected each time. </p>
     *
     * <pre>
     * tool.repeat(null, null, 2) = null
     * tool.repeat(null, "x", 2)  = null
     * tool.repeat("", null, 0)   = ""
     * tool.repeat("", "", 2)     = ""
     * tool.repeat("", "x", 3)    = "xxx"
     * tool.repeat("?", ", ", 3)  = "?, ?, ?"
     * </pre>
     *
     * @param str        the String to repeat, may be null
     * @param separator  the String to inject, may be null
     * @param repeat     number of times to repeat str, negative treated as zero
     * @return a new String consisting of the original String repeated,
     *  {@code null} if null String input
     * @since 2.5
     */
    fun repeat(str: String?, separator: String, repeat: Int): String? {
        return StringUtils.repeat(str, separator, repeat)
    }

    /**
     * <p>Checks if the CharSequence contains only Unicode digits.
     * A decimal point is not a Unicode digit and returns false.</p>
     *
     * <p>{@code null} will return {@code false}.
     * An empty CharSequence (length()=0) will return {@code false}.</p>
     *
     * <p>Note that the method does not allow for a leading sign, either positive or negative.
     * Also, if a String passes the numeric test, it may still generate a NumberFormatException
     * when parsed by Integer.parseInt or Long.parseLong, e.g. if the value is outside the range
     * for int or long respectively.</p>
     *
     * <pre>
     * tool.isNumeric(null)   = false
     * tool.isNumeric("")     = false
     * tool.isNumeric("  ")   = false
     * tool.isNumeric("123")  = true
     * tool.isNumeric("\u0967\u0968\u0969")  = true
     * tool.isNumeric("12 3") = false
     * tool.isNumeric("ab2c") = false
     * tool.isNumeric("12-3") = false
     * tool.isNumeric("12.3") = false
     * tool.isNumeric("-123") = false
     * tool.isNumeric("+123") = false
     * </pre>
     *
     * @param str  the CharSequence to check, may be null
     * @return {@code true} if only contains digits, and is non-null
     * @since 3.0 Changed signature from isNumeric(String) to isNumeric(CharSequence)
     * @since 3.0 Changed "" to return false and not true
     */
    fun isNumeric(str: String?): Boolean {
        return StringUtils.isNumeric(str)
    }

    /**
     * <p>Checks if the CharSequence contains only Unicode letters.</p>
     *
     * <p>{@code null} will return {@code false}.
     * An empty CharSequence (length()=0) will return {@code false}.</p>
     *
     * <pre>
     * tool.isAlpha(null)   = false
     * tool.isAlpha("")     = false
     * tool.isAlpha("  ")   = false
     * tool.isAlpha("abc")  = true
     * tool.isAlpha("ab2c") = false
     * tool.isAlpha("ab-c") = false
     * </pre>
     *
     * @param cs  the CharSequence to check, may be null
     * @return {@code true} if only contains letters, and is non-null
     * @since 3.0 Changed signature from isAlpha(String) to isAlpha(CharSequence)
     * @since 3.0 Changed "" to return false and not true
     */
    fun isAlpha(str: String?): Boolean {
        return StringUtils.isAlpha(str)
    }

    /**
     * <p>Gets the substring before the first occurrence of a separator.
     * The separator is not returned.</p>
     *
     * <p>A {@code null} string input will return {@code null}.
     * An empty ("") string input will return the empty string.
     * A {@code null} separator will return the input string.</p>
     *
     * <p>If nothing is found, the string input is returned.</p>
     *
     * <pre>
     * tool.substringBefore(null, *)      = null
     * tool.substringBefore("", *)        = ""
     * tool.substringBefore("abc", "a")   = ""
     * tool.substringBefore("abcba", "b") = "a"
     * tool.substringBefore("abc", "c")   = "ab"
     * tool.substringBefore("abc", "d")   = "abc"
     * tool.substringBefore("abc", "")    = ""
     * tool.substringBefore("abc", null)  = "abc"
     * </pre>
     *
     * @param str  the String to get a substring from, may be null
     * @param separator  the String to search for, may be null
     * @return the substring before the first occurrence of the separator,
     *  {@code null} if null String input
     * @since 2.0
     */
    fun substringBefore(str: String?, separator: String): String {
        return StringUtils.substringBefore(str, separator)
    }

    /**
     * <p>Gets the substring after the first occurrence of a separator.
     * The separator is not returned.</p>
     *
     * <p>A {@code null} string input will return {@code null}.
     * An empty ("") string input will return the empty string.
     * A {@code null} separator will return the empty string if the
     * input string is not {@code null}.</p>
     *
     * <p>If nothing is found, the empty string is returned.</p>
     *
     * <pre>
     * tool.substringAfter(null, *)      = null
     * tool.substringAfter("", *)        = ""
     * tool.substringAfter(*, null)      = ""
     * tool.substringAfter("abc", "a")   = "bc"
     * tool.substringAfter("abcba", "b") = "cba"
     * tool.substringAfter("abc", "c")   = ""
     * tool.substringAfter("abc", "d")   = ""
     * tool.substringAfter("abc", "")    = "abc"
     * </pre>
     *
     * @param str  the String to get a substring from, may be null
     * @param separator  the String to search for, may be null
     * @return the substring after the first occurrence of the separator,
     *  {@code null} if null String input
     * @since 2.0
     */
    fun substringAfter(str: String?, separator: String): String {
        return StringUtils.substringAfter(str, separator)
    }

    /**
     * <p>Gets the substring before the last occurrence of a separator.
     * The separator is not returned.</p>
     *
     * <p>A {@code null} string input will return {@code null}.
     * An empty ("") string input will return the empty string.
     * An empty or {@code null} separator will return the input string.</p>
     *
     * <p>If nothing is found, the string input is returned.</p>
     *
     * <pre>
     * tool.substringBeforeLast(null, *)      = null
     * tool.substringBeforeLast("", *)        = ""
     * tool.substringBeforeLast("abcba", "b") = "abc"
     * tool.substringBeforeLast("abc", "c")   = "ab"
     * tool.substringBeforeLast("a", "a")     = ""
     * tool.substringBeforeLast("a", "z")     = "a"
     * tool.substringBeforeLast("a", null)    = "a"
     * tool.substringBeforeLast("a", "")      = "a"
     * </pre>
     *
     * @param str  the String to get a substring from, may be null
     * @param separator  the String to search for, may be null
     * @return the substring before the last occurrence of the separator,
     *  {@code null} if null String input
     * @since 2.0
     */
    fun substringBeforeLast(str: String?, separator: String): String {
        return StringUtils.substringBeforeLast(str, separator)
    }

    /**
     * <p>Gets the substring after the last occurrence of a separator.
     * The separator is not returned.</p>
     *
     * <p>A {@code null} string input will return {@code null}.
     * An empty ("") string input will return the empty string.
     * An empty or {@code null} separator will return the empty string if
     * the input string is not {@code null}.</p>
     *
     * <p>If nothing is found, the empty string is returned.</p>
     *
     * <pre>
     * tool.substringAfterLast(null, *)      = null
     * tool.substringAfterLast("", *)        = ""
     * tool.substringAfterLast(*, "")        = ""
     * tool.substringAfterLast(*, null)      = ""
     * tool.substringAfterLast("abc", "a")   = "bc"
     * tool.substringAfterLast("abcba", "b") = "a"
     * tool.substringAfterLast("abc", "c")   = ""
     * tool.substringAfterLast("a", "a")     = ""
     * tool.substringAfterLast("a", "z")     = ""
     * </pre>
     *
     * @param str  the String to get a substring from, may be null
     * @param separator  the String to search for, may be null
     * @return the substring after the last occurrence of the separator,
     *  {@code null} if null String input
     * @since 2.0
     */
    fun substringAfterLast(str: String?, separator: String): String {
        return StringUtils.substringAfterLast(str, separator)
    }

    /**
     * <p>Gets the String that is nested in between two instances of the
     * same String.</p>
     *
     * <p>A {@code null} input String returns {@code null}.
     * A {@code null} tag returns {@code null}.</p>
     *
     * <pre>
     * tool.substringBetween(null, *)            = null
     * tool.substringBetween("", "")             = ""
     * tool.substringBetween("", "tag")          = null
     * tool.substringBetween("tagabctag", null)  = null
     * tool.substringBetween("tagabctag", "")    = ""
     * tool.substringBetween("tagabctag", "tag") = "abc"
     * </pre>
     *
     * @param str  the String containing the substring, may be null
     * @param tag  the String before and after the substring, may be null
     * @return the substring, {@code null} if no match
     * @since 2.0
     */
    fun substringBetween(str: String?, tag: String): String {
        return StringUtils.substringBetween(str, tag)
    }

    /**
     * <p>Gets the String that is nested in between two Strings.
     * Only the first match is returned.</p>
     *
     * <p>A {@code null} input String returns {@code null}.
     * A {@code null} open/close returns {@code null} (no match).
     * An empty ("") open and close returns an empty string.</p>
     *
     * <pre>
     * tool.substringBetween("wx[b]yz", "[", "]") = "b"
     * tool.substringBetween(null, *, *)          = null
     * tool.substringBetween(*, null, *)          = null
     * tool.substringBetween(*, *, null)          = null
     * tool.substringBetween("", "", "")          = ""
     * tool.substringBetween("", "", "]")         = null
     * tool.substringBetween("", "[", "]")        = null
     * tool.substringBetween("yabcz", "", "")     = ""
     * tool.substringBetween("yabcz", "y", "z")   = "abc"
     * tool.substringBetween("yabczyabcz", "y", "z")   = "abc"
     * </pre>
     *
     * @param str  the String containing the substring, may be null
     * @param open  the String before the substring, may be null
     * @param close  the String after the substring, may be null
     * @return the substring, {@code null} if no match
     * @since 2.0
     */
    fun substringBetween(str: String?, open: String, close: String): String {
        return StringUtils.substringBetween(str, open, close)
    }

    /**
     * <p>Searches a String for substrings delimited by a start and end tag,
     * returning all matching substrings in an array.</p>
     *
     * <p>A {@code null} input String returns {@code null}.
     * A {@code null} open/close returns {@code null} (no match).
     * An empty ("") open/close returns {@code null} (no match).</p>
     *
     * <pre>
     * tool.substringsBetween("[a][b][c]", "[", "]") = ["a","b","c"]
     * tool.substringsBetween(null, *, *)            = null
     * tool.substringsBetween(*, null, *)            = null
     * tool.substringsBetween(*, *, null)            = null
     * tool.substringsBetween("", "[", "]")          = []
     * </pre>
     *
     * @param str  the String containing the substrings, null returns null, empty returns empty
     * @param open  the String identifying the start of the substring, empty returns null
     * @param close  the String identifying the end of the substring, empty returns null
     * @return a String Array of substrings, or {@code null} if no match
     * @since 2.3
     */
    fun substringsBetween(str: String?, open: String, close: String): Array<String>? {
        return StringUtils.substringsBetween(str, open, close)
    }

    /**
     * <p>Splits the provided text into an array, using whitespace as the
     * separator.
     * Whitespace is defined by {@link Character#isWhitespace(char)}.</p>
     *
     * <p>The separator is not included in the returned String array.
     * Adjacent separators are treated as one separator.
     * For more control over the split use the StrTokenizer class.</p>
     *
     * <p>A {@code null} input String returns {@code null}.</p>
     *
     * <pre>
     * tool.split(null)       = null
     * tool.split("")         = []
     * tool.split("abc def")  = ["abc", "def"]
     * tool.split("abc  def") = ["abc", "def"]
     * tool.split(" abc ")    = ["abc"]
     * </pre>
     *
     * @param str  the String to parse, may be null
     * @return an array of parsed Strings, {@code null} if null String input
     */
    fun split(str: String?): Array<String>? {
        return StringUtils.split(str)
    }

    /**
     * <p>Splits the provided text into an array, separators specified.
     * This is an alternative to using StringTokenizer.</p>
     *
     * <p>The separator is not included in the returned String array.
     * Adjacent separators are treated as one separator.
     * For more control over the split use the StrTokenizer class.</p>
     *
     * <p>A {@code null} input String returns {@code null}.
     * A {@code null} separatorChars splits on whitespace.</p>
     *
     * <pre>
     * tool.split(null, *)         = null
     * tool.split("", *)           = []
     * tool.split("abc def", null) = ["abc", "def"]
     * tool.split("abc def", " ")  = ["abc", "def"]
     * tool.split("abc  def", " ") = ["abc", "def"]
     * tool.split("ab:cd:ef", ":") = ["ab", "cd", "ef"]
     * </pre>
     *
     * @param str  the String to parse, may be null
     * @param separatorChars  the characters used as the delimiters,
     *  {@code null} splits on whitespace
     * @return an array of parsed Strings, {@code null} if null String input
     */
    fun split(str: String?, separatorChars: String): Array<String>? {
        return StringUtils.split(str, separatorChars)
    }

    //region CamelCase copy from org.apache.commons.lang3.StringUtils.CaseUtils

    /**
     * <p>Converts all the delimiter separated words in a String into camelCase,
     * that is each word is made up of a titlecase character and then a series of
     * lowercase characters.</p>
     *
     * <p>The delimiters represent a set of characters understood to separate words.
     * The first non-delimiter character after a delimiter will be capitalized. The first String
     * character may or may not be capitalized and it's determined by the user input for capitalizeFirstLetter
     * variable.</p>
     *
     * <p>A <code>null</code> input String returns <code>null</code>.
     * Capitalization uses the Unicode title case, normally equivalent to
     * upper case and cannot perform locale-sensitive mappings.</p>
     *
     * <pre>
     * tool.toCamelCase(null, false)                                 = null
     * tool.toCamelCase("", false, *)                                = ""
     * tool.toCamelCase(*, false, null)                              = *
     * tool.toCamelCase(*, true, new char[0])                        = *
     * tool.toCamelCase("To.Camel.Case", false, new char[]{'.'})     = "toCamelCase"
     * tool.toCamelCase(" to @ Camel case", true, new char[]{'@'})   = "ToCamelCase"
     * tool.toCamelCase(" @to @ Camel case", false, new char[]{'@'}) = "toCamelCase"
     * </pre>
     *
     * @param str  the String to be converted to camelCase, may be null
     * @param capitalizeFirstLetter boolean that determines if the first character of first word should be title case.
     * @param delimiters  set of characters to determine capitalization, null and/or empty array means whitespace
     * @return camelCase of String, <code>null</code> if null String input
     */
    fun toCamelCase(str: String?, capitalizeFirstLetter: Boolean, vararg delimiters: Char): String? {
        if (str.isNullOrBlank()) {
            return str
        }
        val strLow = str.toLowerCase()
        val strLen = strLow.length
        val newCodePoints = IntArray(strLen) { 0 }
        var outOffset = 0
        val delimiterSet = generateDelimiterSet(delimiters)
        var capitalizeNext = false
        if (capitalizeFirstLetter) {
            capitalizeNext = true
        }
        var index = 0
        while (index < strLen) {
            val codePoint = strLow.codePointAt(index)

            if (delimiterSet.contains(codePoint)) {
                capitalizeNext = true
                if (outOffset == 0) {
                    capitalizeNext = false
                }
                index += Character.charCount(codePoint)
            } else if (capitalizeNext || outOffset == 0 && capitalizeFirstLetter) {
                val titleCaseCodePoint = Character.toTitleCase(codePoint)
                newCodePoints[outOffset++] = titleCaseCodePoint
                index += Character.charCount(titleCaseCodePoint)
                capitalizeNext = false
            } else {
                newCodePoints[outOffset++] = codePoint;
                index += Character.charCount(codePoint);
            }
        }
        if (outOffset != 0) {
            return String(newCodePoints, 0, outOffset);
        }
        return strLow
    }

    /**
     * <p>Converts an array of delimiters to a hash set of code points. Code point of space(32) is added
     * as the default value. The generated hash set provides O(1) lookup time.</p>
     *
     * @param delimiters  set of characters to determine capitalization, null means whitespace
     * @return Set<Integer>
     */
    private fun generateDelimiterSet(delimiters: CharArray?): Set<Int> {
        val delimiterHashSet: HashSet<Int> = HashSet()
        delimiterHashSet.add(Character.codePointAt(charArrayOf(' '), 0))
        if (delimiters == null || delimiters.isEmpty()) {
            return delimiterHashSet
        }

        for (index in 0 until delimiters.size) {
            delimiterHashSet.add(Character.codePointAt(delimiters, index))
        }
        return delimiterHashSet
    }

    //endregion

    /**
     * Convert camel words to underline, all lowercase
     *
     * @param str camel words
     * @return underline words
     */
    fun camel2Underline(str: String?): String? {
        if (str.isNullOrBlank()) {
            return str
        }
        val matcher = TO_LINE_PATTERN.matcher(str)
        val buffer = StringBuffer()
        while (matcher.find()) {
            if (matcher.start() > 0) {
                matcher.appendReplacement(buffer, "_" + matcher.group(0).toLowerCase())
            } else {
                matcher.appendReplacement(buffer, matcher.group(0).toLowerCase())
            }
        }
        matcher.appendTail(buffer)
        return buffer.toString()
    }

    /**
     * If this string starts with the given [prefix], returns a copy of this string
     * with the prefix removed. Otherwise, returns this string.
     */
    fun removePrefix(str: String, prefix: String): String {
        return str.removePrefix(prefix)
    }

    /**
     * If this string ends with the given [suffix], returns a copy of this string
     * with the suffix removed. Otherwise, returns this string.
     */
    fun removeSuffix(str: String, suffix: String): String {
        return str.removeSuffix(suffix)
    }

    //endregion

    //region time&date

    /**
     * current time as "yyyy-MM-dd HH:mm:ss"
     */
    fun now(): String {
        return now(null)
    }

    /**
     * current time as the special pattern
     */
    fun now(pattern: String?): String {
        return DateFormatUtils.format(Date(), pattern ?: "yyyy-MM-dd HH:mm:ss")
    }

    /**
     * current time as "yyyy-MM-dd"
     */
    fun today(): String {
        return now(null)
    }

    /**
     * format the time as the special pattern
     */
    fun format(time: Long, pattern: String?): String {
        return DateFormatUtils.format(Date(time), pattern ?: "yyyy-MM-dd HH:mm:ss")
    }

    //endregion

    fun debug(any: Any?): String {
        if (any == null) {
            return "debug object is null"
        }

        val kClass: KClass<out Any> = any::class
        val qualifiedName = kClass.qualifiedName ?: return "debug error"
        if (!qualifiedName.startsWith("com.itangcent")) {
            return "[$qualifiedName] cannot debug"
        }

        val sb = StringBuilder()
        sb.append("type:")
                .append(typeName(kClass))
                .appendln()

        val ignoreMethods: ArrayList<String> = ArrayList()
        kClass.findAnnotation<ScriptIgnore>()?.let { ignoreMethods.addAll(it.name) }
        kClass.allSuperclasses.map { it.findAnnotation<ScriptIgnore>() }
                .filter { it != null }
                .map { it!!.name }
                .forEach { ignoreMethods.addAll(it) }

        val functions = kClass.functions
        if (functions.isNotEmpty()) {
            sb.append("methods:").appendln()
            val set: HashSet<String> = HashSet()
            for (function in functions) {
                val functionSb = StringBuilder()
                if (function.visibility != KVisibility.PUBLIC
                        || excludedMethods.contains(function.name)
                        || function.findAnnotation<ScriptIgnore>() != null
                        || (ignoreMethods.contains(function.name) &&
                                function.findAnnotation<ScriptUnIgnore>() == null)
                ) {
                    continue
                }


                functionSb.append(returnTypeOfFun(function))
                        .append(" ")
                        .append(function.name)
                        .append("(")
                var appended = false
                for (param in function.parameters) {
                    if (param.kind != KParameter.Kind.VALUE) {
                        continue
                    }

                    if (appended) {
                        functionSb.append(", ")
                    } else {
                        appended = true
                    }

                    if (param.isVararg) {
                        val type = param.type.arguments.firstOrNull()?.type
                        if (type == null) {
                            functionSb.append(typeName(param.type))
                        } else {
                            functionSb.append(typeName(type))
                                    .append("...")
                        }
                    } else {
                        functionSb.append(typeName(param.type))
                    }
                }
                functionSb.append(")")
                val functionStr = functionSb.toString()
                if (set.add(functionStr)) {
                    sb.append(functionStr).appendln()
                }
            }
        }

        return sb.toString()
    }

    private fun returnTypeOfFun(function: KFunction<*>): String {
        val scriptReturn = function.findAnnotation<ScriptReturn>()
        if (scriptReturn != null) {
            return scriptReturn.name
        }
        return typeName(function.returnType)
    }

    private fun typeName(kType: KType): String {
        val arguments = kType.arguments
        val classifier = kType.classifier
        if (arguments.isEmpty()) {
            return if (classifier is KClass<*>) {
                typeName(classifier)
            } else {
                typeName(classifier.toString())
            }
        } else {
            val sb = StringBuilder()

            sb.append(if (classifier is KClass<*>) {
                typeName(classifier)
            } else {
                typeName(classifier.toString())
            })

            sb.append("<")
            sb.append(arguments.joinToString(separator = ", ") { argument ->
                argument.type?.let { typeName(it) } ?: "object"
            })
            sb.append(">")

            return sb.toString()
        }
    }

    private fun typeName(kClass: KClass<*>): String {
        val annotation = kClass.findAnnotation<ScriptTypeName>()
        if (annotation != null) return annotation.name
        val qualifiedName = kClass.qualifiedName ?: return "object"
        return typeName(qualifiedName)
    }

    private fun typeName(qualifiedName: String): String {
        return typeMapper[qualifiedName] ?: qualifiedName
    }

    fun copy2Clipboard(str: String) {
        ToolUtils.copy2Clipboard(str)
    }

    companion object {
        private val excludedMethods = Arrays.asList("hashCode", "toString", "equals", "getClass", "clone", "notify", "notifyAll", "wait", "finalize")

        private val TO_LINE_PATTERN = Pattern.compile("[A-Z]+")

        private val typeMapper = KV.create<String, String>()
                .set("java.lang.String", "string")
                .set("java.lang.Long", "long")
                .set("java.lang.Double", "double")
                .set("java.lang.Short", "short")
                .set("java.lang.Integer", "int")
                .set("java.lang.Object", "object")
                .set("kotlin.String", "string")
                .set("kotlin.Array", "array")
                .set("kotlin.Int", "int")
                .set("kotlin.Unit", "void")
                .set("kotlin.collections.List", "array")
                .set("kotlin.Any", "object")
                .set("kotlin.Boolean", "bool")
                .set("kotlin.collections.Map", "map")
                .set("kotlin.collections.Set", "array")
                .set("kotlin.CharArray", "array<char>")

    }
}