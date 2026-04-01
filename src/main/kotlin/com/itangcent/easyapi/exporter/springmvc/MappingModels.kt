package com.itangcent.easyapi.exporter.springmvc

import com.itangcent.easyapi.exporter.model.HttpMethod

/**
 * Represents a fully resolved request mapping for an API endpoint.
 *
 * Contains the final path, HTTP method, and content negotiation settings
 * after combining class-level and method-level annotations.
 *
 * @param path The resolved URL path
 * @param method The HTTP method
 * @param consumes Accepted content types
 * @param produces Produced content types
 * @param headers Required headers
 */
data class ResolvedMapping(
    val path: String,
    val method: HttpMethod,
    val consumes: List<String> = emptyList(),
    val produces: List<String> = emptyList(),
    val headers: List<Pair<String, String>> = emptyList()
)

/**
 * Intermediate representation of mapping annotation data.
 *
 * Used during the resolution process before combining class and method mappings.
 *
 * @param paths The paths from the annotation
 * @param methods The HTTP methods from the annotation
 * @param consumes Accepted content types
 * @param produces Produced content types
 * @param headers Required headers
 */
data class MappingInfo(
    val paths: List<String> = emptyList(),
    val methods: List<HttpMethod> = emptyList(),
    val consumes: List<String> = emptyList(),
    val produces: List<String> = emptyList(),
    val headers: List<Pair<String, String>> = emptyList()
) {
    companion object {
        /**
         * Empty mapping info with no values set.
         */
        val EMPTY = MappingInfo()
    }
}