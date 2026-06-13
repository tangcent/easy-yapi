package com.itangcent.easyapi.ide.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.itangcent.easyapi.exporter.model.ExportResult
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class ExportApiActionPlatformTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var action: ExportApiAction

    override fun setUp() {
        super.setUp()
        action = ExportApiAction()
    }

    fun testUpdateWithProject() {
        val presentation = Presentation()
        val event = AnActionEvent.createFromDataContext("test", presentation) { project }

        action.update(event)

        assertTrue("Action should be enabled with project", event.presentation.isEnabled)
    }

    fun testUpdateWithoutProject() {
        val presentation = Presentation()
        val event = AnActionEvent.createFromDataContext("test", presentation) { null }

        action.update(event)

        assertFalse("Action should be disabled without project", event.presentation.isEnabled)
    }

    fun testActionPerformedReturnsEarlyWithoutProject() {
        val presentation = Presentation()
        val event = AnActionEvent.createFromDataContext("test", presentation) { null }

        // Should not throw
        action.actionPerformed(event)
    }

    fun testExportResultErrorCreation() {
        val error = ExportResult.Error("Test error message")
        assertEquals("Test error message", error.message)
    }

    fun testExportResultSuccessCreation() {
        val success = ExportResult.Success(count = 5, target = "postman")
        assertEquals(5, success.count)
        assertEquals("postman", success.target)
    }

    fun testExportResultCancelledIsSingleton() {
        val cancelled = ExportResult.Cancelled
        assertNotNull(cancelled)
    }

    fun testExportResultErrorWithNoChannelMessage() {
        val error = ExportResult.Error("No channel registered for id: unknown")
        assertTrue(
            "Error message should contain 'No channel registered'",
            error.message.startsWith("No channel registered for id:")
        )
    }

    fun testExportResultErrorWithGenericMessage() {
        val error = ExportResult.Error("Connection failed")
        assertEquals("Connection failed", error.message)
    }

    fun testExportResultSealedClassHierarchy() {
        val success: ExportResult = ExportResult.Success(count = 1, target = "test")
        val error: ExportResult = ExportResult.Error("fail")
        val cancelled: ExportResult = ExportResult.Cancelled

        assertTrue("Success should be ExportResult", success is ExportResult)
        assertTrue("Error should be ExportResult", error is ExportResult)
        assertTrue("Cancelled should be ExportResult", cancelled is ExportResult)
    }
}
