package com.itangcent.idea.plugin.render

import com.google.inject.Singleton
import com.itangcent.plugin.adapter.MarkdownHtmlGenerator

/**
 *
 * Bundled `markdown` render
 *
 * see https://github.com/JetBrains/markdown
 */
@Singleton
class BundledMarkdownRender : MarkdownRender {

    override fun render(markdown: String): String? {
        if (markdown.isBlank()) {
            return null
        }
        return MarkdownHtmlGenerator().render(markdown)
    }
}