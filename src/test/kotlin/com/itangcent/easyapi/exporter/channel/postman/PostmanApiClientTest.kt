package com.itangcent.easyapi.exporter.channel.postman

import com.itangcent.easyapi.exporter.channel.postman.model.CollectionInfo
import com.itangcent.easyapi.exporter.channel.postman.model.PostmanCollection
import com.itangcent.easyapi.http.HttpClient
import com.itangcent.easyapi.http.HttpRequest
import com.itangcent.easyapi.http.HttpResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for PostmanApiClient with a mock HttpClient.
 * Covers upload, update, listWorkspaces, listCollections, and error handling.
 */
class PostmanApiClientTest {

    private lateinit var mockClient: MockHttpClient
    private lateinit var apiClient: PostmanApiClient

    @Before
    fun setUp() {
        mockClient = MockHttpClient()
        apiClient = PostmanApiClient(
            apiKey = "test-api-key",
            workspaceId = "ws-123",
            httpClient = mockClient
        )
    }

    // ==================== uploadCollection ====================

    @Test
    fun `uploadCollection returns success on 200`() = runBlocking {
        mockClient.nextResponse = HttpResponse(
            code = 200,
            body = """{"collection":{"id":"col-1","uid":"col-1-uid","name":"Test","schema":"https://schema.getpostman.com/json/collection/v2.1.0/collection.json"}}"""
        )
        val collection = PostmanCollection(info = CollectionInfo(name = "Test"))
        val result = apiClient.uploadCollection(collection)
        assertTrue(result.success)
        assertEquals("col-1-uid", result.collectionId)
        assertEquals("POST", mockClient.lastRequest?.method)
        assertTrue(mockClient.lastRequest?.url?.contains("workspace=ws-123") == true)
    }

    @Test
    fun `uploadCollection without workspaceId uses base URL`() = runBlocking {
        val client = PostmanApiClient(apiKey = "test-key", httpClient = mockClient)
        mockClient.nextResponse = HttpResponse(code = 200, body = """{"collection":{"uid":"col-uid"}}""")
        val collection = PostmanCollection(info = CollectionInfo(name = "Test"))
        val result = client.uploadCollection(collection)
        assertTrue(result.success)
        assertFalse(mockClient.lastRequest?.url?.contains("workspace") == true)
    }

    @Test
    fun `uploadCollection returns failure on non-200`() = runBlocking {
        mockClient.nextResponse = HttpResponse(code = 401, body = "Unauthorized")
        val collection = PostmanCollection(info = CollectionInfo(name = "Test"))
        val result = apiClient.uploadCollection(collection)
        assertFalse(result.success)
        assertTrue(result.message?.contains("401") == true)
    }

    @Test
    fun `uploadCollection handles exception`() = runBlocking {
        mockClient.shouldThrow = true
        val collection = PostmanCollection(info = CollectionInfo(name = "Test"))
        val result = apiClient.uploadCollection(collection)
        assertFalse(result.success)
        assertNotNull(result.message)
    }

    @Test
    fun `uploadCollection with blank apiKey returns mock mode`() = runBlocking {
        val client = PostmanApiClient(apiKey = "", httpClient = mockClient)
        val collection = PostmanCollection(info = CollectionInfo(name = "Test"))
        val result = client.uploadCollection(collection)
        assertTrue(result.success)
        assertTrue(result.message?.contains("Mock mode") == true)
        assertNull(mockClient.lastRequest) // No HTTP call made
    }

    // ==================== updateCollection ====================

    @Test
    fun `updateCollection returns success on 200`() = runBlocking {
        mockClient.nextResponse = HttpResponse(code = 200, body = """{"collection":{"id":"col-1"}}""")
        val collection = PostmanCollection(info = CollectionInfo(name = "Test"))
        val result = apiClient.updateCollection("col-uid-123", collection)
        assertTrue(result.success)
        assertEquals("PUT", mockClient.lastRequest?.method)
        assertTrue(mockClient.lastRequest?.url?.contains("/collections/col-uid-123") == true)
    }

    @Test
    fun `updateCollection returns failure on non-200`() = runBlocking {
        mockClient.nextResponse = HttpResponse(code = 404, body = "Not Found")
        val collection = PostmanCollection(info = CollectionInfo(name = "Test"))
        val result = apiClient.updateCollection("col-uid-123", collection)
        assertFalse(result.success)
    }

    @Test
    fun `updateCollection handles exception`() = runBlocking {
        mockClient.shouldThrow = true
        val collection = PostmanCollection(info = CollectionInfo(name = "Test"))
        val result = apiClient.updateCollection("col-uid-123", collection)
        assertFalse(result.success)
    }

    @Test
    fun `updateCollection with blank apiKey returns mock mode`() = runBlocking {
        val client = PostmanApiClient(apiKey = "", httpClient = mockClient)
        val collection = PostmanCollection(info = CollectionInfo(name = "Test"))
        val result = client.updateCollection("col-uid-123", collection)
        assertTrue(result.success)
        assertTrue(result.message?.contains("Mock mode") == true)
    }

    // ==================== listWorkspaces ====================

    @Test
    fun `listWorkspaces returns workspaces on 200`() = runBlocking {
        mockClient.nextResponse = HttpResponse(
            code = 200,
            body = """{"workspaces":[{"id":"ws-1","name":"Workspace 1"},{"id":"ws-2","name":"Workspace 2"}]}"""
        )
        val result = apiClient.listWorkspaces()
        assertEquals(2, result.size)
        assertEquals("ws-1", result[0].id)
        assertEquals("Workspace 1", result[0].name)
    }

    @Test
    fun `listWorkspaces returns empty on non-200`() = runBlocking {
        mockClient.nextResponse = HttpResponse(code = 401, body = "Unauthorized")
        val result = apiClient.listWorkspaces()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `listWorkspaces returns empty on malformed JSON`() = runBlocking {
        mockClient.nextResponse = HttpResponse(code = 200, body = "not json")
        val result = apiClient.listWorkspaces()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `listWorkspaces with blank apiKey returns empty`() = runBlocking {
        val client = PostmanApiClient(apiKey = "", httpClient = mockClient)
        val result = client.listWorkspaces()
        assertTrue(result.isEmpty())
    }

    // ==================== listCollections ====================

    @Test
    fun `listCollections returns collections on 200`() = runBlocking {
        mockClient.nextResponse = HttpResponse(
            code = 200,
            body = """{"collections":[{"id":"col-1","name":"Collection 1","uid":"col-1-uid"},{"id":"col-2","name":"Collection 2","uid":"col-2-uid"}]}"""
        )
        val result = apiClient.listCollections("ws-123")
        assertEquals(2, result.size)
        assertEquals("col-1", result[0].id)
        assertEquals("Collection 1", result[0].name)
        assertEquals("col-1-uid", result[0].uid)
    }

    @Test
    fun `listCollections returns empty on non-200`() = runBlocking {
        mockClient.nextResponse = HttpResponse(code = 500, body = "Server Error")
        val result = apiClient.listCollections("ws-123")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `listCollections returns empty on malformed JSON`() = runBlocking {
        mockClient.nextResponse = HttpResponse(code = 200, body = "invalid")
        val result = apiClient.listCollections("ws-123")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `listCollections with blank apiKey returns empty`() = runBlocking {
        val client = PostmanApiClient(apiKey = "", httpClient = mockClient)
        val result = client.listCollections("ws-123")
        assertTrue(result.isEmpty())
    }

    // ==================== Data classes ====================

    @Test
    fun `UploadResult data class`() {
        val result = UploadResult(success = true, message = "OK", collectionId = "col-1")
        assertTrue(result.success)
        assertEquals("OK", result.message)
        assertEquals("col-1", result.collectionId)
    }

    @Test
    fun `UploadResult copy`() {
        val result = UploadResult(success = true, message = "OK", collectionId = "col-1")
        val copy = result.copy(success = false)
        assertFalse(copy.success)
        assertEquals("OK", copy.message)
    }

    @Test
    fun `Workspace data class`() {
        val workspace = Workspace(id = "ws-1", name = "My Workspace")
        assertEquals("ws-1", workspace.id)
        assertEquals("My Workspace", workspace.name)
    }

    @Test
    fun `PostmanCollectionInfo data class`() {
        val info = PostmanCollectionInfo(id = "col-1", name = "Test", uid = "col-1-uid")
        assertEquals("col-1", info.id)
        assertEquals("Test", info.name)
        assertEquals("col-1-uid", info.uid)
    }

    @Test
    fun `PostmanCollectionInfo with null uid`() {
        val info = PostmanCollectionInfo(id = "col-1", name = "Test", uid = null)
        assertNull(info.uid)
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
