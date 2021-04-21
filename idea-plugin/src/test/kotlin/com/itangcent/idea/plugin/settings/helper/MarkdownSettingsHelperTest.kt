package com.itangcent.idea.plugin.settings.helper

import com.google.inject.Inject
import com.itangcent.idea.plugin.settings.MarkdownFormatType
import com.itangcent.idea.utils.Charsets
import org.junit.jupiter.api.Test

/**
 * Test case of [MarkdownSettingsHelper]
 */
internal class MarkdownSettingsHelperTest : SettingsHelperTest() {

    @Inject
    private lateinit var markdownSettingsHelper: MarkdownSettingsHelper

    @Test
    fun testOutputCharset() {
        for (charset in Charsets.SUPPORTED_CHARSETS) {
            settings.outputCharset = charset.displayName()
            kotlin.test.assertEquals(charset.charset(), markdownSettingsHelper.outputCharset())
        }

        settings.outputCharset = "invalid charset"
        kotlin.test.assertEquals(kotlin.text.Charsets.UTF_8, markdownSettingsHelper.outputCharset())
    }

    @Test
    fun testOutputDemo() {
        settings.outputDemo = false
        kotlin.test.assertFalse(markdownSettingsHelper.outputDemo())
        settings.outputDemo = true
        kotlin.test.assertTrue(markdownSettingsHelper.outputDemo())
    }

    @Test
    fun testMarkdownFormatType() {
        settings.markdownFormatType = MarkdownFormatType.SIMPLE.name
        kotlin.test.assertEquals(MarkdownFormatType.SIMPLE,
                markdownSettingsHelper.markdownFormatType())

        settings.markdownFormatType = MarkdownFormatType.ULTIMATE.name
        kotlin.test.assertEquals(MarkdownFormatType.ULTIMATE,
                markdownSettingsHelper.markdownFormatType())

    }
}