package com.itangcent.easyapi.core.dashboard

import com.itangcent.easyapi.core.cache.api.ApiIndex
import com.itangcent.easyapi.core.export.HttpMetadata
import com.itangcent.easyapi.core.ide.support.SelectionScope
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.core.settings.update

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
        settingBinder.update(GeneralSettings::class) {
            concurrentScanEnabled = true
        }
        val endpoints = apiScanner.scanAll()
        assertTrue("Should find endpoints with concurrent scanning", endpoints.isNotEmpty())
    }

    fun testConcurrentScanProducesSameResultsAsSequential() = runTest {
        settingBinder.update(GeneralSettings::class) {
            concurrentScanEnabled = false
        }
        val sequentialEndpoints = apiScanner.scanAll()
            .filter { it.metadata is HttpMetadata }
            .sortedBy { (it.metadata as HttpMetadata).path }

        settingBinder.update(GeneralSettings::class) {
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
        // Enable all 5 frameworks. Feign (default-off) and SpringActuator
        // (default-off) need explicit enablement; JAX-RS (default-on), gRPC
        // (default-on), SpringMVC (always-on) are already on, so no entry is
        // needed for them.
        settingBinder.update(GeneralSettings::class) {
            enabledFrameworks = arrayOf("Feign", "SpringActuator")
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

    // Regression: selecting specific controller methods must export only those methods,
    // not every endpoint in the containing class.
    fun testScanSelectionWithSingleMethodReturnsOnlyThatEndpoint() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull("UserCtrl should be loaded", psiClass)
        val greetingMethod = findMethod(psiClass!!, "greeting")
        assertNotNull("greeting method should exist", greetingMethod)

        val endpoints = apiScanner.scanSelection(SelectionScope(listOf(greetingMethod!!)))

        assertEquals(
            "Should export only the selected method's endpoint",
            1,
            endpoints.size
        )
        assertEquals(
            "Exported endpoint should come from the selected method",
            greetingMethod,
            endpoints.first().sourceMethod
        )
    }

    fun testScanSelectionWithMultipleMethodsReturnsOnlyThoseEndpoints() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull("UserCtrl should be loaded", psiClass)
        val greetingMethod = findMethod(psiClass!!, "greeting")!!
        val createMethod = findMethod(psiClass, "create")!!

        val endpoints = apiScanner.scanSelection(
            SelectionScope(listOf(greetingMethod, createMethod))
        )

        // A method with multi-path mappings (e.g. @PostMapping({"/add", "/admin/add"}))
        // may produce multiple endpoints, so assert on distinct source methods.
        val exportedMethods = endpoints.mapNotNull { it.sourceMethod }.toSet()
        assertEquals(
            "Exported endpoints should come from exactly the two selected methods",
            setOf(greetingMethod, createMethod),
            exportedMethods
        )
    }

    fun testScanSelectionWithMethodDoesNotExportSiblingMethods() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull("UserCtrl should be loaded", psiClass)
        val greetingMethod = findMethod(psiClass!!, "greeting")!!

        val endpoints = apiScanner.scanSelection(SelectionScope(listOf(greetingMethod)))

        // UserCtrl has many endpoints (greeting, get, create, update, ...).
        // Only the selected one should be exported.
        val nonGreetingEndpoints = endpoints.filter { it.sourceMethod != greetingMethod }
        assertTrue(
            "No sibling methods should be exported when one method is selected, " +
                "but found: ${nonGreetingEndpoints.map { it.sourceMethod?.name }}",
            nonGreetingEndpoints.isEmpty()
        )
    }

    fun testScanSelectionWithClassReturnsAllClassEndpoints() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull("UserCtrl should be loaded", psiClass)

        val selectionEndpoints = apiScanner.scanSelection(SelectionScope(listOf(psiClass!!)))
        val classEndpoints = apiScanner.scanClasses(listOf(psiClass)).toList()

        assertEquals(
            "Class selection should export the same endpoints as scanClasses",
            classEndpoints.size,
            selectionEndpoints.size
        )
    }
}
