package com.itangcent.easyapi.core.dashboard

import com.itangcent.easyapi.core.http.ResponseBody
import com.itangcent.easyapi.core.http.ResponseBodyReader

/**
 * Renders a response [ResponseBody] carrier into the Dashboard's response panel.
 *
 * The registry pattern keeps the (already large) `EndpointDetailsPanel` from growing a
 * `when` branch per content type: each renderer is self-contained and [supports] decides
 * whether it claims a given body. A renderer produces a plain [RenderDescription] (no Swing),
 * which the panel turns into UI, so renderers stay unit-testable.
 */
interface ResponseRenderer {

    /** Whether this renderer claims [body]. Evaluated in registration order (first wins). */
    fun supports(body: ResponseBody, contentType: String?): Boolean

    /** Describes how [body] should be presented. */
    fun describe(body: ResponseBody, contentType: String?): RenderDescription
}

/**
 * Plain description of how a response body should be rendered, decoupled from Swing.
 *
 * @param text the text to show in the response body editor (formatted text, or a metadata
 *   summary for binary bodies)
 * @param showSaveAs whether to surface a "Save as" action for the body
 */
data class RenderDescription(
    val text: String,
    val showSaveAs: Boolean = false
)

/**
 * Registry of [ResponseRenderer]s. The default [TextRenderer] is the fallback for any
 * body no renderer claims, so an unrecognised content type never throws.
 */
object ResponseRendererRegistry {

    private val renderers: MutableList<ResponseRenderer> = mutableListOf(TextRenderer, BinaryRenderer)

    fun register(renderer: ResponseRenderer) {
        renderers.add(renderer)
    }

    fun lookup(body: ResponseBody, contentType: String?): ResponseRenderer =
        renderers.firstOrNull { it.supports(body, contentType) } ?: TextRenderer
}

/**
 * Renders text bodies as-is; formatting and syntax highlighting are the panel's concern.
 */
object TextRenderer : ResponseRenderer {
    override fun supports(body: ResponseBody, contentType: String?): Boolean = body is ResponseBody.Text
    override fun describe(body: ResponseBody, contentType: String?): RenderDescription =
        RenderDescription(text = (body as ResponseBody.Text).value)
}

/**
 * Renders binary bodies ([ResponseBody.Bytes] / [ResponseBody.File]) as a human-readable
 * metadata summary and surfaces a "Save as" action to download the raw bytes.
 */
object BinaryRenderer : ResponseRenderer {

    override fun supports(body: ResponseBody, contentType: String?): Boolean =
        body is ResponseBody.Bytes || body is ResponseBody.File

    override fun describe(body: ResponseBody, contentType: String?): RenderDescription {
        val size = ResponseBodyReader.sizeOf(body)?.let { "$it bytes" } ?: "unknown size"
        val carrier = when (body) {
            is ResponseBody.Bytes -> "in-memory bytes"
            is ResponseBody.File -> "spilled to temp file"
            else -> "binary"
        }
        val type = contentType ?: "unknown"
        val summary = buildString {
            appendLine("Binary response body")
            appendLine("Size: $size")
            appendLine("Content-Type: $type")
            appendLine("Carrier: $carrier")
            appendLine()
            appendLine("Use \"Save as…\" to download the raw bytes.")
        }
        return RenderDescription(text = summary, showSaveAs = true)
    }
}
