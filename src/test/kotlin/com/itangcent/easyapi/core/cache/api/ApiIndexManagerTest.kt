package com.itangcent.easyapi.core.cache.api

import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.core.settings.update
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import com.itangcent.easyapi.testFramework.waitUntil
import com.itangcent.easyapi.testFramework.waitUntilNotEmpty
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ApiIndexManagerTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var apiIndexManager: ApiIndexManager
    private lateinit var apiIndex: ApiIndex

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        apiIndexManager = ApiIndexManager.getInstance(project)
        apiIndex = ApiIndex.getInstance(project)
        runBlocking { apiIndex.invalidate() }
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

    /**
     * Waits until the index holds a non-empty endpoint list.
     *
     * A full scan may mark the cache valid before PSI is fully resolved, finding
     * 0 endpoints. When that happens we re-request a scan so the index is
     * repopulated once PSI resolves. The initial [waitUntil] on [ApiIndex.isReady]
     * bounds the wait for the first scan, avoiding a hang on `endpoints()`'
     * internal `awaitCacheReady()` if a scan fails before populating the cache.
     */
    private suspend fun waitForEndpoints(timeout: Duration = 30.seconds): List<ApiEndpoint> {
        waitUntil(timeout = timeout) { apiIndex.isReady() }
        return waitUntilNotEmpty(timeout = timeout) {
            val eps = apiIndex.endpoints()
            if (eps.isEmpty() && apiIndex.isValid()) {
                apiIndexManager.requestScan()
            }
            eps
        }
    }

    fun testGetInstance() {
        assertNotNull("ApiIndexManager should be retrievable", apiIndexManager)
        assertSame("Should return same instance", apiIndexManager, ApiIndexManager.getInstance(project))
    }

    fun testRequestScan() = runTest {
        apiIndexManager.requestScan()

        val endpoints = waitForEndpoints()

        assertTrue("Cache should be valid after scan", apiIndex.isValid())
        assertTrue("Cache should have endpoints", endpoints.isNotEmpty())
    }

    fun testMultipleRequestScanDoesNotDuplicate() = runTest {
        apiIndexManager.requestScan()

        val firstCount = waitForEndpoints().size

        apiIndexManager.requestScan()
        // updateEndpoints replaces the cache (it does not append), so a second scan
        // must not duplicate. Wait until the count settles back to firstCount.
        waitUntil { apiIndex.endpoints().size == firstCount }

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

        val endpoints = waitForEndpoints()
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

        val endpoints = waitForEndpoints()
        assertTrue("Should have endpoints", endpoints.isNotEmpty())

        val endpoint = endpoints.first()
        assertNotNull("Endpoint should have path", endpoint.httpMetadata?.path)
        assertNotNull("Endpoint should have method", endpoint.httpMetadata?.method)
        assertNotNull("Endpoint should have className", endpoint.className)
    }

    fun testThrottling() = runTest {
        apiIndexManager.requestScan()
        apiIndexManager.requestScan()
        apiIndexManager.requestScan()

        val endpoints = waitForEndpoints()

        assertTrue("Cache should be valid after scan", apiIndex.isValid())
        assertTrue("Should have endpoints", endpoints.isNotEmpty())
    }

    /**
     * Reproduces the bug: project opened with `apiScanEnabled=false`, so the
     * startup activity never calls `ApiIndexManager.start()`. The user then
     * enables the toggle in Settings and clicks Refresh in the API Dashboard.
     *
     * `requestScan()` must auto-start the index services on demand (defense
     * in depth) so the scan actually runs without a project restart.
     */
    fun testRequestScanAutoStartsServicesWhenNotStarted() = runTest {
        // Tear down the manager started in setUp() to simulate "services never
        // started because apiScanEnabled was false at project open".
        apiIndexManager.stop()
        runBlocking { apiIndex.invalidate() }
        assertFalse("Index should be invalid after invalidate", apiIndex.isValid())

        // apiScanEnabled defaults to true in GeneralSettings, so requestScan
        // must auto-start services and dispatch the scan immediately.
        apiIndexManager.requestScan()

        val endpoints = waitForEndpoints()
        assertTrue("Cache should be valid after auto-start scan", apiIndex.isValid())
        assertTrue("Auto-start scan should populate endpoints", endpoints.isNotEmpty())
    }

    /**
     * When `apiScanEnabled=false`, `requestScan()` must remain a no-op even
     * if the index services were never started. This guards against scans
     * running while the master toggle is off.
     */
    fun testRequestScanNoOpWhenApiScanDisabledAndNotStarted() = runTest {
        apiIndexManager.stop()
        runBlocking { apiIndex.invalidate() }

        settingBinder.update(GeneralSettings::class) {
            apiScanEnabled = false
        }

        try {
            apiIndexManager.requestScan()
            // Give the background dispatcher a brief window to prove the
            // negative — if a scan were dispatched, the cache would become
            // valid shortly. Polling for a short timeout keeps the test fast.
            val settledInvalid = waitUntilSettledInvalid(timeoutMs = 1500)
            assertTrue(
                "Cache should remain invalid when apiScanEnabled is false",
                settledInvalid
            )
        } finally {
            settingBinder.update(GeneralSettings::class) {
                apiScanEnabled = true
            }
        }
    }

    /**
     * Polls briefly to confirm the cache stays invalid. Returns `true` if the
     * cache remained invalid for the entire [timeoutMs] window, `false` if it
     * became valid (indicating a scan ran despite expectations).
     */
    private suspend fun waitUntilSettledInvalid(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (apiIndex.isValid()) return false
            kotlinx.coroutines.delay(50)
        }
        return !apiIndex.isValid()
    }
}
