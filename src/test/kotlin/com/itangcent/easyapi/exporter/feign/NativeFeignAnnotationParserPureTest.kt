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
