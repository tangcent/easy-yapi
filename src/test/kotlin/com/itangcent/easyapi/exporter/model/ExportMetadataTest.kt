package com.itangcent.easyapi.exporter.model

import org.junit.Assert.*
import org.junit.Test

class ExportMetadataTest {

    @Test
    fun testExportMetadataImplementation() {
        val metadata = object : ExportMetadata {
            override fun formatDisplay(): String? = "test display"
        }
        assertEquals("test display", metadata.formatDisplay())
    }

    @Test
    fun testExportMetadataWithNullDisplay() {
        val metadata = object : ExportMetadata {
            override fun formatDisplay(): String? = null
        }
        assertNull(metadata.formatDisplay())
    }
}
