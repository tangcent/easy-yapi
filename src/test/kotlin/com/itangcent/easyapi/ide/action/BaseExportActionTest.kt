package com.itangcent.easyapi.ide.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.ExportContext
import com.itangcent.easyapi.exporter.model.ExportFormat
import com.itangcent.easyapi.exporter.model.ExportResult
import com.itangcent.easyapi.exporter.model.HttpMethod
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

class BaseExportActionTest {

    private lateinit var action: TestExportAction
    private lateinit var mockProject: Project
    private lateinit var mockEvent: AnActionEvent

    @Before
    fun setUp() {
        action = TestExportAction()
        mockProject = mock(Project::class.java)
        mockEvent = mock(AnActionEvent::class.java)

        `when`(mockEvent.project).thenReturn(mockProject)
        `when`(mockEvent.presentation).thenReturn(Presentation())
    }

    @Test
    fun testExportResultSuccess() {
        val result = ExportResult.Success(5, "/tmp/api.md")
        assertEquals(5, result.count)
        assertEquals("/tmp/api.md", result.target)
    }

    @Test
    fun testExportResultError() {
        val result = ExportResult.Error("Test error")
        assertEquals("Test error", result.message)
    }

    @Test
    fun testExportContextHasCorrectFormat() {
        val endpoint = ApiEndpoint(
            name = "test",
            path = "/test",
            method = HttpMethod.GET
        )

        val context = ExportContext(
            project = mockProject,
            endpoints = listOf(endpoint),
            exportFormat = ExportFormat.POSTMAN
        )

        assertEquals(ExportFormat.POSTMAN, context.exportFormat)
        assertEquals(1, context.endpoints.size)
    }

    class TestExportAction : BaseExportAction() {
        override val exportFormat: ExportFormat = ExportFormat.MARKDOWN
        override val actionName: String = "Test Export"
    }
}
