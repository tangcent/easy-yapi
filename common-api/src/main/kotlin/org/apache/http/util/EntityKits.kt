//
// Copy From IntelliJ IDEA SDK
//
package org.apache.http.util

import com.itangcent.common.utils.readString
import org.apache.http.HttpEntity
import java.nio.charset.Charset

fun HttpEntity.toByteArray(): ByteArray {
    return this.content.use { it.readBytes() }
}

fun HttpEntity.consume() {
    this.takeIf { it.isStreaming }
            ?.content
            ?.close()
}

fun HttpEntity.getContentCharSet(): String? {
    Args.notNull(this, "this")
    var charset: String? = null
    if (this.contentType != null) {
        val values = this.contentType.elements
        if (values.isNotEmpty()) {
            val param = values[0].getParameterByName("charset")
            if (param != null) {
                charset = param.value
            }
        }
    }

    return charset
}

fun HttpEntity.getContentMimeType(): String? {
    Args.notNull(this, "this")
    var mimeType: String? = null
    if (this.contentType != null) {
        val values = this.contentType.elements
        if (values.isNotEmpty()) {
            mimeType = values[0].name
        }
    }

    return mimeType
}

fun HttpEntity.readString(defaultCharset: Charset? = null): String {
    val charset = getContentCharSet()?.let { Charset.forName(it) }
            ?: defaultCharset ?: Charsets.UTF_8
    return this.content.use { it.readString(charset) }
}

fun HttpEntity.readString(defaultCharset: String?): String {
    return this.readString(if (defaultCharset != null) Charset.forName(defaultCharset) else null)
}