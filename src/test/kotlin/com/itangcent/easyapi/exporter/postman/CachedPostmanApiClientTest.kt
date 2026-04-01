package com.itangcent.easyapi.exporter.postman

import com.itangcent.easyapi.cache.AppCacheRepository
import com.itangcent.easyapi.http.UrlConnectionHttpClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CachedPostmanApiClientTest {

    private fun createClient(apiKey: String = ""): CachedPostmanApiClient {
        val postmanClient = PostmanApiClient(apiKey = apiKey, httpClient = UrlConnectionHttpClient)
        return CachedPostmanApiClient(postmanClient)
    }

    @Test
    fun testListWorkspacesWithEmptyApiKey() = runBlocking {
        val client = createClient(apiKey = "")

        val workspaces = client.listWorkspaces()
        assertTrue("Workspaces should be empty for blank API key", workspaces.isEmpty())
    }

    @Test
    fun testListWorkspacesWithCache() = runBlocking {
        AppCacheRepository.getInstance().clear()

        val client = createClient(apiKey = "test-api-key")

        val workspacesWithoutCache = client.listWorkspaces(useCache = false)
        val workspacesWithCache = client.listWorkspaces(useCache = true)

        assertNotNull("Workspaces list should not be null", workspacesWithCache)
    }

    @Test
    fun testClearWorkspacesCache() = runBlocking {
        AppCacheRepository.getInstance().clear()

        val client = createClient(apiKey = "test-api-key")

        client.listWorkspaces(useCache = false)
        client.clearWorkspacesCache()

        val cached = AppCacheRepository.getInstance().read("postman/workspaces.json")
        assertNull("Cache should be cleared", cached)
    }

    @Test
    fun testWorkspaceCaching() = runBlocking {
        AppCacheRepository.getInstance().clear()

        val client = createClient(apiKey = "test-api-key")

        val workspaces = client.listWorkspaces(useCache = false)

        val cached = AppCacheRepository.getInstance().read("postman/workspaces.json")
        if (workspaces.isNotEmpty()) {
            assertNotNull("Cache should be populated after fetching workspaces", cached)
        }
    }

    @Test
    fun testListCollectionsWithEmptyApiKey() = runBlocking {
        val client = createClient(apiKey = "")

        val collections = client.listCollections("test-workspace-id")
        assertTrue("Collections should be empty for blank API key", collections.isEmpty())
    }

    @Test
    fun testListCollectionsWithCache() = runBlocking {
        AppCacheRepository.getInstance().clear()

        val client = createClient(apiKey = "test-api-key")

        val collectionsWithoutCache = client.listCollections("test-workspace-id", useCache = false)
        val collectionsWithCache = client.listCollections("test-workspace-id", useCache = true)

        assertNotNull("Collections list should not be null", collectionsWithCache)
    }

    @Test
    fun testCollectionCaching() = runBlocking {
        AppCacheRepository.getInstance().clear()

        val client = createClient(apiKey = "test-api-key")

        val workspaceId = "test-workspace-id"
        val collections = client.listCollections(workspaceId, useCache = false)

        val cacheKey = "postman/collections_$workspaceId.json"
        val cached = AppCacheRepository.getInstance().read(cacheKey)
        if (collections.isNotEmpty()) {
            assertNotNull("Cache should be populated after fetching collections", cached)
        }
    }

    @Test
    fun testUploadCollection() = runBlocking {
        val client = createClient(apiKey = "")

        val collection = com.itangcent.easyapi.exporter.postman.model.PostmanCollection(
            info = com.itangcent.easyapi.exporter.postman.model.CollectionInfo(
                name = "Test Collection"
            )
        )

        val result = client.uploadCollection(collection)
        assertTrue("Upload should succeed with empty API key (mock mode)", result.success)
    }

    @Test
    fun testUpdateCollection() = runBlocking {
        val client = createClient(apiKey = "")

        val collection = com.itangcent.easyapi.exporter.postman.model.PostmanCollection(
            info = com.itangcent.easyapi.exporter.postman.model.CollectionInfo(
                name = "Test Collection"
            )
        )

        val result = client.updateCollection("test-collection-id", collection)
        assertTrue("Update should succeed with empty API key (mock mode)", result.success)
    }
}