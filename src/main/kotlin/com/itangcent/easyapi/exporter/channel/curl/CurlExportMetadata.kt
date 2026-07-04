package com.itangcent.easyapi.exporter.channel.curl

import com.itangcent.easyapi.exporter.model.ExportMetadata

data class CurlExportMetadata(val content: String) : ExportMetadata {
    override fun formatDisplay(): String? = null
}
