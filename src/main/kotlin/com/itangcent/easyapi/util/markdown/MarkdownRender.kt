package com.itangcent.easyapi.util.markdown

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * Renders Markdown text to HTML.
 *
 * Used primarily when exporting API documentation to platforms like YApi
 * that expect HTML content for descriptions. The raw Markdown is preserved
 * alongside the rendered HTML so that consumers can choose which format to use.
 *
 * Typical usage:
 * ```kotlin
 * val render = MarkdownRender.getInstance(project)
 * val html = render.render("# Hello") // "<h1>Hello</h1>"
 * ```
 */
interface MarkdownRender {
    /**
     * Renders the given [markdown] string to HTML.
     *
     * Implementations should return an empty string when the input is blank
     * rather than throwing an exception.
     *
     * @param markdown The Markdown text to render
     * @return The rendered HTML string, or an empty string if the input is blank
     */
    fun render(markdown: String): String

    companion object {
        /**
         * Returns the MarkdownRender service for the given project.
         *
         * This returns the [BundledMarkdownRender] implementation which uses
         * the IntelliJ Platform's built-in Markdown parser.
         *
         * @param project The IntelliJ project context
         * @return The MarkdownRender instance for this project
         */
        fun getInstance(project: Project): MarkdownRender = project.service()
    }
}
