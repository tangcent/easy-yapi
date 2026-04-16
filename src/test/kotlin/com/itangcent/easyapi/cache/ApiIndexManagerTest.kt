package com.itangcent.easyapi.cache

import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.HttpMetadata
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import kotlinx.coroutines.delay

class ApiIndexManagerTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var apiIndexManager: ApiIndexManager
    private lateinit var apiIndex: ApiIndex

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        apiIndexManager = ApiIndexManager.getInstance(project)
        apiIndex = ApiIndex.getInstance(project)
        apiIndex.invalidate()
        apiIndexManager.start(triggerInitialScan = false)
    }

    override fun tearDown() {
        apiIndexManager.stop()
        // Give background coroutines time to finish cancellation before next test
        Thread.sleep(200)
        super.tearDown()
    }

    private fun loadTestFiles() {
        loadFile("spring/RequestMapping.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/RestController.java")
        loadFile("spring/Controller.java")
        loadFile("spring/ResponseBody.java")
        loadFile("spring/RequestParam.java")
        loadFile("spring/PathVariable.java")
        loadFile("spring/RequestBody.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("api/UserCtrl.java")
    }

    override fun createConfigReader() = TestConfigReader.empty(project)

    fun testGetInstance() {
        assertNotNull("ApiIndexManager should be retrievable", apiIndexManager)
        assertSame("Should return same instance", apiIndexManager, ApiIndexManager.getInstance(project))
    }

    fun testRequestScan() = runTest {
        apiIndexManager.requestScan()

        apiIndex.waitUntilValid()

        // endpoints() awaits cacheReady, so no need for delay
        val endpoints = apiIndex.endpoints()
        assertTrue("Cache should be valid after scan", apiIndex.isValid())
        assertTrue("Cache should have endpoints", endpoints.isNotEmpty())
    }

    fun testMultipleRequestScanDoesNotDuplicate() = runTest {
        apiIndexManager.requestScan()

        apiIndex.waitUntilValid()

        val firstCount = apiIndex.endpoints().size

        apiIndexManager.requestScan()
        delay(2000)

        val secondCount = apiIndex.endpoints().size

        assertEquals("Multiple scans should not duplicate endpoints", firstCount, secondCount)
    }

    fun testCacheUpdateEndpoints() = runTest {
        val testEndpoints = listOf(
            ApiEndpoint(
                metadata = httpMetadata(
                    path = "/test",
                    method = HttpMethod.GET
                ),
                name = "Test Endpoint",
                className = "com.test.TestCtrl"
            )
        )

        apiIndex.updateEndpoints(testEndpoints)

        assertTrue("Cache should be valid", apiIndex.isValid())
        assertTrue("Cache should be ready", apiIndex.isReady())
        assertEquals("Cache should contain test endpoints", testEndpoints, apiIndex.endpoints())
    }

    fun testCacheInvalidate() = runTest {
        val testEndpoints = listOf(
            ApiEndpoint(
                metadata = httpMetadata(
                    path = "/test",
                    method = HttpMethod.GET
                ),
                name = "Test Endpoint",
                className = "com.test.TestCtrl"
            )
        )

        apiIndex.updateEndpoints(testEndpoints)
        assertTrue("Cache should be valid", apiIndex.isValid())
        assertTrue("Cache should be ready after update", apiIndex.isReady())

        apiIndex.invalidate()
        assertFalse("Cache should be invalid after invalidate", apiIndex.isValid())
        assertTrue("Cache should still be ready after invalidate (cacheReady is never reset)", apiIndex.isReady())
    }

    fun testCacheAwait() = runTest {
        val testEndpoints = listOf(
            ApiEndpoint(
                metadata = httpMetadata(
                    path = "/test",
                    method = HttpMethod.POST
                ),
                name = "Create Endpoint",
                className = "com.test.TestCtrl"
            )
        )

        apiIndex.updateEndpoints(testEndpoints)

        val cachedEndpoints = apiIndex.endpoints()
        assertEquals("await should return cached endpoints", testEndpoints, cachedEndpoints)
    }

    fun testStartAndStop() = runTest {
        apiIndexManager.requestScan()

        apiIndex.waitUntilValid()

        val endpoints = apiIndex.endpoints()
        assertTrue("Cache should have endpoints after start and scan", endpoints.isNotEmpty())

        apiIndexManager.stop()

        assertTrue("Cache should still be valid after stop", apiIndex.isValid())
    }

    fun testCacheValidAfterUpdate() = runTest {
        assertFalse("Cache should not be valid initially", apiIndex.isValid())

        apiIndex.updateEndpoints(emptyList())

        assertTrue("Cache should be valid after update even with empty list", apiIndex.isValid())
    }

    fun testEndpointProperties() = runTest {
        apiIndexManager.requestScan()

        apiIndex.waitUntilValid()

        val endpoints = apiIndex.endpoints()
        assertTrue("Should have endpoints", endpoints.isNotEmpty())

        val endpoint = endpoints.first()
        assertNotNull("Endpoint should have path", endpoint.httpMetadata?.path)
        assertNotNull("Endpoint should have method", endpoint.httpMetadata?.method)
        assertNotNull("Endpoint should have className", endpoint.className)
    }

    fun testThrottling() = runTest {
        // Test that multiple rapid scan requests are throttled properly
        // Use requestScan which triggers a full scan

        // Trigger multiple scan requests rapidly
        apiIndexManager.requestScan()
        apiIndexManager.requestScan()
        apiIndexManager.requestScan()

        // Wait for initial scan delay + processing time
        delay(7000)

        // Cache should be valid after scan completes
        assertTrue("Cache should be valid after scan", apiIndex.isValid())

        val endpoints = apiIndex.endpoints()
        assertTrue("Should have endpoints", endpoints.isNotEmpty())
    }
}

private suspend fun ApiIndex.waitUntilValid(deadline: Long = System.currentTimeMillis() + 15_000) {
    while (!isValid() && System.currentTimeMillis() < deadline) {
        delay(200)
    }
}
