package com.itangcent.easyapi.util.markdown

import com.intellij.openapi.components.Service

/**
 * Bundled Markdown-to-HTML renderer using the IntelliJ Markdown parser.
 *
 * This implementation is self-contained and requires no external services,
 * shell commands, or network access. It uses the
 * [org.intellij.markdown](https://github.com/JetBrains/markdown) library
 * that ships with the IntelliJ Platform, parsing Markdown according to the
 * GitHub Flavored Markdown (GFM) specification.
 *
 * GFM extends CommonMark with:
 * - Tables
 * - Strikethrough
 * - Task lists
 * - Autolinks
 * - Fenced code blocks with language info
 *
 * Rendering pipeline:
 * 1. Blank input is short-circuited to an empty string
 * 2. A [GFMFlavourDescriptor] is created for the dialect
 * 3. The Markdown source is parsed into an AST via [MarkdownParser]
 * 4. The AST is serialised to HTML via [HtmlGenerator]
 *
 * This is the only [MarkdownRender] implementation in the project.
 * Previous implementations (shell-based, remote HTTP, adaptive, file-writing)
 * have been removed in favour of this single, reliable, offline renderer.
 */
@Service(Service.Level.PROJECT)
class BundledMarkdownRender : MarkdownRender {
    override fun render(markdown: String): String {
        if (markdown.isBlank()) return ""
        val flavour = org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor()
        val parsedTree = org.intellij.markdown.parser.MarkdownParser(flavour).buildMarkdownTreeFromString(markdown)
        return org.intellij.markdown.html.HtmlGenerator(markdown, parsedTree, flavour).generateHtml()
    }
}
