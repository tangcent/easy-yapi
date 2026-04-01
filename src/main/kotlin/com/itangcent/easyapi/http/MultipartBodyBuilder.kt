package com.itangcent.easyapi.http

import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Builds RFC 2046 multipart/form-data bodies from [FormParam] entries.
 *
 * A pure Kotlin implementation with no external dependencies.
 * Generates the multipart boundary, headers, and body content.
 *
 * ## Usage
 * ```kotlin
 * val params = listOf(
 *     FormParam.Text("username", "john"),
 *     FormParam.File("avatar", "photo.jpg", "image/jpeg", imageBytes)
 * )
 * val multipart = MultipartBodyBuilder.build(params)
 * // Use multipart.contentType for Content-Type header
 * // Use multipart.bytes for request body
 * ```
 *
 * ## Output Format
 * ```
 * ----EasyApiBoundary
 * Content-Disposition: form-data; name="username"
 *
 * john
 * ----EasyApiBoundary
 * Content-Disposition: form-data; name="avatar"; filename="photo.jpg"
 * Content-Type: image/jpeg
 *
 * <binary data>
 * ----EasyApiBoundary--
 * ```
 *
 * @see FormParam for parameter types
 * @see HttpClient for HTTP client usage
 */
object MultipartBodyBuilder {

    /**
     * Represents a built multipart body.
     *
     * @param contentType The Content-Type header value (includes boundary)
     * @param bytes The raw body bytes
     */
    data class MultipartBody(
        val contentType: String,
        val bytes: ByteArray
    )

    fun build(params: List<FormParam>): MultipartBody {
        val boundary = "----EasyApi${UUID.randomUUID().toString().replace("-", "")}"
        val crlf = "\r\n"
        val out = ByteArrayOutputStream()

        for (param in params) {
            out.write("--$boundary$crlf".toByteArray())
            when (param) {
                is FormParam.Text -> {
                    out.write("Content-Disposition: form-data; name=\"${param.name}\"$crlf".toByteArray())
                    out.write(crlf.toByteArray())
                    out.write(param.value.toByteArray(Charsets.UTF_8))
                }
                is FormParam.File -> {
                    val ct = param.contentType ?: "application/octet-stream"
                    out.write("Content-Disposition: form-data; name=\"${param.name}\"; filename=\"${param.fileName}\"$crlf".toByteArray())
                    out.write("Content-Type: $ct$crlf".toByteArray())
                    out.write(crlf.toByteArray())
                    out.write(param.bytes)
                }
            }
            out.write(crlf.toByteArray())
        }

        out.write("--$boundary--$crlf".toByteArray())

        return MultipartBody(
            contentType = "multipart/form-data; boundary=$boundary",
            bytes = out.toByteArray()
        )
    }
}
