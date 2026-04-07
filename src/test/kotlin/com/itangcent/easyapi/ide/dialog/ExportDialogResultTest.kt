package com.itangcent.easyapi.ide.dialog

import com.itangcent.easyapi.exporter.model.ExportFormat
import com.itangcent.easyapi.exporter.model.OutputConfig
import org.junit.Assert.*
import org.junit.Test

class ExportDialogResultTest {

    @Test
    fun testProperties() {
        val config = OutputConfig()
        val result = ExportDialogResult(ExportFormat.MARKDOWN, config)
        assertEquals(ExportFormat.MARKDOWN, result.format)
        assertSame(config, result.outputConfig)
    }

    @Test
    fun testEquality() {
        val config = OutputConfig()
        val r1 = ExportDialogResult(ExportFormat.MARKDOWN, config)
        val r2 = ExportDialogResult(ExportFormat.MARKDOWN, config)
        assertEquals(r1, r2)
    }

    @Test
    fun testInequality_differentFormat() {
        val config = OutputConfig()
        val r1 = ExportDialogResult(ExportFormat.MARKDOWN, config)
        val r2 = ExportDialogResult(ExportFormat.CURL, config)
        assertNotEquals(r1, r2)
    }

    @Test
    fun testCopy() {
        val config = OutputConfig()
        val original = ExportDialogResult(ExportFormat.MARKDOWN, config)
        val copy = original.copy(format = ExportFormat.CURL)
        assertEquals(ExportFormat.CURL, copy.format)
        assertSame(config, copy.outputConfig)
    }
}
