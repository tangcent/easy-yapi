package com.itangcent.idea.plugin.settings

/**
 * types:
 *      api-request: 1
 *      api-response: X
 *      example-request: 4
 *      example-response: 8
 */
enum class PostmanJson5FormatType(val desc: String, private val types: Int) {
    NONE("not use json5 anywhere", 0),
    REQUEST_ONLY("for request only", 1 or 4),
    RESPONSE_ONLY("for response only", 8),
    EXAMPLE_ONLY("for example only", 4 or 8),
    ALL("always use json5", 1 or 4 or 8);

    fun needUseJson5(type: Int): Boolean {
        return (this.types and type) != 0
    }
}