package com.itangcent.idea.plugin.settings.helper

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.utils.Charsets
import java.nio.charset.Charset

@Singleton
class CommonSettingsHelper {

    @Inject
    private lateinit var settingBinder: SettingBinder

    fun outputCharset(): Charset {
        return Charsets.forName(settingBinder.read().outputCharset)?.charset() ?: kotlin.text.Charsets.UTF_8
    }

    fun logLevel(): Int {
        return settingBinder.read().logLevel
    }

    fun currentLogLevel(): VerbosityLevel {
        return VerbosityLevel.toLevel(logLevel())
    }
    
    /**
     * Gets the logger type based on settings.
     */
    fun loggerConsoleType(): LoggerConsoleType {
        val typeStr = settingBinder.read().loggerConsoleType
        return try {
            LoggerConsoleType.valueOf(typeStr)
        } catch (e: IllegalArgumentException) {
            // Fallback to default if the stored value is invalid
            LoggerConsoleType.MULTI_CONTENT
        }
    }

    enum class VerbosityLevel {
        VERBOSE(50),
        NORMAL(250),
        QUIET(450)
        ;

        val level: Int

        constructor(level: Int) {
            this.level = level
        }

        companion object {

            fun toLevel(level: Int): VerbosityLevel {
                return VerbosityLevel.entries.firstOrNull { it.level == level } ?: NORMAL
            }
        }
    }
    
    /**
     * Enum representing different types of loggers available in the application.
     */
    enum class LoggerConsoleType {
        /**
         * A single console logger that outputs all logs to one console
         */
        SINGLE_CONSOLE,
        
        /**
         * A multi-content console logger that organizes logs in multiple content tabs
         */
        MULTI_CONTENT,
        
        /**
         * A multi-tool window console logger that displays logs in separate tool windows
         */
        MULTI_TOOL_WINDOW
    }
}
