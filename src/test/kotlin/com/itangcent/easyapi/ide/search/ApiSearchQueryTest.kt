package com.itangcent.easyapi.ide.search

import com.itangcent.easyapi.exporter.model.HttpMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiSearchQueryTest {

    @Test
    fun `parse empty query`() {
        val query = ApiSearchQuery.parse("")
        assertNull(query.httpMethod)
        assertEquals("", query.searchText)
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
    }

    @Test
    fun `parse GET method query`() {
        val query = ApiSearchQuery.parse("GET /user")
        assertEquals(HttpMethod.GET, query.httpMethod)
        assertEquals("/user", query.searchText)
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
}
