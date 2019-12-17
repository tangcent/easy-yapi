//
// Copy From IntelliJ IDEA SDK
//
package org.apache.http.util

import org.apache.http.HttpEntity
import org.apache.http.ParseException
import org.apache.http.entity.ContentType
import org.apache.http.protocol.HTTP
import java.io.IOException
import java.io.InputStreamReader
import java.io.UnsupportedEncodingException
import java.nio.charset.Charset
import java.nio.charset.UnsupportedCharsetException

object EntityUtils {

    @Throws(IOException::class)
    fun consume(entity: HttpEntity?) {
        if (entity != null) {
            if (entity.isStreaming) {
                val inputStream = entity.content
                inputStream?.close()
            }

        }
    }

    @Throws(IOException::class)
    fun toByteArray(entity: HttpEntity): ByteArray? {
        Args.notNull(entity, "Entity")
        return entity.content?.use { instream ->
            Args.check(entity.contentLength <= 2147483647L, "HTTP entity too large to be buffered in memory")
            var i = entity.contentLength.toInt()
            if (i < 0) {
                i = 4096
            }

            val buffer = ByteArrayBuffer(i)
            val tmp = ByteArray(4096)

            var l: Int
            while (true) {
                l = instream.read(tmp)
                if (l == -1) break
                buffer.append(tmp, 0, l)
            }

            buffer.toByteArray()
        }
    }

    @Deprecated("")
    @Throws(ParseException::class)
    fun getContentCharSet(entity: HttpEntity): String? {
        Args.notNull(entity, "Entity")
        var charset: String? = null
        if (entity.contentType != null) {
            val values = entity.contentType.elements
            if (values.isNotEmpty()) {
                val param = values[0].getParameterByName("charset")
                if (param != null) {
                    charset = param.value
                }
            }
        }

        return charset
    }

    @Deprecated("")
    @Throws(ParseException::class)
    fun getContentMimeType(entity: HttpEntity): String? {
        Args.notNull(entity, "Entity")
        var mimeType: String? = null
        if (entity.contentType != null) {
            val values = entity.contentType.elements
            if (values.isNotEmpty()) {
                mimeType = values[0].name
            }
        }

        return mimeType
    }

    @Throws(IOException::class, ParseException::class)
    @JvmOverloads
    fun toString(entity: HttpEntity, defaultCharset: Charset? = null): String? {
        Args.notNull(entity, "Entity")
        return entity.content?.use { inStream ->
            Args.check(entity.contentLength <= 2147483647L, "HTTP entity too large to be buffered in memory")
            var i = entity.contentLength.toInt()
            if (i < 0) {
                i = 4096
            }

            var charset: Charset? = null

            try {
                val contentType = ContentType.get(entity)
                if (contentType != null) {
                    charset = contentType.charset
                }
            } catch (var13: UnsupportedCharsetException) {
                if (defaultCharset == null) {
                    throw UnsupportedEncodingException(var13.message)
                }
            }

            if (charset == null) {
                charset = defaultCharset
            }

            if (charset == null) {
                charset = HTTP.DEF_CONTENT_CHARSET
            }

            val reader = InputStreamReader(inStream, charset!!)
            val buffer = CharArrayBuffer(i)
            val tmp = CharArray(1024)

            var l: Int
            while (true) {
                l = reader.read(tmp)
                if (l == -1) break
                buffer.append(tmp, 0, l)
            }

            buffer.toString()
        }
    }

    @Throws(IOException::class, ParseException::class)
    fun toString(entity: HttpEntity, defaultCharset: String?): String? {
        return toString(entity, if (defaultCharset != null) Charset.forName(defaultCharset) else null)
    }
}
