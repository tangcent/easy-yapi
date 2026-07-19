package com.itangcent.easyapi.channel.curl

import com.itangcent.easyapi.core.export.ExportMetadata

data class CurlExportMetadata(val content: String) : ExportMetadata {
    override fun formatDisplay(): String? = null
}
