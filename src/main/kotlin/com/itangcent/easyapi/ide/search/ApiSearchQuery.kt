package com.itangcent.easyapi.ide.search

import com.itangcent.easyapi.exporter.model.HttpMethod

/**
 * Represents a parsed search query for API endpoints.
 *
 * Supports searching by HTTP method prefix followed by search text,
 * or just search text alone.
 *
 * ## Query Format
 * - `GET /users` - Search for GET endpoints matching "/users"
 * - `POST user` - Search for POST endpoints matching "user"
 * - `/api/users` - Search all endpoints matching "/api/users"
 *
 * @param httpMethod Optional HTTP method filter (null means search all methods)
 * @param searchText The text to search for in path, name, class, or description
 * @see ApiSearchEverywhereContributor for the search contributor
 */
data class ApiSearchQuery(
    val httpMethod: HttpMethod?,
    val searchText: String
) {
    companion object {
        private val HTTP_METHOD_PATTERN = Regex(
            "^(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\\s+(.+)$",
            RegexOption.IGNORE_CASE
        )

        /**
         * Parses a search input string into an [ApiSearchQuery].
         *
         * @param input The raw search input
         * @return The parsed query with optional HTTP method and search text
         */
        fun parse(input: String): ApiSearchQuery {
            val trimmed = input.trim()
            val match = HTTP_METHOD_PATTERN.find(trimmed)

            return if (match != null) {
                val methodStr = match.groupValues[1].uppercase()
                val method = HttpMethod.fromSpring(methodStr)
                val text = match.groupValues[2].trim()
                ApiSearchQuery(method, text)
            } else {
                ApiSearchQuery(null, trimmed)
            }
        }
    }
}
