package com.itangcent.easyapi.http

import java.net.URLEncoder

/**
 * A name-value pair used for headers, query params, etc.
 */
typealias KeyValue = Pair<String, String>

/**
 * The name (key) of the key-value pair.
 */
val KeyValue.name: String
    get() = this.first

/**
 * The value of the key-value pair.
 */
val KeyValue.value: String
    get() = this.second

/**
 * Creates a key-value pair.
 *
 * @param key The name/key
 * @param value The value
 * @return A KeyValue pair
 */
fun kv(key: String, value: String): KeyValue {
    return KeyValue(key, value)
}

/**
 * A form parameter for HTTP requests.
 *
 * Can be either a text field or a file upload.
 * Used for both `application/x-www-form-urlencoded` and `multipart/form-data` requests.
 */
sealed class FormParam {
    abstract val name: String

    data class Text(
        override val name: String,
        val value: String
    ) : FormParam()

    data class File(
        override val name: String,
        val fileName: String,
        val contentType: String? = null,
        val bytes: ByteArray
    ) : FormParam() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is File) return false
            return name == other.name && fileName == other.fileName
                    && contentType == other.contentType && bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int {
            var result = name.hashCode()
            result = 31 * result + fileName.hashCode()
            result = 31 * result + (contentType?.hashCode() ?: 0)
            result = 31 * result + bytes.contentHashCode()
            return result
        }
    }
}

/**
 * Represents an HTTP request.
 *
 * @param url The request URL
 * @param method The HTTP method (GET, POST, PUT, DELETE, etc.)
 * @param headers The request headers
 * @param query The query parameters
 * @param body The request body (for JSON, XML, etc.)
 * @param formParams Form fields (text + file). Encoding depends on contentType.
 * @param cookies The cookies to send
 * @param contentType Optional content-type hint. When null, auto-detected from formParams.
 */
data class HttpRequest(
    val url: String,
    val method: String = "GET",
    val headers: List<KeyValue> = emptyList(),
    val query: List<KeyValue> = emptyList(),
    val body: String? = null,
    /**
     * Form fields (text + file). Encoding depends on [contentType]:
     * - `application/x-www-form-urlencoded` (default when no file parts)
     * - `multipart/form-data` (default when file parts exist, or set explicitly)
     */
    val formParams: List<FormParam> = emptyList(),
    val cookies: List<HttpCookie> = emptyList(),
    /** Optional content-type hint. When null, auto-detected from formParams. */
    val contentType: String? = null
)

/**
 * Checks whether this request should use multipart encoding.
 *
 * @return true if the request has file parts or explicit multipart content-type
 */
fun HttpRequest.isMultipart(): Boolean {
    if (contentType?.contains("multipart/form-data", ignoreCase = true) == true) return true
    if (formParams.any { it is FormParam.File }) return true
    return false
}

/**
 * Gets text-only form params as a map.
 *
 * Useful for `application/x-www-form-urlencoded` requests.
 *
 * @return Map of form field names to values
 */
fun HttpRequest.textFormParams(): Map<String, String> =
    formParams.filterIsInstance<FormParam.Text>().associate { it.name to it.value }

/**
 * Builds the full URL with query string.
 *
 * @return The complete URL with query parameters
 */
fun HttpRequest.buildUrl(): String {
    if (query.isEmpty()) return url
    val sep = if (url.contains("?")) "&" else "?"
    val qs = query.joinToString("&") {
        "${URLEncoder.encode(it.name, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
    }
    return url + sep + qs
}

/**
 * Represents an HTTP response.
 *
 * @param code The HTTP status code
 * @param headers The response headers
 * @param body The response body
 */
data class HttpResponse(
    val code: Int,
    val headers: Map<String, List<String>> = emptyMap(),
    val body: String? = null
)

/**
 * Represents an HTTP cookie.
 *
 * @param name The cookie name
 * @param value The cookie value
 * @param domain The cookie domain
 * @param path The cookie path
 */
data class HttpCookie(
    val name: String,
    val value: String,
    val domain: String? = null,
    val path: String? = null
)
