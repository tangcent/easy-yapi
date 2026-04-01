package com.itangcent.easyapi.dashboard

/**
 * Cache for edited request data in the dashboard.
 *
 * Stores user modifications to request parameters, headers, body, etc.
 * Used to preserve edits when switching between endpoints or sessions.
 *
 * @param key Unique identifier for this cached request
 * @param name The request name
 * @param path The request path
 * @param method The HTTP method
 * @param host The target host
 * @param headers The request headers
 * @param pathParams Path parameters
 * @param queryParams Query parameters
 * @param formParams Form parameters
 * @param body The request body
 * @param contentType The content type
 */
data class RequestEditCache(
    val key: String? = null,
    val name: String? = null,
    val path: String? = null,
    val method: String? = null,
    val host: String? = null,
    val headers: List<EditableKeyValue> = emptyList(),
    val pathParams: List<EditableKeyValue> = emptyList(),
    val queryParams: List<EditableKeyValue> = emptyList(),
    val formParams: List<EditableKeyValue> = emptyList(),
    val body: String? = null,
    val contentType: String? = null
) {
    /**
     * Returns the cache key for this request.
     */
    fun cacheKey(): String = key ?: ""
}

/**
 * A key-value pair for editable request data.
 *
 * Used for headers, parameters, and other configurable values.
 *
 * @param name The key name
 * @param value The value
 * @param description Optional description
 */
data class EditableKeyValue(
    val name: String,
    val value: String? = null,
    val description: String? = null
)
