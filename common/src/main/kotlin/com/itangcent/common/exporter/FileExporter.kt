package com.itangcent.common.exporter

import com.itangcent.common.model.RequestHandle

interface FileExporter {

    fun export(file: Any, parseHandle: ParseHandle, requestHandle: RequestHandle)
}