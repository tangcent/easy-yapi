package com.itangcent.easyapi.ide.action

import com.itangcent.easyapi.exporter.model.ExportFormat
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.Assert.*

class ExportToMarkdownActionTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var action: ExportToMarkdownAction

    override fun setUp() {
        super.setUp()
        action = ExportToMarkdownAction()
    }

    fun testExportFormatIsMarkdown() {
        assertEquals(
            "Export format should be MARKDOWN",
            ExportFormat.MARKDOWN,
            action.exportFormat
        )
    }

    fun testActionExtendsBaseExportAction() {
        assertTrue(
            "ExportToMarkdownAction should extend BaseExportAction",
            action is BaseExportAction
        )
    }

    fun testActionExtendsEasyApiAction() {
        assertTrue(
            "ExportToMarkdownAction should extend EasyApiAction",
            action is EasyApiAction
        )
    }

    fun testMarkdownFormatSupportsHttp() {
        assertTrue(
            "Markdown format should support HTTP endpoints",
            action.exportFormat.supportsHttp
        )
    }

    fun testMarkdownFormatSupportsGrpc() {
        assertTrue(
            "Markdown format should support gRPC endpoints",
            action.exportFormat.supportsGrpc
        )
    }

    fun testMarkdownFormatDisplayName() {
        assertEquals(
            "Markdown display name should be 'Markdown'",
            "Markdown",
            action.exportFormat.displayName
        )
    }
}
