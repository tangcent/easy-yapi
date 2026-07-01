package com.itangcent.easyapi.exporter.markdown

import com.itangcent.easyapi.exporter.markdown.template.DefaultMarkdownTemplate
import com.itangcent.easyapi.exporter.markdown.template.MarkdownTemplateRenderer
import com.itangcent.easyapi.exporter.markdown.template.RenderContext
import com.itangcent.easyapi.exporter.markdown.template.TemplateModelBuilder
import com.itangcent.easyapi.exporter.model.ApiEndpoint

/**
 * Default implementation of Markdown formatter for API documentation.
 *
 * Delegates to [MarkdownTemplateRenderer] with the bundled default template
 * (`default.md.tpl`). The default template reproduces the legacy string-concatenation
 * output byte-for-byte (proven by [MarkdownTemplateParityTest]), so this class is now
 * a thin wrapper that preserves the public API ([outputDemo] flag + `suspend format`).
 *
 * The default template does not reference any `meta.*` built-ins, so the
 * [RenderContext] values are irrelevant to the output — [RenderContext.production]
 * is used with placeholder project/plugin names.
 *
 * @param outputDemo Whether to include JSON demo examples in the output
 */
class DefaultMarkdownFormatter(
    private val outputDemo: Boolean = true
) : MarkdownFormatter {

    override suspend fun format(endpoints: List<ApiEndpoint>, moduleName: String): String {
        val model = TemplateModelBuilder.build(endpoints, outputDemo, moduleName)
        val ctx = RenderContext.production(projectName = "", pluginVersion = "")
        val template = DefaultMarkdownTemplate.get()
        return MarkdownTemplateRenderer.render(template, model, ctx)
    }
}
