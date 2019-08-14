package com.itangcent.idea.plugin.api.export

import com.itangcent.common.exporter.RequestHelper
import com.itangcent.common.model.Request

interface ClassExporter {
    fun export(cls: Any, requestHelper: RequestHelper, requestHandle: RequestHandle)
}

typealias RequestHandle = (Request) -> Unit