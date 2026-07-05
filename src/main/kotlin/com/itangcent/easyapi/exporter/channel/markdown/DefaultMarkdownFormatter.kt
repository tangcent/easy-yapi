package com.itangcent.easyapi.exporter.channel.markdown

import com.itangcent.easyapi.exporter.channel.markdown.template.DefaultMarkdownTemplate
import com.itangcent.easyapi.exporter.channel.markdown.template.MarkdownTemplateRenderer
import com.itangcent.easyapi.exporter.channel.markdown.template.RenderContext
import com.itangcent.easyapi.exporter.channel.markdown.template.TemplateModelBuilder
import com.itangcent.easyapi.exporter.model.ApiEndpoint

/**
 * Default implementation of Markdown formatter for API documentation.
 *
 * Delegates to [MarkdownTemplateRenderer] with the bundled default template
 * (`default.md.tpl`). The default template reproduces the legacy string-concatenation
 * output byte-for-byte (proven by [MarkdownTemplateParityTest]).
 *
 * The model builder always populates [com.itangcent.easyapi.exporter.channel.markdown.template.BodyView.model]
 * and `fields`; whether demos are rendered is decided solely by the template's
 * `{{#if body}}` + `{{{body.asDemo()}}}` blocks — the formatter itself has no demo toggle.
 *
 * The default template does not reference any `meta.*` built-ins, so the
 * [RenderContext] values are irrelevant to the output — [RenderContext.production]
 * is used with placeholder project/plugin names.
 */
class DefaultMarkdownFormatter : MarkdownFormatter {

    override suspend fun format(endpoints: List<ApiEndpoint>, moduleName: String): String {
        val model = TemplateModelBuilder.build(endpoints, moduleName)
        val ctx = RenderContext.production(projectName = "", pluginVersion = "")
        val template = DefaultMarkdownTemplate.get()
        return MarkdownTemplateRenderer.render(template, model, ctx)
    }
}
