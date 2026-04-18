package com.itangcent.easyapi.ide.action

import org.junit.Assert.*
import org.junit.Test

class EasyApiActionTest {

    @Test
    fun testEasyApiActionClassExists() {
        val clazz = Class.forName("com.itangcent.easyapi.ide.action.EasyApiAction")
        assertNotNull("EasyApiAction class should exist", clazz)
    }

    @Test
    fun testExportToMarkdownActionFormat() {
        val action = ExportToMarkdownAction()
        assertEquals("ExportFormat should be MARKDOWN", "MARKDOWN", action.exportFormat.name)
    }

    @Test
    fun testExportToPostmanActionFormat() {
        val action = ExportToPostmanAction()
        assertEquals("ExportFormat should be POSTMAN", "POSTMAN", action.exportFormat.name)
    }

    @Test
    fun testFieldsToJsonActionTitle() {
        val action = FieldsToJsonAction()
        assertNotNull("FieldsToJsonAction should be created", action)
    }

    @Test
    fun testFieldsToJson5ActionTitle() {
        val action = FieldsToJson5Action()
        assertNotNull("FieldsToJson5Action should be created", action)
    }

    @Test
    fun testFieldsToPropertiesActionTitle() {
        val action = FieldsToPropertiesAction()
        assertNotNull("FieldsToPropertiesAction should be created", action)
    }

    @Test
    fun testOpenApiDashboardActionExists() {
        val action = OpenApiDashboardAction()
        assertNotNull("OpenApiDashboardAction should be created", action)
    }

    @Test
    fun testScriptExecutorActionExists() {
        val action = ScriptExecutorAction()
        assertNotNull("ScriptExecutorAction should be created", action)
    }

    @Test
    fun testOpenScriptExecutorActionExists() {
        val action = OpenScriptExecutorAction()
        assertNotNull("OpenScriptExecutorAction should be created", action)
    }

    @Test
    fun testApiCallActionExists() {
        val action = ApiCallAction()
        assertNotNull("ApiCallAction should be created", action)
    }

    @Test
    fun testExportApiActionExists() {
        val action = ExportApiAction()
        assertNotNull("ExportApiAction should be created", action)
    }
}
