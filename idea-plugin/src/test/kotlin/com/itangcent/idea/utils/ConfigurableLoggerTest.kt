package com.itangcent.idea.utils

import com.itangcent.debug.LoggerCollector
import com.itangcent.idea.plugin.settings.helper.CommonSettingsHelper
import com.itangcent.logger.ConfigurableLogger
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals

/**
 * Test case of [com.itangcent.logger.ConfigurableLogger]
 */
internal class ConfigurableLoggerTest {

    @ParameterizedTest
    @CsvSource(
        "VERBOSE,[TRACE]\ttrace[DEBUG]\tdebug[INFO]\tinfo[WARN]\twarn[ERROR]\terror[INFO]\tlog",
        "NORMAL,[INFO]\tinfo[WARN]\twarn[ERROR]\terror[INFO]\tlog",
        "QUIET,[ERROR]\terror",
    )
    fun testLog(level: CommonSettingsHelper.VerbosityLevel, output: String) {
        val logger = ConfigurableLogger(
            LoggerCollector(),
            level.level
        )
        logger.trace("trace")
        logger.debug("debug")
        logger.info("info")
        logger.warn("warn")
        logger.error("error")
        logger.log("log")
        assertEquals(output, LoggerCollector.getLog().replace("\n", ""))
    }
}