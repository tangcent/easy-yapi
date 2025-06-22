package com.itangcent.test

import com.itangcent.common.utils.asUrl
import com.itangcent.intellij.config.resource.Resource
import java.io.InputStream
import java.net.URL

class StringResource(
    private val _url: String,
    private val str: String,
) : Resource() {
    override val url: URL
        get() = _url.asUrl()

    override val bytes: ByteArray
        get() = str.toByteArray(Charsets.UTF_8)

    override val content: String
        get() = str

    override val inputStream: InputStream
        get() = str.toByteArray(Charsets.UTF_8).inputStream()
}