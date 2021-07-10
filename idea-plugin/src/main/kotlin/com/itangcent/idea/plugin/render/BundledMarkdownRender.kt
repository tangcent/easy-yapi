package com.itangcent.idea.plugin.render

import com.google.inject.Singleton
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser

/**
 *
 * Bundled `markdown` render
 *
 * see https://github.com/JetBrains/markdown
 */
@Singleton
class BundledMarkdownRender : MarkdownRender {

    private val commonMarkFlavourDescriptor = CommonMarkFlavourDescriptor()

    override fun render(markdown: String): String? {
        if (markdown.isBlank()) {
            return null
        }
        val astNode = MarkdownParser(commonMarkFlavourDescriptor).buildMarkdownTreeFromString(markdown)
        val htmlGenerator = HtmlGenerator(markdown, astNode, commonMarkFlavourDescriptor, false)
        val generateHtml = htmlGenerator.generateHtml()
        return generateHtml
            .removePrefix("<body>")
            .removeSuffix("</body>")
            .takeIf { it.isNotBlank() }
    }
}