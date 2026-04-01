package com.itangcent.easyapi.util

import org.junit.Assert.*
import org.junit.Test

class PathVariablePatternTest {

    @Test
    fun testParsePathVariableWithSingleValue() {
        val pattern = PathVariablePattern.parsePathVariable("category:electronics")
        
        assertNotNull(pattern)
        assertEquals("category", pattern!!.name)
        assertEquals(listOf("electronics"), pattern.possibleValues)
        assertEquals("electronics", pattern.defaultValue)
    }

    @Test
    fun testParsePathVariableWithMultipleValues() {
        val pattern = PathVariablePattern.parsePathVariable("status:active|inactive")
        
        assertNotNull(pattern)
        assertEquals("status", pattern!!.name)
        assertEquals(listOf("active", "inactive"), pattern.possibleValues)
        assertEquals("active", pattern.defaultValue)
    }

    @Test
    fun testParsePathVariableWithMultipleValuesAndSpaces() {
        val pattern = PathVariablePattern.parsePathVariable("type: admin | user | guest")
        
        assertNotNull(pattern)
        assertEquals("type", pattern!!.name)
        assertEquals(listOf("admin", "user", "guest"), pattern.possibleValues)
        assertEquals("admin", pattern.defaultValue)
    }

    @Test
    fun testParsePathVariableWithoutColon() {
        val pattern = PathVariablePattern.parsePathVariable("category")
        
        assertNull(pattern)
    }

    @Test
    fun testExtractPathVariablesFromPath() {
        val path = "/api/{category:electronics}/products"
        val patterns = PathVariablePattern.extractPathVariablesFromPath(path)
        
        assertEquals(1, patterns.size)
        assertEquals("category", patterns[0].name)
        assertEquals(listOf("electronics"), patterns[0].possibleValues)
        assertEquals("electronics", patterns[0].defaultValue)
    }

    @Test
    fun testExtractPathVariablesFromPathWithMultipleValues() {
        val path = "/api/{status:active|inactive}/items"
        val patterns = PathVariablePattern.extractPathVariablesFromPath(path)
        
        assertEquals(1, patterns.size)
        assertEquals("status", patterns[0].name)
        assertEquals(listOf("active", "inactive"), patterns[0].possibleValues)
        assertEquals("active", patterns[0].defaultValue)
    }

    @Test
    fun testExtractMultiplePathVariablesFromPath() {
        val path = "/api/{version:v1|v2}/users/{id:123}/profile"
        val patterns = PathVariablePattern.extractPathVariablesFromPath(path)
        
        assertEquals(2, patterns.size)
        
        assertEquals("version", patterns[0].name)
        assertEquals(listOf("v1", "v2"), patterns[0].possibleValues)
        assertEquals("v1", patterns[0].defaultValue)
        
        assertEquals("id", patterns[1].name)
        assertEquals(listOf("123"), patterns[1].possibleValues)
        assertEquals("123", patterns[1].defaultValue)
    }

    @Test
    fun testNormalizePathWithPattern() {
        val path = "/api/{category:electronics}/products"
        val normalized = PathVariablePattern.normalizePath(path)
        
        assertEquals("/api/{category}/products", normalized)
    }

    @Test
    fun testNormalizePathWithMultipleValues() {
        val path = "/api/{status:active|inactive}/items"
        val normalized = PathVariablePattern.normalizePath(path)
        
        assertEquals("/api/{status}/items", normalized)
    }

    @Test
    fun testNormalizePathWithMultipleVariables() {
        val path = "/api/{version:v1|v2}/users/{id:123}/profile"
        val normalized = PathVariablePattern.normalizePath(path)
        
        assertEquals("/api/{version}/users/{id}/profile", normalized)
    }

    @Test
    fun testNormalizePathWithoutPattern() {
        val path = "/api/users/{id}/profile"
        val normalized = PathVariablePattern.normalizePath(path)
        
        assertEquals("/api/users/{id}/profile", normalized)
    }

    @Test
    fun testExtractPathVariablesFromPathWithoutPattern() {
        val path = "/api/users/{id}/profile"
        val patterns = PathVariablePattern.extractPathVariablesFromPath(path)
        
        assertEquals(0, patterns.size)
    }
}
