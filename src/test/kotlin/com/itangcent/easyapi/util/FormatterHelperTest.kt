package com.itangcent.easyapi.util

import org.junit.Assert.*
import org.junit.Test

class FormatterHelperTest {

    @Test
    fun testFormatJson_validJson() {
        val json = "{\"name\":\"John\",\"age\":30}"
        val result = FormatterHelper.formatJson(json)
        assertTrue(result.contains("\"name\""))
        assertTrue(result.contains("\"John\""))
        assertTrue(result.contains("\n"))
    }

    @Test
    fun testFormatJson_alreadyFormatted() {
        val json = "{\n  \"name\": \"John\"\n}"
        val result = FormatterHelper.formatJson(json)
        assertTrue(result.contains("\"name\""))
    }

    @Test
    fun testFormatJson_invalidJson() {
        val invalid = "not json"
        val result = FormatterHelper.formatJson(invalid)
        assertEquals(invalid, result)
    }

    @Test
    fun testFormatJson_emptyObject() {
        val result = FormatterHelper.formatJson("{}")
        assertEquals("{}", result)
    }

    @Test
    fun testFormatJson_array() {
        val json = "[1,2,3]"
        val result = FormatterHelper.formatJson(json)
        assertTrue(result.contains("1"))
        assertTrue(result.contains("2"))
        assertTrue(result.contains("3"))
    }

    @Test
    fun testFormatXml_validXml() {
        val xml = "<root><item>value</item></root>"
        val result = FormatterHelper.formatXml(xml)
        assertTrue(result.contains("<root>"))
        assertTrue(result.contains("<item>"))
        assertTrue(result.contains("value"))
    }

    @Test
    fun testFormatXml_invalidXml() {
        val invalid = "not xml"
        val result = FormatterHelper.formatXml(invalid)
        assertEquals(invalid, result)
    }

    @Test
    fun testFormatXml_selfClosingTag() {
        val xml = "<root><item/></root>"
        val result = FormatterHelper.formatXml(xml)
        assertTrue(result.contains("<root>"))
    }

    @Test
    fun testFormatHtml_simpleHtml() {
        val html = "<div><p>text</p></div>"
        val result = FormatterHelper.formatHtml(html)
        assertTrue(result.contains("<div>"))
        assertTrue(result.contains("<p>text</p>"))
        assertTrue(result.contains("</div>"))
    }

    @Test
    fun testFormatHtml_withWhitespace() {
        val html = "<div>   <p>text</p>   </div>"
        val result = FormatterHelper.formatHtml(html)
        assertTrue(result.contains("<div>"))
        assertTrue(result.contains("text"))
    }

    @Test
    fun testFormatHtml_selfClosingTags() {
        val html = "<div><br/><hr/><img src=\"x\"/></div>"
        val result = FormatterHelper.formatHtml(html)
        assertTrue(result.contains("<div>"))
        assertTrue(result.contains("<br/>"))
    }

    @Test
    fun testFormatHtml_indentation() {
        // Use whitespace between tags to trigger normalization
        val html = "<div> <p>text</p> </div>"
        val result = FormatterHelper.formatHtml(html)
        val lines = result.lines()
        assertTrue(lines.size >= 2)
        val pLine = lines.find { it.contains("<p>") }
        assertNotNull(pLine)
        assertTrue(pLine!!.startsWith("  "))
    }

    @Test
    fun testFormatHtml_emptyInput() {
        val result = FormatterHelper.formatHtml("")
        assertEquals("", result)
    }

    @Test
    fun testFormatHtml_nestedElements() {
        val html = "<ul><li>item1</li><li>item2</li></ul>"
        val result = FormatterHelper.formatHtml(html)
        assertTrue(result.contains("<ul>"))
        assertTrue(result.contains("item1"))
        assertTrue(result.contains("item2"))
    }
}
