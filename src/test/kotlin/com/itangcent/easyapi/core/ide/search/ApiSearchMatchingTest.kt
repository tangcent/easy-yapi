package com.itangcent.easyapi.core.ide.search

import com.itangcent.easyapi.core.export.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for [ApiEndpointMatcher] — the matching rules shared by the Search
 * Everywhere contributor and the Dashboard's search box.
 *
 * These cases used to be asserted through a reflection call into
 * `ApiSearchEverywhereContributor.matchesQuery`; they now exercise the matcher
 * directly, because that is where the rules live.
 */
class ApiSearchMatchingTest {

    private fun matchesQuery(endpoint: ApiEndpoint, query: ApiSearchQuery): Boolean =
        ApiEndpointMatcher.matches(endpoint, query)

    private fun matchesPathWithVariables(concretePath: String, patternPath: String): Boolean =
        ApiEndpointMatcher.matchesPathWithVariables(concretePath, patternPath)

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

    // --- Multi-token (AND across fields) tests ---

    /**
     * The shape requested in #1460: the path and the name each supply one of the
     * tokens, so neither field alone would satisfy the query.
     */
    @Test
    fun testTokensMayLandOnDifferentFields() {
        val endpoint = ApiEndpoint(
            name = "获取用户信息",
            metadata = httpMetadata(path = "/api/user/get", method = HttpMethod.GET)
        )
        assertTrue(
            "Path token + name token should match together",
            matchesQuery(endpoint, ApiSearchQuery(null, "user 用户", false))
        )
    }

    @Test
    fun testEveryTokenMustMatch() {
        val endpoint = ApiEndpoint(
            name = "获取用户信息",
            metadata = httpMetadata(path = "/api/user/get", method = HttpMethod.GET)
        )
        assertFalse(
            "A token that matches nothing must reject the whole query",
            matchesQuery(endpoint, ApiSearchQuery(null, "user order", false))
        )
    }

    @Test
    fun testTokenOrderDoesNotMatter() {
        val endpoint = ApiEndpoint(
            name = "获取用户信息",
            metadata = httpMetadata(path = "/api/user/get", method = HttpMethod.GET)
        )
        assertTrue(
            "Tokens are independent, so their order is irrelevant",
            matchesQuery(endpoint, ApiSearchQuery(null, "用户 user", false))
        )
    }

    // --- Fuzzy (subsequence) tests ---

    /**
     * The literal example from #1460: `aus` matches `/api/user/get` as a
     * subsequence, and the trailing Chinese characters come from the name.
     */
    @Test
    fun testTokenMaySpanPathAndNameAsSubsequence() {
        val endpoint = ApiEndpoint(
            name = "获取用户信息",
            description = "get user info",
            metadata = httpMetadata(path = "/api/user/get", method = HttpMethod.GET)
        )
        assertTrue(
            "A single token should match across the path/name join",
            matchesQuery(endpoint, ApiSearchQuery(null, "aus用户", false))
        )
    }

    /**
     * The same `aus用户` query as it actually reaches the matcher — through
     * [ApiSearchQuery.parse], which is how both search surfaces build it.
     *
     * The case above passes `isPathQuery = false` by hand, so it would stay green
     * even if the parser started classifying `aus用户` as a path; a path query
     * deliberately skips the fuzzy rule, so the #1460 shape would break silently.
     * Going through the parser keeps that dependency covered.
     */
    @Test
    fun testIssue1460QueryStillMatchesAfterParsing() {
        val endpoint = ApiEndpoint(
            name = "获取用户信息",
            metadata = httpMetadata(path = "/api/user/get", method = HttpMethod.GET)
        )

        val query = ApiSearchQuery.parse("aus用户")

        assertFalse(
            "Not a URL and not a /path, so it must not be treated as a path query",
            query.isPathQuery
        )
        assertEquals("aus用户", query.searchText)
        assertTrue(
            "The parsed query must still match the endpoint from #1460",
            matchesQuery(endpoint, query)
        )
    }

    @Test
    fun testSubsequenceWithGapsMatches() {
        val endpoint = ApiEndpoint(
            name = "getUser",
            metadata = httpMetadata(path = "/api/user/get", method = HttpMethod.GET)
        )
        assertTrue(
            "Characters may be matched with gaps",
            matchesQuery(endpoint, ApiSearchQuery(null, "uget", false))
        )
    }

    @Test
    fun testShortTokensMustMatchLiterally() {
        val endpoint = ApiEndpoint(
            name = "updateOrder",
            metadata = httpMetadata(path = "/api/orders/update", method = HttpMethod.GET)
        )
        assertFalse(
            "Below the fuzzy length floor a token must appear literally",
            matchesQuery(endpoint, ApiSearchQuery(null, "us", false))
        )
    }

    @Test
    fun testSubsequenceMatchingNeedsTheCharactersInOrder() {
        val endpoint = ApiEndpoint(
            name = "getUser",
            metadata = httpMetadata(path = "/api/user/get", method = HttpMethod.GET)
        )
        assertFalse(
            "Reversed characters ('teg' against 'get') are not a subsequence",
            matchesQuery(endpoint, ApiSearchQuery(null, "teg", false))
        )
    }

    /**
     * A path query is precise: it matches literally (so a shortened URL still
     * finds its endpoint) but is never fuzzed. The same text typed as free text
     * does match fuzzily — that contrast is the whole point of the rule.
     */
    @Test
    fun testPathQueriesMatchLiterallyButNeverFuzzily() {
        val endpoint = ApiEndpoint(
            name = "updateOrder",
            metadata = httpMetadata(path = "/api/orders/update", method = HttpMethod.GET)
        )

        assertTrue(
            "Free text may match as a subsequence",
            matchesQuery(endpoint, ApiSearchQuery(null, "order/upt", false))
        )
        assertFalse(
            "The same text as a pasted path must not",
            matchesQuery(endpoint, ApiSearchQuery(null, "/order/upt", true))
        )
    }

    // --- Scoring tests ---

    @Test
    fun testLiteralPathHitOutranksFuzzyHit() {
        val literal = ApiEndpoint(
            name = "getUser",
            metadata = httpMetadata(path = "/api/user/get", method = HttpMethod.GET)
        )
        val fuzzy = ApiEndpoint(
            name = "listOrders",
            description = "Usually returns sorted rows",
            metadata = httpMetadata(path = "/api/orders", method = HttpMethod.GET)
        )
        val query = ApiSearchQuery(null, "user", false)

        val literalScore = ApiEndpointMatcher.score(literal, query)
        val fuzzyScore = ApiEndpointMatcher.score(fuzzy, query)

        assertTrue("The literal hit must match", literalScore > ApiEndpointMatcher.NO_MATCH)
        assertTrue("The fuzzy hit must match", fuzzyScore > ApiEndpointMatcher.NO_MATCH)
        assertTrue(
            "Ranking must favour the literal hit ($literalScore vs $fuzzyScore)",
            literalScore > fuzzyScore
        )
    }

    @Test
    fun testScoreIsZeroForNoMatch() {
        val endpoint = ApiEndpoint(
            name = "getUser",
            metadata = httpMetadata(path = "/api/users", method = HttpMethod.GET)
        )
        assertEquals(
            ApiEndpointMatcher.NO_MATCH,
            ApiEndpointMatcher.score(endpoint, ApiSearchQuery(null, "orders", false))
        )
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
