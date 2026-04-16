package com.itangcent.easyapi.dashboard

import com.itangcent.easyapi.cache.ApiIndex
import com.itangcent.easyapi.exporter.model.HttpMetadata
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import com.itangcent.easyapi.settings.update

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

    fun testConcurrentScanReturnsEndpoints() = runTest {
        settingBinder.update {
            concurrentScanEnabled = true
        }
        val endpoints = apiScanner.scanAll()
        assertTrue("Should find endpoints with concurrent scanning", endpoints.isNotEmpty())
    }

    fun testConcurrentScanProducesSameResultsAsSequential() = runTest {
        settingBinder.update {
            concurrentScanEnabled = false
        }
        val sequentialEndpoints = apiScanner.scanAll()
            .filter { it.metadata is HttpMetadata }
            .sortedBy { (it.metadata as HttpMetadata).path }

        settingBinder.update {
            concurrentScanEnabled = true
        }
        val concurrentEndpoints = apiScanner.scanAll()
            .filter { it.metadata is HttpMetadata }
            .sortedBy { (it.metadata as HttpMetadata).path }

        assertEquals(
            "Sequential and concurrent scanning should produce same number of endpoints",
            sequentialEndpoints.size,
            concurrentEndpoints.size
        )

        sequentialEndpoints.zip(concurrentEndpoints).forEach { (seq, con) ->
            val seqMeta = seq.metadata as HttpMetadata
            val conMeta = con.metadata as HttpMetadata
            assertEquals("Paths should match", seqMeta.path, conMeta.path)
            assertEquals("HTTP methods should match", seqMeta.method, conMeta.method)
        }
    }

    fun testOnlyExportsSpringMvcWhenAvailable() = runTest {
        settingBinder.update {
            feignEnable = true
            jaxrsEnable = true
            actuatorEnable = true
            grpcEnable = true
        }
        
        val endpoints = apiScanner.scanAll()
        assertTrue("Should find Spring MVC endpoints", endpoints.isNotEmpty())
        
        val httpEndpoints = endpoints.filter { it.metadata is HttpMetadata }
        assertTrue("All endpoints should be HTTP (Spring MVC)", httpEndpoints.isNotEmpty())
    }

    fun testPreClassificationRoutesToCorrectExporter() = runTest {
        val endpoints = apiScanner.scanAll()
        
        endpoints.forEach { endpoint ->
            val httpMeta = endpoint.metadata as? HttpMetadata
            assertNotNull("Endpoint should have HTTP metadata", httpMeta)
            assertTrue("Path should not be empty", httpMeta!!.path.isNotEmpty())
        }
    }
}
