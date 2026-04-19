package com.itangcent.easyapi.exporter.yapi

import com.itangcent.easyapi.exporter.model.ApiParameter
import com.itangcent.easyapi.psi.type.JsonType

/**
 * Generator for mock data values based on parameter names and types.
 * 
 * This generator produces Mock.js-compatible mock expressions for
 * generating realistic test data. It uses naming conventions and
 * type information to determine appropriate mock values.
 * 
 * Examples:
 * - "email" field -> "@email"
 * - "phone" field -> "@phone"
 * - "age" field -> "@integer(0, 120)"
 * 
 * @param mockRules Custom rules for overriding default behavior
 */
class MockDataGenerator(
    private val mockRules: Map<String, String> = emptyMap()
) {
    /**
     * Generates mock data for an API parameter.
     * Uses custom rules if available, otherwise falls back to type/name heuristics.
     * 
     * @param param The API parameter
     * @return A mock expression string, or null if unable to determine
     */
    fun mockFor(param: ApiParameter): String? {
        val type = param.jsonType ?: param.type.rawType()
        val name = param.name.lowercase()
        
        return mockByRules(param) ?: mockByTypeName(type, name)
    }

    /**
     * Generates mock data for a path parameter by name.
     * 
     * @param name The parameter name
     * @return A mock expression string, or null
     */
    fun mockForParam(name: String): String? {
        val lowerName = name.lowercase()
        return mockByName(lowerName)
            ?: mockRules["param.$name"] 
            ?: mockRules["*.$name"]
    }

    /**
     * Generates mock data for a query parameter by name.
     * 
     * @param name The parameter name
     * @return A mock expression string, or null
     */
    fun mockForQuery(name: String): String? {
        val lowerName = name.lowercase()
        return mockByName(lowerName)
            ?: mockRules["query.$name"] 
            ?: mockRules["*.$name"]
    }

    /**
     * Generates mock data using custom rules.
     * Tries various pattern matches against the rules.
     * 
     * @param param The API parameter
     * @return A mock expression string, or null
     */
    private fun mockByRules(param: ApiParameter): String? {
        val name = param.name
        val type = param.jsonType ?: param.type.rawType()
        
        val keyPatterns = listOf(
            "${name}|$type",
            "*.$name|$type",
            "*.$name",
            "*|$type",
            name
        )

        for (pattern in keyPatterns) {
            mockRules[pattern]?.let { return it }
        }

        return null
    }

    /**
     * Generates mock data based on type and name.
     * Falls back to type-based generation if name matching fails.
     * 
     * @param type The JSON type
     * @param name The parameter name
     * @return A mock expression string, or null
     */
    private fun mockByTypeName(type: String, name: String): String? {
        val lowerName = name.lowercase()
        
        return mockByName(lowerName)
            ?: mockByJsonType(type)
    }
    
    /**
     * Generates mock data based on parameter name patterns.
     * Uses common naming conventions to infer appropriate mock values.
     * 
     * @param name The parameter name (lowercased)
     * @return A mock expression string, or null
     */
    private fun mockByName(name: String): String? {
        return when {
            name.contains("email") -> "@email"
            name.contains("phone") || name.contains("mobile") || name.contains("tel") -> "@phone"
            name.contains("url") || name.contains("link") || name.contains("website") -> "@url"
            name.contains("uuid") || name.contains("guid") -> "@uuid"
            name.contains("name") && (name.contains("user") || name.contains("first") || name.contains("last")) -> "@cname"
            name.contains("name") -> "@string"
            name.contains("address") -> "@county(true)"
            name.contains("city") -> "@city"
            name.contains("country") -> "@county"
            name.contains("province") || name.contains("state") -> "@province"
            name.contains("zip") || name.contains("postal") -> "@zip"
            name.contains("ip") -> "@ip"
            name.contains("id") && !name.contains("identity") -> "@id"
            name.contains("date") && name.contains("birth") -> "@date('yyyy-MM-dd')"
            name.contains("date") || name.contains("time") -> "@datetime"
            name.contains("age") -> "@integer(0, 120)"
            name.contains("price") || name.contains("amount") || name.contains("money") -> "@float(0, 10000, 2, 2)"
            name.contains("count") || name.contains("num") || name.contains("number") || name.contains("qty") || name.contains("quantity") -> "@integer(0, 100)"
            name.contains("password") || name.contains("pwd") -> "******"
            name.contains("token") || name.contains("key") -> "@string(32)"
            name.contains("image") || name.contains("img") || name.contains("avatar") || name.contains("photo") -> "@image"
            name.contains("desc") || name.contains("description") || name.contains("content") || name.contains("remark") || name.contains("comment") -> "@cparagraph"
            name.contains("title") -> "@ctitle"
            name.contains("code") -> "@string(6)"
            else -> null
        }
    }

    /**
     * Generates mock data based on JSON type.
     * Provides basic mock expressions for each primitive type.
     * 
     * @param type The JSON type string
     * @return A mock expression string, or null
     */
    private fun mockByJsonType(type: String): String? {
        return when (type) {
            JsonType.STRING -> "@string"
            JsonType.SHORT -> "@integer(0, 32767)"
            JsonType.INT, "integer" -> "@integer"
            JsonType.LONG -> "@integer"
            JsonType.FLOAT -> "@float"
            JsonType.DOUBLE -> "@float"
            JsonType.BOOLEAN -> "@boolean"
            JsonType.ARRAY -> "@array"
            JsonType.OBJECT -> "@object"
            JsonType.FILE -> "@file"
            JsonType.DATE -> "@date"
            JsonType.DATETIME -> "@datetime"
            "text" -> "@string"
            "file" -> "@file"
            else -> null
        }
    }
}
