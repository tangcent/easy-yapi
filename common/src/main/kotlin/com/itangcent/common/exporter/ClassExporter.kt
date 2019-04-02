package com.itangcent.common.exporter

import com.itangcent.common.model.RequestHandle

interface ClassExporter {
    fun export(cls: Any, parseHandle: ParseHandle, requestHandle: RequestHandle)
}