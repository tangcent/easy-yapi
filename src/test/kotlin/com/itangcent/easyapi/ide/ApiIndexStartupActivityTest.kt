package com.itangcent.easyapi.ide

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking

class ApiIndexStartupActivityTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var startupActivity: ApiIndexStartupActivity

    override fun setUp() {
        super.setUp()
        startupActivity = ApiIndexStartupActivity()
    }

    fun testActivityExists() {
        assertNotNull("ApiIndexStartupActivity should be created", startupActivity)
    }

    fun testActivityIsProjectActivity() {
        assertTrue(
            "ApiIndexStartupActivity should implement ProjectActivity",
            startupActivity is com.intellij.openapi.startup.ProjectActivity
        )
    }

    fun testExecuteDoesNotThrow() = runBlocking {
        try {
            startupActivity.execute(project)
        } catch (e: Exception) {
            fail("execute should not throw: ${e.message}")
        }
    }
}
