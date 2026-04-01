package com.itangcent.easyapi.exporter.postman.model

/**
 * Represents a Postman collection.
 *
 * A collection is a container for API requests organized into folders.
 *
 * @param info Collection metadata
 * @param items List of items (folders or requests)
 * @param event Pre-request/post-response scripts
 * @param variable Collection-level variables
 */
data class PostmanCollection(
    val info: CollectionInfo,
    val item: List<PostmanItem> = emptyList(),
    val event: List<PostmanEvent> = emptyList(),
    val variable: List<PostmanVariable> = emptyList()
)

/**
 * Collection metadata.
 *
 * @param name Collection name
 * @param schema Postman schema version URL
 * @param description Collection description
 * @param _postman_id Postman collection ID (for updates)
 */
data class CollectionInfo(
    val name: String,
    val schema: String = "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
    val description: String? = null,
    val _postman_id: String? = null
)

/**
 * An item in a Postman collection.
 *
 * Can be either:
 * - A folder (contains nested items)
 * - A request (has request property)
 *
 * @param name Item name
 * @param item Nested items (for folders)
 * @param request The HTTP request (for request items)
 * @param response Saved responses
 * @param event Pre-request/post-response scripts
 * @param description Item description
 */
data class PostmanItem(
    val name: String,
    val item: List<PostmanItem> = emptyList(),
    val request: PostmanRequest? = null,
    val response: List<PostmanResponse> = emptyList(),
    val event: List<PostmanEvent> = emptyList(),
    val description: String? = null
)

/**
 * An HTTP request in Postman format.
 *
 * @param method HTTP method (GET, POST, etc.)
 * @param header Request headers
 * @param body Request body
 * @param url Request URL details
 * @param description Request description
 */
data class PostmanRequest(
    val method: String,
    val header: List<PostmanHeader> = emptyList(),
    val body: PostmanBody? = null,
    val url: PostmanUrl,
    val description: String? = null
)

/**
 * URL details for a Postman request.
 *
 * @param raw The full URL string
 * @param host URL host segments
 * @param path URL path segments
 * @param query Query parameters
 * @param variable Path variables
 */
data class PostmanUrl(
    val raw: String,
    val host: List<String> = emptyList(),
    val path: List<String> = emptyList(),
    val query: List<PostmanQuery> = emptyList(),
    val variable: List<PostmanPathVariable> = emptyList()
)

/**
 * An HTTP header in Postman format.
 *
 * @param key Header name
 * @param value Header value
 * @param type Value type (text, secret, etc.)
 * @param description Header description
 * @param name Alternative name field
 */
data class PostmanHeader(
    val key: String,
    val value: String,
    val type: String = "text",
    val description: String? = null,
    val name: String? = null
)

/**
 * Request body in Postman format.
 *
 * @param mode Body mode (raw, urlencoded, formdata, file)
 * @param raw Raw body content (for raw mode)
 * @param urlencoded URL-encoded form parameters
 * @param formdata Multipart form parameters
 * @param options Additional options (e.g., language for raw mode)
 */
data class PostmanBody(
    val mode: String,
    val raw: String? = null,
    val urlencoded: List<PostmanFormParam>? = null,
    val formdata: List<PostmanFormParam>? = null,
    val options: Map<String, Any?>? = null
)

/**
 * A query parameter in Postman format.
 *
 * @param key Parameter name
 * @param value Parameter value
 * @param equals Whether to include the equals sign
 * @param description Parameter description
 * @param disabled Whether this parameter is disabled
 */
data class PostmanQuery(
    val key: String,
    val value: String,
    val equals: Boolean = true,
    val description: String? = null,
    val disabled: Boolean? = null
)

/**
 * A form parameter in Postman format.
 *
 * @param key Parameter name
 * @param value Parameter value
 * @param type Parameter type (text or file)
 * @param description Parameter description
 * @param disabled Whether this parameter is disabled
 * @param src File source path (for file type)
 */
data class PostmanFormParam(
    val key: String,
    val value: String,
    val type: String = "text",
    val description: String? = null,
    val disabled: Boolean? = null,
    val src: String? = null
)

/**
 * A path variable in Postman format.
 *
 * @param key Variable name
 * @param value Variable value
 * @param description Variable description
 */
data class PostmanPathVariable(
    val key: String,
    val value: String,
    val description: String? = null
)

/**
 * A saved response in Postman format.
 *
 * @param name Response name
 * @param originalRequest The original request
 * @param status HTTP status text
 * @param code HTTP status code
 * @param header Response headers
 * @param body Response body
 * @param _postman_previewlanguage Preview language (json, xml, etc.)
 * @param cookie Response cookies
 * @param responseTime Response time in ms
 * @param id Response ID
 */
data class PostmanResponse(
    val name: String,
    val originalRequest: PostmanRequest? = null,
    val status: String? = null,
    val code: Int? = null,
    val header: List<PostmanHeader> = emptyList(),
    val body: String? = null,
    val _postman_previewlanguage: String = "json",
    val cookie: List<Any> = emptyList(),
    val responseTime: String? = null,
    val id: String? = null
)

/**
 * An event (script) in Postman format.
 *
 * @param listen Event type (prerequest, test)
 * @param script The script to execute
 */
data class PostmanEvent(
    val listen: String,
    val script: PostmanScript
)

/**
 * A script in Postman format.
 *
 * @param type Script type (text/javascript)
 * @param exec Script lines to execute
 */
data class PostmanScript(
    val type: String = "text/javascript",
    val exec: List<String> = emptyList()
)

/**
 * A variable in Postman format.
 *
 * @param key Variable name
 * @param value Variable value
 * @param type Value type (string, boolean, etc.)
 * @param description Variable description
 */
data class PostmanVariable(
    val key: String,
    val value: String,
    val type: String = "string",
    val description: String? = null
)
