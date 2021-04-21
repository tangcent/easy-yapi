package com.itangcent.idea.plugin.settings.helper

import com.google.inject.Inject
import com.itangcent.idea.utils.Charsets
import com.itangcent.intellij.logger.Logger
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Test case of [CommonSettingsHelper]
 */
internal class CommonSettingsHelperTest : SettingsHelperTest() {

    @Inject
    private lateinit var commonSettingsHelper: CommonSettingsHelper

    @Test
    fun testOutputCharset() {
        for (charset in Charsets.SUPPORTED_CHARSETS) {
            settings.outputCharset = charset.displayName()
            assertEquals(charset.charset(), commonSettingsHelper.outputCharset())
        }
    }

    @Test
    fun testLogLevel() {
        for (level in Logger.BasicLevel.values()) {
            settings.logLevel = level.getLevel()
            assertEquals(level.getLevel(), commonSettingsHelper.logLevel())
        }
    }

    @Test
    fun testCurrentLogLevel() {
        settings.logLevel = CommonSettingsHelper.CoarseLogLevel.LOW.getLevel()
        assertEquals(CommonSettingsHelper.CoarseLogLevel.LOW, commonSettingsHelper.currentLogLevel())
        settings.logLevel = CommonSettingsHelper.CoarseLogLevel.HIGH.getLevel()
        assertEquals(CommonSettingsHelper.CoarseLogLevel.HIGH, commonSettingsHelper.currentLogLevel())

        for (level in Logger.BasicLevel.values()) {
            settings.logLevel = level.getLevel()
            assertEquals(level, commonSettingsHelper.currentLogLevel())
        }
    }

    @Test
    fun testCoarseLogLevel() {
        val editableValues = CommonSettingsHelper.CoarseLogLevel.editableValues()
        assertFalse(editableValues.contains(CommonSettingsHelper.CoarseLogLevel.EMPTY))

        val helper: CommonSettingsHelper? = null
        assertEquals(CommonSettingsHelper.CoarseLogLevel.LOW, helper.currentLogLevel())
    }
}