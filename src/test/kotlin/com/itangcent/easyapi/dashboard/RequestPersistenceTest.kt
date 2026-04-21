package com.itangcent.easyapi.dashboard

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.Assert.*

class RequestPersistenceTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var persistence: RequestPersistence

    override fun setUp() {
        super.setUp()
        persistence = RequestPersistence(project)
        persistence.reset()
    }

    override fun tearDown() {
        persistence.reset()
        super.tearDown()
    }

    fun testLoadAllReturnsEmptyWhenNoData() {
        val result = persistence.loadAll()
        assertTrue("Should return empty list when no data persisted", result.isEmpty())
    }

    fun testSaveAndLoadSingleRequest() {
        val request = PersistedRequest(
            endpointKey = "com.example.UserCtrl.getUser",
            url = "http://localhost:8080/api/users/1",
            method = "GET",
            headers = mapOf("Authorization" to "Bearer token"),
            body = null
        )

        persistence.saveAll(listOf(request))
        val loaded = persistence.loadAll()

        assertEquals("Should load one request", 1, loaded.size)
        assertEquals("com.example.UserCtrl.getUser", loaded[0].endpointKey)
        assertEquals("http://localhost:8080/api/users/1", loaded[0].url)
        assertEquals("GET", loaded[0].method)
        assertEquals("Bearer token", loaded[0].headers["Authorization"])
        assertNull("Body should be null", loaded[0].body)
    }

    fun testSaveAndLoadMultipleRequests() {
        val requests = listOf(
            PersistedRequest(
                endpointKey = "com.example.UserCtrl.getUser",
                url = "http://localhost:8080/api/users/1",
                method = "GET"
            ),
            PersistedRequest(
                endpointKey = "com.example.UserCtrl.createUser",
                url = "http://localhost:8080/api/users",
                method = "POST",
                headers = mapOf("Content-Type" to "application/json"),
                body = "{\"name\":\"test\"}"
            ),
            PersistedRequest(
                endpointKey = "com.example.UserCtrl.deleteUser",
                url = "http://localhost:8080/api/users/1",
                method = "DELETE"
            )
        )

        persistence.saveAll(requests)
        val loaded = persistence.loadAll()

        assertEquals("Should load all three requests", 3, loaded.size)
        assertEquals("GET", loaded[0].method)
        assertEquals("POST", loaded[1].method)
        assertEquals("DELETE", loaded[2].method)
    }

    fun testSaveOverwritesPreviousData() {
        val request1 = PersistedRequest(
            endpointKey = "key1",
            url = "http://old.com",
            method = "GET"
        )
        val request2 = PersistedRequest(
            endpointKey = "key2",
            url = "http://new.com",
            method = "POST"
        )

        persistence.saveAll(listOf(request1))
        persistence.saveAll(listOf(request2))
        val loaded = persistence.loadAll()

        assertEquals("Should have only the latest saved data", 1, loaded.size)
        assertEquals("key2", loaded[0].endpointKey)
        assertEquals("http://new.com", loaded[0].url)
        assertEquals("POST", loaded[0].method)
    }

    fun testResetClearsAllData() {
        val request = PersistedRequest(
            endpointKey = "key1",
            url = "http://test.com",
            method = "GET"
        )

        persistence.saveAll(listOf(request))
        assertEquals("Should have one request before reset", 1, persistence.loadAll().size)

        persistence.reset()
        assertTrue("Should return empty list after reset", persistence.loadAll().isEmpty())
    }

    fun testLoadAfterResetReturnsEmpty() {
        val request = PersistedRequest(
            endpointKey = "key1",
            url = "http://test.com",
            method = "GET"
        )

        persistence.saveAll(listOf(request))
        persistence.reset()

        val loaded = persistence.loadAll()
        assertTrue("Should return empty list after reset", loaded.isEmpty())
    }

    fun testSaveEmptyList() {
        persistence.saveAll(emptyList())
        val loaded = persistence.loadAll()
        assertTrue("Should return empty list when saved empty", loaded.isEmpty())
    }

    fun testSaveRequestWithBody() {
        val request = PersistedRequest(
            endpointKey = "com.example.UserCtrl.updateUser",
            url = "http://localhost:8080/api/users/1",
            method = "PUT",
            headers = mapOf("Content-Type" to "application/json"),
            body = "{\"name\":\"updated\"}"
        )

        persistence.saveAll(listOf(request))
        val loaded = persistence.loadAll()

        assertEquals("Should load one request", 1, loaded.size)
        assertEquals("{\"name\":\"updated\"}", loaded[0].body)
        assertEquals("application/json", loaded[0].headers["Content-Type"])
    }

    fun testSaveRequestWithMultipleHeaders() {
        val request = PersistedRequest(
            endpointKey = "test",
            url = "http://test.com",
            method = "POST",
            headers = mapOf(
                "Authorization" to "Bearer token",
                "Content-Type" to "application/json",
                "X-Custom-Header" to "custom-value"
            ),
            body = "{}"
        )

        persistence.saveAll(listOf(request))
        val loaded = persistence.loadAll()

        assertEquals("Should preserve all headers", 3, loaded[0].headers.size)
        assertEquals("Bearer token", loaded[0].headers["Authorization"])
        assertEquals("application/json", loaded[0].headers["Content-Type"])
        assertEquals("custom-value", loaded[0].headers["X-Custom-Header"])
    }

    fun testResetWhenNoDataDoesNotCrash() {
        persistence.reset()
        assertTrue("Should not crash on reset with no data", persistence.loadAll().isEmpty())
    }

    fun testDoubleResetDoesNotCrash() {
        persistence.reset()
        persistence.reset()
        assertTrue("Should not crash on double reset", persistence.loadAll().isEmpty())
    }

    fun testSaveAndLoadPreservesAllFields() {
        val request = PersistedRequest(
            endpointKey = "com.example.OrderCtrl.createOrder",
            url = "http://api.example.com/v1/orders",
            method = "POST",
            headers = mapOf(
                "Authorization" to "Bearer abc123",
                "Accept" to "application/json"
            ),
            body = "{\"productId\":42,\"quantity\":2}"
        )

        persistence.saveAll(listOf(request))
        val loaded = persistence.loadAll()

        assertEquals("endpointKey should match", request.endpointKey, loaded[0].endpointKey)
        assertEquals("url should match", request.url, loaded[0].url)
        assertEquals("method should match", request.method, loaded[0].method)
        assertEquals("headers should match", request.headers, loaded[0].headers)
        assertEquals("body should match", request.body, loaded[0].body)
    }
}
