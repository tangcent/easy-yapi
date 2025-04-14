package com.itangcent.logger

import com.google.inject.Inject
import com.google.inject.Provider
import com.google.inject.Singleton
import com.itangcent.idea.plugin.settings.helper.CommonSettingsHelper
import com.itangcent.idea.plugin.settings.helper.CommonSettingsHelper.LoggerConsoleType
import com.itangcent.intellij.logger.IdeaConsoleLogger
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.logger.MultiContentConsoleLogger
import com.itangcent.intellij.logger.MultiToolWindowConsoleLogger

/**
 * Provider for Logger instances that dynamically selects between different logger implementations.
 * This class uses the CommonSettingsHelper to determine which logger implementation to provide
 * based on the loggerType setting.
 */
@Singleton
class LoggerProvider : Provider<Logger> {

    @Inject
    private lateinit var commonSettingsHelper: CommonSettingsHelper

    @Inject
    private lateinit var ideaConsoleLoggerProvider: Provider<IdeaConsoleLogger>

    @Inject
    private lateinit var multiContentConsoleLoggerProvider: Provider<MultiContentConsoleLogger>

    @Inject
    private lateinit var multiToolWindowConsoleLoggerProvider: Provider<MultiToolWindowConsoleLogger>

    /**
     * Creates and returns a Logger instance based on configuration.
     *
     * @return A Logger implementation based on the selected logger type in settings
     */
    override fun get(): Logger {
        val logger = when (commonSettingsHelper.loggerConsoleType()) {
            LoggerConsoleType.MULTI_CONTENT -> multiContentConsoleLoggerProvider.get()
            LoggerConsoleType.MULTI_TOOL_WINDOW -> multiToolWindowConsoleLoggerProvider.get()
            LoggerConsoleType.SINGLE_CONSOLE -> ideaConsoleLoggerProvider.get()
        }

        return ConfigurableLogger(
            delegateLogger = logger,
            loggerLevel = commonSettingsHelper.currentLogLevel().level
        )
    }
}