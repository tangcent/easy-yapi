package com.itangcent.idea.plugin.api.export.markdown

import com.google.inject.Inject
import com.google.inject.Provider
import com.google.inject.Singleton
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.config.resource.ResourceResolver

/**
 * Provider for MarkdownFormatter instances that supports both template-based and default formatting.
 * This class is responsible for creating the appropriate MarkdownFormatter based on configuration.
 *
 * The provider checks for an 'api.template' configuration and if found, creates a MixMarkdownFormatter
 * that combines template-based formatting for APIs with default formatting for method documentation.
 * If no template is configured or the template is not reachable, it falls back to the default formatter.
 */
@Singleton
class MarkdownFormatterProvider : Provider<MarkdownFormatter> {

    @Inject
    private lateinit var configReader: ConfigReader

    @Inject
    private lateinit var resourceResolver: ResourceResolver

    /**
     * Creates and returns a MarkdownFormatter instance based on configuration.
     *
     * @return A MarkdownFormatter that either uses a template if configured, or falls back to the default formatter
     */
    override fun get(): MarkdownFormatter {
        // Check for api.template configuration
        val templatePath = configReader.first("api.template")

        if (templatePath != null) {
            val resource = resourceResolver.resolve(templatePath)
            if (resource.reachable) {
                return MixMarkdownFormatter(
                    apiMarkdownFormatter = TemplateMarkdownFormatter(resource),
                    methodDocMarkdownFormatter = DefaultMarkdownFormatter()
                )
            }
        }

        return DefaultMarkdownFormatter()
    }
} 