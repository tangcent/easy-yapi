package com.itangcent.easyapi.util.markdown

class AdaptiveMarkdownRender(private val renders: List<MarkdownRender>) : MarkdownRender {
    override fun render(markdown: String): String {
        for (render in renders) {
            val html = runCatching { render.render(markdown) }.getOrNull()
            if (html != null) return html
        }
        return markdown
    }
}
