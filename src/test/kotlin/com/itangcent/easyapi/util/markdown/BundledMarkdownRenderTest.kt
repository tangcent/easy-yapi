package com.itangcent.easyapi.util.markdown

import org.junit.Assert.*
import org.junit.Test

class BundledMarkdownRenderTest {

    private val render = BundledMarkdownRender()

    // region Blank / empty input

    @Test
    fun testRenderBlankReturnsEmpty() {
        assertEquals("", render.render(""))
        assertEquals("", render.render("   "))
        assertEquals("", render.render("\t"))
        assertEquals("", render.render("\n"))
    }

    // endregion

    // region Headings

    @Test
    fun testRenderH1() {
        val html = render.render("# Hello")
        assertTrue("Should contain h1 tag", html.contains("<h1"))
        assertTrue("Should contain Hello", html.contains("Hello"))
    }

    @Test
    fun testRenderH2() {
        val html = render.render("## World")
        assertTrue("Should contain h2 tag", html.contains("<h2"))
        assertTrue("Should contain World", html.contains("World"))
    }

    @Test
    fun testRenderH3() {
        val html = render.render("### Section")
        assertTrue("Should contain h3 tag", html.contains("<h3"))
    }

    // endregion

    // region Inline formatting

    @Test
    fun testRenderParagraph() {
        val html = render.render("Hello world")
        assertTrue("Should contain paragraph text", html.contains("Hello"))
    }

    @Test
    fun testRenderBold() {
        val html = render.render("**bold**")
        assertTrue("Should contain bold text", html.contains("bold"))
        assertTrue("Should contain strong tag", html.contains("<strong>") || html.contains("<b>"))
    }

    @Test
    fun testRenderItalic() {
        val html = render.render("*italic*")
        assertTrue("Should contain italic text", html.contains("italic"))
        assertTrue("Should contain em tag", html.contains("<em>") || html.contains("<i>"))
    }

    @Test
    fun testRenderInlineCode() {
        val html = render.render("`code`")
        assertTrue("Should contain code tag", html.contains("<code>"))
        assertTrue("Should contain code text", html.contains("code"))
    }

    // endregion

    // region Links and images

    @Test
    fun testRenderLink() {
        val html = render.render("[Example](https://example.com)")
        assertTrue("Should contain href", html.contains("href"))
        assertTrue("Should contain URL", html.contains("https://example.com"))
        assertTrue("Should contain link text", html.contains("Example"))
    }

    @Test
    fun testRenderImage() {
        val html = render.render("![Alt text](https://example.com/image.png)")
        assertTrue("Should contain img tag", html.contains("<img"))
        assertTrue("Should contain src", html.contains("https://example.com/image.png"))
    }

    // endregion

    // region Code blocks

    @Test
    fun testRenderCodeBlock() {
        val html = render.render("```\ncode\n```")
        assertTrue("Should contain code", html.contains("code"))
        assertTrue("Should contain code tag", html.contains("<code>"))
    }

    @Test
    fun testRenderFencedCodeBlockWithLanguage() {
        val html = render.render("```kotlin\nval x = 1\n```")
        assertTrue("Should contain code", html.contains("val x = 1"))
        assertTrue("Should contain pre tag", html.contains("<pre>"))
    }

    // endregion

    // region Lists

    @Test
    fun testRenderUnorderedList() {
        val html = render.render("- item1\n- item2\n- item3")
        assertTrue("Should contain list items", html.contains("item1"))
        assertTrue("Should contain list items", html.contains("item2"))
        assertTrue("Should contain list items", html.contains("item3"))
        assertTrue("Should contain ul tag", html.contains("<ul") || html.contains("<li"))
    }

    @Test
    fun testRenderOrderedList() {
        val html = render.render("1. first\n2. second\n3. third")
        assertTrue("Should contain list items", html.contains("first"))
        assertTrue("Should contain list items", html.contains("second"))
        assertTrue("Should contain list items", html.contains("third"))
        assertTrue("Should contain ol tag", html.contains("<ol") || html.contains("<li"))
    }

    // endregion

    // region Blockquotes

    @Test
    fun testRenderBlockquote() {
        val html = render.render("> This is a quote")
        assertTrue("Should contain quote text", html.contains("This is a quote"))
        assertTrue("Should contain blockquote tag", html.contains("<blockquote"))
    }

    // endregion

    // region Horizontal rules

    @Test
    fun testRenderHorizontalRule() {
        val html = render.render("---")
        assertTrue("Should contain hr tag", html.contains("<hr"))
    }

    // endregion

    // region Multi-line and mixed content

    @Test
    fun testRenderMultiLineInput() {
        val markdown = """
            # API Documentation
            
            This API provides user management.
            
            ## Endpoints
            
            - GET /users
            - POST /users
        """.trimIndent()
        val html = render.render(markdown)
        assertTrue("Should contain h1", html.contains("<h1"))
        assertTrue("Should contain h2", html.contains("<h2"))
        assertTrue("Should contain endpoint text", html.contains("GET /users"))
    }

    @Test
    fun testRenderDescriptionWithCodeBlock() {
        val markdown = """
            Creates a new user.
            
            Example request:
            ```json
            {"name": "John", "age": 30}
            ```
        """.trimIndent()
        val html = render.render(markdown)
        assertTrue("Should contain description", html.contains("Creates a new user"))
        assertTrue("Should contain code", html.contains("John"))
    }

    @Test
    fun testRenderTable() {
        val markdown = """
            | Name  | Type   | Description |
            |-------|--------|-------------|
            | id    | int    | User ID     |
            | name  | string | User name   |
        """.trimIndent()
        val html = render.render(markdown)
        assertTrue("Should contain table tag", html.contains("<table"))
        assertTrue("Should contain header", html.contains("Name"))
        assertTrue("Should contain data", html.contains("User ID"))
    }

    // endregion

    // region Special characters and edge cases

    @Test
    fun testRenderHtmlEntities() {
        val html = render.render("A < B > C & D")
        assertNotNull(html)
        assertTrue(html.isNotEmpty())
    }

    @Test
    fun testRenderNonEnglishText() {
        val html = render.render("# 用户管理")
        assertTrue("Should contain non-English text", html.contains("用户管理"))
    }

    @Test
    fun testRenderMixedFormatting() {
        val html = render.render("**bold** and *italic* and `code`")
        assertTrue("Should contain bold", html.contains("bold"))
        assertTrue("Should contain italic", html.contains("italic"))
        assertTrue("Should contain code", html.contains("code"))
    }

    @Test
    fun testRenderNewlineOnly() {
        assertEquals("", render.render("\n\n\n"))
    }

    @Test
    fun testRenderSingleCharacter() {
        val html = render.render("a")
        assertTrue("Should contain character", html.contains("a"))
    }

    @Test
    fun testRenderLongDocument() {
        val lines = (1..100).joinToString("\n") { "Line $it with some content." }
        val html = render.render(lines)
        assertTrue("Should contain first line", html.contains("Line 1"))
        assertTrue("Should contain last line", html.contains("Line 100"))
    }

    // endregion

    // region YApi-style descriptions

    @Test
    fun testRenderYapiStyleDescription() {
        val markdown = """
            Get user information by ID.
            
            **Path Parameters:**
            - `id`: User ID
            
            **Response:**
            ```json
            {
              "id": 1,
              "name": "John"
            }
            ```
        """.trimIndent()
        val html = render.render(markdown)
        assertTrue("Should contain description", html.contains("Get user information"))
        assertTrue("Should contain bold", html.contains("<strong>") || html.contains("<b>"))
        assertTrue("Should contain code", html.contains("<code>"))
        assertTrue("Should contain json content", html.contains("John"))
    }

    // endregion
}
