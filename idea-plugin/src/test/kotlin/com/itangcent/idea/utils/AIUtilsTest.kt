package com.itangcent.idea.utils

import com.itangcent.idea.plugin.utils.AIUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Test cases for [AIUtils]
 */
class AIUtilsTest {

    @Test
    fun testCleanMarkdownCodeBlocksWithLanguage() {
        val input = """
            ```kotlin
            fun test() {
                println("Hello, World!")
            }
            ```
        """.trimIndent()
        
        val expected = """
            fun test() {
                println("Hello, World!")
            }
        """.trimIndent()
        
        assertEquals(expected, AIUtils.cleanMarkdownCodeBlocks(input))
    }
    
    @Test
    fun testCleanMarkdownCodeBlocksWithoutLanguage() {
        val input = """
            ```
            This is a code block without language specification
            ```
        """.trimIndent()
        
        val expected = "This is a code block without language specification"
        
        assertEquals(expected, AIUtils.cleanMarkdownCodeBlocks(input))
    }
    
    @Test
    fun testCleanMarkdownCodeBlocksWithoutNewlineBeforeClosingDelimiter() {
        val input = "```kotlin\nval x = 10\n```"
        val expected = "val x = 10"
        
        assertEquals(expected, AIUtils.cleanMarkdownCodeBlocks(input))
    }
    
    @Test
    fun testCleanMarkdownCodeBlocksWithContentNotInCodeBlock() {
        val input = "This is not in a code block"
        val expected = "This is not in a code block"
        
        assertEquals(expected, AIUtils.cleanMarkdownCodeBlocks(input))
    }
    
    @Test
    fun testCleanMarkdownCodeBlocksWithMixedContent() {
        val input = """
            Some text before
            
            ```kotlin
            val x = 10
            ```
            
            Some text after
        """.trimIndent()
        
        // The function should only clean if the entire content is a code block
        assertEquals(input, AIUtils.cleanMarkdownCodeBlocks(input))
    }
    
    @Test
    fun testCleanMarkdownCodeBlocksWithEmptyContent() {
        val input = ""
        val expected = ""
        
        assertEquals(expected, AIUtils.cleanMarkdownCodeBlocks(input))
    }
    
    @Test
    fun testCleanMarkdownCodeBlocksWithOnlyDelimiters() {
        val input = "``````"
        val expected = ""
        
        assertEquals(expected, AIUtils.cleanMarkdownCodeBlocks(input))
    }
} 