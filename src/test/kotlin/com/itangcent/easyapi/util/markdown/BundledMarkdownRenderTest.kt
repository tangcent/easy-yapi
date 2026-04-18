package com.itangcent.easyapi.util.markdown

import org.junit.Assert.*
import org.junit.Test

class BundledMarkdownRenderTest {

    private val render = BundledMarkdownRender()

    @Test
    fun testRenderBlankReturnsEmpty() {
        assertEquals("", render.render(""))
        assertEquals("", render.render("   "))
    }

    @Test
    fun testRenderHeading() {
        val html = render.render("# Hello")
        assertTrue("Should contain h1 tag", html.contains("<h1"))
        assertTrue("Should contain Hello", html.contains("Hello"))
    }

    @Test
    fun testRenderParagraph() {
        val html = render.render("Hello world")
        assertTrue("Should contain paragraph text", html.contains("Hello"))
    }

    @Test
    fun testRenderBold() {
        val html = render.render("**bold**")
        assertTrue("Should contain bold text", html.contains("bold"))
    }

    @Test
    fun testRenderItalic() {
        val html = render.render("*italic*")
        assertTrue("Should contain italic text", html.contains("italic"))
    }

    @Test
    fun testRenderLink() {
        val html = render.render("[Example](https://example.com)")
        assertTrue("Should contain link", html.contains("href"))
        assertTrue("Should contain URL", html.contains("https://example.com"))
    }

    @Test
    fun testRenderCodeBlock() {
        val html = render.render("```\ncode\n```")
        assertTrue("Should contain code", html.contains("code"))
    }

    @Test
    fun testRenderUnorderedList() {
        val html = render.render("- item1\n- item2")
        assertTrue("Should contain list items", html.contains("item1"))
        assertTrue("Should contain list items", html.contains("item2"))
    }
}
