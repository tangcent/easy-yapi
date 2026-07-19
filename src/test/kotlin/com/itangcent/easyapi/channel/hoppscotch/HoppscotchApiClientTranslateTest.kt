package com.itangcent.easyapi.channel.hoppscotch

import com.itangcent.easyapi.channel.hoppscotch.model.*
import com.itangcent.easyapi.core.http.HttpClient
import com.itangcent.easyapi.core.http.HttpRequest
import com.itangcent.easyapi.core.http.HttpResponse
import com.itangcent.easyapi.core.util.json.GsonUtils
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for HoppscotchApiClient.translateToPersonalCollectionFormat and
 * additional companion method edge cases.
 */
class HoppscotchApiClientTranslateTest {

    private lateinit var mockHttpClient: MockHttpClient
    private lateinit var client: HoppscotchApiClient

    @Before
    fun setUp() {
        mockHttpClient = MockHttpClient()
        client = HoppscotchApiClient(
            token = "test-token",
            serverUrl = "https://hoppscotch.test",
            httpClient = mockHttpClient
        )
    }

    // ==================== translateToPersonalCollectionFormat via reflection ====================

    @Test
    fun `translateToPersonalCollectionFormat moves auth headers variables to data sub-object`() {
        val method = HoppscotchApiClient::class.java.getDeclaredMethod(
            "translateToPersonalCollectionFormat", HoppCollection::class.java
        )
        method.isAccessible = true

        val collection = HoppCollection(
            name = "Test Collection",
            auth = HoppAuth(authType = "bearer"),
            headers = listOf(HoppKeyValue(key = "X-Custom", value = "test")),
            variables = listOf(HoppCollectionVariable(key = "baseUrl", initialValue = "https://api.example.com")),
            description = "A test collection",
            preRequestScript = "console.log('pre')",
            testScript = "console.log('test')",
            requests = listOf(
                HoppRESTRequest(name = "GET /api", method = "GET", endpoint = "/api")
            ),
            folders = listOf(
                HoppCollection(name = "Sub Folder")
            )
        )

        val result = method.invoke(client, collection) as JsonObject

        // Top-level fields
        assertEquals(12, result.get("v").asInt)
        assertEquals("Test Collection", result.get("name").asString)
        assertNotNull(result.get("_ref_id"))
        assertTrue(result.get("folders").isJsonArray)
        assertTrue(result.get("requests").isJsonArray)

        // Data sub-object
        val data = result.getAsJsonObject("data")
        assertNotNull(data)
        assertNotNull(data.get("auth"))
        assertNotNull(data.get("headers"))
        assertNotNull(data.get("variables"))
        assertEquals("A test collection", data.get("description").asString)
        assertEquals("console.log('pre')", data.get("preRequestScript").asString)
        assertEquals("console.log('test')", data.get("testScript").asString)
    }

    @Test
    fun `translateToPersonalCollectionFormat handles empty collection`() {
        val method = HoppscotchApiClient::class.java.getDeclaredMethod(
            "translateToPersonalCollectionFormat", HoppCollection::class.java
        )
        method.isAccessible = true

        val collection = HoppCollection(name = "Empty")
        val result = method.invoke(client, collection) as JsonObject

        assertEquals("Empty", result.get("name").asString)
        val data = result.getAsJsonObject("data")
        assertNotNull(data)
        assertEquals("", data.get("description").asString)
        assertEquals("", data.get("preRequestScript").asString)
        assertEquals("", data.get("testScript").asString)
    }

    @Test
    fun `translateToPersonalCollectionFormat recursively transforms folders`() {
        val method = HoppscotchApiClient::class.java.getDeclaredMethod(
            "translateToPersonalCollectionFormat", HoppCollection::class.java
        )
        method.isAccessible = true

        val nestedFolder = HoppCollection(
            name = "Nested",
            requests = listOf(HoppRESTRequest(name = "POST /inner", method = "POST", endpoint = "/inner"))
        )
        val collection = HoppCollection(
            name = "Root",
            folders = listOf(nestedFolder)
        )

        val result = method.invoke(client, collection) as JsonObject
        val folders = result.getAsJsonArray("folders")
        assertEquals(1, folders.size())

        val nestedResult = folders[0].asJsonObject
        assertEquals("Nested", nestedResult.get("name").asString)
        // Nested folder should also have data sub-object
        assertNotNull(nestedResult.getAsJsonObject("data"))
    }

    // ==================== resolveApiBaseUrl edge cases ====================

    @Test
    fun `resolveApiBaseUrl handles URL with port`() {
        val result = HoppscotchApiClient.resolveApiBaseUrl("https://hoppscotch.io:443")
        assertEquals("https://api.hoppscotch.io", result)
    }

    @Test
    fun `resolveApiBaseUrl handles HTTP scheme for self-hosted`() {
        val result = HoppscotchApiClient.resolveApiBaseUrl("http://localhost:3000")
        assertEquals("http://localhost:3000", result)
    }

    @Test
    fun `resolveApiBaseUrl handles HTTP with backend URL`() {
        val result = HoppscotchApiClient.resolveApiBaseUrl("http://localhost:3000", "http://localhost:3170")
        assertEquals("http://localhost:3170", result)
    }

    // ==================== resolveApiV1BaseUrl edge cases ====================

    @Test
    fun `resolveApiV1BaseUrl for cloud with trailing slash`() {
        val result = HoppscotchApiClient.resolveApiV1BaseUrl("https://hoppscotch.io/")
        assertEquals("https://api.hoppscotch.io/v1", result)
    }

    @Test
    fun `resolveApiV1BaseUrl for self-hosted HTTP`() {
        val result = HoppscotchApiClient.resolveApiV1BaseUrl("http://localhost:3000")
        assertEquals("http://localhost:3000/v1", result)
    }

    // ==================== resolveGraphQLUrl edge cases ====================

    @Test
    fun `resolveGraphQLUrl for self-hosted with backend URL`() {
        val result = HoppscotchApiClient.resolveGraphQLUrl("http://localhost:3000", "http://localhost:3170")
        assertEquals("http://localhost:3170/graphql", result)
    }

    // ==================== isCloudServer edge cases ====================

    @Test
    fun `isCloudServer returns false for localhost`() {
        assertFalse(HoppscotchApiClient.isCloudServer("http://localhost:3000"))
    }

    @Test
    fun `isCloudServer returns false for IP address`() {
        assertFalse(HoppscotchApiClient.isCloudServer("http://192.168.1.1:3000"))
    }

    // ==================== 405 Method Not Allowed handling ====================

    @Test
    fun `405 response makes listTeams return empty list`() = runBlocking {
        mockHttpClient.nextResponse = HttpResponse(code = 405, body = "Method Not Allowed")
        val result = client.listTeams()
        assertTrue(result.isEmpty())
    }

    // ==================== HoppTeam data class tests ====================

    @Test
    fun `HoppTeam data class operations`() {
        val team = HoppTeam(id = "team-1", name = "My Team")
        assertEquals("team-1", team.id)
        assertEquals("My Team", team.name)

        val copy = team.copy(name = "Updated Team")
        assertEquals("team-1", copy.id)
        assertEquals("Updated Team", copy.name)

        val team2 = HoppTeam(id = "team-1", name = "My Team")
        assertEquals(team, team2)
        assertEquals(team.hashCode(), team2.hashCode())
    }

    // ==================== HoppCollectionInfo data class tests ====================

    @Test
    fun `HoppCollectionInfo data class operations`() {
        val info = HoppCollectionInfo(id = "col-1", name = "My Collection")
        assertEquals("col-1", info.id)
        assertEquals("My Collection", info.name)

        val copy = info.copy(name = "Renamed")
        assertEquals("col-1", copy.id)
        assertEquals("Renamed", copy.name)
    }

    // ==================== HoppUploadResult data class tests ====================

    @Test
    fun `HoppUploadResult success with collectionId`() {
        val result = HoppUploadResult(success = true, message = "OK", collectionId = "col-1")
        assertTrue(result.success)
        assertEquals("OK", result.message)
        assertEquals("col-1", result.collectionId)
    }

    @Test
    fun `HoppUploadResult failure`() {
        val result = HoppUploadResult(success = false, message = "Upload failed")
        assertFalse(result.success)
        assertEquals("Upload failed", result.message)
        assertNull(result.collectionId)
    }

    @Test
    fun `HoppUploadResult default values`() {
        val result = HoppUploadResult(success = true)
        assertTrue(result.success)
        assertNull(result.message)
        assertNull(result.collectionId)
    }

    @Test
    fun `HoppUploadResult copy`() {
        val result = HoppUploadResult(success = true, message = "OK", collectionId = "col-1")
        val copy = result.copy(collectionId = "col-2")
        assertEquals("col-2", copy.collectionId)
        assertEquals("OK", copy.message)
    }

    private fun graphqlResponse(data: JsonObject): HttpResponse {
        val wrapper = JsonObject().apply { add("data", data) }
        return HttpResponse(code = 200, body = GsonUtils.GSON.toJson(wrapper))
    }

    class MockHttpClient : HttpClient {
        var nextResponse: HttpResponse = HttpResponse(code = 200, body = "{}")
        var nextException: Exception? = null
        var responseQueue: ArrayDeque<HttpResponse> = ArrayDeque()
        var lastRequest: HttpRequest? = null

        override suspend fun execute(request: HttpRequest): HttpResponse {
            lastRequest = request
            nextException?.let { throw it }
            return if (responseQueue.isNotEmpty()) responseQueue.removeFirst() else nextResponse
        }

        override fun close() {}
    }
}
