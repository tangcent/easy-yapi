package com.itangcent.idea.plugin.settings.helper

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.utils.Charsets
import com.itangcent.intellij.logger.Logger
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

    fun currentLogLevel(): Logger.Level {
        val logLevel: Int = logLevel()
        return logLevel.let { CoarseLogLevel.toLevel(it) }
    }

    enum class CoarseLogLevel : Logger.Level {
        EMPTY(1000) {
            override fun getLevelStr(): String {
                return ""
            }
        },
        LOW(50),
        MEDIUM(250),
        HIGH(450)
        ;

        private val level: Int

        constructor(level: Int) {
            this.level = level
        }

        override fun getLevelStr(): String {
            throw UnsupportedOperationException("CoarseLogLevel only be used as level")
        }

        override fun getLevel(): Int {
            return level
        }

        override fun toString(): String {
            return name
        }

        companion object {

            fun toLevel(level: Int): Logger.Level {
                return toLevel(level, LOW)
            }

            fun toLevel(level: Int, defaultLevel: Logger.Level): Logger.Level {
                return when (level) {
                    LOW.level -> LOW
                    MEDIUM.level -> MEDIUM
                    HIGH.level -> HIGH
                    else -> Logger.BasicLevel.toLevel(level, defaultLevel)
                }
            }

            fun editableValues(): Array<CoarseLogLevel> {
                return values().filter { it != EMPTY }.toTypedArray()
            }
        }
    }
}

fun CommonSettingsHelper?.currentLogLevel(): Logger.Level {
    return this?.currentLogLevel() ?: CommonSettingsHelper.CoarseLogLevel.LOW
}
