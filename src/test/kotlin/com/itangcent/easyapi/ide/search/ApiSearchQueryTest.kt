package com.itangcent.easyapi.ide.search

import com.itangcent.easyapi.exporter.model.HttpMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiSearchQueryTest {

    @Test
    fun `parse empty query`() {
        val query = ApiSearchQuery.parse("")
        assertNull(query.httpMethod)
        assertEquals("", query.searchText)
        assertFalse(query.isPathQuery)
    }

    @Test
    fun `parse whitespace query`() {
        val query = ApiSearchQuery.parse("   ")
        assertNull(query.httpMethod)
        assertEquals("", query.searchText)
    }

    @Test
    fun `parse plain text query`() {
        val query = ApiSearchQuery.parse("user")
        assertNull(query.httpMethod)
        assertEquals("user", query.searchText)
        assertFalse(query.isPathQuery)
    }

    @Test
    fun `parse GET method query`() {
        val query = ApiSearchQuery.parse("GET /user")
        assertEquals(HttpMethod.GET, query.httpMethod)
        assertEquals("/user", query.searchText)
        assertTrue(query.isPathQuery)
    }

    @Test
    fun `parse POST method query`() {
        val query = ApiSearchQuery.parse("POST /api/users")
        assertEquals(HttpMethod.POST, query.httpMethod)
        assertEquals("/api/users", query.searchText)
    }

    @Test
    fun `parse PUT method query`() {
        val query = ApiSearchQuery.parse("PUT user")
        assertEquals(HttpMethod.PUT, query.httpMethod)
        assertEquals("user", query.searchText)
        assertFalse(query.isPathQuery)
    }

    @Test
    fun `parse DELETE method query`() {
        val query = ApiSearchQuery.parse("DELETE /user/{id}")
        assertEquals(HttpMethod.DELETE, query.httpMethod)
        assertEquals("/user/{id}", query.searchText)
    }

    @Test
    fun `parse PATCH method query`() {
        val query = ApiSearchQuery.parse("PATCH /user")
        assertEquals(HttpMethod.PATCH, query.httpMethod)
        assertEquals("/user", query.searchText)
    }

    @Test
    fun `parse case-insensitive method`() {
        val query = ApiSearchQuery.parse("get /user")
        assertEquals(HttpMethod.GET, query.httpMethod)
        assertEquals("/user", query.searchText)
    }

    @Test
    fun `parse mixed case method`() {
        val query = ApiSearchQuery.parse("PoSt /user")
        assertEquals(HttpMethod.POST, query.httpMethod)
        assertEquals("/user", query.searchText)
    }

    @Test
    fun `parse method with extra spaces`() {
        val query = ApiSearchQuery.parse("GET   /user")
        assertEquals(HttpMethod.GET, query.httpMethod)
        assertEquals("/user", query.searchText)
    }

    @Test
    fun `parse query with leading and trailing spaces`() {
        val query = ApiSearchQuery.parse("  GET /user  ")
        assertEquals(HttpMethod.GET, query.httpMethod)
        assertEquals("/user", query.searchText)
    }

    @Test
    fun `parse HEAD method query`() {
        val query = ApiSearchQuery.parse("HEAD /user")
        assertEquals(HttpMethod.HEAD, query.httpMethod)
        assertEquals("/user", query.searchText)
    }

    @Test
    fun `parse OPTIONS method query`() {
        val query = ApiSearchQuery.parse("OPTIONS /user")
        assertEquals(HttpMethod.OPTIONS, query.httpMethod)
        assertEquals("/user", query.searchText)
    }

    // --- URL parsing tests ---

    @Test
    fun `parse full URL with query parameters`() {
        val query = ApiSearchQuery.parse("http://127.0.0.1:3000/api/interface/get?id=116")
        assertNull(query.httpMethod)
        assertEquals("/api/interface/get", query.searchText)
        assertTrue(query.isPathQuery)
    }

    @Test
    fun `parse full URL without query parameters`() {
        val query = ApiSearchQuery.parse("http://127.0.0.1:3000/api/users")
        assertNull(query.httpMethod)
        assertEquals("/api/users", query.searchText)
        assertTrue(query.isPathQuery)
    }

    @Test
    fun `parse HTTPS URL`() {
        val query = ApiSearchQuery.parse("https://example.com/api/users")
        assertNull(query.httpMethod)
        assertEquals("/api/users", query.searchText)
        assertTrue(query.isPathQuery)
    }

    @Test
    fun `parse URL with no path`() {
        val query = ApiSearchQuery.parse("http://127.0.0.1:3000")
        assertNull(query.httpMethod)
        assertEquals("/", query.searchText)
        assertTrue(query.isPathQuery)
    }

    @Test
    fun `parse URL with method prefix`() {
        val query = ApiSearchQuery.parse("GET http://127.0.0.1:3000/api/users?id=1")
        assertEquals(HttpMethod.GET, query.httpMethod)
        assertEquals("/api/users", query.searchText)
        assertTrue(query.isPathQuery)
    }

    @Test
    fun `parse URL with port and complex path`() {
        val query = ApiSearchQuery.parse("http://localhost:8080/api/v1/users/123/profile")
        assertNull(query.httpMethod)
        assertEquals("/api/v1/users/123/profile", query.searchText)
        assertTrue(query.isPathQuery)
    }

    @Test
    fun `parse plain path is treated as path query`() {
        val query = ApiSearchQuery.parse("/api/users")
        assertNull(query.httpMethod)
        assertEquals("/api/users", query.searchText)
        assertTrue(query.isPathQuery)
    }

    @Test
    fun `parse grpc URL`() {
        val query = ApiSearchQuery.parse("grpc://localhost:8080/my.service.Method")
        assertNull(query.httpMethod)
        assertEquals("/my.service.Method", query.searchText)
        assertTrue(query.isPathQuery)
    }

    @Test
    fun `parse grpcs URL`() {
        val query = ApiSearchQuery.parse("grpcs://example.com/my.package.Service/Method")
        assertNull(query.httpMethod)
        assertEquals("/my.package.Service/Method", query.searchText)
        assertTrue(query.isPathQuery)
    }

    @Test
    fun `parse grpc URL with query parameters`() {
        val query = ApiSearchQuery.parse("grpc://localhost:8080/my.Service/Method?timeout=5s")
        assertNull(query.httpMethod)
        assertEquals("/my.Service/Method", query.searchText)
        assertTrue(query.isPathQuery)
    }

    // --- Scheme-less URL tests ---

    @Test
    fun `parse scheme-less URL with port and query`() {
        val query = ApiSearchQuery.parse("127.0.0.1:3000/api/interface/list_menu?project_id=11")
        assertNull(query.httpMethod)
        assertEquals("/api/interface/list_menu", query.searchText)
        assertTrue(query.isPathQuery)
    }

    @Test
    fun `parse scheme-less URL with port`() {
        val query = ApiSearchQuery.parse("127.0.0.1:3000/api/interface/list_menu")
        assertNull(query.httpMethod)
        assertEquals("/api/interface/list_menu", query.searchText)
        assertTrue(query.isPathQuery)
    }

    @Test
    fun `parse localhost URL with port`() {
        val query = ApiSearchQuery.parse("localhost:8080/api/users")
        assertNull(query.httpMethod)
        assertEquals("/api/users", query.searchText)
        assertTrue(query.isPathQuery)
    }

    // --- Path with query params tests ---

    @Test
    fun `parse path with query parameters`() {
        val query = ApiSearchQuery.parse("/api/interface/list_menu?project_id=11")
        assertNull(query.httpMethod)
        assertEquals("/api/interface/list_menu", query.searchText)
        assertTrue(query.isPathQuery)
    }

    @Test
    fun `parse path without query parameters`() {
        val query = ApiSearchQuery.parse("/api/interface/list_menu")
        assertNull(query.httpMethod)
        assertEquals("/api/interface/list_menu", query.searchText)
        assertTrue(query.isPathQuery)
    }

    // --- Path variable matching tests ---

    @Test
    fun `path variable matching - concrete path matches pattern`() {
        val regex = pathPatternToRegex("/api/interface/{id}")
        assertTrue(regex.matches("/api/interface/116"))
        assertTrue(regex.matches("/api/interface/abc"))
        assertFalse(regex.matches("/api/interface/116/sub"))
        assertFalse(regex.matches("/api/interface"))
    }

    @Test
    fun `path variable matching - multiple variables`() {
        val regex = pathPatternToRegex("/api/{version}/users/{id}")
        assertTrue(regex.matches("/api/v1/users/123"))
        assertTrue(regex.matches("/api/v2/users/abc"))
        assertFalse(regex.matches("/api/users/123"))
        assertFalse(regex.matches("/api/v1/users/123/extra"))
    }

    @Test
    fun `path variable matching - no variables`() {
        val regex = pathPatternToRegex("/api/users/list")
        assertTrue(regex.matches("/api/users/list"))
        assertFalse(regex.matches("/api/users/other"))
    }

    @Test
    fun `path variable matching - variable at end`() {
        val regex = pathPatternToRegex("/api/users/{userId}")
        assertTrue(regex.matches("/api/users/42"))
        assertFalse(regex.matches("/api/users"))
    }

    @Test
    fun `path variable matching - special chars in path`() {
        val regex = pathPatternToRegex("/api/v1/user-profile/{id}")
        assertTrue(regex.matches("/api/v1/user-profile/123"))
    }

    private fun pathPatternToRegex(pattern: String): Regex {
        val parts = pattern.split(Regex("\\{[^}]*\\}"))
        val regexStr = parts.joinToString("[^/]+") { Regex.escape(it) }
        return Regex("^$regexStr$")
    }
}
