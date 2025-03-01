package com.itangcent.idea.utils

import com.itangcent.debug.LoggerCollector
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.idea.plugin.settings.helper.CommonSettingsHelper
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.logger.Logger
import com.itangcent.mock.BaseContextTest
import com.itangcent.mock.SettingBinderAdaptor
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals

/**
 * Test case of [ConfigurableLogger]
 */
internal class ConfigurableLoggerTest : BaseContextTest() {

    private val settings = Settings()

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        builder.bind(Logger::class) { it.with(ConfigurableLogger::class) }
        builder.bind(Logger::class, "delegate.logger") { it.with(LoggerCollector::class).singleton() }
        builder.bind(SettingBinder::class) { it.toInstance(SettingBinderAdaptor(settings)) }
    }

    @ParameterizedTest
    @CsvSource(
        "EMPTY,[TRACE]\ttrace[DEBUG]\tdebug[INFO]\tinfo[WARN]\twarn[ERROR]\terrorlog",
        "LOW,[TRACE]\ttrace[DEBUG]\tdebug[INFO]\tinfo[WARN]\twarn[ERROR]\terrorlog",
        "MEDIUM,[INFO]\tinfo[WARN]\twarn[ERROR]\terrorlog",
        "HIGH,[ERROR]\terrorlog",
    )
    fun testLog(level: CommonSettingsHelper.CoarseLogLevel, output: String) {
        settings.logLevel = level.getLevel()
        (logger as ConfigurableLogger).init()
        logger.trace("trace")
        logger.debug("debug")
        logger.info("info")
        logger.warn("warn")
        logger.error("error")
        logger.log("log")
        assertEquals(output, LoggerCollector.getLog().replace("\n", ""))
    }
}