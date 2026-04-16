package com.itangcent.easyapi.dashboard

import com.itangcent.easyapi.cache.ApiIndex
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class ApiScannerTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var apiScanner: ApiScanner
    private lateinit var apiIndex: ApiIndex

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        apiScanner = ApiScanner.getInstance(project)
        apiIndex = ApiIndex.getInstance(project)
    }

    private fun loadTestFiles() {
        loadFile("spring/RequestMapping.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/RestController.java")
        loadFile("spring/Controller.java")
        loadFile("spring/RequestParam.java")
        loadFile("spring/PathVariable.java")
        loadFile("spring/RequestBody.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("api/UserCtrl.java")
    }

    override fun createConfigReader() = TestConfigReader.empty(project)

    fun testScanAllReturnsEndpoints() = runTest {
        val endpoints = apiScanner.scanAll()
        assertTrue("Should find endpoints", endpoints.isNotEmpty())
    }

    fun testScanAllUpdatesCache() = runTest {
        val endpoints = apiScanner.scanAll()
        apiIndex.updateEndpoints(endpoints)
        assertTrue("Cache should be valid", apiIndex.isValid())
        assertTrue("Cache should be ready", apiIndex.isReady())
        assertEquals("Cache should contain same endpoints", endpoints, apiIndex.endpoints())
    }

    fun testCacheAwait() = runTest {
        val endpoints = apiScanner.scanAll()
        apiIndex.updateEndpoints(endpoints)
        val cachedEndpoints = apiIndex.endpoints()
        assertEquals("await should return cached endpoints", endpoints, cachedEndpoints)
    }

    fun testInvalidateCache() = runTest {
        val endpoints = apiScanner.scanAll()
        apiIndex.updateEndpoints(endpoints)
        apiIndex.invalidate()
        assertFalse("Cache should be invalid after invalidate", apiIndex.isValid())
        assertTrue("Cached endpoints should be empty after invalidate", apiIndex.endpoints().isEmpty())
    }
}
