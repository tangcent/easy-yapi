package com.itangcent.easyapi.exporter.channel.postman

import com.itangcent.easyapi.http.HttpClient
import com.itangcent.easyapi.http.HttpResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito

class PostmanApiClientErrorTest {

    private fun createClient(
        apiKey: String = "test-key",
        workspaceId: String? = null,
        httpClient: HttpClient = Mockito.mock(HttpClient::class.java)
    ): PostmanApiClient {
        return PostmanApiClient(apiKey, workspaceId, httpClient)
    }

    // --- Blank API key tests ---

    @Test
    fun testUploadCollectionBlankApiKey() = runBlocking {
        val client = createClient(apiKey = "")
        val collection = com.itangcent.easyapi.exporter.channel.postman.model.PostmanCollection(
            info = com.itangcent.easyapi.exporter.channel.postman.model.CollectionInfo(
                name = "Test",
                schema = "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
            )
        )
        val result = client.uploadCollection(collection)
        assertTrue("Should succeed in mock mode", result.success)
        assertTrue("Message should mention mock mode", result.message?.contains("Mock mode") == true)
    }

    @Test
    fun testUpdateCollectionBlankApiKey() = runBlocking {
        val client = createClient(apiKey = "")
        val collection = com.itangcent.easyapi.exporter.channel.postman.model.PostmanCollection(
            info = com.itangcent.easyapi.exporter.channel.postman.model.CollectionInfo(
                name = "Test",
                schema = "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
            )
        )
        val result = client.updateCollection("uid-123", collection)
        assertTrue("Should succeed in mock mode", result.success)
    }

    @Test
    fun testListWorkspacesBlankApiKey() = runBlocking {
        val client = createClient(apiKey = "")
        val result = client.listWorkspaces()
        assertTrue("Should return empty list for blank API key", result.isEmpty())
    }

    @Test
    fun testListCollectionsBlankApiKey() = runBlocking {
        val client = createClient(apiKey = "")
        val result = client.listCollections("ws-123")
        assertTrue("Should return empty list for blank API key", result.isEmpty())
    }

    // --- UploadResult data class tests ---

    @Test
    fun testUploadResultCreation() {
        val result = UploadResult(success = true, message = "OK", collectionId = "uid-123")
        assertTrue(result.success)
        assertEquals("OK", result.message)
        assertEquals("uid-123", result.collectionId)
    }

    @Test
    fun testUploadResultDefaults() {
        val result = UploadResult(success = false)
        assertFalse(result.success)
        assertNull(result.message)
        assertNull(result.collectionId)
    }

    // --- Workspace data class tests ---

    @Test
    fun testWorkspaceCreation() {
        val ws = Workspace(id = "ws-1", name = "My Workspace")
        assertEquals("ws-1", ws.id)
        assertEquals("My Workspace", ws.name)
    }

    // --- PostmanCollectionInfo data class tests ---

    @Test
    fun testPostmanCollectionInfoCreation() {
        val info = PostmanCollectionInfo(id = "col-1", name = "API Collection", uid = "uid-1")
        assertEquals("col-1", info.id)
        assertEquals("API Collection", info.name)
        assertEquals("uid-1", info.uid)
    }

    @Test
    fun testPostmanCollectionInfoWithoutUid() {
        val info = PostmanCollectionInfo(id = "col-1", name = "API Collection")
        assertEquals("col-1", info.id)
        assertNull(info.uid)
    }

    // --- API base URL constant ---

    @Test
    fun testApiBaseUrlConstant() {
        assertEquals("https://api.getpostman.com", PostmanApiClient.API_BASE_URL)
    }

    // --- PostmanApiClient.asCached() ---

    @Test
    fun testAsCachedReturnsCachedClient() {
        val client = createClient(apiKey = "test-key")
        val cached = client.asCached()
        assertNotNull("Should return cached client", cached)
        assertTrue("Should be CachedPostmanApiClient", cached is CachedPostmanApiClient)
    }
}
