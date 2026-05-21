package com.itangcent.easyapi.exporter.markdown

import com.itangcent.easyapi.exporter.model.ExportMetadata

data class MarkdownExportMetadata(val content: String) : ExportMetadata {
    override fun formatDisplay(): String? = null
}
