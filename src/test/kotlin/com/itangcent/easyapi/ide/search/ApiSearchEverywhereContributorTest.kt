package com.itangcent.easyapi.ide.search

import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.exporter.model.HttpMetadata
import com.itangcent.easyapi.exporter.model.HttpMethod
import org.junit.Assert.*
import org.junit.Test

class ApiSearchEverywhereContributorTest {

    private fun createEndpoint(
        path: String,
        method: HttpMethod = HttpMethod.GET,
        name: String? = null,
        className: String? = null,
        description: String? = null
    ): ApiEndpoint {
        return ApiEndpoint(
            metadata = HttpMetadata(path = path, method = method),
            name = name,
            className = className,
            description = description
        )
    }

    @Test
    fun `matchesQuery with empty search text returns true`() {
        val endpoint = createEndpoint("/user")
        val query = ApiSearchQuery(null, "")
        
        assertTrue(matchesQuery(endpoint, query))
    }

    @Test
    fun `matchesQuery with matching HTTP method returns true`() {
        val endpoint = createEndpoint("/user", HttpMethod.GET)
        val query = ApiSearchQuery(HttpMethod.GET, "")
        
        assertTrue(matchesQuery(endpoint, query))
    }

    @Test
    fun `matchesQuery with non-matching HTTP method returns false`() {
        val endpoint = createEndpoint("/user", HttpMethod.GET)
        val query = ApiSearchQuery(HttpMethod.POST, "")
        
        assertFalse(matchesQuery(endpoint, query))
    }

    @Test
    fun `matchesQuery with matching path returns true`() {
        val endpoint = createEndpoint("/api/user")
        val query = ApiSearchQuery(null, "user")
        
        assertTrue(matchesQuery(endpoint, query))
    }

    @Test
    fun `matchesQuery with non-matching path returns false`() {
        val endpoint = createEndpoint("/api/admin")
        val query = ApiSearchQuery(null, "user")
        
        assertFalse(matchesQuery(endpoint, query))
    }

    @Test
    fun `matchesQuery with matching name returns true`() {
        val endpoint = createEndpoint("/user", name = "Get User")
        val query = ApiSearchQuery(null, "Get")
        
        assertTrue(matchesQuery(endpoint, query))
    }

    @Test
    fun `matchesQuery with matching className returns true`() {
        val endpoint = createEndpoint("/user", className = "UserController")
        val query = ApiSearchQuery(null, "Controller")
        
        assertTrue(matchesQuery(endpoint, query))
    }

    @Test
    fun `matchesQuery with matching description returns true`() {
        val endpoint = createEndpoint("/user", description = "Get user by ID")
        val query = ApiSearchQuery(null, "ID")
        
        assertTrue(matchesQuery(endpoint, query))
    }

    @Test
    fun `matchesQuery with HTTP method and matching path returns true`() {
        val endpoint = createEndpoint("/api/user", HttpMethod.POST)
        val query = ApiSearchQuery(HttpMethod.POST, "user")
        
        assertTrue(matchesQuery(endpoint, query))
    }

    @Test
    fun `matchesQuery with HTTP method and non-matching path returns false`() {
        val endpoint = createEndpoint("/api/user", HttpMethod.POST)
        val query = ApiSearchQuery(HttpMethod.POST, "admin")
        
        assertFalse(matchesQuery(endpoint, query))
    }

    @Test
    fun `matchesQuery with HTTP method mismatch and matching path returns false`() {
        val endpoint = createEndpoint("/api/user", HttpMethod.GET)
        val query = ApiSearchQuery(HttpMethod.POST, "user")
        
        assertFalse(matchesQuery(endpoint, query))
    }

    @Test
    fun `matchesQuery is case-insensitive`() {
        val endpoint = createEndpoint("/API/USER")
        val query = ApiSearchQuery(null, "user")
        
        assertTrue(matchesQuery(endpoint, query))
    }

    @Test
    fun `matchesQuery with partial path match returns true`() {
        val endpoint = createEndpoint("/api/v1/user/profile")
        val query = ApiSearchQuery(null, "user")
        
        assertTrue(matchesQuery(endpoint, query))
    }

    @Test
    fun `matchesQuery with null name does not crash`() {
        val endpoint = createEndpoint("/user", name = null)
        val query = ApiSearchQuery(null, "test")
        
        assertFalse(matchesQuery(endpoint, query))
    }

    @Test
    fun `matchesQuery with null className does not crash`() {
        val endpoint = createEndpoint("/user", className = null)
        val query = ApiSearchQuery(null, "test")
        
        assertFalse(matchesQuery(endpoint, query))
    }

    @Test
    fun `matchesQuery with null description does not crash`() {
        val endpoint = createEndpoint("/user", description = null)
        val query = ApiSearchQuery(null, "test")
        
        assertFalse(matchesQuery(endpoint, query))
    }

    private fun matchesQuery(endpoint: ApiEndpoint, query: ApiSearchQuery): Boolean {
        if (query.httpMethod != null && endpoint.httpMetadata?.method != query.httpMethod) {
            return false
        }

        if (query.searchText.isBlank()) {
            return true
        }

        val searchLower = query.searchText.lowercase()
        val path = endpoint.httpMetadata?.path ?: ""

        val matchesPath = path.lowercase().contains(searchLower)
        val matchesName = endpoint.name?.lowercase()?.contains(searchLower) == true
        val matchesClassName = endpoint.className?.lowercase()?.contains(searchLower) == true
        val matchesDescription = endpoint.description?.lowercase()?.contains(searchLower) == true
        val matchesFolder = endpoint.folder?.lowercase()?.contains(searchLower) == true

        return matchesPath || matchesName || matchesClassName || matchesDescription || matchesFolder
    }
}
