package com.itangcent.easyapi.channel.markdown

import com.itangcent.easyapi.core.export.ExportMetadata

data class MarkdownExportMetadata(val content: String) : ExportMetadata {
    override fun formatDisplay(): String? = null
}
