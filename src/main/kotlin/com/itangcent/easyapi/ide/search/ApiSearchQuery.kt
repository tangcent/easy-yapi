package com.itangcent.easyapi.ide.search

import com.itangcent.easyapi.exporter.model.HttpMethod
import java.net.URI

/**
 * Represents a parsed search query for API endpoints.
 *
 * Supports searching by HTTP method prefix followed by search text,
 * or just search text alone. Also supports pasting full URLs to
 * automatically extract the path for matching.
 *
 * ## Query Format
 * - `GET /users` - Search for GET endpoints matching "/users"
 * - `POST user` - Search for POST endpoints matching "user"
 * - `/api/users` - Search all endpoints matching "/api/users"
 * - `http://127.0.0.1:3000/api/interface/get?id=116` - Extracts "/api/interface/get" for matching
 * - `https://example.com/users` - Extracts "/users" for matching
 * - `127.0.0.1:3000/api/users` - Extracts "/api/users" for matching
 * - `/api/users?id=1` - Strips query params, matches "/api/users"
 *
 * ## Path Variable Matching
 * When the search text is a concrete path (e.g., `/api/interface/116`),
 * it can match endpoints with path variables (e.g., `/api/interface/{id}`).
 *
 * @param httpMethod Optional HTTP method filter (null means search all methods)
 * @param searchText The text to search for in path, name, class, or description
 * @param isPathQuery Whether the search text was extracted from a URL/path (enables path variable matching)
 * @see ApiSearchEverywhereContributor for the search contributor
 */
data class ApiSearchQuery(
    val httpMethod: HttpMethod?,
    val searchText: String,
    val isPathQuery: Boolean = false
) {
    companion object {
        private val HTTP_METHOD_PATTERN = Regex(
            "^(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\\s+(.+)$",
            RegexOption.IGNORE_CASE
        )

        private val HOST_PORT_PATH_PATTERN = Regex(
            "^[^/\\s]+:\\d+(/[\\S]*)?"
        )

        private val PATH_WITH_QUERY_PATTERN = Regex(
            "^(/[^?\\s]*)[?\\s]"
        )

        /**
         * Parses a search input string into an [ApiSearchQuery].
         *
         * @param input The raw search input
         * @return The parsed query with optional HTTP method and search text
         */
        fun parse(input: String): ApiSearchQuery {
            val trimmed = input.trim()

            // First check for HTTP method prefix
            val methodMatch = HTTP_METHOD_PATTERN.find(trimmed)
            if (methodMatch != null) {
                val methodStr = methodMatch.groupValues[1].uppercase()
                val method = HttpMethod.fromSpring(methodStr)
                val text = methodMatch.groupValues[2].trim()
                val (path, isPath) = extractPath(text)
                return ApiSearchQuery(method, path, isPath)
            }

            // Then try to extract path from URL-like input
            val (path, isPath) = extractPath(trimmed)
            return ApiSearchQuery(null, path, isPath)
        }

        /**
         * Extracts the path from various URL-like formats.
         *
         * Handles:
         * - Full URLs: `http://127.0.0.1:3000/api/interface/get?id=116` -> `/api/interface/get`
         * - Scheme-less URLs: `127.0.0.1:3000/api/interface/get?id=116` -> `/api/interface/get`
         * - Paths with query: `/api/interface/get?id=116` -> `/api/interface/get`
         * - Plain paths: `/api/interface/get` -> `/api/interface/get`
         * - Non-path text: `user` -> `user`
         *
         * @return Pair of (extracted path, whether it was extracted from a URL/path)
         */
        private fun extractPath(input: String): Pair<String, Boolean> {
            // Try java.net.URI for full URLs with scheme
            val uriPath = tryParseUri(input)
            if (uriPath != null) {
                return uriPath to true
            }

            // Try host:port/path format (e.g., 127.0.0.1:3000/api/users)
            val hostPortMatch = HOST_PORT_PATH_PATTERN.find(input)
            if (hostPortMatch != null) {
                val path = hostPortMatch.groupValues[1]
                if (path != null && path.startsWith("/")) {
                    return stripQueryParams(path) to true
                } else {
                    return "/" to true
                }
            }

            // Try /path?query format
            if (input.startsWith("/")) {
                return stripQueryParams(input) to true
            }

            // Not a URL/path, return as-is
            return input to false
        }

        private fun tryParseUri(input: String): String? {
            return try {
                val uri = URI(input)
                if (uri.scheme != null && uri.path != null) {
                    val path = uri.path
                    if (path.isNotEmpty()) path else "/"
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }

        private fun stripQueryParams(path: String): String {
            val match = PATH_WITH_QUERY_PATTERN.find(path)
            return if (match != null) match.groupValues[1] else path
        }
    }
}
