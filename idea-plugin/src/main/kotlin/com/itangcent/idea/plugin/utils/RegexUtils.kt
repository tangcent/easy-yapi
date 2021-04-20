package com.itangcent.idea.plugin.utils

import com.itangcent.annotation.script.ScriptTypeName
import org.apache.commons.lang3.StringUtils
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Refactoring from {@url https://github.com/aoju/bus/blob/master/bus-core/src/main/java/org/aoju/bus/core/utils/PatternUtils.java}
 */
@ScriptTypeName("regex")
class RegexUtils {

    private val cache = WeakHashMap<RegexWithFlag, Pattern>()
    private val cacheLock = ReentrantReadWriteLock()
    private val readLock = cacheLock.readLock()
    private val writeLock = cacheLock.writeLock()

    /**
     * find the [Pattern] from cache or compile the regular expression and cache it
     */
    private fun getPattern(regex: String?, flags: Int = 0): Pattern? {
        if (regex == null) return null
        val regexWithFlag = RegexWithFlag(regex, flags)
        var pattern: Pattern? = tryGet(regexWithFlag)
        if (null == pattern) {
            pattern = Pattern.compile(regex, flags)
            cachePattern(regexWithFlag, pattern)
        }
        return pattern
    }

    /**
     * return the group 0 value($0) if matched
     * otherwise null if not matched
     */
    fun getGroup0(regex: String, content: String): String? {
        return get(regex, content, 0)
    }

    /**
     * return the group 1 value($1) if matched
     * otherwise null if not matched
     */
    fun getGroup1(regex: String, content: String): String? {
        return get(regex, content, 1)
    }

    /**
     * return the special group value if matched
     * otherwise null if not matched
     */
    fun get(regex: String?, content: String?, groupIndex: Int): String? {
        if (null == content || null == regex) {
            return null
        }
        val pattern = getPattern(regex, Pattern.DOTALL)
        return get(pattern!!, content, groupIndex)
    }

    private fun get(pattern: Pattern, content: String, groupIndex: Int): String? {
        val matcher = pattern.matcher(content)
        return if (matcher.find()) {
            matcher.group(groupIndex)
        } else null
    }

    /**
     * return all group value as List if matched
     * otherwise null if not matched
     */
    fun getAllGroups(pattern: String?, content: String?): List<String>? {
        return getAllGroups(getPattern(pattern), content)
    }

    private fun getAllGroups(pattern: Pattern?, content: String?): List<String>? {
        if (null == content || null == pattern) {
            return null
        }

        val result = ArrayList<String>()
        val matcher = pattern.matcher(content)
        if (matcher.find()) {
            val groupCount = matcher.groupCount()
            for (i in 1..groupCount) {
                result.add(matcher.group(i))
            }
        }
        return result
    }

    fun extract(regex: String?, content: String?, template: String?): String? {
        if (null == content || null == regex || null == template) {
            return null
        }
        val pattern = getPattern(regex, Pattern.DOTALL)
        return extract(pattern!!, content, template)
    }

    private fun extract(pattern: Pattern, content: String, template: String): String? {
        return if (template.contains("$")) {
            extract(pattern, content) { template }
        } else {
            extract(pattern, content) { matcher ->
                extract(GROUP_VAR, template) {
                    matcher.group(it.group(0).toInt())
                }
            }
        }
    }

    private fun extract(pattern: Pattern, content: String, handle: (Matcher) -> String): String {
        val matcher = pattern.matcher(content)
        val sb = StringBuffer()
        while (matcher.find()) {
            matcher.appendReplacement(sb, handle(matcher))
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    /**
     * Remove the first subString of the input String that matches the
     * pattern with the given replacement string.
     */
    fun delFirst(pattern: String, content: String): String? {
        return delFirst(getPattern(pattern)!!, content)
    }

    private fun delFirst(pattern: Pattern, content: String): String {
        return if (content.isBlank()) {
            content
        } else {
            pattern.matcher(content).replaceFirst("")
        }

    }

    /**
     * Remove the first subString of the input String that matches the
     * pattern
     */
    fun delAll(regex: String, content: String): String {
        if (StringUtils.isAnyBlank(regex, content)) {
            return content
        }

        // RegEx pattern = RegEx.compile(regex, RegEx.DOTALL);
        val pattern = getPattern(regex, Pattern.DOTALL)
        return delAll(pattern!!, content)
    }

    /**
     *
     * Replaces every subString of the input String that matches the
     * pattern
     */
    private fun delAll(pattern: Pattern, content: String): String {
        return if (content.isBlank()) {
            content
        } else pattern.matcher(content).replaceAll("")

    }

    fun delBefore(regex: String, content: String): String? {
        val pattern = getPattern(regex, Pattern.DOTALL)!!
        val matcher = pattern.matcher(content)
        return if (matcher.find()) {
            StringUtils.substring(content, matcher.end() - 1, content.length)
        } else {
            content
        }
    }

    fun findAllGroup0(regex: String, content: String): List<String>? {
        return findAll(regex, content, 0)
    }

    fun findAllGroup1(regex: String, content: String): List<String>? {
        return findAll(regex, content, 1)
    }

    fun findAll(regex: String, content: String, group: Int): List<String>? {
        return findAll(regex, content, group, ArrayList())
    }

    private fun <T : MutableCollection<String>> findAll(regex: String, content: String, group: Int, collection: T): T? {
        val pattern = getPattern(regex, Pattern.DOTALL)
        return findAll(pattern, content, group, collection)
    }

    private fun <T : MutableCollection<String>> findAll(pattern: Pattern?, content: String?, group: Int, collection: T?): T? {
        if (null == pattern || null == content) {
            return null
        }

        if (null == collection) {
            throw NullPointerException("Null collection param provided!")
        }

        val matcher = pattern.matcher(content)
        while (matcher.find()) {
            collection.add(matcher.group(group))
        }
        return collection
    }

    fun count(regex: String?, content: String?): Int {
        if (null == regex || null == content) {
            return 0
        }
        val pattern = getPattern(regex, Pattern.DOTALL)
        return count(pattern!!, content)
    }

    private fun count(pattern: Pattern, content: String): Int {
        var count = 0
        val matcher = pattern.matcher(content)
        while (matcher.find()) {
            count++
        }

        return count
    }

    fun contains(regex: String?, content: String?): Boolean {
        if (null == regex || null == content) {
            return false
        }
        val pattern = getPattern(regex, Pattern.DOTALL)
        return pattern!!.matcher(content).find()
    }

    fun isMatch(regex: String?, content: String?): Boolean {
        if (content == null || regex.isNullOrBlank()) {
            return false
        }
        val pattern = getPattern(regex, Pattern.DOTALL)
        return pattern!!.matcher(content).matches()
    }

    fun replaceAll(content: String, regex: String, replacementTemplate: String): String? {
        return extract(regex, content, replacementTemplate)
    }

    /**
     * escape for Regex keywords
     */
    fun escape(content: String?): String? {
        if (content.isNullOrBlank()) {
            return content
        }

        val builder = StringBuilder()
        val len = content.length
        var current: Char
        for (i in 0 until len) {
            current = content[i]
            if (REGEX_KEYWORD.contains(current)) {
                builder.append('\\')
            }
            builder.append(current)
        }
        return builder.toString()
    }

    /**
     * get [Pattern] from cache
     */
    private fun tryGet(key: RegexWithFlag): Pattern? {
        readLock.lock()
        val value: Pattern?
        try {
            value = cache[key]
        } finally {
            readLock.unlock()
        }
        return value
    }

    /**
     * cache [Pattern]
     */
    private fun cachePattern(key: RegexWithFlag, value: Pattern): Any {
        writeLock.lock()
        try {
            cache[key] = value
        } finally {
            writeLock.unlock()
        }
        return value
    }

    companion object {

        private const val GROUP_VAR_PATTERN = "\\$(\\d+)"
        val GROUP_VAR = Pattern.compile(GROUP_VAR_PATTERN)!!

        val REGEX_KEYWORD = setOf('$', '(', ')', '*', '+', '.',
                '[', ']', '?', '\\', '^', '{', '}', '|')

        val INSTANCE = RegexUtils()
    }

    private class RegexWithFlag(private val regex: String?, private val flag: Int) {

        override fun hashCode(): Int {
            val prime = 31
            var result = 1
            result = prime * result + flag
            result = prime * result + (regex?.hashCode() ?: 0)
            return result
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other == null) {
                return false
            }
            if (javaClass != other.javaClass) {
                return false
            }
            val regexWithFlag = other as RegexWithFlag?
            if (flag != regexWithFlag!!.flag) {
                return false
            }
            return if (regex == null) {
                regexWithFlag.regex == null
            } else
                regex == regexWithFlag.regex
        }

    }

}
