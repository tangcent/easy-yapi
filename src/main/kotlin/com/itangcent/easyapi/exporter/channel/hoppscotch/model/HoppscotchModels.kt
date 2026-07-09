package com.itangcent.easyapi.exporter.channel.hoppscotch.model

import com.google.gson.Gson
import com.google.gson.GsonBuilder

/**
 * Represents a Hoppscotch collection (schema v12).
 *
 * Collections can be nested: top-level collections contain folders (sub-collections)
 * and requests. Each collection may define pre-request scripts, test scripts,
 * collection variables, auth, and headers.
 *
 * @property v schema version (always 12 for collections, as Int per Hoppscotch spec)
 * @property name display name of the collection
 * @property folders nested sub-collections (used as folder grouping)
 * @property requests REST requests at this level
 * @property auth authentication configuration for this collection
 * @property headers headers applied to all requests in this collection
 * @property variables collection-level variables
 * @property description optional description
 * @property preRequestScript pre-request script (HoppScript / JavaScript)
 * @property testScript test script (HoppScript / JavaScript)
 */
data class HoppCollection(
    val v: Int = 12,
    val name: String,
    val _ref_id: String = generateUniqueRefId("coll"),
    val folders: List<HoppCollection> = emptyList(),
    val requests: List<HoppRESTRequest> = emptyList(),
    val auth: HoppAuth = HoppAuth(),
    val headers: List<HoppKeyValue> = emptyList(),
    val variables: List<HoppCollectionVariable> = emptyList(),
    val description: String? = null,
    val preRequestScript: String = "",
    val testScript: String = ""
)

/**
 * Represents a Hoppscotch REST request (schema v17).
 *
 * @property v schema version (always "17" for requests, as String per Hoppscotch spec)
 * @property name display name of the request
 * @property method HTTP method (GET, POST, PUT, DELETE, etc.)
 * @property endpoint full URL (may include Hoppscotch variables like `{{host}}`)
 * @property params query and path parameters
 * @property headers request headers
 * @property auth request-level authentication override
 * @property body request body (JSON, form-data, etc.)
 * @property preRequestScript pre-request script for this request
 * @property testScript test script for this request
 * @property requestVariables request-scoped variables
 * @property responses saved response examples
 * @property description optional description
 */
data class HoppRESTRequest(
    val v: String = "17",
    val name: String,
    val method: String,
    val endpoint: String,
    val _ref_id: String = generateUniqueRefId("req"),
    val params: List<HoppKeyValue> = emptyList(),
    val headers: List<HoppKeyValue> = emptyList(),
    val auth: HoppAuth = HoppAuth(),
    val body: HoppRequestBody = HoppRequestBody(),
    val preRequestScript: String = "",
    val testScript: String = "",
    val requestVariables: List<HoppRequestVariable> = emptyList(),
    val responses: Map<String, Any?> = emptyMap(),
    val description: String? = null
)

/**
 * A key-value pair used for parameters, headers, and other name-value entries.
 *
 * @property key the parameter/header name
 * @property value the parameter/header value
 * @property active whether this entry is enabled
 * @property description optional description
 */
data class HoppKeyValue(
    val key: String,
    val value: String = "",
    val active: Boolean = true,
    val description: String? = null
)

/**
 * Authentication configuration for a Hoppscotch request or collection.
 *
 * @property authType authentication type (e.g., "inherit", "bearer", "basic", "oauth2", "api-key", "none")
 * @property authActive whether authentication is enabled
 */
data class HoppAuth(
    val authType: String = "inherit",
    val authActive: Boolean = true
)

/**
 * Request body configuration.
 *
 * The [contentType] determines how [body] is interpreted:
 * - `"application/json"` → [body] is a JSON string
 * - `"application/x-www-form-urlencoded"` → [body] is a Map of key-value pairs
 * - `"multipart/form-data"` → [body] is a List of [HoppFormDataEntry]
 * - `null` → no body
 *
 * Null fields are serialized via `serializeNulls()` in [hoppscotchGson] to match
 * Hoppscotch's expected schema.
 *
 * @property contentType the Content-Type header value, or null for no body
 * @property body the body content (type depends on contentType)
 */
data class HoppRequestBody(
    val contentType: String? = null,
    val body: Any? = null
)

/**
 * A collection-level variable in Hoppscotch.
 *
 * Collection variables are accessible to all requests within the collection
 * via the `<<key>>` syntax.
 *
 * @property key variable name
 * @property initialValue default value (used when first creating the variable)
 * @property currentValue active value (used during request execution)
 * @property secret whether this variable contains sensitive data
 */
data class HoppCollectionVariable(
    val key: String,
    val initialValue: String = "",
    val currentValue: String = "",
    val secret: Boolean = false
)

/**
 * A request-scoped variable in Hoppscotch.
 *
 * @property key variable name
 * @property value variable value
 * @property active whether this variable is enabled
 */
data class HoppRequestVariable(
    val key: String,
    val value: String = "",
    val active: Boolean = true
)

/**
 * A form-data entry for multipart/form-data request bodies.
 *
 * Matches Hoppscotch's FormDataKeyValue Zod schema:
 * - `key` and `active` are always present
 * - `contentType` is optional
 * - `isFile` determines whether `value` is a file reference or a plain string
 * - When `isFile` is false, `value` is a string
 *
 * @property key field name
 * @property value field value (string when isFile is false)
 * @property active whether this entry is enabled
 * @property isFile whether this entry represents a file upload
 * @property contentType optional content type for this entry
 */
data class HoppFormDataEntry(
    val key: String,
    val value: String = "",
    val active: Boolean = true,
    val isFile: Boolean = false,
    val contentType: String? = null
)

/**
 * Creates a Gson instance configured for Hoppscotch JSON serialization.
 *
 * Key configuration:
 * - `serializeNulls()` — ensures null fields in [HoppRequestBody] are included
 *   (required by Hoppscotch's schema)
 * - Optional pretty printing for file export readability
 *
 * @param prettyPrint whether to enable pretty-printed JSON output
 * @return a configured Gson instance
 */
fun hoppscotchGson(prettyPrint: Boolean = true): Gson {
    val builder = GsonBuilder()
    if (prettyPrint) {
        builder.setPrettyPrinting()
    }
    builder.serializeNulls()
    return builder.create()
}

fun generateUniqueRefId(prefix: String = ""): String {
    val timestamp = System.currentTimeMillis().toString(36)
    val uuid = java.util.UUID.randomUUID().toString()
    return if (prefix.isNotEmpty()) {
        "${prefix}_${timestamp}_${uuid}"
    } else {
        "${timestamp}_${uuid}"
    }
}
