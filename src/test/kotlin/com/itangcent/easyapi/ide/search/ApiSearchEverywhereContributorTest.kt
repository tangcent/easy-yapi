package com.itangcent.easyapi.ide.search

import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.GrpcMetadata
import com.itangcent.easyapi.exporter.model.GrpcStreamingType
import com.itangcent.easyapi.exporter.model.HttpMetadata
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.httpMetadata
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
            metadata = httpMetadata(path = path, method = method),
            name = name,
            className = className,
            description = description
        )
    }

    private fun createGrpcEndpoint(
        path: String,
        serviceName: String,
        methodName: String,
        packageName: String,
        name: String? = null,
        className: String? = null
    ): ApiEndpoint {
        return ApiEndpoint(
            metadata = GrpcMetadata(
                path = path,
                serviceName = serviceName,
                methodName = methodName,
                packageName = packageName,
                streamingType = GrpcStreamingType.UNARY
            ),
            name = name,
            className = className
        )
    }

    // --- Basic matching tests ---

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

    // --- URL-based search tests ---

    @Test
    fun `matchesQuery with full URL extracts path and matches`() {
        val endpoint = createEndpoint("/api/interface/get", HttpMethod.GET)
        val query = ApiSearchQuery.parse("http://127.0.0.1:3000/api/interface/get?id=116")

        assertTrue(matchesQuery(endpoint, query))
    }

    @Test
    fun `matchesQuery with full URL does not match different path`() {
        val endpoint = createEndpoint("/api/interface/list", HttpMethod.GET)
        val query = ApiSearchQuery.parse("http://127.0.0.1:3000/api/interface/get?id=116")

        assertFalse(matchesQuery(endpoint, query))
    }

    @Test
    fun `matchesQuery with HTTPS URL extracts path and matches`() {
        val endpoint = createEndpoint("/api/users", HttpMethod.GET)
        val query = ApiSearchQuery.parse("https://example.com/api/users")

        assertTrue(matchesQuery(endpoint, query))
    }

    @Test
    fun `matchesQuery with scheme-less URL extracts path and matches`() {
        val endpoint = createEndpoint("/api/interface/list_menu", HttpMethod.GET)
        val query = ApiSearchQuery.parse("127.0.0.1:3000/api/interface/list_menu?project_id=11")

        assertTrue(matchesQuery(endpoint, query))
    }

    @Test
    fun `matchesQuery with path and query params strips and matches`() {
        val endpoint = createEndpoint("/api/interface/list_menu", HttpMethod.GET)
        val query = ApiSearchQuery.parse("/api/interface/list_menu?project_id=11")

        assertTrue(matchesQuery(endpoint, query))
    }

    @Test
    fun `matchesQuery with URL and method prefix filters method`() {
        val endpoint = createEndpoint("/api/users", HttpMethod.POST)
        val query = ApiSearchQuery.parse("GET http://127.0.0.1:3000/api/users")

        assertFalse(matchesQuery(endpoint, query))
    }

    @Test
    fun `matchesQuery with URL and matching method prefix`() {
        val endpoint = createEndpoint("/api/users", HttpMethod.GET)
        val query = ApiSearchQuery.parse("GET http://127.0.0.1:3000/api/users")

        assertTrue(matchesQuery(endpoint, query))
    }

    @Test
    fun `matchesQuery with URL matches by path contains when path variable match fails`() {
        val endpoint = createEndpoint("/api/v1/user-profile", HttpMethod.GET)
        val query = ApiSearchQuery.parse("http://localhost:8080/api/v1/user-profile")

        assertTrue(matchesQuery(endpoint, query))
    }

    // --- Path variable matching tests ---

    @Test
    fun `matchesQuery with path variable - concrete value matches pattern`() {
        val endpoint = createEndpoint("/api/interface/{id}", HttpMethod.GET)
        val query = ApiSearchQuery.parse("http://127.0.0.1:3000/api/interface/116")

        assertTrue(matchesQuery(endpoint, query))
    }

    @Test
    fun `matchesQuery with path variable - different concrete value matches`() {
        val endpoint = createEndpoint("/api/interface/{id}", HttpMethod.GET)
        val query = ApiSearchQuery.parse("http://127.0.0.1:3000/api/interface/abc-123")

        assertTrue(matchesQuery(endpoint, query))
    }

    @Test
    fun `matchesQuery with path variable - extra segment does not match`() {
        val endpoint = createEndpoint("/api/interface/{id}", HttpMethod.GET)
        val query = ApiSearchQuery.parse("http://127.0.0.1:3000/api/interface/116/sub")

        assertFalse(matchesQuery(endpoint, query))
    }

    @Test
    fun `matchesQuery with path variable - missing segment still matches via contains`() {
        val endpoint = createEndpoint("/api/interface/{id}", HttpMethod.GET)
        val query = ApiSearchQuery.parse("http://127.0.0.1:3000/api/interface")

        assertTrue(matchesQuery(endpoint, query))
    }

    @Test
    fun `matchesQuery with multiple path variables`() {
        val endpoint = createEndpoint("/api/{version}/users/{id}", HttpMethod.GET)
        val query = ApiSearchQuery.parse("http://localhost:8080/api/v1/users/123")

        assertTrue(matchesQuery(endpoint, query))
    }

    @Test
    fun `matchesQuery with multiple path variables - wrong segment count does not match`() {
        val endpoint = createEndpoint("/api/{version}/users/{id}", HttpMethod.GET)
        val query = ApiSearchQuery.parse("http://localhost:8080/api/users/123")

        assertFalse(matchesQuery(endpoint, query))
    }

    @Test
    fun `matchesQuery path variable - exact path also matches via contains`() {
        val endpoint = createEndpoint("/api/users/{id}", HttpMethod.GET)
        val query = ApiSearchQuery.parse("/api/users/42")

        assertTrue(matchesQuery(endpoint, query))
    }

    @Test
    fun `matchesQuery path variable - non-path query does not trigger variable matching`() {
        val endpoint = createEndpoint("/api/users/{id}", HttpMethod.GET)
        val query = ApiSearchQuery(null, "users", isPathQuery = false)

        assertTrue(matchesQuery(endpoint, query))
    }

    @Test
    fun `matchesQuery path variable - concrete path with no variables matches exactly`() {
        val endpoint = createEndpoint("/api/users/list", HttpMethod.GET)
        val query = ApiSearchQuery.parse("http://localhost:8080/api/users/list")

        assertTrue(matchesQuery(endpoint, query))
    }

    @Test
    fun `matchesQuery path variable - concrete path does not match different fixed segment`() {
        val endpoint = createEndpoint("/api/users/list", HttpMethod.GET)
        val query = ApiSearchQuery.parse("http://localhost:8080/api/users/other")

        assertFalse(matchesQuery(endpoint, query))
    }

    @Test
    fun `matchesQuery path variable with hyphen in path`() {
        val endpoint = createEndpoint("/api/v1/user-profile/{id}", HttpMethod.GET)
        val query = ApiSearchQuery.parse("http://localhost:8080/api/v1/user-profile/123")

        assertTrue(matchesQuery(endpoint, query))
    }

    @Test
    fun `matchesQuery path variable - variable at start of path`() {
        val endpoint = createEndpoint("/{org}/projects", HttpMethod.GET)
        val query = ApiSearchQuery.parse("http://localhost:8080/my-org/projects")

        assertTrue(matchesQuery(endpoint, query))
    }

    // --- gRPC endpoint matching tests ---

    @Test
    fun `matchesQuery with gRPC URL matches grpc endpoint`() {
        val endpoint = createGrpcEndpoint(
            path = "/my.package.UserService/GetUser",
            serviceName = "UserService",
            methodName = "GetUser",
            packageName = "my.package"
        )
        val query = ApiSearchQuery.parse("grpc://localhost:8080/my.package.UserService/GetUser")

        assertTrue(matchesQuery(endpoint, query))
    }

    @Test
    fun `matchesQuery with grpcs URL matches grpc endpoint`() {
        val endpoint = createGrpcEndpoint(
            path = "/my.package.Service/Method",
            serviceName = "Service",
            methodName = "Method",
            packageName = "my.package"
        )
        val query = ApiSearchQuery.parse("grpcs://example.com/my.package.Service/Method")

        assertTrue(matchesQuery(endpoint, query))
    }

    @Test
    fun `matchesQuery with grpc URL and query params`() {
        val endpoint = createGrpcEndpoint(
            path = "/my.Service/Method",
            serviceName = "Service",
            methodName = "Method",
            packageName = "my"
        )
        val query = ApiSearchQuery.parse("grpc://localhost:8080/my.Service/Method?timeout=5s")

        assertTrue(matchesQuery(endpoint, query))
    }

    @Test
    fun `matchesQuery with text search matches grpc endpoint by path`() {
        val endpoint = createGrpcEndpoint(
            path = "/my.package.UserService/GetUser",
            serviceName = "UserService",
            methodName = "GetUser",
            packageName = "my.package"
        )
        val query = ApiSearchQuery(null, "UserService")

        assertTrue(matchesQuery(endpoint, query))
    }

    @Test
    fun `matchesQuery with text search matches grpc endpoint by name`() {
        val endpoint = createGrpcEndpoint(
            path = "/my.package.UserService/GetUser",
            serviceName = "UserService",
            methodName = "GetUser",
            packageName = "my.package",
            name = "Get User"
        )
        val query = ApiSearchQuery(null, "Get User")

        assertTrue(matchesQuery(endpoint, query))
    }

    // --- Realistic scenario tests ---

    @Test
    fun `realistic - paste full URL from browser matches correct endpoint`() {
        val endpoints = listOf(
            createEndpoint("/api/interface/get", HttpMethod.GET, name = "getInterface"),
            createEndpoint("/api/interface/list", HttpMethod.GET, name = "listInterfaces"),
            createEndpoint("/api/interface/update", HttpMethod.PUT, name = "updateInterface"),
            createEndpoint("/api/project/get", HttpMethod.GET, name = "getProject")
        )
        val query = ApiSearchQuery.parse("http://127.0.0.1:3000/api/interface/get?id=116")

        val matched = endpoints.filter { matchesQuery(it, query) }
        assertEquals(1, matched.size)
        assertEquals("getInterface", matched[0].name)
    }

    @Test
    fun `realistic - paste URL with path variable matches correct endpoint`() {
        val endpoints = listOf(
            createEndpoint("/api/user/{id}", HttpMethod.GET, name = "getUser"),
            createEndpoint("/api/user/list", HttpMethod.GET, name = "listUsers"),
            createEndpoint("/api/user/{id}/profile", HttpMethod.GET, name = "getUserProfile")
        )
        val query = ApiSearchQuery.parse("http://127.0.0.1:3000/api/user/42")

        val matched = endpoints.filter { matchesQuery(it, query) }
        assertEquals(1, matched.size)
        assertEquals("getUser", matched[0].name)
    }

    @Test
    fun `realistic - paste scheme-less URL matches correct endpoint`() {
        val endpoints = listOf(
            createEndpoint("/api/interface/list_menu", HttpMethod.GET, name = "listMenu"),
            createEndpoint("/api/interface/get", HttpMethod.GET, name = "getInterface")
        )
        val query = ApiSearchQuery.parse("127.0.0.1:3000/api/interface/list_menu?project_id=11")

        val matched = endpoints.filter { matchesQuery(it, query) }
        assertEquals(1, matched.size)
        assertEquals("listMenu", matched[0].name)
    }

    @Test
    fun `realistic - paste path with query params matches correct endpoint`() {
        val endpoints = listOf(
            createEndpoint("/api/interface/list_menu", HttpMethod.GET, name = "listMenu"),
            createEndpoint("/api/interface/get", HttpMethod.GET, name = "getInterface")
        )
        val query = ApiSearchQuery.parse("/api/interface/list_menu?project_id=11")

        val matched = endpoints.filter { matchesQuery(it, query) }
        assertEquals(1, matched.size)
        assertEquals("listMenu", matched[0].name)
    }

    @Test
    fun `realistic - URL with method prefix filters correctly`() {
        val endpoints = listOf(
            createEndpoint("/api/user/{id}", HttpMethod.GET, name = "getUser"),
            createEndpoint("/api/user/{id}", HttpMethod.DELETE, name = "deleteUser"),
            createEndpoint("/api/user/{id}", HttpMethod.PUT, name = "updateUser")
        )
        val query = ApiSearchQuery.parse("DELETE http://127.0.0.1:3000/api/user/42")

        val matched = endpoints.filter { matchesQuery(it, query) }
        assertEquals(1, matched.size)
        assertEquals("deleteUser", matched[0].name)
    }

    // --- matchesQuery implementation matching ApiSearchEverywhereContributor ---

    private fun matchesQuery(endpoint: ApiEndpoint, query: ApiSearchQuery): Boolean {
        if (query.httpMethod != null && endpoint.httpMetadata?.method != query.httpMethod) {
            return false
        }

        if (query.searchText.isBlank()) {
            return true
        }

        val searchLower = query.searchText.lowercase()
        val path = when (val meta = endpoint.metadata) {
            is HttpMetadata -> meta.path
            is GrpcMetadata -> meta.path
            else -> ""
        }

        if (query.isPathQuery && searchLower.startsWith("/")) {
            if (matchesPathWithVariables(searchLower, path.lowercase())) {
                return true
            }
        }

        return path.lowercase().contains(searchLower) ||
                endpoint.name?.lowercase()?.contains(searchLower) == true ||
                endpoint.className?.lowercase()?.contains(searchLower) == true ||
                endpoint.description?.lowercase()?.contains(searchLower) == true ||
                endpoint.folder?.lowercase()?.contains(searchLower) == true
    }

    private fun matchesPathWithVariables(concretePath: String, patternPath: String): Boolean {
        val regex = pathPatternToRegex(patternPath)
        return regex.matches(concretePath)
    }

    private fun pathPatternToRegex(pattern: String): Regex {
        val parts = pattern.split(Regex("\\{[^}]*\\}"))
        val regexStr = parts.joinToString("[^/]+") { Regex.escape(it) }
        return Regex("^$regexStr$")
    }
}
