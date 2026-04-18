package com.itangcent.easyapi.ide.action

import com.itangcent.easyapi.exporter.model.ExportFormat
import org.junit.Assert.*

class ExportToMarkdownActionTest {

    fun testExportFormat() {
        val action = ExportToMarkdownAction()
        assertEquals("Export format should be MARKDOWN", ExportFormat.MARKDOWN, action.exportFormat)
    }
}
