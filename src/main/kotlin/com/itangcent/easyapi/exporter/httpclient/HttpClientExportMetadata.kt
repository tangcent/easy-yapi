package com.itangcent.easyapi.exporter.httpclient

import com.itangcent.easyapi.exporter.model.ExportMetadata

data class HttpClientExportMetadata(val content: String) : ExportMetadata {
    override fun formatDisplay(): String? = null
}
