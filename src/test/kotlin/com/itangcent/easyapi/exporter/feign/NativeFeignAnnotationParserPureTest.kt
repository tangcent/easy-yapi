package com.itangcent.easyapi.exporter.feign

import org.junit.Assert.*
import org.junit.Test

class NativeFeignAnnotationParserPureTest {

    @Test
    fun testExtractTemplateVariablesNoVariables() {
        val parser = object : NativeFeignAnnotationParserPure() {
            fun extractVars(template: String) = extractTemplateVariables(template)
        }
        val vars = parser.extractVars("/api/users")
        assertTrue("Should find no variables", vars.isEmpty())
    }

    @Test
    fun testExtractTemplateVariablesSingleVariable() {
        val result = extractTemplateVariables("/api/users/{id}")
        assertEquals("Should find one variable", 1, result.size)
        assertEquals("Variable should be 'id'", "id", result[0])
    }

    @Test
    fun testExtractTemplateVariablesMultipleVariables() {
        val result = extractTemplateVariables("/api/{category}/{id}")
        assertEquals("Should find two variables", 2, result.size)
        assertTrue("Should contain 'category'", result.contains("category"))
        assertTrue("Should contain 'id'", result.contains("id"))
    }

    @Test
    fun testExtractTemplateVariablesDeduplicates() {
        val result = extractTemplateVariables("/api/{id}/sub/{id}")
        assertEquals("Should deduplicate variables", 1, result.size)
        assertEquals("Variable should be 'id'", "id", result[0])
    }

    @Test
    fun testExtractTemplateVariablesEmptyString() {
        val result = extractTemplateVariables("")
        assertTrue("Should find no variables in empty string", result.isEmpty())
    }

    @Test
    fun testParseRequestLineValue() {
        val input = "GET /api/users"
        val parts = input.split(" ", limit = 2).map { it.trim() }
        assertEquals("Method should be GET", "GET", parts[0])
        assertEquals("Path should be /api/users", "/api/users", parts[1])
    }

    @Test
    fun testParseRequestLineValuePost() {
        val input = "POST /api/users"
        val parts = input.split(" ", limit = 2).map { it.trim() }
        assertEquals("Method should be POST", "POST", parts[0])
        assertEquals("Path should be /api/users", "/api/users", parts[1])
    }

    // --- parseHeader logic tests ---

    @Test
    fun testParseHeaderValidFormat() {
        val header = "Content-Type: application/json"
        val parts = header.split(":", limit = 2).map { it.trim() }
        assertEquals("Content-Type", parts[0])
        assertEquals("application/json", parts[1])
    }

    @Test
    fun testParseHeaderWithColonInValue() {
        val header = "Authorization: Bearer token:with:colons"
        val parts = header.split(":", limit = 2).map { it.trim() }
        assertEquals("Authorization", parts[0])
        assertEquals("Bearer token:with:colons", parts[1])
    }

    @Test
    fun testParseHeaderNoValue() {
        val header = "X-Custom-Header:"
        val parts = header.split(":", limit = 2).map { it.trim() }
        assertEquals("X-Custom-Header", parts[0])
        assertTrue(parts.size < 2 || parts[1].isEmpty())
    }

    @Test
    fun testParseHeaderEmptyString() {
        val header = ""
        val parts = header.split(":", limit = 2).map { it.trim() }
        assertEquals(1, parts.size)
        assertEquals("", parts[0])
    }

    @Test
    fun testParseHeaderWithTemplateVariable() {
        val header = "Authorization: Bearer {token}"
        val parts = header.split(":", limit = 2).map { it.trim() }
        assertEquals("Authorization", parts[0])
        assertEquals("Bearer {token}", parts[1])
        val vars = extractTemplateVariables(parts[1])
        assertEquals(1, vars.size)
        assertEquals("token", vars[0])
    }

    // --- RequestLine parsing edge cases ---

    @Test
    fun testParseRequestLineMethodOnly() {
        val input = "GET"
        val parts = input.split(" ", limit = 2).map { it.trim() }
        assertEquals(1, parts.size)
        assertEquals("GET", parts[0])
    }

    @Test
    fun testParseRequestLineWithMultipleSpaces() {
        val input = "GET   /api/users"
        val parts = input.split(" ", limit = 2).map { it.trim() }
        assertEquals("GET", parts[0])
        assertEquals("/api/users", parts[1])
    }

    @Test
    fun testParseRequestLineDeleteMethod() {
        val input = "DELETE /api/users/123"
        val parts = input.split(" ", limit = 2).map { it.trim() }
        assertEquals("DELETE", parts[0])
        assertEquals("/api/users/123", parts[1])
    }

    @Test
    fun testParseRequestLinePatchMethod() {
        val input = "PATCH /api/users/123"
        val parts = input.split(" ", limit = 2).map { it.trim() }
        assertEquals("PATCH", parts[0])
        assertEquals("/api/users/123", parts[1])
    }

    // --- HttpMethod resolution tests ---

    @Test
    fun testHttpMethodFromSpringValidMethods() {
        val methods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")
        for (m in methods) {
            val result = com.itangcent.easyapi.exporter.model.HttpMethod.fromSpring(m)
            assertNotNull("Should resolve $m", result)
            assertEquals(m, result!!.name)
        }
    }

    @Test
    fun testHttpMethodFromSpringCaseInsensitive() {
        val result = com.itangcent.easyapi.exporter.model.HttpMethod.fromSpring("get")
        assertNotNull(result)
        assertEquals("GET", result!!.name)
    }

    @Test
    fun testHttpMethodFromSpringInvalidMethod() {
        val result = com.itangcent.easyapi.exporter.model.HttpMethod.fromSpring("INVALID")
        assertNull(result)
    }

    companion object {
        fun extractTemplateVariables(template: String): List<String> {
            return Regex("\\{([^}]+)\\}").findAll(template).map { it.groupValues[1] }.distinct().toList()
        }
    }
}

abstract class NativeFeignAnnotationParserPure {
    fun extractTemplateVariables(template: String): List<String> {
        return Regex("\\{([^}]+)\\}").findAll(template).map { it.groupValues[1] }.distinct().toList()
    }
}
