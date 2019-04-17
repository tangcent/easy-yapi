package com.itangcent.common.http

import org.apache.http.entity.ContentType
import java.io.InputStream
import java.nio.charset.Charset

interface HttpResponse {

    fun getCode(): Int?

    fun getHeader(): List<Pair<String, String>>?

    fun asBytes(): ByteArray

    fun asString(): String

    fun asString(charset: Charset): String

    fun asStream(): InputStream
    fun getContentType(): ContentType?
}