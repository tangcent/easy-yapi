package com.itangcent.idea.plugin.settings.helper

import com.google.inject.Inject
import com.itangcent.idea.plugin.settings.helper.CommonSettingsHelper.LoggerConsoleType
import com.itangcent.idea.plugin.settings.helper.CommonSettingsHelper.VerbosityLevel
import com.itangcent.idea.utils.Charsets
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

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
        for (level in VerbosityLevel.entries) {
            settings.logLevel = level.level
            assertEquals(level.level, commonSettingsHelper.logLevel())
        }
    }

    @Test
    fun testCurrentLogLevel() {
        settings.logLevel = VerbosityLevel.QUIET.level
        assertEquals(VerbosityLevel.QUIET, commonSettingsHelper.currentLogLevel())
        settings.logLevel = VerbosityLevel.VERBOSE.level
        assertEquals(VerbosityLevel.VERBOSE, commonSettingsHelper.currentLogLevel())

        for (level in VerbosityLevel.entries) {
            settings.logLevel = level.level
            assertEquals(level, commonSettingsHelper.currentLogLevel())
        }
    }
    
    @Test
    fun testLoggerConsoleType() {
        for (consoleType in LoggerConsoleType.entries) {
            settings.loggerConsoleType = consoleType.name
            assertEquals(consoleType, commonSettingsHelper.loggerConsoleType())
        }
        
        // Test fallback to default when invalid type is provided
        settings.loggerConsoleType = "INVALID_TYPE"
        assertEquals(LoggerConsoleType.MULTI_CONTENT, commonSettingsHelper.loggerConsoleType())
    }
}