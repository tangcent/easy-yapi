package com.itangcent.easyapi.ide.support

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*

class ProgressHelperTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testRunWithProgressInUnitTestMode() = runBlocking {
        var indicatorPassed = false
        var blockExecuted = false

        val result = runWithProgress(project, "Test Task") { indicator ->
            blockExecuted = true
            indicatorPassed = indicator != null
            "success"
        }

        assertTrue("Block should be executed", blockExecuted)
        assertTrue("Indicator should be passed", indicatorPassed)
        assertEquals("Result should be returned", "success", result)
    }

    fun testRunWithProgressReturnsValue() = runBlocking {
        val result = runWithProgress(project, "Test Task") {
            42
        }

        assertEquals("Should return the correct value", 42, result)
    }

    fun testRunWithProgressWithException() = runBlocking {
        var exceptionThrown = false
        try {
            runWithProgress(project, "Test Task") {
                throw RuntimeException("Test error")
            }
        } catch (e: RuntimeException) {
            exceptionThrown = true
            assertEquals("Exception message should match", "Test error", e.message)
        }

        assertTrue("Exception should be thrown", exceptionThrown)
    }

    fun testRunWithProgressWithIndicator() = runBlocking {
        var receivedIndicator: ProgressIndicator? = null

        runWithProgress(project, "Test Task") { indicator ->
            receivedIndicator = indicator
            assertNotNull("Indicator should not be null", indicator)
        }

        assertNotNull("Indicator should have been received", receivedIndicator)
    }

    fun testRunWithProgressCancellable() = runBlocking {
        val result = runWithProgress(project, "Test Task", cancellable = true) { indicator ->
            indicator.checkCanceled()
            "completed"
        }

        assertEquals("Should complete successfully", "completed", result)
    }
}
