package com.itangcent.plugin.adapter

import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import kotlin.reflect.full.functions

/**
 * see https://github.com/JetBrains/markdown
 */
class MarkdownHtmlGenerator {

    private val commonMarkFlavourDescriptor by lazy { commonMarkFlavourDescriptorBuilder() }

    fun render(markdown: String): String? {
        if (markdown.isBlank()) {
            return null
        }
        val astNode = MarkdownParser(commonMarkFlavourDescriptor).buildMarkdownTreeFromString(markdown)
        val htmlGenerator = HtmlGenerator(markdown, astNode, commonMarkFlavourDescriptor, false)
        val generateHtml = generateHtmlCall(htmlGenerator)
        return generateHtml
            .removePrefix("<body>")
            .removeSuffix("</body>")
            .takeIf { it.isNotBlank() }
    }

    companion object {
        val generateHtmlCall: (HtmlGenerator) -> String by lazy { findAdaptedGenerateHtmlCall() }

        private fun findAdaptedGenerateHtmlCall(): (HtmlGenerator) -> String {
            val functions = HtmlGenerator::class.functions
            for (function in functions) {
                if (function.name == "generateHtml" && function.parameters.size == 1) {
                    return { function.call(it) as? String ?: "" }
                }
            }
            return { it.generateHtml() }
        }

        val commonMarkFlavourDescriptorBuilder: () -> CommonMarkFlavourDescriptor by lazy {
            findAdaptedCommonMarkFlavourDescriptor()
        }

        private fun findAdaptedCommonMarkFlavourDescriptor(): () -> CommonMarkFlavourDescriptor {
            val constructors = CommonMarkFlavourDescriptor::class.constructors
            for (constructor in constructors) {
                if (constructor.parameters.isEmpty()) {
                    return { constructor.call() }
                }
            }
            return { CommonMarkFlavourDescriptor() }
        }
    }
}