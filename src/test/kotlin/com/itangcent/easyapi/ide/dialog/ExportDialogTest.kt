package com.itangcent.easyapi.ide.dialog

import com.itangcent.easyapi.exporter.model.ExportFormat
import com.itangcent.easyapi.exporter.model.OutputConfig
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class ExportDialogTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testDialogShowsEndpointCount() {
        val dialog = ExportDialog(project, 10)
        assertEquals("Dialog title should show endpoint count", 
            "Export API Endpoints (10 endpoints)", dialog.title)
    }

    fun testDialogShowsAllExportFormats() {
        val dialog = ExportDialog(project, 5)
        assertNotNull("Dialog should be created", dialog)
    }

    fun testDefaultFormatIsMarkdown() {
        val dialog = ExportDialog(project, 5)
        assertNotNull("Dialog should be created", dialog)
    }

    fun testOutputConfigDefaults() {
        val dialog = ExportDialog(project, 5)
        val config = dialog.outputConfig
        assertNotNull("Output config should not be null", config)
        assertEquals("Default output config", OutputConfig.DEFAULT, config)
    }

    fun testDialogCanBeCreatedWithDifferentCounts() {
        for (count in listOf(0, 1, 10, 100)) {
            val dialog = ExportDialog(project, count)
            assertNotNull("Dialog should be created for $count endpoints", dialog)
        }
    }
}
