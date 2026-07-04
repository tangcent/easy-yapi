package com.itangcent.easyapi.exporter.channel.markdown.template

import com.itangcent.easyapi.logging.IdeaLog

/**
 * Orchestrates Markdown rendering from a [TemplateModel] via a template string.
 *
 * The core [render] method is a thin delegate to [TemplateEngine.render] — pure, no side
 * effects, no fallback. It is the entry point used by [MarkdownTemplateParityTest] to prove
 * byte-parity with `DefaultMarkdownFormatter`.
 *
 * Production callers that need fault-tolerance (a user-supplied template that fails to parse
 * or execute) use [renderWithFallback], which catches exceptions, logs via `IdeaConsole.error`,
 * posts `NotificationUtils.notifyError`, and re-renders with the bundled default template,
 * appending the distinguishable log line `"Rendered with DEFAULT template (user template
 * failed: <reason>)"` . Missing variables/helpers are **not** hard failures
 * — the engine resolves them to empty + `debug` log  — so they never trigger fallback.
 *
 * @see TemplateEngine
 * @see DefaultMarkdownTemplate
 * @see TemplateModelBuilder
 */
object MarkdownTemplateRenderer : IdeaLog {

    /**
     * Renders [template] against [model] with the given [ctx]. Pure: no fallback, no side
     * effects beyond [IdeaLog.info] traces inside the engine.
     *
     * @throws Exception if the template fails to parse or the engine hits an internal error.
     *   Production callers should use [renderWithFallback] to catch these.
     */
    fun render(template: String, model: TemplateModel, ctx: RenderContext): String {
        return TemplateEngine.render(template, model, ctx)
    }

    /**
     * Renders [template] against [model]; on parse/exec failure, logs the error, notifies the
     * user, and re-renders with the bundled default template .
     *
     * The fallback output is suffixed with a distinguishable log line so the user can see that
     * their custom template was bypassed:
     * ```
     * Rendered with DEFAULT template (user template failed: <reason>)
     * ```
     *
     * Missing variables/helpers do **not** trigger fallback  — they resolve to empty.
     */
    fun renderWithFallback(template: String, model: TemplateModel, ctx: RenderContext): String {
        return try {
            TemplateEngine.render(template, model, ctx)
        } catch (t: Throwable) {
            val reason = t.message ?: t::class.simpleName ?: "unknown"
            LOG.warn("Template render failed; falling back to default template", t)
            val fallback = DefaultMarkdownTemplate.get()
            val rendered = if (fallback.isBlank()) {
                ""
            } else {
                TemplateEngine.render(fallback, model, ctx)
            }
            rendered + "\nRendered with DEFAULT template (user template failed: $reason)\n"
        }
    }
}
