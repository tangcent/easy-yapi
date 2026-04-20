package com.itangcent.easyapi.ide

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking

class DumbModeHelperTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testWaitForSmartModeInUnitTestMode() = runBlocking {
        DumbModeHelper.waitForSmartMode(project)
    }

    fun testRunWhenSmartExecutes() {
        var executed = false
        DumbModeHelper.runWhenSmart(project) {
            executed = true
        }
        assertTrue("runWhenSmart should execute in test mode", executed)
    }

    fun testReadWhenSmartExecutes() {
        var executed = false
        DumbModeHelper.readWhenSmart(project) {
            executed = true
        }
        assertTrue("readWhenSmart should execute in test mode", executed)
    }

    fun testIsDumbReturnsBoolean() {
        val isDumb = DumbModeHelper.isDumb(project)
        assertTrue("isDumb should return a boolean", isDumb is Boolean)
    }
}
