package com.itangcent.easyapi.exporter.channel.markdown.template

import com.itangcent.easyapi.logging.IdeaLog

/**
 * Loads the bundled default Markdown template from the classpath.
 *
 * The template lives at `/markdown/templates/default.md.tpl` and is intended to reproduce
 * `DefaultMarkdownFormatter` output byte-for-byte (proven by `MarkdownTemplateParityTest`).
 * Loaded once and memoized.
 *
 * The content is **not** trimmed — whitespace (blank-line counts, trailing newlines) is
 * load-bearing for byte-parity with the legacy formatter.
 *
 * @see MarkdownTemplateRenderer
 * @see TemplateModel
 */
object DefaultMarkdownTemplate : IdeaLog {

    private const val DEFAULT_TEMPLATE_RESOURCE = "/markdown/templates/default.md.tpl"

    private val TEMPLATE: String by lazy { loadTemplate() }

    /** The bundled default template text. */
    fun get(): String = TEMPLATE

    private fun loadTemplate(): String {
        return javaClass.getResourceAsStream(DEFAULT_TEMPLATE_RESOURCE)?.use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        } ?: run {
            // Should never happen — the resource ships in the JAR.
            LOG.warn("$DEFAULT_TEMPLATE_RESOURCE not found on classpath; falling back to empty template")
            ""
        }
    }
}
