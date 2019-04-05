package com.itangcent.common.exporter

import com.itangcent.common.model.RequestHandle

interface ClassExporter<T> {
    fun export(cls: T, parseHandle: ParseHandle, requestHandle: RequestHandle)
}