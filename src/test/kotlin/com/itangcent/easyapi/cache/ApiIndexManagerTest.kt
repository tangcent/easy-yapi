package com.itangcent.easyapi.cache

import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class ApiIndexManagerTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var apiIndexManager: ApiIndexManager
    private lateinit var apiIndex: ApiIndex

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        apiIndexManager = ApiIndexManager.getInstance(project)
        apiIndex = ApiIndex.getInstance(project)
        apiIndex.invalidate()
    }

    override fun tearDown() {
        runBlocking {
            apiIndexManager.stop()
        }
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

    override fun createConfigReader() = TestConfigReader.EMPTY

    fun testGetInstance() {
        assertNotNull("ApiIndexManager should be retrievable", apiIndexManager)
        assertSame("Should return same instance", apiIndexManager, ApiIndexManager.getInstance(project))
    }

    fun testRequestScan() = runTest {
        apiIndexManager.requestScan()

        delay(1000)

        assertTrue("Cache should be valid after scan", apiIndex.isValid())
        assertTrue("Cache should have endpoints", apiIndex.endpoints().isNotEmpty())
    }

    fun testScanPopulatesCache() = runTest {
        assertFalse("Cache should be invalid initially", apiIndex.isValid())

        apiIndexManager.requestScan()

        delay(1000)

        assertTrue("Cache should be valid after scan", apiIndex.isValid())
        val endpoints = apiIndex.endpoints()
        assertTrue("Cache should contain endpoints", endpoints.isNotEmpty())

        val userEndpoints = endpoints.filter { it.className?.contains("UserCtrl") == true }
        assertTrue("Should find UserCtrl endpoints", userEndpoints.isNotEmpty())
    }

    fun testMultipleRequestScanDoesNotDuplicate() = runTest {
        apiIndexManager.requestScan()
        delay(1000)

        val firstCount = apiIndex.endpoints().size

        apiIndexManager.requestScan()
        delay(1000)

        val secondCount = apiIndex.endpoints().size

        assertEquals("Multiple scans should not duplicate endpoints", firstCount, secondCount)
    }

    fun testCacheUpdateEndpoints() = runTest {
        val testEndpoints = listOf(
            ApiEndpoint(
                path = "/test",
                method = HttpMethod.GET,
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
                path = "/test",
                method = HttpMethod.GET,
                name = "Test Endpoint",
                className = "com.test.TestCtrl"
            )
        )

        apiIndex.updateEndpoints(testEndpoints)
        assertTrue("Cache should be valid", apiIndex.isValid())

        apiIndex.invalidate()
        assertFalse("Cache should be invalid after invalidate", apiIndex.isValid())
        assertTrue("Cache endpoints should be empty after invalidate", apiIndex.endpoints().isEmpty())
    }

    fun testCacheAwait() = runTest {
        val testEndpoints = listOf(
            ApiEndpoint(
                path = "/test",
                method = HttpMethod.POST,
                name = "Create Endpoint",
                className = "com.test.TestCtrl"
            )
        )

        apiIndex.updateEndpoints(testEndpoints)

        val cachedEndpoints = apiIndex.endpoints()
        assertEquals("await should return cached endpoints", testEndpoints, cachedEndpoints)
    }

    fun testStartAndStop() = runTest {
        apiIndexManager.start()

        delay(500)

        apiIndexManager.requestScan()
        delay(1000)

        assertTrue("Cache should have endpoints after start and scan", apiIndex.endpoints().isNotEmpty())

        apiIndexManager.stop()

        assertTrue("Cache should still be valid after stop", apiIndex.isValid())
    }

    fun testCacheReadyAfterUpdate() = runTest {
        assertFalse("Cache should not be ready initially", apiIndex.isReady())

        apiIndex.updateEndpoints(emptyList())

        assertTrue("Cache should be ready after update even with empty list", apiIndex.isReady())
    }

    fun testEndpointProperties() = runTest {
        apiIndexManager.requestScan()
        delay(1000)

        val endpoints = apiIndex.endpoints()
        assertTrue("Should have endpoints", endpoints.isNotEmpty())

        val endpoint = endpoints.first()
        assertNotNull("Endpoint should have path", endpoint.path)
        assertNotNull("Endpoint should have method", endpoint.method)
        assertNotNull("Endpoint should have className", endpoint.className)
    }
}
