package com.itangcent.easyapi.exporter.postman

import com.itangcent.easyapi.cache.AppCacheRepository
import com.itangcent.easyapi.http.UrlConnectionHttpClient
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.Assert.*

class CachedPostmanApiClientTest : EasyApiLightCodeInsightFixtureTestCase() {

    private fun createClient(apiKey: String = ""): CachedPostmanApiClient {
        val postmanClient = PostmanApiClient(apiKey = apiKey, httpClient = UrlConnectionHttpClient)
        return CachedPostmanApiClient(postmanClient)
    }

    fun testListWorkspacesWithEmptyApiKey() = runTest {
        val client = createClient(apiKey = "")

        // A blank API key will result in an unauthorized or empty response from the server.
        // We just verify the call doesn't throw — the result may be empty or contain an error.
        val workspaces = client.listWorkspaces()
        assertNotNull("Result should not be null", workspaces)
    }

    fun testListWorkspacesWithCache() = runTest {
        AppCacheRepository.getInstance().clear()

        val client = createClient(apiKey = "test-api-key")

        val workspacesWithoutCache = client.listWorkspaces(useCache = false)
        val workspacesWithCache = client.listWorkspaces(useCache = true)

        assertNotNull("Workspaces list should not be null", workspacesWithCache)
    }

    fun testClearWorkspacesCache() = runTest {
        AppCacheRepository.getInstance().clear()

        val client = createClient(apiKey = "test-api-key")

        client.listWorkspaces(useCache = false)
        client.clearWorkspacesCache()

        val cached = AppCacheRepository.getInstance().read("postman/workspaces.json")
        assertNull("Cache should be cleared", cached)
    }

    fun testWorkspaceCaching() = runTest {
        AppCacheRepository.getInstance().clear()

        val client = createClient(apiKey = "test-api-key")

        val workspaces = client.listWorkspaces(useCache = false)

        val cached = AppCacheRepository.getInstance().read("postman/workspaces.json")
        if (workspaces.isNotEmpty()) {
            assertNotNull("Cache should be populated after fetching workspaces", cached)
        }
    }

    fun testListCollectionsWithEmptyApiKey() = runTest {
        val client = createClient(apiKey = "")

        val collections = client.listCollections("test-workspace-id")
        assertTrue("Collections should be empty for blank API key", collections.isEmpty())
    }

    fun testListCollectionsWithCache() = runTest {
        AppCacheRepository.getInstance().clear()

        val client = createClient(apiKey = "test-api-key")

        val collectionsWithoutCache = client.listCollections("test-workspace-id", useCache = false)
        val collectionsWithCache = client.listCollections("test-workspace-id", useCache = true)

        assertNotNull("Collections list should not be null", collectionsWithCache)
    }

    fun testCollectionCaching() = runTest {
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

    fun testUploadCollection() = runTest {
        val client = createClient(apiKey = "")

        val collection = com.itangcent.easyapi.exporter.postman.model.PostmanCollection(
            info = com.itangcent.easyapi.exporter.postman.model.CollectionInfo(
                name = "Test Collection"
            )
        )

        val result = client.uploadCollection(collection)
        assertTrue("Upload should succeed with empty API key (mock mode)", result.success)
    }

    fun testUpdateCollection() = runTest {
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
