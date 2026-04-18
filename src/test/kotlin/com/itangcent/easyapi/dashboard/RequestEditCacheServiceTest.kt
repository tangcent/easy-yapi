package com.itangcent.easyapi.dashboard

import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.HttpMetadata
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.Assert.*

class RequestEditCacheServiceTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testGetInstance() {
        val service = RequestEditCacheService.getInstance(project)
        assertNotNull("Service instance should not be null", service)
    }

    fun testCreateDefaultCacheForHttpEndpoint() {
        val service = RequestEditCacheService.getInstance(project)

        val endpoint = ApiEndpoint(
            name = "Test Endpoint",
            metadata = HttpMetadata(
                method = com.itangcent.easyapi.exporter.model.HttpMethod.GET,
                path = "/api/test"
            )
        )

        val cache = service.createDefaultCache(endpoint, "test-key")

        assertNotNull("Cache should be created", cache)
        assertTrue("Cache should be HttpRequestEditCache", cache is HttpRequestEditCache)
    }

    fun testLoadNonExistentKey() {
        val service = RequestEditCacheService.getInstance(project)

        val endpoint = ApiEndpoint(
            name = "Test Endpoint",
            metadata = HttpMetadata(
                method = com.itangcent.easyapi.exporter.model.HttpMethod.GET,
                path = "/api/test"
            )
        )

        val cache = service.load(endpoint, "nonexistent-key-12345")

        assertNull("Should return null for non-existent key", cache)
    }

    fun testSaveAndLoad() {
        val service = RequestEditCacheService.getInstance(project)

        val endpoint = ApiEndpoint(
            name = "Test Endpoint",
            metadata = HttpMetadata(
                method = com.itangcent.easyapi.exporter.model.HttpMethod.GET,
                path = "/api/test"
            )
        )

        val key = "test-save-load-key"
        val cache = HttpRequestEditCache(
            key = key,
            name = "Test",
            path = "/api/test",
            method = "GET"
        )

        service.save(endpoint, cache, key)
        val loaded = service.load(endpoint, key)

        assertNotNull("Should load saved cache", loaded)
        assertTrue("Loaded cache should be HttpRequestEditCache", loaded is HttpRequestEditCache)
        val loadedHttp = loaded as HttpRequestEditCache
        assertEquals("Name should match", cache.name, loadedHttp.name)
        assertEquals("Path should match", cache.path, loadedHttp.path)
    }

    fun testDelete() {
        val service = RequestEditCacheService.getInstance(project)

        val endpoint = ApiEndpoint(
            name = "Test Endpoint",
            metadata = HttpMetadata(
                method = com.itangcent.easyapi.exporter.model.HttpMethod.GET,
                path = "/api/test"
            )
        )

        val key = "test-delete-key"
        val cache = HttpRequestEditCache(
            key = key,
            name = "Test",
            path = "/api/test",
            method = "GET"
        )

        service.save(endpoint, cache, key)
        service.delete(key, false)
        val loaded = service.load(endpoint, key)

        assertNull("Should return null after delete", loaded)
    }
}
