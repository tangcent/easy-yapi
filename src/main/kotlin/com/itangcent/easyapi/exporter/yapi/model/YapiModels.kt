package com.itangcent.easyapi.exporter.yapi.model

/**
 * Represents a complete API documentation for YAPI.
 * Contains all information needed to create or update an API in YAPI.
 * 
 * @property title The API title/name
 * @property path The API path (e.g., "/api/users")
 * @property method The HTTP method (e.g., "get", "post")
 * @property desc API description
 * @property status API status (e.g., "done", "undone")
 * @property tag Tags for categorization
 * @property reqHeaders Request headers
 * @property reqQuery Query parameters
 * @property reqParams Path parameters
 * @property reqBodyForm Form data parameters
 * @property reqBodyOther Request body (JSON Schema or JSON5)
 * @property reqBodyType Request body type ("json", "form", etc.)
 * @property reqBodyIsJsonSchema Whether reqBodyOther is JSON Schema format
 * @property resBody Response body (JSON Schema or JSON5)
 * @property resBodyType Response body type
 * @property resBodyIsJsonSchema Whether resBody is JSON Schema format
 * @property tags API tags
 * @property open Whether the API is publicly accessible
 */
data class YapiApiDoc(
    val title: String,
    val path: String,
    val method: String,
    val desc: String? = null,
    val status: String? = null,
    val tag: List<String>? = null,
    val reqHeaders: List<YapiHeader>? = null,
    val reqQuery: List<YapiQuery>? = null,
    val reqParams: List<YapiPathParam>? = null,
    val reqBodyForm: List<YapiFormParam>? = null,
    val reqBodyOther: String? = null,
    val reqBodyType: String? = null,
    val reqBodyIsJsonSchema: Boolean = false,
    val resBody: String? = null,
    val resBodyType: String? = "json",
    val resBodyIsJsonSchema: Boolean = true,
    val tags: List<String>? = null,
    val open: Boolean? = null
)

/**
 * Represents an HTTP header in YAPI API documentation.
 * 
 * @property name Header name
 * @property value Header value
 * @property desc Header description
 * @property example Example value
 * @property required Whether the header is required (1 = required, 0 = optional)
 */
data class YapiHeader(
    val name: String, 
    val value: String? = null,
    val desc: String? = null,
    val example: String? = null,
    val required: Int = 0
)

/**
 * Represents a query parameter in YAPI API documentation.
 * 
 * @property name Parameter name
 * @property value Default value
 * @property desc Parameter description
 * @property example Example value
 * @property required Whether the parameter is required (1 = required, 0 = optional)
 */
data class YapiQuery(
    val name: String, 
    val value: String? = null,
    val desc: String? = null,
    val example: String? = null,
    val required: Int = 0
)

/**
 * Represents a path parameter in YAPI API documentation.
 * 
 * @property name Parameter name
 * @property example Example value
 * @property desc Parameter description
 */
data class YapiPathParam(
    val name: String, 
    val example: String? = null,
    val desc: String? = null
)

/**
 * Represents a form parameter in YAPI API documentation.
 * 
 * @property name Parameter name
 * @property example Example value
 * @property type Parameter type ("text", "file", etc.)
 * @property required Whether the parameter is required (1 = required, 0 = optional)
 * @property desc Parameter description
 */
data class YapiFormParam(
    val name: String, 
    val example: String? = null,
    val type: String = "text",
    val required: Int = 0,
    val desc: String? = null
)

/**
 * Represents a YAPI category (cart) for organizing APIs.
 * 
 * @property id The cart ID
 * @property name The cart name
 */
data class YapiCart(val id: Long, val name: String)

/**
 * Result of saving an API to YAPI.
 * 
 * @property success Whether the save operation succeeded
 * @property message Error message if failed
 */
data class YapiSaveResult(val success: Boolean, val message: String? = null)

/**
 * Sealed class representing token validation result.
 * Can be either Valid with project ID, or Failed with reason.
 */
sealed class TokenValidationResult {
    /**
     * Token is valid and resolved to a project.
     * 
     * @property projectId The resolved project ID
     */
    data class Valid(val projectId: String) : TokenValidationResult()
    
    /**
     * Token validation failed.
     * 
     * @property reason The reason for validation failure
     */
    data class Failed(val reason: String) : TokenValidationResult()

    /** Whether the validation was successful */
    val isValid: Boolean get() = this is Valid
}
