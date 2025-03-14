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
} 