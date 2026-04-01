package com.itangcent.easyapi.util.markdown

import java.nio.file.Files
import java.nio.file.Path

class FileMarkdownRender(
    private val outputPath: Path,
    private val delegate: MarkdownRender = BundledMarkdownRender()
) : MarkdownRender {
    override fun render(markdown: String): String {
        val html = delegate.render(markdown)
        outputPath.parent?.let { Files.createDirectories(it) }
        Files.writeString(outputPath, html, Charsets.UTF_8)
        return html
    }
}
