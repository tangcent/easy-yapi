package com.itangcent.idea.plugin.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Test cases for [AIUtils]
 */
class AIUtilsTest {

    @Test
    fun testCleanMarkdownCodeBlocks() {
        // Test with no code blocks
        assertEquals("plain text", AIUtils.cleanMarkdownCodeBlocks("plain text"))
        assertEquals("This is not in a code block", AIUtils.cleanMarkdownCodeBlocks("This is not in a code block"))

        // Test with simple code block
        assertEquals("code content", AIUtils.cleanMarkdownCodeBlocks("```\ncode content\n```"))
        assertEquals(
            "This is a code block without language specification",
            AIUtils.cleanMarkdownCodeBlocks("```\nThis is a code block without language specification\n```")
        )

        // Test with language identifier
        assertEquals("java code", AIUtils.cleanMarkdownCodeBlocks("```java\njava code\n```"))
        assertEquals(
            """
            fun test() {
                println("Hello, World!")
            }
        """.trimIndent(), AIUtils.cleanMarkdownCodeBlocks(
                """
            ```kotlin
            fun test() {
                println("Hello, World!")
            }
            ```
        """.trimIndent()
            )
        )

        // Test with extra whitespace
        assertEquals("code content", AIUtils.cleanMarkdownCodeBlocks("  ```\n  code content\n  ```  "))

        // Test with no newline after opening delimiter
        assertEquals("content", AIUtils.cleanMarkdownCodeBlocks("```code content\n```"))

        // Test with no newline before closing delimiter
        assertEquals("code content", AIUtils.cleanMarkdownCodeBlocks("```\ncode content```"))
        assertEquals("val x = 10", AIUtils.cleanMarkdownCodeBlocks("```kotlin\nval x = 10\n```"))

        // Test with mixed content (should not clean if not entire content is a code block)
        val mixedContent = """
            Some text before
            
            ```kotlin
            val x = 10
            ```
            
            Some text after
        """.trimIndent()
        assertEquals(mixedContent, AIUtils.cleanMarkdownCodeBlocks(mixedContent))

        // Test with empty content
        assertEquals("", AIUtils.cleanMarkdownCodeBlocks(""))

        // Test with only delimiters
        assertEquals("", AIUtils.cleanMarkdownCodeBlocks("``````"))
    }

    @Test
    fun testExtractFirstCodeBlock() {
        // Test with no code blocks
        assertNull(AIUtils.extractFirstCodeBlock("plain text"))

        // Test with single code block
        assertEquals("code content", AIUtils.extractFirstCodeBlock("```\ncode content\n```"))

        // Test with single code block
        assertEquals("code content", AIUtils.extractFirstCodeBlock("```text\ncode content\n```"))

        // Test with multiple code blocks
        assertEquals(
            "first block", AIUtils.extractFirstCodeBlock(
                """
            ```
            first block
            ```
            ```
            second block
            ```
        """.trimIndent()
            )
        )

        // Test with language identifier
        assertEquals("java code", AIUtils.extractFirstCodeBlock("```java\njava code\n```"))

        // Test with text before and after code block
        assertEquals(
            "code content", AIUtils.extractFirstCodeBlock(
                """
            some text
            ```
            code content
            ```
            more text
        """.trimIndent()
            )
        )

        // Test with content on same line as delimiters
        assertEquals("content", AIUtils.extractFirstCodeBlock("```content```"))

        // Test with no newline after opening delimiter
        assertEquals("", AIUtils.extractFirstCodeBlock("```content\n```"))

        // Test with no newline before closing delimiter
        assertEquals("code content", AIUtils.extractFirstCodeBlock("```\ncode content```"))

        // Test with language identifier and no newline after opening delimiter (should not match)
        assertNull(AIUtils.extractFirstCodeBlock("```java content\n```"))

        // Test with both single-line and multi-line code blocks (should get multi-line)
        assertEquals(
            "multi line content", AIUtils.extractFirstCodeBlock(
                """
                ```single line content```
                ```text
                multi line content
                ```
                """.trimIndent()
            )
        )

        // Test with both single-line and multi-line code blocks in reverse order (should get multi-line)
        assertEquals(
            "multi line content", AIUtils.extractFirstCodeBlock(
                """
            ```text
            multi line content
            ```
            ```single line content```
        """.trimIndent()
            )
        )

        // Test with multiple single-line and one multi-line code block (should get multi-line)
        assertEquals(
            "multi line content", AIUtils.extractFirstCodeBlock(
                """
            ```first single line```
            ```second single line```
            ```text
            multi line content
            ```
            ```third single line```
        """.trimIndent()
            )
        )
    }

    @Test
    fun testExtractFirstCodeBlockByType() {
        // Test with no code blocks
        assertNull(AIUtils.extractFirstCodeBlockByType("plain text", "json"))

        // Test with matching language type
        assertEquals(
            "{\"key\": \"value\"}",
            AIUtils.extractFirstCodeBlockByType("```json\n{\"key\": \"value\"}\n```", "json")
        )

        // Test with non-matching language type
        assertNull(AIUtils.extractFirstCodeBlockByType("```java\nSystem.out.println();\n```", "json"))

        // Test with multiple code blocks
        assertEquals(
            "{\"first\": true}",
            AIUtils.extractFirstCodeBlockByType(
                """
                ```json
                {"first": true}
                ```
                ```json
                {"second": true}
                ```
            """.trimIndent(), "json"
            )
        )

        // Test with text before and after code block
        assertEquals(
            "System.out.println();",
            AIUtils.extractFirstCodeBlockByType(
                """
                some text
                ```java
                System.out.println();
                ```
                more text
            """.trimIndent(), "java"
            )
        )

        // Test with case sensitivity
        assertNull(AIUtils.extractFirstCodeBlockByType("```JSON\n{}\n```", "json"))

        // Test with content on same line as delimiters
        assertEquals("content", AIUtils.extractFirstCodeBlockByType("```json\ncontent\n```", "json"))
    }

    @Test
    fun testGetGeneralContent() {
        // Test with no code blocks
        assertEquals("plain text", AIUtils.getGeneralContent("plain text"))
        assertEquals("This is not in a code block", AIUtils.getGeneralContent("This is not in a code block"))

        // Test with simple code block
        assertEquals("code content", AIUtils.getGeneralContent("```\ncode content\n```"))
        assertEquals(
            "This is a code block without language specification",
            AIUtils.getGeneralContent("```\nThis is a code block without language specification\n```")
        )

        // Test with language identifier
        assertEquals("java code", AIUtils.getGeneralContent("```java\njava code\n```"))
        assertEquals(
            """
            fun test() {
                println("Hello, World!")
            }
        """.trimIndent(), AIUtils.getGeneralContent(
                """
            ```kotlin
            fun test() {
                println("Hello, World!")
            }
            ```
        """.trimIndent()
            )
        )

        // Test with mixed content (should get first code block)
        assertEquals(
            "first block", AIUtils.getGeneralContent(
                """
            some text
            ```
            first block
            ```
            ```
            second block
            ```
            more text
        """.trimIndent()
            )
        )

        // Test with content on same line as delimiters
        assertEquals("content", AIUtils.getGeneralContent("```content```"))

        // Test with no newline after opening delimiter
        assertEquals("", AIUtils.getGeneralContent("```content\n```"))

        // Test with no newline before closing delimiter
        assertEquals("code content", AIUtils.getGeneralContent("```\ncode content```"))

        // Test with empty content
        assertEquals("", AIUtils.getGeneralContent(""))

        // Test with only delimiters
        assertEquals("``````", AIUtils.getGeneralContent("``````"))
    }
} 