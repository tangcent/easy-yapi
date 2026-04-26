package com.itangcent.easyapi.dashboard.script

/**
 * Validates a JSON value against a simplified JSON Schema, compatible with Postman's
 * `pm.response.to.have.jsonSchema()` assertion.
 *
 * Supports the following JSON Schema keywords:
 * - `type`: Validates the JSON value type ("string", "integer", "number", "boolean", "object", "array", "null")
 * - `properties`: Validates each property of an object against its sub-schema
 * - `required`: Checks that all listed property names exist in the object
 * - `items`: Validates each element of an array against the given sub-schema
 *
 * Throws [IllegalStateException] on validation failure.
 *
 * Example:
 * ```kotlin
 * val schema = mapOf(
 *     "type" to "object",
 *     "required" to listOf("name", "age"),
 *     "properties" to mapOf(
 *         "name" to mapOf("type" to "string"),
 *         "age" to mapOf("type" to "integer")
 *     )
 * )
 * JsonSchemaValidator.validate(json, schema) // throws if invalid
 * ```
 */
object JsonSchemaValidator {

    /**
     * Validates the given JSON value against the provided schema.
     *
     * @param value The JSON value to validate (parsed from response body)
     * @param schema The JSON Schema as a map
     * @throws IllegalStateException if validation fails
     */
    fun validate(value: Any?, schema: Map<String, Any?>) {
        validateValue(value, schema)
    }

    private fun validateValue(value: Any?, schema: Map<String, Any?>) {
        val type = schema["type"] as? String
        if (type != null) {
            validateType(value, type)
        }

        when (value) {
            is Map<*, *> -> validateObject(value, schema)
            is List<*> -> validateArray(value, schema)
        }

        val required = schema["required"] as? List<*>
        if (required != null && value is Map<*, *>) {
            for (key in required) {
                check(value.containsKey(key)) { "Required property '$key' is missing" }
            }
        }
    }

    private fun validateType(value: Any?, type: String) {
        val valid = when (type) {
            "string" -> value is String
            "integer" -> value is Int || value is Long
            "number" -> value is Number
            "boolean" -> value is Boolean
            "object" -> value is Map<*, *>
            "array" -> value is List<*>
            "null" -> value == null
            else -> true
        }
        check(valid) { "Expected type '$type' but was '${value?.javaClass?.simpleName ?: "null"}'" }
    }

    private fun validateObject(obj: Map<*, *>, schema: Map<String, Any?>) {
        val properties = schema["properties"] as? Map<*, *> ?: return
        for ((key, propSchema) in properties) {
            if (obj.containsKey(key)) {
                validateValue(obj[key], propSchema as Map<String, Any?>)
            }
        }
    }

    private fun validateArray(arr: List<*>, schema: Map<String, Any?>) {
        val items = schema["items"] as? Map<String, Any?> ?: return
        for (item in arr) {
            validateValue(item, items)
        }
    }
}
