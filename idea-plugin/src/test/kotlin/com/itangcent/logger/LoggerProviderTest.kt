package com.itangcent.logger

import com.google.inject.Inject
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.idea.plugin.settings.helper.CommonSettingsHelper
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.logger.IdeaConsoleLogger
import com.itangcent.intellij.logger.MultiContentConsoleLogger
import com.itangcent.intellij.logger.MultiToolWindowConsoleLogger
import com.itangcent.mock.AdvancedContextTest
import com.itangcent.mock.SettingBinderAdaptor
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

/**
 * Test case of [LoggerProvider]
 */
internal abstract class LoggerProviderTest : AdvancedContextTest() {

    @Inject
    protected lateinit var loggerProvider: LoggerProvider

    val settings = Settings()

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        builder.bind(SettingBinder::class) {
            it.toInstance(SettingBinderAdaptor(settings))
        }
    }

    class MultiContentLoggerTest : LoggerProviderTest() {
        override fun beforeBind() {
            settings.loggerConsoleType = CommonSettingsHelper.LoggerConsoleType.MULTI_CONTENT.name
            settings.logLevel = CommonSettingsHelper.VerbosityLevel.NORMAL.level
        }

        @Test
        fun testGetMultiContentLogger() {
            val logger = loggerProvider.get()
            assertIs<ConfigurableLogger>(logger)
            val delegateLogger =
                logger::class.java.getDeclaredField("delegateLogger").apply { isAccessible = true }.get(logger)
            assertIs<MultiContentConsoleLogger>(delegateLogger)
        }
    }

    class MultiToolWindowLoggerTest : LoggerProviderTest() {
        override fun beforeBind() {
            settings.loggerConsoleType = CommonSettingsHelper.LoggerConsoleType.MULTI_TOOL_WINDOW.name
            settings.logLevel = CommonSettingsHelper.VerbosityLevel.NORMAL.level
        }

        @Test
        fun testGetMultiToolWindowLogger() {
            val logger = loggerProvider.get()
            assertIs<ConfigurableLogger>(logger)
            val delegateLogger =
                logger::class.java.getDeclaredField("delegateLogger").apply { isAccessible = true }.get(logger)
            assertIs<MultiToolWindowConsoleLogger>(delegateLogger)
        }
    }

    class SingleConsoleLoggerTest : LoggerProviderTest() {
        override fun beforeBind() {
            settings.loggerConsoleType = CommonSettingsHelper.LoggerConsoleType.SINGLE_CONSOLE.name
            settings.logLevel = CommonSettingsHelper.VerbosityLevel.NORMAL.level
        }

        @Test
        fun testGetSingleConsoleLogger() {
            val logger = loggerProvider.get()
            assertIs<ConfigurableLogger>(logger)
            val delegateLogger =
                logger::class.java.getDeclaredField("delegateLogger").apply { isAccessible = true }.get(logger)
            assertIs<IdeaConsoleLogger>(delegateLogger)
        }
    }

    class LogLevelTest : LoggerProviderTest() {
        override fun beforeBind() {
            settings.loggerConsoleType = CommonSettingsHelper.LoggerConsoleType.SINGLE_CONSOLE.name
            settings.logLevel = CommonSettingsHelper.VerbosityLevel.VERBOSE.level
        }

        @Test
        fun testLogLevelConfig() {
            val logger = loggerProvider.get()
            assertIs<ConfigurableLogger>(logger)
            val loggerLevel =
                logger::class.java.getDeclaredField("loggerLevel").apply { isAccessible = true }.get(logger)
            assert(loggerLevel == CommonSettingsHelper.VerbosityLevel.VERBOSE.level)
        }
    }
}