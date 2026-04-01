package com.itangcent.easyapi.util.markdown

class BundledMarkdownRender : MarkdownRender {
    override fun render(markdown: String): String {
        if (markdown.isBlank()) return ""
        val flavour = org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor()
        val parsedTree = org.intellij.markdown.parser.MarkdownParser(flavour).buildMarkdownTreeFromString(markdown)
        return org.intellij.markdown.html.HtmlGenerator(markdown, parsedTree, flavour).generateHtml()
    }
}
