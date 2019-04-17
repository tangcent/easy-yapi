package com.itangcent.common.http

import org.apache.http.Consts
import org.apache.http.entity.ContentType
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.Charset

class BasicHttpResponse : HttpResponse {
    override fun getCode(): Int? {
        return code
    }

    override fun getHeader(): List<Pair<String, String>>? {
        return header
    }

    override fun getContentType(): ContentType? {
        return this.type
    }

    override fun asBytes(): ByteArray {
        return this.raw!!.clone()
    }

    override fun asString(): String {
        var charset: Charset? = this.type?.charset
        if (charset == null) {
            charset = Consts.UTF_8
        }
        return this.asString(charset!!)
    }

    override fun asString(charset: Charset): String {
        return String(this.raw!!, charset)
    }

    override fun asStream(): InputStream {
        return ByteArrayInputStream(this.raw)
    }

    private var code: Int? = null

    private var header: List<Pair<String, String>>? = null

    private var raw: ByteArray? = null

    private var type: ContentType? = null

    constructor(code: Int?,
                header: List<Pair<String, String>>?,
                raw: ByteArray?,
                type: ContentType?) {
        this.code = code
        this.header = header
        this.raw = raw
        this.type = type
    }
}