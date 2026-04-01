package com.itangcent.easyapi.util

import com.google.gson.JsonParser
import java.io.StringReader
import java.io.StringWriter
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.xml.sax.InputSource

/**
 * Utility object for formatting various data formats.
 *
 * Provides pretty-printing for JSON, XML, and HTML content.
 * Used for displaying formatted output in the UI.
 *
 * ## Usage
 * ```kotlin
 * // Format JSON
 * val prettyJson = FormatterHelper.formatJson("{\"name\":\"John\"}")
 *
 * // Format XML
 * val prettyXml = FormatterHelper.formatXml("<root><item/></root>")
 *
 * // Format HTML
 * val prettyHtml = FormatterHelper.formatHtml("<div><p>text</p></div>")
 * ```
 */
object FormatterHelper {
    fun formatJson(json: String): String {
        return runCatching {
            val element = JsonParser.parseString(json)
            GsonUtils.prettyJson(element)
        }.getOrElse { json }
    }

    fun formatXml(xml: String): String {
        return runCatching {
            val documentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            }

            val document = documentBuilderFactory.newDocumentBuilder().parse(InputSource(StringReader(xml)))

            val transformerFactory = TransformerFactory.newInstance().apply {
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            }

            val transformer = transformerFactory.newTransformer().apply {
                setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
                setOutputProperty(OutputKeys.INDENT, "yes")
                setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
            }

            val writer = StringWriter()
            transformer.transform(DOMSource(document), StreamResult(writer))
            writer.toString().trim()
        }.getOrElse { xml }
    }

    fun formatHtml(html: String): String {
        val normalized = html.trim().replace(Regex(">\\s+<"), ">\n<")
        val selfClosing = Regex(
            "^<\\s*(br|hr|img|input|meta|link)(\\s+[^>]*)?>$",
            setOf(RegexOption.IGNORE_CASE)
        )

        var indent = 0
        return normalized.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n") { line ->
                val isClosing = line.startsWith("</")
                if (isClosing) indent = (indent - 1).coerceAtLeast(0)
                val indented = "  ".repeat(indent) + line

                val isOpeningTag = line.startsWith("<") && !line.startsWith("</")
                val isSelfClosing = line.endsWith("/>") || selfClosing.matches(line)
                val opensAndClosesSameLine = isOpeningTag && line.contains("</")
                if (isOpeningTag && !isSelfClosing && !opensAndClosesSameLine) indent++

                indented
            }
    }
}
