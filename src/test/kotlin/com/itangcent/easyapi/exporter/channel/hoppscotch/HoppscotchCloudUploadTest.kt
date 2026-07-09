package com.itangcent.easyapi.exporter.channel.hoppscotch

import com.itangcent.easyapi.exporter.channel.ChannelConfig
import com.itangcent.easyapi.exporter.channel.hoppscotch.model.HoppCollection
import com.itangcent.easyapi.exporter.channel.hoppscotch.model.HoppRESTRequest
import com.itangcent.easyapi.http.HttpClient
import com.itangcent.easyapi.http.HttpRequest
import com.itangcent.easyapi.http.HttpResponse
import com.itangcent.easyapi.util.json.GsonUtils
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class HoppscotchCloudUploadTest {

    private lateinit var mockHttpClient: MockHttpClient
    private lateinit var client: HoppscotchApiClient
    private lateinit var cachedClient: CachedHoppscotchApiClient

    @Before
    fun setUp() {
        mockHttpClient = MockHttpClient()
        client = HoppscotchApiClient(
            token = "test-token",
            serverUrl = "https://hoppscotch.test",
            httpClient = mockHttpClient
        )
        cachedClient = client.asCached()
    }

    @Test
    fun `asCached extension creates CachedHoppscotchApiClient`() {
        val cached = HoppscotchApiClient("t", "u", httpClient = mockHttpClient).asCached()
        assertNotNull(cached)
        assertTrue(cached is CachedHoppscotchApiClient)
    }

    @Test
    fun `full upload flow - create new collection`() = runBlocking {
        val importData = JsonObject().apply {
            add("importUserCollectionsFromJSON", JsonObject().apply {
                addProperty("exportedCollection", "col-new-1")
                addProperty("collectionType", "REST")
            })
        }
        mockHttpClient.nextResponse = graphqlResponse(importData)

        val collection = HoppCollection(
            name = "My API",
            requests = listOf(
                HoppRESTRequest(name = "GET /users", method = "GET", endpoint = "/users"),
                HoppRESTRequest(name = "POST /users", method = "POST", endpoint = "/users")
            )
        )
        val result = cachedClient.uploadCollection(collection)
        assertTrue(result.success)
        assertEquals("col-new-1", result.collectionId)
    }

    @Test
    fun `full upload flow - update existing collection`() = runBlocking {
        val importData = JsonObject().apply {
            add("importUserCollectionsFromJSON", JsonObject().apply {
                addProperty("exportedCollection", "col-new-2")
                addProperty("collectionType", "REST")
            })
        }
        mockHttpClient.responseQueue.add(graphqlResponse(importData))
        val deleteData = JsonObject().apply {
            addProperty("deleteUserCollection", true)
        }
        mockHttpClient.responseQueue.add(graphqlResponse(deleteData))

        val collection = HoppCollection(name = "My API v2")
        val result = cachedClient.updateCollection("col-old-1", collection)
        assertTrue(result.success)
        assertEquals("col-new-2", result.collectionId)
        assertTrue(result.message!!.contains("updated successfully"))
    }

    @Test
    fun `cached client delegates upload to underlying client`() = runBlocking {
        val importData = JsonObject().apply {
            add("importUserCollectionsFromJSON", JsonObject().apply {
                addProperty("exportedCollection", "col-1")
                addProperty("collectionType", "REST")
            })
        }
        mockHttpClient.nextResponse = graphqlResponse(importData)

        val collection = HoppCollection(name = "Test")
        val result = cachedClient.uploadCollection(collection)
        assertTrue(result.success)
    }

    @Test
    fun `cached client delegates delete to underlying client`() = runBlocking {
        val deleteData = JsonObject().apply {
            addProperty("deleteUserCollection", true)
        }
        mockHttpClient.nextResponse = graphqlResponse(deleteData)
        assertTrue(cachedClient.deleteCollection("col-1"))
    }

    @Test
    fun `cached client delegates testConnection to underlying client`() = runBlocking {
        val data = JsonObject().apply {
            add("me", JsonObject().apply {
                addProperty("uid", "user1")
                addProperty("displayName", "Test User")
            })
        }
        mockHttpClient.nextResponse = graphqlResponse(data)
        assertTrue(cachedClient.testConnection())
    }

    @Test
    fun `HoppscotchConfig stores collection update info`() {
        val config = HoppscotchConfig(
            collectionId = "col-1",
            collectionName = "My Collection",
            isUpdate = true
        )
        assertEquals("col-1", config.collectionId)
        assertEquals("My Collection", config.collectionName)
        assertTrue(config.isUpdate)
    }

    @Test
    fun `HoppscotchConfig defaults to create mode`() {
        val config = HoppscotchConfig()
        assertNull(config.collectionId)
        assertNull(config.collectionName)
        assertFalse(config.isUpdate)
    }

    @Test
    fun `upload with teamId uses importCollectionsFromJSON`() = runBlocking {
        val importData = JsonObject().apply {
            addProperty("importCollectionsFromJSON", true)
        }
        mockHttpClient.nextResponse = graphqlResponse(importData)

        val collection = HoppCollection(name = "Team Collection")
        val result = cachedClient.uploadCollection(collection, teamId = "team-1")
        assertTrue(result.success)

        val lastBody = mockHttpClient.lastRequest?.body
        assertNotNull(lastBody)
        assertTrue(lastBody!!.contains("importCollectionsFromJSON"))
        assertTrue(lastBody.contains("teamID"))
    }

    @Test
    fun `upload failure does not delete old collection during update`() = runBlocking {
        val errorBody = """{"errors":[{"message":"Server error"}]}"""
        mockHttpClient.nextResponse = HttpResponse(code = 200, body = errorBody)

        val collection = HoppCollection(name = "Failed Upload")
        val result = client.updateCollection("old-col-1", collection)
        assertFalse(result.success)
    }

    @Test
    fun `HoppUploadResult copy preserves fields`() {
        val original = HoppUploadResult(success = true, message = "OK", collectionId = "col-1")
        val copied = original.copy(message = "Updated successfully (new ID: col-1)")
        assertTrue(copied.success)
        assertEquals("Updated successfully (new ID: col-1)", copied.message)
        assertEquals("col-1", copied.collectionId)
    }

    private fun graphqlResponse(data: JsonObject): HttpResponse {
        val wrapper = JsonObject().apply { add("data", data) }
        return HttpResponse(code = 200, body = GsonUtils.GSON.toJson(wrapper))
    }

    class MockHttpClient : HttpClient {
        var nextResponse: HttpResponse = HttpResponse(code = 200, body = "{}")
        var responseQueue: ArrayDeque<HttpResponse> = ArrayDeque()
        var lastRequest: HttpRequest? = null

        override suspend fun execute(request: HttpRequest): HttpResponse {
            lastRequest = request
            return if (responseQueue.isNotEmpty()) responseQueue.removeFirst() else nextResponse
        }

        override fun close() {}
    }
}
