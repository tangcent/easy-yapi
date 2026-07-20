package com.itangcent.easyapi.channel.hoppscotch

import com.itangcent.easyapi.core.cache.AppCacheRepository
import com.itangcent.easyapi.channel.hoppscotch.model.HoppCollection
import com.itangcent.easyapi.channel.hoppscotch.model.HoppRESTRequest
import com.itangcent.easyapi.core.http.HttpClient
import com.itangcent.easyapi.core.http.HttpRequest
import com.itangcent.easyapi.core.http.HttpResponse
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.core.util.json.GsonUtils
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking

/**
 * Tests for CachedHoppscotchApiClient — cache hit/miss/clear logic
 * and delegation to underlying client.
 */
class CachedHoppscotchApiClientIntegrationTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var mockHttpClient: MockHttpClient
    private lateinit var client: HoppscotchApiClient
    private lateinit var cachedClient: CachedHoppscotchApiClient
    private lateinit var cacheRepo: AppCacheRepository

    override fun setUp() {
        super.setUp()
        mockHttpClient = MockHttpClient()
        client = HoppscotchApiClient(
            token = "test-token",
            serverUrl = "https://hoppscotch.test",
            httpClient = mockHttpClient
        )
        cachedClient = client.asCached()
        cacheRepo = AppCacheRepository.getInstance()
        // Clean up any existing cache
        cachedClient.clearTeamsCache()
        cachedClient.clearCollectionsCache()
        cachedClient.clearCollectionsCache("team1")
    }

    override fun tearDown() {
        cachedClient.clearTeamsCache()
        cachedClient.clearCollectionsCache()
        cachedClient.clearCollectionsCache("team1")
        super.tearDown()
    }

    // ==================== listTeams cache tests ====================

    fun testListTeamsReturnsTeamsFromApiOnCacheMiss() = runBlocking {
        val teamsData = JsonObject().apply {
            add("myTeams", GsonUtils.GSON.toJsonTree(listOf(
                mapOf("id" to "team1", "name" to "Team A")
            )))
        }
        mockHttpClient.nextResponse = graphqlResponse(teamsData)

        val teams = cachedClient.listTeams()
        assertEquals(1, teams.size)
        assertEquals("team1", teams[0].id)
        assertEquals("Team A", teams[0].name)
    }

    fun testListTeamsReturnsCachedTeamsOnCacheHit() = runBlocking {
        // First call populates cache
        val teamsData = JsonObject().apply {
            add("myTeams", GsonUtils.GSON.toJsonTree(listOf(
                mapOf("id" to "team1", "name" to "Team A")
            )))
        }
        mockHttpClient.nextResponse = graphqlResponse(teamsData)
        cachedClient.listTeams()

        // Second call should use cache
        val teams = cachedClient.listTeams()
        assertEquals(1, teams.size)
        assertEquals("team1", teams[0].id)
    }

    fun testListTeamsBypassesCacheWhenUseCacheIsFalse() = runBlocking {
        // First call populates cache
        val teamsData1 = JsonObject().apply {
            add("myTeams", GsonUtils.GSON.toJsonTree(listOf(
                mapOf("id" to "team1", "name" to "Team A")
            )))
        }
        mockHttpClient.nextResponse = graphqlResponse(teamsData1)
        cachedClient.listTeams()

        // Second call with useCache=false should hit API again
        val teamsData2 = JsonObject().apply {
            add("myTeams", GsonUtils.GSON.toJsonTree(listOf(
                mapOf("id" to "team2", "name" to "Team B")
            )))
        }
        mockHttpClient.nextResponse = graphqlResponse(teamsData2)
        val teams = cachedClient.listTeams(useCache = false)
        assertEquals(1, teams.size)
        assertEquals("team2", teams[0].id)
    }

    fun testClearTeamsCache() = runBlocking {
        // Populate cache
        val teamsData = JsonObject().apply {
            add("myTeams", GsonUtils.GSON.toJsonTree(listOf(
                mapOf("id" to "team1", "name" to "Team A")
            )))
        }
        mockHttpClient.nextResponse = graphqlResponse(teamsData)
        cachedClient.listTeams()

        // Clear cache
        cachedClient.clearTeamsCache()

        // Next call should hit API again
        val teamsData2 = JsonObject().apply {
            add("myTeams", GsonUtils.GSON.toJsonTree(listOf(
                mapOf("id" to "team2", "name" to "Team B")
            )))
        }
        mockHttpClient.nextResponse = graphqlResponse(teamsData2)
        val teams = cachedClient.listTeams()
        assertEquals(1, teams.size)
        assertEquals("team2", teams[0].id)
    }

    // ==================== listCollections cache tests ====================

    fun testListCollectionsReturnsCollectionsFromApiOnCacheMiss() = runBlocking {
        val collectionsData = JsonObject().apply {
            add("rootCollectionsOfTeam", GsonUtils.GSON.toJsonTree(listOf(
                mapOf("id" to "col1", "title" to "Collection A")
            )))
        }
        mockHttpClient.nextResponse = graphqlResponse(collectionsData)

        val collections = cachedClient.listCollections()
        assertEquals(1, collections.size)
        assertEquals("col1", collections[0].id)
    }

    fun testListCollectionsReturnsCachedCollectionsOnCacheHit() = runBlocking {
        // First call populates cache
        val collectionsData = JsonObject().apply {
            add("rootCollectionsOfTeam", GsonUtils.GSON.toJsonTree(listOf(
                mapOf("id" to "col1", "title" to "Collection A")
            )))
        }
        mockHttpClient.nextResponse = graphqlResponse(collectionsData)
        cachedClient.listCollections()

        // Second call should use cache
        val collections = cachedClient.listCollections()
        assertEquals(1, collections.size)
        assertEquals("col1", collections[0].id)
    }

    fun testListCollectionsWithTeamIdUsesTeamSpecificCache() = runBlocking {
        val collectionsData = JsonObject().apply {
            add("rootCollectionsOfTeam", GsonUtils.GSON.toJsonTree(listOf(
                mapOf("id" to "col1", "title" to "Team Collection")
            )))
        }
        mockHttpClient.nextResponse = graphqlResponse(collectionsData)

        val collections = cachedClient.listCollections(teamId = "team1")
        assertEquals(1, collections.size)
        assertEquals("col1", collections[0].id)
    }

    fun testListCollectionsBypassesCacheWhenUseCacheIsFalse() = runBlocking {
        // First call populates cache
        val collectionsData1 = JsonObject().apply {
            add("rootCollectionsOfTeam", GsonUtils.GSON.toJsonTree(listOf(
                mapOf("id" to "col1", "title" to "Collection A")
            )))
        }
        mockHttpClient.nextResponse = graphqlResponse(collectionsData1)
        cachedClient.listCollections()

        // Second call with useCache=false should hit API again
        val collectionsData2 = JsonObject().apply {
            add("rootCollectionsOfTeam", GsonUtils.GSON.toJsonTree(listOf(
                mapOf("id" to "col2", "title" to "Collection B")
            )))
        }
        mockHttpClient.nextResponse = graphqlResponse(collectionsData2)
        val collections = cachedClient.listCollections(useCache = false)
        assertEquals(1, collections.size)
        assertEquals("col2", collections[0].id)
    }

    fun testClearCollectionsCache() = runBlocking {
        // Populate cache
        val collectionsData = JsonObject().apply {
            add("rootCollectionsOfTeam", GsonUtils.GSON.toJsonTree(listOf(
                mapOf("id" to "col1", "title" to "Collection A")
            )))
        }
        mockHttpClient.nextResponse = graphqlResponse(collectionsData)
        cachedClient.listCollections()

        // Clear cache
        cachedClient.clearCollectionsCache()

        // Next call should hit API again
        val collectionsData2 = JsonObject().apply {
            add("rootCollectionsOfTeam", GsonUtils.GSON.toJsonTree(listOf(
                mapOf("id" to "col2", "title" to "Collection B")
            )))
        }
        mockHttpClient.nextResponse = graphqlResponse(collectionsData2)
        val collections = cachedClient.listCollections()
        assertEquals(1, collections.size)
        assertEquals("col2", collections[0].id)
    }

    // ==================== Delegation tests ====================

    fun testTestConnectionDelegatesToUnderlyingClient() = runBlocking {
        val data = JsonObject().apply {
            add("me", JsonObject().apply {
                addProperty("uid", "user1")
                addProperty("displayName", "Test User")
            })
        }
        mockHttpClient.nextResponse = graphqlResponse(data)
        assertTrue(cachedClient.testConnection())
    }

    fun testUploadCollectionDelegatesToUnderlyingClient() = runBlocking {
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

    fun testDeleteCollectionDelegatesToUnderlyingClient() = runBlocking {
        val deleteData = JsonObject().apply {
            addProperty("deleteUserCollection", true)
        }
        mockHttpClient.nextResponse = graphqlResponse(deleteData)
        assertTrue(cachedClient.deleteCollection("col-1"))
    }

    // ==================== asCached extension ====================

    fun testAsCachedExtensionCreatesCachedHoppscotchApiClient() {
        val cached = HoppscotchApiClient("t", "u", httpClient = mockHttpClient).asCached()
        assertNotNull(cached)
        assertTrue(cached is CachedHoppscotchApiClient)
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
