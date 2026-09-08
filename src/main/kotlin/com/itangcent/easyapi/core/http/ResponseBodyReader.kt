package com.itangcent.easyapi.core.http

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * Shared logic for materializing an HTTP response body into a [ResponseBody] carrier.
 *
 * Splits by content type (via [ResponseBodyClassifier.isTextContentType]) and, for binary
 * bodies, by size ([ResponseBody.MAX_IN_MEMORY_BYTES]): small bodies stay in memory as
 * [ResponseBody.Bytes], large bodies are streamed to a temporary file as [ResponseBody.File].
 */
object ResponseBodyReader {

    private const val TEMP_PREFIX = "easyapi-response-"

    /**
     * Reads [input] into a [ResponseBody] based on [contentType] and [contentLength].
     *
     * [contentLength] is a hint (may be -1 when unknown); when it indicates a large binary
     * body, the stream is spilled to disk without first buffering it in memory.
     *
     * The [input] stream is consumed and closed by this method.
     *
     * @return the carrier, or null when [input] is null (no body)
     */
    fun read(input: InputStream?, contentType: String?, contentLength: Long): ResponseBody? {
        if (input == null) return null
        return if (ResponseBodyClassifier.isTextContentType(contentType)) {
            input.use { ResponseBody.Text(it.readBytes().toString(Charsets.UTF_8)) }
        } else if (contentLength in 0..ResponseBody.MAX_IN_MEMORY_BYTES) {
            input.use { ResponseBody.Bytes(it.readBytes()) }
        } else {
            ResponseBody.File(spillToTempFile(input))
        }
    }

    /**
     * Streams [input] into a fresh temporary file, returning its path.
     *
     * The caller is responsible for eventually deleting the file (there is no RAII);
     * files live under the system temp directory as an OS-level fallback.
     */
    fun spillToTempFile(input: InputStream): Path {
        val path = Files.createTempFile(TEMP_PREFIX, null)
        input.use { inputStream ->
            Files.newOutputStream(path).use { output ->
                inputStream.copyTo(output)
            }
        }
        return path
    }

    /**
     * Extracts a byte count for a given [ResponseBody] carrier, or null when unavailable
     * (e.g. a spilled file whose size has not been stat'd).
     */
    fun sizeOf(body: ResponseBody?): Long? = when (body) {
        null -> null
        is ResponseBody.Text -> body.value.length.toLong()
        is ResponseBody.Bytes -> body.value.size.toLong()
        is ResponseBody.File -> runCatching { Files.size(body.path) }.getOrNull()
    }
}

/**
 * Writes a [ResponseBody] carrier to disk, preserving the raw bytes regardless of carrier.
 *
 * Shared by the Dashboard's "Save as" action and the script-side `saveBody(path)` API so
 * both behave identically.
 */
object ResponseBodyWriter {

    /**
     * Writes [body] to [target]. Text is UTF-8 encoded; bytes are written verbatim; a
     * spilled [ResponseBody.File] is copied without reading it back into memory.
     *
     * @return true on success, false when [body] is null
     */
    fun save(body: ResponseBody?, target: java.io.File): Boolean {
        if (body == null) return false
        target.parentFile?.mkdirs()
        when (body) {
            is ResponseBody.Text -> target.writeBytes(body.value.toByteArray(Charsets.UTF_8))
            is ResponseBody.Bytes -> target.writeBytes(body.value)
            is ResponseBody.File -> Files.copy(body.path, target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        return true
    }
}
