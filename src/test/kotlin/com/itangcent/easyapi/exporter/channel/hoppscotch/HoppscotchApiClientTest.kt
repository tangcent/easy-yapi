package com.itangcent.easyapi.exporter.channel.hoppscotch

import com.itangcent.easyapi.http.HttpClient
import com.itangcent.easyapi.http.HttpRequest
import com.itangcent.easyapi.http.HttpResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for HoppscotchApiClient companion methods and API operations with mock HttpClient.
 */
class HoppscotchApiClientTest {

    private lateinit var mockClient: MockHttpClient
    private lateinit var apiClient: HoppscotchApiClient

    @Before
    fun setUp() {
        mockClient = MockHttpClient()
        apiClient = HoppscotchApiClient(
            token = "test-token",
            serverUrl = "https://hoppscotch.io",
            httpClient = mockClient
        )
    }

    // ==================== URL Resolution (companion methods) ====================

    @Test
    fun `resolveApiBaseUrl for cloud returns api subdomain`() {
        assertEquals(
            "https://api.hoppscotch.io",
            HoppscotchApiClient.resolveApiBaseUrl("https://hoppscotch.io")
        )
    }

    @Test
    fun `resolveApiBaseUrl for cloud with trailing slash`() {
        assertEquals(
            "https://api.hoppscotch.io",
            HoppscotchApiClient.resolveApiBaseUrl("https://hoppscotch.io/")
        )
    }

    @Test
    fun `resolveApiBaseUrl for self-hosted with backend URL`() {
        assertEquals(
            "http://localhost:3170/v1",
            HoppscotchApiClient.resolveApiBaseUrl("https://custom.example.com", "http://localhost:3170/v1")
        )
    }

    @Test
    fun `resolveApiBaseUrl for self-hosted without backend URL`() {
        assertEquals(
            "https://custom.example.com",
            HoppscotchApiClient.resolveApiBaseUrl("https://custom.example.com")
        )
    }

    @Test
    fun `resolveApiV1BaseUrl for cloud`() {
        assertEquals(
            "https://api.hoppscotch.io/v1",
            HoppscotchApiClient.resolveApiV1BaseUrl("https://hoppscotch.io")
        )
    }

    @Test
    fun `resolveApiV1BaseUrl for self-hosted with backend URL`() {
        assertEquals(
            "http://localhost:3170/v1",
            HoppscotchApiClient.resolveApiV1BaseUrl("https://custom.example.com", "http://localhost:3170/v1")
        )
    }

    @Test
    fun `resolveApiV1BaseUrl for self-hosted without backend URL`() {
        assertEquals(
            "https://custom.example.com/v1",
            HoppscotchApiClient.resolveApiV1BaseUrl("https://custom.example.com")
        )
    }

    @Test
    fun `resolveGraphQLUrl for cloud`() {
        assertEquals(
            "https://api.hoppscotch.io/graphql",
            HoppscotchApiClient.resolveGraphQLUrl("https://hoppscotch.io")
        )
    }

    @Test
    fun `resolveGraphQLUrl for self-hosted with backend URL`() {
        assertEquals(
            "http://localhost:3170/v1/graphql",
            HoppscotchApiClient.resolveGraphQLUrl("https://custom.example.com", "http://localhost:3170/v1")
        )
    }

    @Test
    fun `resolveGraphQLUrl for self-hosted without backend URL`() {
        assertEquals(
            "https://custom.example.com/graphql",
            HoppscotchApiClient.resolveGraphQLUrl("https://custom.example.com")
        )
    }

    @Test
    fun `isCloudServer for hoppscotch io`() {
        assertTrue(HoppscotchApiClient.isCloudServer("https://hoppscotch.io"))
    }

    @Test
    fun `isCloudServer for hoppscotch io with trailing slash`() {
        assertTrue(HoppscotchApiClient.isCloudServer("https://hoppscotch.io/"))
    }

    @Test
    fun `isCloudServer for self-hosted`() {
        assertFalse(HoppscotchApiClient.isCloudServer("https://custom.example.com"))
    }

    // ==================== testConnection ====================

    @Test
    fun `testConnection returns true on success`() = runBlocking {
        mockClient.nextResponse = HttpResponse(
            code = 200,
            body = """{"data":{"me":{"uid":"user-1","displayName":"Test User"}}}"""
        )
        assertTrue(apiClient.testConnection())
    }

    @Test
    fun `testConnection returns false on GraphQL error`() = runBlocking {
        mockClient.nextResponse = HttpResponse(
            code = 200,
            body = """{"errors":[{"message":"Unauthorized"}]}"""
        )
        assertFalse(apiClient.testConnection())
    }

    @Test
    fun `testConnection returns false on HTTP error`() = runBlocking {
        mockClient.nextResponse = HttpResponse(code = 500, body = "Internal Server Error")
        assertFalse(apiClient.testConnection())
    }

    @Test
    fun `testConnection returns false on exception`() = runBlocking {
        mockClient.shouldThrow = true
        assertFalse(apiClient.testConnection())
    }

    @Test
    fun `testConnection with blank token returns false`() = runBlocking {
        val client = HoppscotchApiClient(token = "", httpClient = mockClient)
        assertFalse(client.testConnection())
    }

    // ==================== listTeams ====================

    @Test
    fun `listTeams returns teams on success`() = runBlocking {
        mockClient.nextResponse = HttpResponse(
            code = 200,
            body = """{"data":{"myTeams":[{"id":"team-1","name":"Team Alpha"},{"id":"team-2","name":"Team Beta"}]}}"""
        )
        val teams = apiClient.listTeams()
        assertEquals(2, teams.size)
        assertEquals("team-1", teams[0].id)
        assertEquals("Team Alpha", teams[0].name)
    }

    @Test
    fun `listTeams returns empty on error`() = runBlocking {
        mockClient.nextResponse = HttpResponse(code = 500, body = "Error")
        val teams = apiClient.listTeams()
        assertTrue(teams.isEmpty())
    }

    @Test
    fun `listTeams with blank token returns empty`() = runBlocking {
        val client = HoppscotchApiClient(token = "", httpClient = mockClient)
        val teams = client.listTeams()
        assertTrue(teams.isEmpty())
    }

    // ==================== listCollections ====================

    @Test
    fun `listCollections returns collections on success`() = runBlocking {
        mockClient.nextResponse = HttpResponse(
            code = 200,
            body = """{"data":{"rootCollectionsOfTeam":[{"id":"col-1","title":"Collection 1"},{"id":"col-2","title":"Collection 2"}]}}"""
        )
        val collections = apiClient.listCollections()
        assertEquals(2, collections.size)
        assertEquals("col-1", collections[0].id)
        assertEquals("Collection 1", collections[0].name)
    }

    @Test
    fun `listCollections with teamId includes teamID in query`() = runBlocking {
        mockClient.nextResponse = HttpResponse(
            code = 200,
            body = """{"data":{"rootCollectionsOfTeam":[{"id":"col-1","title":"Test"}]}}"""
        )
        val collections = apiClient.listCollections(teamId = "team-123")
        assertEquals(1, collections.size)
        // Verify the request body contains teamID
        val requestBody = mockClient.lastRequest?.body ?: ""
        assertTrue(requestBody.contains("teamID"))
    }

    @Test
    fun `listCollections returns empty on error`() = runBlocking {
        mockClient.nextResponse = HttpResponse(code = 500, body = "Error")
        val collections = apiClient.listCollections()
        assertTrue(collections.isEmpty())
    }

    @Test
    fun `listCollections with blank token returns empty`() = runBlocking {
        val client = HoppscotchApiClient(token = "", httpClient = mockClient)
        val collections = client.listCollections()
        assertTrue(collections.isEmpty())
    }

    // ==================== uploadCollection ====================

    @Test
    fun `uploadCollection with blank token returns failure`() = runBlocking {
        val client = HoppscotchApiClient(token = "", httpClient = mockClient)
        val collection = com.itangcent.easyapi.exporter.channel.hoppscotch.model.HoppCollection(name = "Test")
        val result = client.uploadCollection(collection)
        assertFalse(result.success)
        assertTrue(result.message?.contains("No access token") == true)
    }

    @Test
    fun `uploadCollection handles GraphQL errors`() = runBlocking {
        mockClient.nextResponse = HttpResponse(
            code = 200,
            body = """{"errors":[{"message":"Invalid JSON format"}]}"""
        )
        val collection = com.itangcent.easyapi.exporter.channel.hoppscotch.model.HoppCollection(name = "Test")
        val result = apiClient.uploadCollection(collection)
        assertFalse(result.success)
        assertTrue(result.message?.contains("Invalid JSON format") == true)
    }

    @Test
    fun `uploadCollection handles exception`() = runBlocking {
        mockClient.shouldThrow = true
        val collection = com.itangcent.easyapi.exporter.channel.hoppscotch.model.HoppCollection(name = "Test")
        val result = apiClient.uploadCollection(collection)
        assertFalse(result.success)
    }

    // ==================== deleteCollection ====================

    @Test
    fun `deleteCollection returns true on success`() = runBlocking {
        mockClient.nextResponse = HttpResponse(
            code = 200,
            body = """{"data":{"deleteUserCollection":true}}"""
        )
        assertTrue(apiClient.deleteCollection("col-123"))
    }

    @Test
    fun `deleteCollection returns false on error`() = runBlocking {
        mockClient.nextResponse = HttpResponse(code = 500, body = "Error")
        assertFalse(apiClient.deleteCollection("col-123"))
    }

    @Test
    fun `deleteCollection with blank token returns false`() = runBlocking {
        val client = HoppscotchApiClient(token = "", httpClient = mockClient)
        assertFalse(client.deleteCollection("col-123"))
    }

    // ==================== 401 Auth Exception ====================

    @Test
    fun `testConnection returns false on 401`() = runBlocking {
        mockClient.nextResponse = HttpResponse(code = 401, body = "Unauthorized")
        assertFalse(apiClient.testConnection())
    }

    @Test
    fun `listCollections returns empty on 401`() = runBlocking {
        mockClient.nextResponse = HttpResponse(code = 401, body = "Unauthorized")
        val result = apiClient.listCollections("team-1")
        assertTrue(result.isEmpty())
    }

    // ==================== Data classes ====================

    @Test
    fun `HoppTeam data class`() {
        val team = HoppTeam(id = "team-1", name = "Test Team")
        assertEquals("team-1", team.id)
        assertEquals("Test Team", team.name)
    }

    @Test
    fun `HoppCollectionInfo data class`() {
        val info = HoppCollectionInfo(id = "col-1", name = "Test Collection")
        assertEquals("col-1", info.id)
        assertEquals("Test Collection", info.name)
    }

    @Test
    fun `HoppUploadResult data class`() {
        val result = HoppUploadResult(success = true, message = "OK", collectionId = "col-1")
        assertTrue(result.success)
        assertEquals("OK", result.message)
        assertEquals("col-1", result.collectionId)
    }

    @Test
    fun `HoppUploadResult copy`() {
        val result = HoppUploadResult(success = true, message = "OK", collectionId = "col-1")
        val copy = result.copy(success = false)
        assertFalse(copy.success)
        assertEquals("OK", copy.message)
    }

    @Test
    fun `HoppscotchAuthException is an Exception`() {
        val ex = HoppscotchAuthException("Token expired")
        assertTrue(ex is Exception)
        assertEquals("Token expired", ex.message)
    }

    /**
     * Simple mock HttpClient that returns a pre-configured response.
     */
    class MockHttpClient : HttpClient {
        var nextResponse: HttpResponse = HttpResponse(code = 200, body = "")
        var lastRequest: HttpRequest? = null
        var shouldThrow: Boolean = false

        override suspend fun execute(request: HttpRequest): HttpResponse {
            lastRequest = request
            if (shouldThrow) {
                throw RuntimeException("Test error")
            }
            return nextResponse
        }

        override fun close() {}
    }
}
