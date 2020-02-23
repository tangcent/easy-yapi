package com.itangcent.idea.plugin.render

import com.google.inject.ImplementedBy

@ImplementedBy(AdaptiveMarkdownRender::class)
interface MarkdownRender {

    fun render(markdown: String): String?

}