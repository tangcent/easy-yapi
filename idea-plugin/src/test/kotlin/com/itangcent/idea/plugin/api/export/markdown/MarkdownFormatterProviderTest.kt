package com.itangcent.idea.plugin.api.export.markdown

import com.google.inject.Inject
import com.itangcent.mock.AdvancedContextTest
import com.itangcent.testFramework.sub
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

/**
 * Test case of [MarkdownFormatterProvider]
 */
internal abstract class MarkdownFormatterProviderTest : AdvancedContextTest() {

    @Inject
    protected lateinit var markdownFormatterProvider: MarkdownFormatterProvider

    class DefaultFormatterTest : MarkdownFormatterProviderTest() {
        override fun customConfig(): String? {
            return null // No template configured
        }

        @Test
        fun testGetDefaultFormatter() {
            val formatter = markdownFormatterProvider.get()
            assertIs<DefaultMarkdownFormatter>(formatter)
        }
    }

    class MixFormatterTest : MarkdownFormatterProviderTest() {

        override fun customConfig(): String? {
            val templateFile = tempDir!!.sub("template.md")
            templateFile.createNewFile()
            templateFile.writeText("# template")
            return """
                api.template=${templateFile.path}
            """.trimIndent()
        }

        @Test
        fun testGetMixFormatter() {
            val formatter = markdownFormatterProvider.get()
            assertIs<MixMarkdownFormatter>(formatter)
        }
    }

    class NonExistentTemplateTest : MarkdownFormatterProviderTest() {

        override fun customConfig(): String? {
            return """
                api.template=/path/to/nonexistent/template.md
            """.trimIndent()
        }

        @Test
        fun testGetDefaultFormatterWhenTemplateNotExist() {
            val formatter = markdownFormatterProvider.get()
            assertIs<DefaultMarkdownFormatter>(formatter)
        }
    }
} 