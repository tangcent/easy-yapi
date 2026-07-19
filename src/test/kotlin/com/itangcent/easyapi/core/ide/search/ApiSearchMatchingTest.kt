package com.itangcent.easyapi.core.ide.search

import com.itangcent.easyapi.core.export.*
import com.itangcent.easyapi.core.export.httpMetadata
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for ApiSearchEverywhereContributor's matching logic.
 * Uses reflection to test private methods since they contain the core branch logic.
 */
class ApiSearchMatchingTest {

    private val contributorClass = ApiSearchEverywhereContributor::class.java

    private fun matchesQuery(endpoint: ApiEndpoint, query: ApiSearchQuery): Boolean {
        val method = contributorClass.getDeclaredMethod(
            "matchesQuery", ApiEndpoint::class.java, ApiSearchQuery::class.java
        )
        method.isAccessible = true
        // Need an instance - create with null project (only used for apiIndex which we don't call)
        val constructor = contributorClass.getConstructor(com.intellij.openapi.project.Project::class.java)
        // Use a mock project
        val mockProject = org.mockito.Mockito.mock(com.intellij.openapi.project.Project::class.java)
        val instance = constructor.newInstance(mockProject)
        return method.invoke(instance, endpoint, query) as Boolean
    }

    private fun matchesPathWithVariables(concretePath: String, patternPath: String): Boolean {
        val method = contributorClass.getDeclaredMethod(
            "matchesPathWithVariables", String::class.java, String::class.java
        )
        method.isAccessible = true
        val constructor = contributorClass.getConstructor(com.intellij.openapi.project.Project::class.java)
        val mockProject = org.mockito.Mockito.mock(com.intellij.openapi.project.Project::class.java)
        val instance = constructor.newInstance(mockProject)
        return method.invoke(instance, concretePath, patternPath) as Boolean
    }

    // --- matchesQuery tests ---

    @Test
    fun testMatchesQueryWithHttpMethodFilter() {
        val endpoint = ApiEndpoint(
            name = "getUser",
            metadata = httpMetadata(path = "/api/users", method = HttpMethod.GET)
        )
        val query = ApiSearchQuery(HttpMethod.GET, "/api/users", true)
        assertTrue("Should match GET method", matchesQuery(endpoint, query))
    }

    @Test
    fun testMatchesQueryWithWrongHttpMethod() {
        val endpoint = ApiEndpoint(
            name = "createUser",
            metadata = httpMetadata(path = "/api/users", method = HttpMethod.POST)
        )
        val query = ApiSearchQuery(HttpMethod.GET, "/api/users", true)
        assertFalse("Should not match different method", matchesQuery(endpoint, query))
    }

    @Test
    fun testMatchesQueryWithNullMethod() {
        val endpoint = ApiEndpoint(
            name = "getUser",
            metadata = httpMetadata(path = "/api/users", method = HttpMethod.GET)
        )
        val query = ApiSearchQuery(null, "/api/users", true)
        assertTrue("Should match when no method filter", matchesQuery(endpoint, query))
    }

    @Test
    fun testMatchesQueryWithBlankSearchText() {
        val endpoint = ApiEndpoint(
            name = "getUser",
            metadata = httpMetadata(path = "/api/users", method = HttpMethod.GET)
        )
        val query = ApiSearchQuery(null, "", false)
        assertTrue("Should match blank search text", matchesQuery(endpoint, query))
    }

    @Test
    fun testMatchesQueryByPath() {
        val endpoint = ApiEndpoint(
            name = "getUser",
            metadata = httpMetadata(path = "/api/users", method = HttpMethod.GET)
        )
        val query = ApiSearchQuery(null, "users", false)
        assertTrue("Should match by path substring", matchesQuery(endpoint, query))
    }

    @Test
    fun testMatchesQueryByName() {
        val endpoint = ApiEndpoint(
            name = "getUser",
            metadata = httpMetadata(path = "/api/users", method = HttpMethod.GET)
        )
        val query = ApiSearchQuery(null, "getUser", false)
        assertTrue("Should match by name", matchesQuery(endpoint, query))
    }

    @Test
    fun testMatchesQueryByClassName() {
        val endpoint = ApiEndpoint(
            name = "getUser",
            className = "com.example.UserController",
            metadata = httpMetadata(path = "/api/users", method = HttpMethod.GET)
        )
        val query = ApiSearchQuery(null, "UserController", false)
        assertTrue("Should match by class name", matchesQuery(endpoint, query))
    }

    @Test
    fun testMatchesQueryByDescription() {
        val endpoint = ApiEndpoint(
            name = "getUser",
            description = "Get user by ID",
            metadata = httpMetadata(path = "/api/users", method = HttpMethod.GET)
        )
        val query = ApiSearchQuery(null, "user by ID", false)
        assertTrue("Should match by description", matchesQuery(endpoint, query))
    }

    @Test
    fun testMatchesQueryByFolder() {
        val endpoint = ApiEndpoint(
            name = "getUser",
            folder = "User Management",
            metadata = httpMetadata(path = "/api/users", method = HttpMethod.GET)
        )
        val query = ApiSearchQuery(null, "management", false)
        assertTrue("Should match by folder", matchesQuery(endpoint, query))
    }

    @Test
    fun testMatchesQueryNoMatch() {
        val endpoint = ApiEndpoint(
            name = "getUser",
            metadata = httpMetadata(path = "/api/users", method = HttpMethod.GET)
        )
        val query = ApiSearchQuery(null, "orders", false)
        assertFalse("Should not match unrelated query", matchesQuery(endpoint, query))
    }

    @Test
    fun testMatchesQueryWithGrpcMetadata() {
        val endpoint = ApiEndpoint(
            name = "GetUser",
            metadata = GrpcMetadata(
                path = "/com.example.UserService/GetUser",
                serviceName = "UserService",
                methodName = "GetUser",
                packageName = "com.example",
                streamingType = GrpcStreamingType.UNARY
            )
        )
        val query = ApiSearchQuery(null, "UserService", false)
        assertTrue("Should match gRPC endpoint by path", matchesQuery(endpoint, query))
    }

    @Test
    fun testMatchesQueryPathWithVariables() {
        val endpoint = ApiEndpoint(
            name = "getUser",
            metadata = httpMetadata(path = "/api/users/{id}", method = HttpMethod.GET)
        )
        val query = ApiSearchQuery(null, "/api/users/123", true)
        assertTrue("Should match path with variables", matchesQuery(endpoint, query))
    }

    @Test
    fun testMatchesQueryPathWithVariablesNoMatch() {
        val endpoint = ApiEndpoint(
            name = "getUser",
            metadata = httpMetadata(path = "/api/users/{id}", method = HttpMethod.GET)
        )
        val query = ApiSearchQuery(null, "/api/orders/123", true)
        assertFalse("Should not match different path pattern", matchesQuery(endpoint, query))
    }

    @Test
    fun testMatchesQueryCaseInsensitive() {
        val endpoint = ApiEndpoint(
            name = "GetUser",
            metadata = httpMetadata(path = "/API/Users", method = HttpMethod.GET)
        )
        val query = ApiSearchQuery(null, "getuser", false)
        assertTrue("Should match case-insensitively", matchesQuery(endpoint, query))
    }

    // --- matchesPathWithVariables tests ---

    @Test
    fun testMatchesPathWithSingleVariable() {
        assertTrue(matchesPathWithVariables("/api/users/123", "/api/users/{id}"))
    }

    @Test
    fun testMatchesPathWithMultipleVariables() {
        assertTrue(matchesPathWithVariables("/api/users/123/orders/456", "/api/users/{userId}/orders/{orderId}"))
    }

    @Test
    fun testMatchesPathNoVariable() {
        assertTrue(matchesPathWithVariables("/api/users", "/api/users"))
    }

    @Test
    fun testMatchesPathWrongLength() {
        assertFalse(matchesPathWithVariables("/api/users/123/extra", "/api/users/{id}"))
    }

    @Test
    fun testMatchesPathDifferentPrefix() {
        assertFalse(matchesPathWithVariables("/api/orders/123", "/api/users/{id}"))
    }

    @Test
    fun testMatchesPathTrailingVariable() {
        assertTrue(matchesPathWithVariables("/api/users/123", "/api/users/{id}"))
    }
}
