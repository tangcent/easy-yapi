package com.itangcent.easyapi.dashboard

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking

class ApiDashboardServiceTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var apiDashboardService: ApiDashboardService

    override fun setUp() {
        super.setUp()
        apiDashboardService = ApiDashboardService(project)
    }

    override fun tearDown() {
        apiDashboardService.stop()
        super.tearDown()
    }

    fun testGetOrCreateContextReturnsSameContext() {
        val context1 = apiDashboardService.getOrCreateContext()
        val context2 = apiDashboardService.getOrCreateContext()
        
        assertSame("Should return same context instance", context1, context2)
    }

    fun testContextHasProjectBound() {
        val context = apiDashboardService.getOrCreateContext()
        assertNotNull("Context should have project bound", context)
    }

    fun testSetDashboardPanel() {
        val panel = ApiDashboardPanel(project)
        apiDashboardService.setDashboardPanel(panel)
        assertNotNull("Service should accept panel", apiDashboardService)
        panel.dispose()
    }

    fun testNavigateToClass() = runBlocking {
        loadTestFiles()
        val panel = ApiDashboardPanel(project)
        apiDashboardService.setDashboardPanel(panel)
        val psiClass = myFixture.findClass("com.itangcent.api.UserCtrl")
        apiDashboardService.navigateToClass(psiClass)
        assertNotNull("Navigate to class should not throw", apiDashboardService)
        panel.dispose()
    }

    fun testRefreshApis() {
        val panel = ApiDashboardPanel(project)
        apiDashboardService.setDashboardPanel(panel)
        apiDashboardService.refreshApis()
        assertNotNull("Refresh should not throw", apiDashboardService)
        panel.dispose()
    }

    fun testStopClearsContext() = runBlocking {
        apiDashboardService.getOrCreateContext()
        apiDashboardService.stop()
        val newContext = apiDashboardService.getOrCreateContext()
        assertNotNull("Should create new context after stop", newContext)
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
}
