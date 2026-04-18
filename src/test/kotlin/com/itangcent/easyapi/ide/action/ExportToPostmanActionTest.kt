package com.itangcent.easyapi.ide.action

import com.itangcent.easyapi.exporter.model.ExportFormat
import org.junit.Assert.*

class ExportToPostmanActionTest {

    fun testExportFormat() {
        val action = ExportToPostmanAction()
        assertEquals("Export format should be POSTMAN", ExportFormat.POSTMAN, action.exportFormat)
    }
}
