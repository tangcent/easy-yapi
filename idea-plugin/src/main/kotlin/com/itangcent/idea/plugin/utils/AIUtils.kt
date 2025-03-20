package com.itangcent.idea.plugin.utils

/**
 * Utility functions for AI-related operations
 */
object AIUtils {

    /**
     * Cleans AI response content by removing markdown code block delimiters
     *
     * @param content The raw content from AI response
     * @return Cleaned content without markdown code block delimiters
     */
    fun cleanMarkdownCodeBlocks(content: String): String {
        val trimmedContent = content.trim()

        // Check if content is surrounded by code block delimiters
        if (trimmedContent.startsWith("```") && trimmedContent.endsWith("```")) {
            // Remove starting delimiter (with optional language identifier)
            val withoutStart = trimmedContent.replaceFirst(Regex("^```\\w*", RegexOption.MULTILINE), "")

            // Remove ending delimiter - handles both with and without newline
            val result = withoutStart.replace(Regex("(\\n)?```$", RegexOption.MULTILINE), "")

            // Trim any leading/trailing whitespace
            return result.trim()
        }

        return trimmedContent
    }

    /**
     * Extracts the first code block from content, including the language identifier if present
     *
     * @param content The content that may contain one or more code blocks
     * @return The first code block found, or null if no code block is found
     */
    fun extractFirstCodeBlock(content: String): String? {
        // First try to find a multi-line code block
        val multiLinePattern = Regex("^```\\w*\\n([\\s\\S]*?)```$", RegexOption.MULTILINE)
        val multiLineMatch = multiLinePattern.find(content)
        if (multiLineMatch != null) {
            return multiLineMatch.groupValues[1].trim()
        }

        // If no multi-line block found, try single-line
        val singleLinePattern = Regex("```([^\\n]+?)```", RegexOption.MULTILINE)
        val singleLineMatch = singleLinePattern.find(content)
        if (singleLineMatch != null) {
            return singleLineMatch.groupValues[1].trim()
        }

        return null
    }

    /**
     * Extracts the first code block of a specific language type from content
     *
     * @param content The content that may contain one or more code blocks
     * @param languageType The specific language type to look for (e.g., "json", "java", "kotlin")
     * @return The first code block found with the specified language type, or null if no matching code block is found
     */
    fun extractFirstCodeBlockByType(content: String, languageType: String): String? {
        val codeBlockPattern = Regex("^```$languageType\\n([\\s\\S]*?)```$", RegexOption.MULTILINE)
        return codeBlockPattern.find(content)?.groupValues?.get(1)?.trim()
    }

    /**
     * Gets the general content by first trying to extract a code block.
     * If no code block is found, returns the input content.
     *
     * @param content The content that may contain code blocks
     * @return The extracted code block content if found, otherwise the original content
     */
    fun getGeneralContent(content: String): String {
        return extractFirstCodeBlock(content) ?: content
    }
} 