package com.itangcent.easyapi.ide.action

import com.itangcent.easyapi.exporter.model.ExportFormat
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class ExportToPostmanActionTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var action: ExportToPostmanAction

    override fun setUp() {
        super.setUp()
        action = ExportToPostmanAction()
    }

    fun testExportFormatIsPostman() {
        assertEquals(
            "Export format should be POSTMAN",
            ExportFormat.POSTMAN,
            action.exportFormat
        )
    }

    fun testActionExtendsBaseExportAction() {
        assertTrue(
            "ExportToPostmanAction should extend BaseExportAction",
            action is BaseExportAction
        )
    }

    fun testActionExtendsEasyApiAction() {
        assertTrue(
            "ExportToPostmanAction should extend EasyApiAction",
            action is EasyApiAction
        )
    }

    fun testPostmanFormatSupportsHttp() {
        assertTrue(
            "Postman format should support HTTP endpoints",
            action.exportFormat.supportsHttp
        )
    }

    fun testPostmanFormatDoesNotSupportGrpc() {
        assertFalse(
            "Postman format should NOT support gRPC endpoints",
            action.exportFormat.supportsGrpc
        )
    }

    fun testPostmanFormatDisplayName() {
        assertEquals(
            "Postman display name should be 'Postman'",
            "Postman",
            action.exportFormat.displayName
        )
    }
}
