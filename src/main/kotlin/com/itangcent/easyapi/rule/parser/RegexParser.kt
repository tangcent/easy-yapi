package com.itangcent.easyapi.rule.parser

import com.itangcent.easyapi.rule.RuleKey
import com.itangcent.easyapi.rule.context.RuleContext
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * Parser for regex-based rule expressions and placeholder resolution.
 *
 * ## Regex Matching
 * Matches element text against a regular expression pattern.
 * Expressions must start with `#regex:` prefix.
 *
 * ```
 * # Check if element contains "api"
 * #regex:.*api.*
 *
 * # Match URL pattern
 * #regex:/api/v\d+/.*
 *
 * # Capture groups for use in value expressions
 * #regex:reactor.core.publisher.Mono<(.*?)>
 * # Then use ${1} in the value to reference the captured group
 * ```
 *
 * ## Placeholder Resolution
 * Resolves `${n}` placeholders using captured regex groups.
 * When a regex filter matches and captures groups, the value expression
 * can reference them using `${1}`, `${2}`, etc.
 *
 * ```
 * # After #regex:reactor.core.publisher.Mono<(.*?)> matches
 * # The value ${1} will be replaced with the captured group
 * ```
 *
 * @see RuleParser for the parser interface
 */
class RegexParser : RuleParser {

    private val patternCache = ConcurrentHashMap<String, Pattern>()

    override fun canParse(expression: String): Boolean {
        return expression.startsWith(REGEX_PREFIX)
    }

    override suspend fun parse(expression: String, context: RuleContext, ruleKey: RuleKey<*>?): Any {
        if (ruleKey is RuleKey.BooleanKey) {
            return parseRegexAsFilter(expression, context)
        }

        if (ruleKey is RuleKey.StringKey) {
            val matchResult = parseRegexWithGroups(expression, context) ?: return ""
            return resolvePlaceholders(expression, matchResult)
        }

        return expression
    }

    private fun parseRegexAsFilter(expression: String, context: RuleContext): Boolean {
        val groups = parseRegexWithGroups(expression, context) ?: return false
        // Store captured groups in context so value expressions can resolve ${1}, ${2}, etc.
        context.regexGroups = groups
        return true
    }

    private fun parseRegexWithGroups(expression: String, context: RuleContext): List<String>? {
        val text = context.matchText ?: return null
        val pattern = expression.removePrefix(REGEX_PREFIX)
        if (pattern.isEmpty()) return null

        return runCatching {
            val compiledPattern = getOrCompilePattern(pattern)
            val matcher = compiledPattern.matcher(text)

            if (matcher.find()) {
                if (matcher.groupCount() > 0) {
                    (1..matcher.groupCount()).map { matcher.group(it) ?: "" }
                } else {
                    emptyList()
                }
            } else {
                LOG.info("RegexParser: pattern '$pattern' did NOT match text '$text'")
                null
            }
        }.getOrNull()
    }

    private fun resolvePlaceholders(text: String, groups: List<String>): String {
        if (groups.isEmpty()) return text

        return PLACEHOLDER_PATTERN.replace(text) { match ->
            val groupIndex = match.groupValues[1].toIntOrNull()
            if (groupIndex != null && groupIndex in 1..groups.size) {
                groups[groupIndex - 1]
            } else {
                match.value
            }
        }
    }

    internal fun getOrCompilePattern(pattern: String): Pattern {
        return patternCache.getOrPut(pattern) {
            Pattern.compile(pattern, Pattern.DOTALL)
        }
    }

    companion object : com.itangcent.easyapi.logging.IdeaLog {
        private const val REGEX_PREFIX = "#regex:"
        private val PLACEHOLDER_PATTERN = Regex("\\$\\{(\\d+)}")
    }
}
