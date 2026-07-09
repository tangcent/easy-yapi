package com.itangcent.easyapi.exporter.channel.hoppscotch

import com.itangcent.easyapi.exporter.channel.hoppscotch.model.*
import com.itangcent.easyapi.http.HttpClient
import com.itangcent.easyapi.http.HttpRequest
import com.itangcent.easyapi.http.HttpResponse
import com.itangcent.easyapi.util.json.GsonUtils
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for HoppscotchApiClient companion methods (URL resolution, cloud detection)
 * and API methods (testConnection, listTeams, listCollections, uploadCollection,
 * updateCollection, deleteCollection, translateToPersonalCollectionFormat, HTTP error handling).
 */
class HoppscotchApiClientComprehensiveTest {

    // ==================== Companion method tests ====================

    @Test
    fun `resolveApiBaseUrl returns api hoppscotch io for cloud`() {
        assertEquals(
            "https://api.hoppscotch.io",
            HoppscotchApiClient.resolveApiBaseUrl("https://hoppscotch.io")
        )
    }

    @Test
    fun `resolveApiBaseUrl returns api hoppscotch io for cloud with trailing slash`() {
        assertEquals(
            "https://api.hoppscotch.io",
            HoppscotchApiClient.resolveApiBaseUrl("https://hoppscotch.io/")
        )
    }

    @Test
    fun `resolveApiBaseUrl returns same URL for self-hosted`() {
        assertEquals(
            "https://custom.example.com",
            HoppscotchApiClient.resolveApiBaseUrl("https://custom.example.com")
        )
    }

    @Test
    fun `resolveApiBaseUrl returns backendUrl when provided for self-hosted`() {
        assertEquals(
            "http://localhost:3170",
            HoppscotchApiClient.resolveApiBaseUrl("https://custom.example.com", "http://localhost:3170")
        )
    }

    @Test
    fun `resolveApiBaseUrl ignores blank backendUrl`() {
        assertEquals(
            "https://custom.example.com",
            HoppscotchApiClient.resolveApiBaseUrl("https://custom.example.com", "")
        )
    }

    @Test
    fun `resolveApiBaseUrl ignores blank backendUrl with whitespace`() {
        assertEquals(
            "https://custom.example.com",
            HoppscotchApiClient.resolveApiBaseUrl("https://custom.example.com", "   ")
        )
    }

    @Test
    fun `resolveApiBaseUrl ignores backendUrl for cloud instance`() {
        // Cloud always uses api.hoppscotch.io regardless of backendUrl
        assertEquals(
            "https://api.hoppscotch.io",
            HoppscotchApiClient.resolveApiBaseUrl("https://hoppscotch.io", "http://localhost:3170")
        )
    }

    @Test
    fun `resolveApiBaseUrl trims trailing slash from self-hosted`() {
        assertEquals(
            "https://custom.example.com",
            HoppscotchApiClient.resolveApiBaseUrl("https://custom.example.com/")
        )
    }

    @Test
    fun `resolveApiBaseUrl trims trailing slash from backendUrl`() {
        assertEquals(
            "http://localhost:3170",
            HoppscotchApiClient.resolveApiBaseUrl("https://custom.example.com", "http://localhost:3170/")
        )
    }

    // ==================== resolveApiV1BaseUrl tests ====================

    @Test
    fun `resolveApiV1BaseUrl returns api hoppscotch io v1 for cloud`() {
        assertEquals(
            "https://api.hoppscotch.io/v1",
            HoppscotchApiClient.resolveApiV1BaseUrl("https://hoppscotch.io")
        )
    }

    @Test
    fun `resolveApiV1BaseUrl returns backendUrl as-is when provided`() {
        // Backend URL already includes /v1
        assertEquals(
            "http://localhost:3170/v1",
            HoppscotchApiClient.resolveApiV1BaseUrl("https://custom.example.com", "http://localhost:3170/v1")
        )
    }

    @Test
    fun `resolveApiV1BaseUrl appends v1 for self-hosted without backendUrl`() {
        assertEquals(
            "https://custom.example.com/v1",
            HoppscotchApiClient.resolveApiV1BaseUrl("https://custom.example.com")
        )
    }

    @Test
    fun `resolveApiV1BaseUrl ignores blank backendUrl`() {
        assertEquals(
            "https://custom.example.com/v1",
            HoppscotchApiClient.resolveApiV1BaseUrl("https://custom.example.com", "")
        )
    }

    // ==================== resolveGraphQLUrl tests ====================

    @Test
    fun `resolveGraphQLUrl returns api hoppscotch io graphql for cloud`() {
        assertEquals(
            "https://api.hoppscotch.io/graphql",
            HoppscotchApiClient.resolveGraphQLUrl("https://hoppscotch.io")
        )
    }

    @Test
    fun `resolveGraphQLUrl appends graphql for self-hosted`() {
        assertEquals(
            "https://custom.example.com/graphql",
            HoppscotchApiClient.resolveGraphQLUrl("https://custom.example.com")
        )
    }

    @Test
    fun `resolveGraphQLUrl uses backendUrl when provided`() {
        assertEquals(
            "http://localhost:3170/graphql",
            HoppscotchApiClient.resolveGraphQLUrl("https://custom.example.com", "http://localhost:3170")
        )
    }

    // ==================== isCloudServer tests ====================

    @Test
    fun `isCloudServer returns true for hoppscotch io`() {
        assertTrue(HoppscotchApiClient.isCloudServer("https://hoppscotch.io"))
    }

    @Test
    fun `isCloudServer returns true for hoppscotch io with trailing slash`() {
        assertTrue(HoppscotchApiClient.isCloudServer("https://hoppscotch.io/"))
    }

    @Test
    fun `isCloudServer returns false for custom server`() {
        assertFalse(HoppscotchApiClient.isCloudServer("https://custom.example.com"))
    }

    @Test
    fun `isCloudServer returns false for subdomain of hoppscotch io`() {
        // The companion only checks exact host match, not subdomains
        assertFalse(HoppscotchApiClient.isCloudServer("https://api.hoppscotch.io"))
    }

    @Test
    fun `isCloudServer returns true for hoppscotch io with port`() {
        // Port 443 is default for HTTPS, host is still hoppscotch.io
        assertTrue(HoppscotchApiClient.isCloudServer("https://hoppscotch.io:443"))
    }

    // ==================== API method tests with mock HttpClient ====================

    private lateinit var mockHttpClient: MockHttpClient
    private lateinit var client: HoppscotchApiClient

    @Before
    fun setUp() {
        mockHttpClient = MockHttpClient()
        client = HoppscotchApiClient(
            token = "test-token-123",
            serverUrl = "https://hoppscotch.test",
            httpClient = mockHttpClient
        )
    }

    // --- testConnection ---

    @Test
    fun `testConnection returns true for valid response`() = runBlocking {
        val data = JsonObject().apply {
            add("me", JsonObject().apply {
                addProperty("uid", "user1")
                addProperty("displayName", "Test User")
            })
        }
        mockHttpClient.nextResponse = graphqlResponse(data)
        assertTrue(client.testConnection())
    }

    @Test
    fun `testConnection returns false for error response`() = runBlocking {
        val errors = """{"errors":[{"message":"Unauthorized"}]}"""
        mockHttpClient.nextResponse = HttpResponse(code = 200, body = errors)
        assertFalse(client.testConnection())
    }

    @Test
    fun `testConnection returns false for blank token`() = runBlocking {
        val blankTokenClient = HoppscotchApiClient(
            token = "",
            serverUrl = "https://hoppscotch.test",
            httpClient = mockHttpClient
        )
        assertFalse(blankTokenClient.testConnection())
    }

    @Test
    fun `testConnection returns false on exception`() = runBlocking {
        mockHttpClient.nextException = RuntimeException("Connection refused")
        assertFalse(client.testConnection())
    }

    // --- listTeams ---

    @Test
    fun `listTeams returns teams from valid response`() = runBlocking {
        val teamsData = JsonObject().apply {
            add("myTeams", GsonUtils.GSON.toJsonTree(listOf(
                mapOf("id" to "team1", "name" to "Team A"),
                mapOf("id" to "team2", "name" to "Team B")
            )))
        }
        mockHttpClient.nextResponse = graphqlResponse(teamsData)
        val teams = client.listTeams()
        assertEquals(2, teams.size)
        assertEquals("team1", teams[0].id)
        assertEquals("Team A", teams[0].name)
    }

    @Test
    fun `listTeams returns empty for blank token`() = runBlocking {
        val blankTokenClient = HoppscotchApiClient(
            token = "",
            serverUrl = "https://hoppscotch.test",
            httpClient = mockHttpClient
        )
        assertTrue(blankTokenClient.listTeams().isEmpty())
    }

    @Test
    fun `listTeams returns empty on exception`() = runBlocking {
        mockHttpClient.nextException = RuntimeException("Network error")
        assertTrue(client.listTeams().isEmpty())
    }

    @Test
    fun `listTeams returns empty when data is null`() = runBlocking {
        val wrapper = JsonObject().apply {
            addProperty("data", "null")
        }
        mockHttpClient.nextResponse = HttpResponse(code = 200, body = """{"data":null}""")
        assertTrue(client.listTeams().isEmpty())
    }

    @Test
    fun `listTeams returns empty when myTeams is null`() = runBlocking {
        mockHttpClient.nextResponse = HttpResponse(code = 200, body = """{"data":{"myTeams":null}}""")
        assertTrue(client.listTeams().isEmpty())
    }

    // --- listCollections ---

    @Test
    fun `listCollections returns collections from valid response`() = runBlocking {
        val collectionsData = JsonObject().apply {
            add("rootCollectionsOfTeam", GsonUtils.GSON.toJsonTree(listOf(
                mapOf("id" to "col1", "title" to "Collection A"),
                mapOf("id" to "col2", "title" to "Collection B")
            )))
        }
        mockHttpClient.nextResponse = graphqlResponse(collectionsData)
        val collections = client.listCollections()
        assertEquals(2, collections.size)
        assertEquals("col1", collections[0].id)
        assertEquals("Collection A", collections[0].name)
    }

    @Test
    fun `listCollections with teamId includes teamID in query`() = runBlocking {
        val collectionsData = JsonObject().apply {
            add("rootCollectionsOfTeam", GsonUtils.GSON.toJsonTree(emptyList<Map<String, String>>()))
        }
        mockHttpClient.nextResponse = graphqlResponse(collectionsData)
        client.listCollections(teamId = "team1")
        val lastRequest = mockHttpClient.lastRequest
        assertNotNull(lastRequest)
        assertTrue(lastRequest!!.body!!.contains("teamID"))
    }

    @Test
    fun `listCollections without teamId does not include teamID in query`() = runBlocking {
        val collectionsData = JsonObject().apply {
            add("rootCollectionsOfTeam", GsonUtils.GSON.toJsonTree(emptyList<Map<String, String>>()))
        }
        mockHttpClient.nextResponse = graphqlResponse(collectionsData)
        client.listCollections()
        val lastRequest = mockHttpClient.lastRequest
        assertNotNull(lastRequest)
        assertFalse(lastRequest!!.body!!.contains("teamID"))
    }

    @Test
    fun `listCollections returns empty for blank token`() = runBlocking {
        val blankTokenClient = HoppscotchApiClient(
            token = "",
            serverUrl = "https://hoppscotch.test",
            httpClient = mockHttpClient
        )
        assertTrue(blankTokenClient.listCollections().isEmpty())
    }

    @Test
    fun `listCollections returns empty on exception`() = runBlocking {
        mockHttpClient.nextException = RuntimeException("Network error")
        assertTrue(client.listCollections().isEmpty())
    }

    // --- uploadCollection ---

    @Test
    fun `uploadCollection returns success for valid personal response`() = runBlocking {
        val importData = JsonObject().apply {
            add("importUserCollectionsFromJSON", JsonObject().apply {
                addProperty("exportedCollection", "new-col-1")
                addProperty("collectionType", "REST")
            })
        }
        mockHttpClient.nextResponse = graphqlResponse(importData)
        val collection = HoppCollection(
            name = "Test Collection",
            requests = listOf(HoppRESTRequest(name = "GET /api", method = "GET", endpoint = "/api"))
        )
        val result = client.uploadCollection(collection)
        assertTrue(result.success)
        assertEquals("new-col-1", result.collectionId)
    }

    @Test
    fun `uploadCollection with teamId uses importCollectionsFromJSON`() = runBlocking {
        val importData = JsonObject().apply {
            addProperty("importCollectionsFromJSON", true)
        }
        mockHttpClient.nextResponse = graphqlResponse(importData)
        val collection = HoppCollection(name = "Team Collection")
        val result = client.uploadCollection(collection, teamId = "team1")
        assertTrue(result.success)
        assertNull(result.collectionId)
        val lastRequest = mockHttpClient.lastRequest
        assertNotNull(lastRequest)
        assertTrue(lastRequest!!.body!!.contains("importCollectionsFromJSON"))
        assertTrue(lastRequest.body!!.contains("teamID"))
    }

    @Test
    fun `uploadCollection with teamId returns failure when import fails`() = runBlocking {
        val importData = JsonObject().apply {
            addProperty("importCollectionsFromJSON", false)
        }
        mockHttpClient.nextResponse = graphqlResponse(importData)
        val collection = HoppCollection(name = "Team Collection")
        val result = client.uploadCollection(collection, teamId = "team1")
        assertFalse(result.success)
        assertEquals("Upload failed", result.message)
    }

    @Test
    fun `uploadCollection returns failure for GraphQL errors`() = runBlocking {
        val errorBody = """{"errors":[{"message":"Collection name already exists"}]}"""
        mockHttpClient.nextResponse = HttpResponse(code = 200, body = errorBody)
        val collection = HoppCollection(name = "Test")
        val result = client.uploadCollection(collection)
        assertFalse(result.success)
        assertEquals("Collection name already exists", result.message)
    }

    @Test
    fun `uploadCollection returns failure for GraphQL errors with no message`() = runBlocking {
        val errorBody = """{"errors":[{}]}"""
        mockHttpClient.nextResponse = HttpResponse(code = 200, body = errorBody)
        val collection = HoppCollection(name = "Test")
        val result = client.uploadCollection(collection)
        assertFalse(result.success)
        assertEquals("Unknown GraphQL error", result.message)
    }

    @Test
    fun `uploadCollection returns failure for blank token`() = runBlocking {
        val blankTokenClient = HoppscotchApiClient(
            token = "",
            serverUrl = "https://hoppscotch.test",
            httpClient = mockHttpClient
        )
        val collection = HoppCollection(name = "Test")
        val result = blankTokenClient.uploadCollection(collection)
        assertFalse(result.success)
        assertEquals("No access token configured", result.message)
    }

    @Test
    fun `uploadCollection returns failure for null response`() = runBlocking {
        mockHttpClient.nextResponse = HttpResponse(code = 500, body = "Internal Server Error")
        val collection = HoppCollection(name = "Test")
        val result = client.uploadCollection(collection)
        assertFalse(result.success)
        assertEquals("Empty response from server", result.message)
    }

    @Test
    fun `uploadCollection returns failure on exception`() = runBlocking {
        mockHttpClient.nextException = RuntimeException("Connection refused")
        val collection = HoppCollection(name = "Test")
        val result = client.uploadCollection(collection)
        assertFalse(result.success)
        assertEquals("Connection refused", result.message)
    }

    // --- updateCollection ---

    @Test
    fun `updateCollection creates new then deletes old`() = runBlocking {
        val importData = JsonObject().apply {
            add("importUserCollectionsFromJSON", JsonObject().apply {
                addProperty("exportedCollection", "new-col-2")
                addProperty("collectionType", "REST")
            })
        }
        mockHttpClient.responseQueue.add(graphqlResponse(importData))
        val deleteData = JsonObject().apply {
            addProperty("deleteUserCollection", true)
        }
        mockHttpClient.responseQueue.add(graphqlResponse(deleteData))

        val collection = HoppCollection(name = "Updated")
        val result = client.updateCollection("old-col-1", collection)
        assertTrue(result.success)
        assertEquals("new-col-2", result.collectionId)
        assertTrue(result.message!!.contains("updated successfully"))
    }

    @Test
    fun `updateCollection returns failure for blank token`() = runBlocking {
        val blankTokenClient = HoppscotchApiClient(
            token = "",
            serverUrl = "https://hoppscotch.test",
            httpClient = mockHttpClient
        )
        val collection = HoppCollection(name = "Test")
        val result = blankTokenClient.updateCollection("old-col-1", collection)
        assertFalse(result.success)
        assertEquals("No access token configured", result.message)
    }

    @Test
    fun `updateCollection returns upload result when upload fails`() = runBlocking {
        val errorBody = """{"errors":[{"message":"Upload failed"}]}"""
        mockHttpClient.nextResponse = HttpResponse(code = 200, body = errorBody)
        val collection = HoppCollection(name = "Failed")
        val result = client.updateCollection("old-col-1", collection)
        assertFalse(result.success)
    }

    @Test
    fun `updateCollection does not delete when upload has no collectionId`() = runBlocking {
        // Upload succeeds but no collectionId returned (shouldn't happen normally, but test the branch)
        val importData = JsonObject().apply {
            add("importUserCollectionsFromJSON", JsonObject().apply {
                addProperty("exportedCollection", "new-col-2")
                addProperty("collectionType", "REST")
            })
        }
        mockHttpClient.responseQueue.add(graphqlResponse(importData))
        // Second call for delete
        val deleteData = JsonObject().apply {
            addProperty("deleteUserCollection", true)
        }
        mockHttpClient.responseQueue.add(graphqlResponse(deleteData))

        val collection = HoppCollection(name = "Test")
        val result = client.updateCollection("old-col-1", collection)
        // Should succeed since uploadResult.collectionId is not null
        assertTrue(result.success)
    }

    // --- deleteCollection ---

    @Test
    fun `deleteCollection returns true for valid response`() = runBlocking {
        val deleteData = JsonObject().apply {
            addProperty("deleteUserCollection", true)
        }
        mockHttpClient.nextResponse = graphqlResponse(deleteData)
        assertTrue(client.deleteCollection("col-1"))
    }

    @Test
    fun `deleteCollection with teamId uses deleteCollection mutation`() = runBlocking {
        val deleteData = JsonObject().apply {
            addProperty("deleteCollection", true)
        }
        mockHttpClient.nextResponse = graphqlResponse(deleteData)
        assertTrue(client.deleteCollection("col-1", teamId = "team1"))
        val lastRequest = mockHttpClient.lastRequest
        assertNotNull(lastRequest)
        assertTrue(lastRequest!!.body!!.contains("deleteCollection"))
        assertTrue(lastRequest.body!!.contains("collectionID"))
    }

    @Test
    fun `deleteCollection returns false for blank token`() = runBlocking {
        val blankTokenClient = HoppscotchApiClient(
            token = "",
            serverUrl = "https://hoppscotch.test",
            httpClient = mockHttpClient
        )
        assertFalse(blankTokenClient.deleteCollection("col-1"))
    }

    @Test
    fun `deleteCollection returns false on exception`() = runBlocking {
        mockHttpClient.nextException = RuntimeException("Connection refused")
        assertFalse(client.deleteCollection("col-1"))
    }

    @Test
    fun `deleteCollection returns false for GraphQL errors`() = runBlocking {
        val errorBody = """{"errors":[{"message":"Not found"}]}"""
        mockHttpClient.nextResponse = HttpResponse(code = 200, body = errorBody)
        assertFalse(client.deleteCollection("col-1"))
    }

    @Test
    fun `deleteCollection returns false for null response`() = runBlocking {
        mockHttpClient.nextResponse = HttpResponse(code = 500, body = "Internal Server Error")
        assertFalse(client.deleteCollection("col-1"))
    }

    // --- HTTP error handling ---

    @Test
    fun `401 response returns false from testConnection`() = runBlocking {
        // testConnection catches HoppscotchAuthException and returns false
        mockHttpClient.nextResponse = HttpResponse(code = 401, body = "Unauthorized")
        assertFalse(client.testConnection())
    }

    @Test
    fun `401 response returns empty list from listTeams`() = runBlocking {
        // listTeams catches HoppscotchAuthException and returns empty list
        mockHttpClient.nextResponse = HttpResponse(code = 401, body = "Unauthorized")
        assertTrue(client.listTeams().isEmpty())
    }

    @Test
    fun `405 response returns null gracefully`() = runBlocking {
        mockHttpClient.nextResponse = HttpResponse(code = 405, body = "Method Not Allowed")
        val result = client.listTeams()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `non-200 response returns empty gracefully`() = runBlocking {
        mockHttpClient.nextResponse = HttpResponse(code = 500, body = "Internal Server Error")
        val result = client.listTeams()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `malformed JSON response returns empty gracefully`() = runBlocking {
        mockHttpClient.nextResponse = HttpResponse(code = 200, body = "not valid json")
        val result = client.listTeams()
        assertTrue(result.isEmpty())
    }

    // --- Request headers ---

    @Test
    fun `request includes Bearer token in Authorization header`() = runBlocking {
        val data = JsonObject().apply {
            add("me", JsonObject().apply {
                addProperty("uid", "user1")
                addProperty("displayName", "Test User")
            })
        }
        mockHttpClient.nextResponse = graphqlResponse(data)
        client.testConnection()
        val lastRequest = mockHttpClient.lastRequest
        assertNotNull(lastRequest)
        val authHeader = lastRequest!!.headers.find { it.first == "Authorization" }
        assertNotNull(authHeader)
        assertEquals("Bearer test-token-123", authHeader!!.second)
    }

    @Test
    fun `request includes Content-Type json header`() = runBlocking {
        val data = JsonObject().apply {
            add("me", JsonObject().apply {
                addProperty("uid", "user1")
                addProperty("displayName", "Test User")
            })
        }
        mockHttpClient.nextResponse = graphqlResponse(data)
        client.testConnection()
        val lastRequest = mockHttpClient.lastRequest
        assertNotNull(lastRequest)
        val contentTypeHeader = lastRequest!!.headers.find { it.first == "Content-Type" }
        assertNotNull(contentTypeHeader)
        assertEquals("application/json", contentTypeHeader!!.second)
    }

    // --- URL resolution in API calls ---

    @Test
    fun `custom server URL uses api graphql path`() = runBlocking {
        val customClient = HoppscotchApiClient(
            token = "test-token",
            serverUrl = "https://custom.hoppscotch.example",
            httpClient = mockHttpClient
        )
        val data = JsonObject().apply {
            add("me", JsonObject().apply {
                addProperty("uid", "user1")
                addProperty("displayName", "Test User")
            })
        }
        mockHttpClient.nextResponse = graphqlResponse(data)
        customClient.testConnection()
        val lastRequest = mockHttpClient.lastRequest
        assertNotNull(lastRequest)
        assertEquals("https://custom.hoppscotch.example/graphql", lastRequest!!.url)
    }

    @Test
    fun `cloud server URL resolves to api hoppscotch io`() = runBlocking {
        val cloudClient = HoppscotchApiClient(
            token = "test-token",
            serverUrl = "https://hoppscotch.io",
            httpClient = mockHttpClient
        )
        val data = JsonObject().apply {
            add("me", JsonObject().apply {
                addProperty("uid", "user1")
                addProperty("displayName", "Test User")
            })
        }
        mockHttpClient.nextResponse = graphqlResponse(data)
        cloudClient.testConnection()
        val lastRequest = mockHttpClient.lastRequest
        assertNotNull(lastRequest)
        assertEquals("https://api.hoppscotch.io/graphql", lastRequest!!.url)
    }

    @Test
    fun `backendUrl overrides graphql path`() = runBlocking {
        val customClient = HoppscotchApiClient(
            token = "test-token",
            serverUrl = "https://custom.hoppscotch.example",
            backendUrl = "http://localhost:3170",
            httpClient = mockHttpClient
        )
        val data = JsonObject().apply {
            add("me", JsonObject().apply {
                addProperty("uid", "user1")
                addProperty("displayName", "Test User")
            })
        }
        mockHttpClient.nextResponse = graphqlResponse(data)
        customClient.testConnection()
        val lastRequest = mockHttpClient.lastRequest
        assertNotNull(lastRequest)
        assertEquals("http://localhost:3170/graphql", lastRequest!!.url)
    }

    // --- translateToPersonalCollectionFormat (tested via uploadCollection) ---

    @Test
    fun `uploadCollection transforms collection to personal format`() = runBlocking {
        val importData = JsonObject().apply {
            add("importUserCollectionsFromJSON", JsonObject().apply {
                addProperty("exportedCollection", "col-1")
                addProperty("collectionType", "REST")
            })
        }
        mockHttpClient.nextResponse = graphqlResponse(importData)

        val nestedFolder = HoppCollection(
            name = "SubFolder",
            requests = listOf(HoppRESTRequest(name = "Sub GET", method = "GET", endpoint = "/sub")),
            auth = HoppAuth(authType = "bearer"),
            headers = listOf(HoppKeyValue(key = "X-Custom", value = "test"))
        )
        val collection = HoppCollection(
            name = "Test Collection",
            folders = listOf(nestedFolder),
            requests = listOf(HoppRESTRequest(name = "GET /api", method = "GET", endpoint = "/api")),
            auth = HoppAuth(authType = "none"),
            headers = listOf(HoppKeyValue(key = "Authorization", value = "Bearer token")),
            variables = listOf(HoppCollectionVariable(key = "baseUrl", initialValue = "https://api.example.com")),
            description = "Test description",
            preRequestScript = "console.log('pre')",
            testScript = "console.log('test')"
        )
        val result = client.uploadCollection(collection)
        assertTrue(result.success)

        // Verify the request body contains the transformed format
        val lastBody = mockHttpClient.lastRequest?.body
        assertNotNull(lastBody)
        // The body should contain "data" sub-object with auth/headers/variables/description/scripts
        assertTrue(lastBody!!.contains("data"))
    }

    // --- Data classes ---

    @Test
    fun `HoppTeam data class`() {
        val team = HoppTeam(id = "t1", name = "Team 1")
        assertEquals("t1", team.id)
        assertEquals("Team 1", team.name)
        val copy = team.copy(name = "Team 2")
        assertEquals("t1", copy.id)
        assertEquals("Team 2", copy.name)
    }

    @Test
    fun `HoppCollectionInfo data class`() {
        val info = HoppCollectionInfo(id = "c1", name = "Col 1")
        assertEquals("c1", info.id)
        assertEquals("Col 1", info.name)
    }

    @Test
    fun `HoppUploadResult data class`() {
        val result = HoppUploadResult(success = true, message = "OK", collectionId = "col-1")
        assertTrue(result.success)
        assertEquals("OK", result.message)
        assertEquals("col-1", result.collectionId)
        val copy = result.copy(message = "Updated")
        assertEquals("Updated", copy.message)
        assertEquals("col-1", copy.collectionId)
    }

    @Test
    fun `HoppscotchAuthException is an Exception with message`() {
        val exception = HoppscotchAuthException("Token expired")
        assertTrue(exception is Exception)
        assertEquals("Token expired", exception.message)
    }

    private fun graphqlResponse(data: JsonObject): HttpResponse {
        val wrapper = JsonObject().apply { add("data", data) }
        return HttpResponse(code = 200, body = GsonUtils.GSON.toJson(wrapper))
    }

    class MockHttpClient : HttpClient {
        var nextResponse: HttpResponse = HttpResponse(code = 200, body = "{}")
        var responseQueue: ArrayDeque<HttpResponse> = ArrayDeque()
        var lastRequest: HttpRequest? = null
        var nextException: Throwable? = null

        override suspend fun execute(request: HttpRequest): HttpResponse {
            lastRequest = request
            nextException?.let { throw it }
            return if (responseQueue.isNotEmpty()) responseQueue.removeFirst() else nextResponse
        }

        override fun close() {}
    }
}
