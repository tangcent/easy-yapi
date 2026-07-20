package com.itangcent.easyapi.core.ide.search

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

    @Test
    fun testParsePatchMethod() {
        val query = ApiSearchQuery.parse("PATCH /api/users/1")
        assertNotNull(query.httpMethod)
        assertEquals("PATCH", query.httpMethod!!.name)
    }

    @Test
    fun testParseHeadMethod() {
        val query = ApiSearchQuery.parse("HEAD /api/users")
        assertNotNull(query.httpMethod)
        assertEquals("HEAD", query.httpMethod!!.name)
    }

    @Test
    fun testParseOptionsMethod() {
        val query = ApiSearchQuery.parse("OPTIONS /api/users")
        assertNotNull(query.httpMethod)
        assertEquals("OPTIONS", query.httpMethod!!.name)
    }

    @Test
    fun testParseHostPortPathWithQueryParams() {
        val query = ApiSearchQuery.parse("127.0.0.1:3000/api/users?id=1")
        assertEquals("/api/users", query.searchText)
        assertTrue(query.isPathQuery)
    }

    @Test
    fun testParseHostPortWithoutPath() {
        val query = ApiSearchQuery.parse("127.0.0.1:3000")
        assertEquals("/", query.searchText)
        assertTrue(query.isPathQuery)
    }

    @Test
    fun testParseUrlWithEmptyPath() {
        val query = ApiSearchQuery.parse("http://example.com")
        assertEquals("/", query.searchText)
        assertTrue(query.isPathQuery)
    }

    @Test
    fun testParseUrlWithOnlyRootPath() {
        val query = ApiSearchQuery.parse("http://example.com/")
        assertEquals("/", query.searchText)
        assertTrue(query.isPathQuery)
    }

    @Test
    fun testParsePathWithTrailingQueryMark() {
        val query = ApiSearchQuery.parse("/api/users?")
        // PATH_WITH_QUERY_PATTERN matches "/api/users?" and strips the query marker
        assertEquals("/api/users", query.searchText)
        assertTrue(query.isPathQuery)
    }

    @Test
    fun testParseMethodWithSimpleText() {
        val query = ApiSearchQuery.parse("GET users")
        assertNotNull(query.httpMethod)
        assertEquals("GET", query.httpMethod!!.name)
        assertEquals("users", query.searchText)
        assertFalse(query.isPathQuery)
    }

    @Test
    fun testParseInvalidUrl() {
        // Not a valid URI, not a host:port pattern, not a path
        val query = ApiSearchQuery.parse("not a url")
        assertEquals("not a url", query.searchText)
        assertFalse(query.isPathQuery)
    }
}
