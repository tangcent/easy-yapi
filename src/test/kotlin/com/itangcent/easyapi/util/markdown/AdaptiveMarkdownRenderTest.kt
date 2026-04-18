package com.itangcent.easyapi.util.markdown

import org.junit.Assert.*
import org.junit.Test

class AdaptiveMarkdownRenderTest {

    @Test
    fun testRenderWithSuccessfulRender() {
        val render = AdaptiveMarkdownRender(listOf(
            object : MarkdownRender {
                override fun render(markdown: String): String = "<p>$markdown</p>"
            }
        ))
        val html = render.render("Hello")
        assertEquals("<p>Hello</p>", html)
    }

    @Test
    fun testRenderFallsBackToNextRender() {
        val render = AdaptiveMarkdownRender(listOf(
            object : MarkdownRender {
                override fun render(markdown: String): String = throw RuntimeException("fail")
            },
            object : MarkdownRender {
                override fun render(markdown: String): String = "<p>fallback</p>"
            }
        ))
        val html = render.render("Hello")
        assertEquals("<p>fallback</p>", html)
    }

    @Test
    fun testRenderFallsBackToRawMarkdown() {
        val render = AdaptiveMarkdownRender(emptyList())
        val result = render.render("Hello")
        assertEquals("Hello", result)
    }

    @Test
    fun testRenderReturnsEmptyString() {
        val render = AdaptiveMarkdownRender(listOf(
            object : MarkdownRender {
                override fun render(markdown: String): String = ""
            }
        ))
        val html = render.render("test")
        assertEquals("Empty string is a valid result", "", html)
    }
}
