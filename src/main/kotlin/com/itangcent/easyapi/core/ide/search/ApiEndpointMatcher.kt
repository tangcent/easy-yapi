package com.itangcent.easyapi.core.ide.search

import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.core.export.path

/**
 * Single source of truth for "does this endpoint match what the user typed?".
 *
 * Both search surfaces go through this object — the Search Everywhere
 * contributor ([ApiSearchEverywhereContributor]) and the Dashboard's search
 * box ([com.itangcent.easyapi.core.dashboard.ApiDashboardPanel]) — so an API
 * that matches in one matches in the other. Before this, each surface carried
 * its own copy of the rule, and they disagreed.
 *
 * ## Matching model
 *
 * A query is split on whitespace into tokens, and **every** token must match
 * something (AND). Tokens are independent, so different tokens may land on
 * different fields: `user 用户` matches an endpoint whose path says
 * `/api/user/get` and whose name says `获取用户信息`.
 *
 * Each token is tried in this order:
 *
 * 1. **Path substring** — highest score. A token that hits the path outranks
 *    one that hits a description.
 * 2. **Any-field substring** — the token appears literally somewhere in
 *    [searchableText].
 * 3. **Fuzzy subsequence** — the token's characters appear in order, with
 *    gaps, anywhere in [searchableText]. This is what makes the shape requested
 *    in #1460 work: with path `/api/user/get` and name `获取用户信息`, the token
 *    `aus用户` matches (`a`…`u`…`s` from the path, then `用户` from the name).
 *    Tokens shorter than [MIN_FUZZY_TOKEN_LENGTH] are excluded, otherwise a
 *    one-character query would match almost every endpoint.
 *
 * The score is the sum of the token scores, so more precise queries rank above
 * fuzzier ones. Callers that only need a yes/no use [matches].
 *
 * ## Path queries
 *
 * A query parsed from a URL or path (see [ApiSearchQuery.parse]) is matched
 * against the endpoint's path template first, so a concrete path such as
 * `/api/users/42` matches `/api/users/{id}` ([matchesPathWithVariables]). Such
 * queries deliberately skip the fuzzy rule: a pasted URL is a precise
 * statement, and fuzzy-matching it only produces surprise hits. They still fall
 * back to the literal rules, which is what makes a *shortened* URL (one with
 * the path variables stripped) match.
 */
object ApiEndpointMatcher {

    /** Score returned when the endpoint does not match at all. */
    const val NO_MATCH = 0

    /** A concrete path matched the endpoint's path template. */
    private const val SCORE_PATH_VARIABLE = 100

    /** A token appeared literally in the path. */
    private const val SCORE_PATH_SUBSTRING = 11

    /** A token appeared literally in some other field. */
    private const val SCORE_FIELD_SUBSTRING = 10

    /** A token matched as a subsequence, i.e. with gaps. */
    private const val SCORE_FUZZY = 1

    /**
     * Minimum length of a token allowed to match as a subsequence.
     *
     * Fuzzy matching is inherently loose — one or two characters can be found in
     * almost any endpoint — so short tokens must match literally.
     */
    private const val MIN_FUZZY_TOKEN_LENGTH = 3

    /**
     * Scores [endpoint] against [query].
     *
     * @return [NO_MATCH] when the endpoint does not match, a positive score
     *   otherwise. Higher is a better match; the absolute value is meaningless
     *   across queries.
     */
    fun score(endpoint: ApiEndpoint, query: ApiSearchQuery): Int {
        if (query.httpMethod != null && endpoint.httpMetadata?.method != query.httpMethod) {
            return NO_MATCH
        }

        val text = query.searchText.trim().lowercase()
        if (text.isEmpty()) {
            // An empty query matches everything; the HTTP method filter above is
            // the only constraint the caller asked for.
            return SCORE_FUZZY
        }

        val path = endpoint.path.lowercase()

        if (query.isPathQuery && text.startsWith("/") && matchesPathWithVariables(text, path)) {
            return SCORE_PATH_VARIABLE
        }

        val searchable = searchableText(endpoint)
        // A pasted path or URL is a precise statement: it may still match
        // literally (that is how a shortened URL keeps working), but it must not
        // be fuzzed — a URL that nearly fits another endpoint is not a hit.
        val allowFuzzy = !query.isPathQuery
        var total = 0
        for (token in text.split(WHITESPACE)) {
            if (token.isEmpty()) continue
            val tokenScore = tokenScore(token, path, searchable, allowFuzzy)
            if (tokenScore == NO_MATCH) return NO_MATCH
            total += tokenScore
        }
        return total
    }

    /** Convenience: whether [endpoint] matches [query] at all. */
    fun matches(endpoint: ApiEndpoint, query: ApiSearchQuery): Boolean =
        score(endpoint, query) != NO_MATCH

    /**
     * Whether the concrete path [concretePath] fits the endpoint path template
     * [patternPath], treating every `{...}` in the template as exactly one path
     * segment.
     *
     * Both arguments are expected to be lowercase.
     */
    fun matchesPathWithVariables(concretePath: String, patternPath: String): Boolean =
        pathPatternToRegex(patternPath).matches(concretePath)

    private fun tokenScore(token: String, path: String, searchable: String, allowFuzzy: Boolean): Int = when {
        path.contains(token) -> SCORE_PATH_SUBSTRING
        searchable.contains(token) -> SCORE_FIELD_SUBSTRING
        allowFuzzy && token.length >= MIN_FUZZY_TOKEN_LENGTH && searchable.hasSubsequence(token) -> SCORE_FUZZY
        else -> NO_MATCH
    }

    /**
     * Everything a token may match against, lowercased, separated by spaces.
     *
     * The fields are joined rather than tested one by one so that a single token
     * can span two fields (the `aus用户` case). The separator is a space, which
     * no single token can contain — so joining never creates a literal match
     * that neither field has on its own.
     */
    private fun searchableText(endpoint: ApiEndpoint): String = buildString {
        append(endpoint.path)
        endpoint.name?.let { append(' ').append(it) }
        endpoint.className?.let { append(' ').append(it) }
        endpoint.folder?.let { append(' ').append(it) }
        endpoint.description?.let { append(' ').append(it) }
    }.lowercase()

    /** Whether the characters of [token] appear in this string in order, gaps allowed. */
    private fun String.hasSubsequence(token: String): Boolean {
        var from = 0
        for (char in token) {
            val at = indexOf(char, from)
            if (at < 0) return false
            from = at + 1
        }
        return true
    }

    private fun pathPatternToRegex(pattern: String): Regex {
        val parts = pattern.split(Regex("\\{[^}]*\\}"))
        val regexStr = parts.joinToString("[^/]+") { Regex.escape(it) }
        return Regex("^$regexStr$")
    }

    private val WHITESPACE = Regex("\\s+")
}
