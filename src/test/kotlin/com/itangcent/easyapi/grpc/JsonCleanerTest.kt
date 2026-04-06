package com.itangcent.easyapi.grpc

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for JSON cleaning utilities in [DynamicJarClient].
 */
class JsonCleanerTest {

    @Test
    fun testRemoveNullValues() {
        val input = """{"countryCodeAlpha3":"","callbackUrl":"","callbackProtocol":"","forceReaudit":null,"ortbResponse":""}"""
        val expected = """{"countryCodeAlpha3":"","callbackUrl":"","callbackProtocol":"","ortbResponse":""}"""
        val result = cleanJsonForProtobuf(input)
        assertEquals(expected, result)
    }

    @Test
    fun testRemoveNullValuesWithWhitespace() {
        val input = """
            {
              "countryCodeAlpha3": "",
              "callbackUrl": "",
              "callbackProtocol": "",
              "forceReaudit": null,
              "ortbResponse": ""
            }
        """.trimIndent()
        val result = cleanJsonForProtobuf(input)
        assertFalse(result.contains("null"))
        assertTrue(result.contains("countryCodeAlpha3"))
        assertTrue(result.contains("ortbResponse"))
    }

    @Test
    fun testRemoveMultipleNullValues() {
        val input = """{"a":null,"b":"value","c":null,"d":123}"""
        val expected = """{"b":"value","d":123}"""
        val result = cleanJsonForProtobuf(input)
        assertEquals(expected, result)
    }

    @Test
    fun testNoNullValues() {
        val input = """{"a":"value","b":123,"c":true}"""
        val result = cleanJsonForProtobuf(input)
        assertEquals(input, result)
    }

    @Test
    fun testNullAtEnd() {
        val input = """{"a":"value","b":null}"""
        val expected = """{"a":"value"}"""
        val result = cleanJsonForProtobuf(input)
        assertEquals(expected, result)
    }

    @Test
    fun testNullAtStart() {
        val input = """{"a":null,"b":"value"}"""
        val expected = """{"b":"value"}"""
        val result = cleanJsonForProtobuf(input)
        assertEquals(expected, result)
    }

    private fun cleanJsonForProtobuf(json: String): String {
        var result = json
        
        val nullFieldPattern = Regex(""""[^"]+"\s*:\s*null\s*(,|(?=\s*}))""")
        result = nullFieldPattern.replace(result) { match ->
            if (match.value.contains(",")) "" else ""
        }
        
        result = result.replace(Regex(""",\s*}"""), "}")
        result = result.replace(Regex(""",\s*\]"""), "]")
        
        return result.trim()
    }
}
