package com.itangcent.easyapi.util

import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Utility object for regex operations with caching.
 *
 * Provides common regex operations with pattern caching for performance.
 * All patterns are compiled with DOTALL flag by default.
 *
 * ## Usage
 * ```kotlin
 * // Extract first group
 * val name = RegexUtils.getGroup1("user:(\\w+)", "user:john")
 *
 * // Find all matches
 * val emails = RegexUtils.findAllGroup0("[\\w.]+@[\\w.]+", text)
 *
 * // Check if matches
 * if (RegexUtils.contains("error", log)) { ... }
 * ```
 */
object RegexUtils {
    private val cache = ConcurrentHashMap<Pair<String, Int>, Pattern>()

    private fun getPattern(regex: String, flags: Int = Pattern.DOTALL): Pattern {
        return cache.computeIfAbsent(regex to flags) { (r, f) -> Pattern.compile(r, f) }
    }

    fun getGroup0(regex: String, content: String): String? = get(regex, content, 0)

    fun getGroup1(regex: String, content: String): String? = get(regex, content, 1)

    fun get(regex: String?, content: String?, groupIndex: Int): String? {
        if (regex.isNullOrEmpty() || content == null) return null
        val matcher = getPattern(regex).matcher(content)
        return if (matcher.find()) matcher.group(groupIndex) else null
    }

    fun getAllGroups(regex: String?, content: String?): List<String>? {
        if (regex.isNullOrEmpty() || content == null) return null
        val matcher = getPattern(regex).matcher(content)
        if (!matcher.find()) return emptyList()
        val groupCount = matcher.groupCount()
        return (1..groupCount).map { matcher.group(it) }
    }

    fun extract(regex: String?, content: String?, template: String?): String? {
        if (regex.isNullOrEmpty() || content == null || template == null) return null
        val pattern = getPattern(regex)
        val matcher = pattern.matcher(content)
        val sb = StringBuffer()
        while (matcher.find()) {
            matcher.appendReplacement(sb, resolveTemplate(template, matcher))
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    fun delFirst(regex: String, content: String): String {
        if (regex.isEmpty()) return content
        return getPattern(regex).matcher(content).replaceFirst("")
    }

    fun delAll(regex: String, content: String): String {
        if (regex.isEmpty()) return content
        return getPattern(regex).matcher(content).replaceAll("")
    }

    fun delBefore(regex: String, content: String): String? {
        val matcher = getPattern(regex).matcher(content)
        return if (matcher.find()) content.substring(matcher.end() - 1) else content
    }

    fun findAllGroup0(regex: String, content: String): List<String>? = findAll(regex, content, 0)

    fun findAllGroup1(regex: String, content: String): List<String>? = findAll(regex, content, 1)

    fun replaceAll(content: String, regex: String, replacementTemplate: String): String? {
        return extract(regex, content, replacementTemplate)
    }

    fun findAll(regex: String, content: String, group: Int = 0): List<String> {
        if (regex.isEmpty() || content.isEmpty()) return emptyList()
        val matcher = getPattern(regex).matcher(content)
        val result = ArrayList<String>()
        while (matcher.find()) {
            result.add(matcher.group(group))
        }
        return result
    }

    fun count(regex: String?, content: String?): Int {
        if (regex.isNullOrEmpty() || content == null) return 0
        val matcher = getPattern(regex).matcher(content)
        var count = 0
        while (matcher.find()) count++
        return count
    }

    fun contains(regex: String?, content: String?): Boolean {
        if (regex.isNullOrEmpty() || content == null) return false
        return getPattern(regex).matcher(content).find()
    }

    fun isMatch(regex: String?, content: String?): Boolean {
        if (regex.isNullOrEmpty() || content == null) return false
        return getPattern(regex).matcher(content).matches()
    }

    fun escape(content: String?): String? {
        if (content.isNullOrEmpty()) return content
        val builder = StringBuilder()
        for (ch in content) {
            if (REGEX_KEYWORD.contains(ch)) builder.append('\\')
            builder.append(ch)
        }
        return builder.toString()
    }

    private fun resolveTemplate(template: String, matcher: Matcher): String {
        if (!template.contains('$')) return template
        val sb = StringBuilder()
        var i = 0
        while (i < template.length) {
            val ch = template[i]
            if (ch == '$' && i + 1 < template.length && template[i + 1].isDigit()) {
                var j = i + 1
                while (j < template.length && template[j].isDigit()) j++
                val idx = template.substring(i + 1, j).toIntOrNull()
                if (idx != null && idx <= matcher.groupCount()) {
                    sb.append(matcher.group(idx) ?: "")
                    i = j
                    continue
                }
            }
            sb.append(ch)
            i++
        }
        return sb.toString()
    }

    private val REGEX_KEYWORD: Set<Char> = setOf(
        '\\', '$', '(', ')', '*', '+', '.', '[', ']', '?', '^', '{', '}', '|'
    )
}
