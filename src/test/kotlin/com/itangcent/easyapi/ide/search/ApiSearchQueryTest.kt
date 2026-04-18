package com.itangcent.easyapi.ide.search

import org.junit.Assert.*
import org.junit.Test

class ApiSearchQueryTest {

    @Test
    fun testParseSimpleText() {
        val query = ApiSearchQuery.parse("users")
        assertNull("HTTP method should be null for simple text", query.httpMethod)
        assertEquals("Search text should be 'users'", "users", query.searchText)
        assertFalse("Should not be a path query", query.isPathQuery)
    }

    @Test
    fun testParseGetMethod() {
        val query = ApiSearchQuery.parse("GET /api/users")
        assertNotNull("HTTP method should not be null", query.httpMethod)
        assertEquals("Method should be GET", "GET", query.httpMethod!!.name)
        assertEquals("Search text should be '/api/users'", "/api/users", query.searchText)
        assertTrue("Should be a path query", query.isPathQuery)
    }

    @Test
    fun testParsePostMethod() {
        val query = ApiSearchQuery.parse("POST /api/users")
        assertNotNull("HTTP method should not be null", query.httpMethod)
        assertEquals("Method should be POST", "POST", query.httpMethod!!.name)
        assertEquals("Search text should be '/api/users'", "/api/users", query.searchText)
    }

    @Test
    fun testParsePutMethod() {
        val query = ApiSearchQuery.parse("PUT /api/users/1")
        assertNotNull("HTTP method should not be null", query.httpMethod)
        assertEquals("Method should be PUT", "PUT", query.httpMethod!!.name)
    }

    @Test
    fun testParseDeleteMethod() {
        val query = ApiSearchQuery.parse("DELETE /api/users/1")
        assertNotNull("HTTP method should not be null", query.httpMethod)
        assertEquals("Method should be DELETE", "DELETE", query.httpMethod!!.name)
    }

    @Test
    fun testParseCaseInsensitiveMethod() {
        val query = ApiSearchQuery.parse("get /api/users")
        assertNotNull("HTTP method should not be null for lowercase", query.httpMethod)
        assertEquals("Method should be GET", "GET", query.httpMethod!!.name)
    }

    @Test
    fun testParsePathWithQueryParams() {
        val query = ApiSearchQuery.parse("/api/users?id=1")
        assertEquals("Should strip query params", "/api/users", query.searchText)
        assertTrue("Should be a path query", query.isPathQuery)
    }

    @Test
    fun testParseFullUrl() {
        val query = ApiSearchQuery.parse("http://127.0.0.1:3000/api/interface/get?id=116")
        assertEquals("Should extract path from URL", "/api/interface/get", query.searchText)
        assertTrue("Should be a path query", query.isPathQuery)
    }

    @Test
    fun testParseHttpsUrl() {
        val query = ApiSearchQuery.parse("https://example.com/users")
        assertEquals("Should extract path from HTTPS URL", "/users", query.searchText)
        assertTrue("Should be a path query", query.isPathQuery)
    }

    @Test
    fun testParseHostPortPath() {
        val query = ApiSearchQuery.parse("127.0.0.1:3000/api/users")
        assertEquals("Should extract path from host:port/path", "/api/users", query.searchText)
        assertTrue("Should be a path query", query.isPathQuery)
    }

    @Test
    fun testParseSimplePath() {
        val query = ApiSearchQuery.parse("/api/users")
        assertEquals("Should preserve simple path", "/api/users", query.searchText)
        assertTrue("Should be a path query", query.isPathQuery)
    }

    @Test
    fun testParseEmptyString() {
        val query = ApiSearchQuery.parse("")
        assertEquals("Empty string should give empty search text", "", query.searchText)
        assertNull("HTTP method should be null", query.httpMethod)
    }

    @Test
    fun testParseWhitespace() {
        val query = ApiSearchQuery.parse("  GET  /api/users  ")
        assertNotNull("Should handle whitespace", query.httpMethod)
        assertEquals("Method should be GET", "GET", query.httpMethod!!.name)
    }
}
