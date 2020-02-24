package com.itangcent.idea.utils

import com.google.inject.Inject
import com.google.inject.name.Named
import com.itangcent.annotation.script.ScriptIgnore
import com.itangcent.annotation.script.ScriptTypeName
import com.itangcent.annotation.script.ScriptUnIgnore
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.intellij.extend.guice.PostConstruct
import com.itangcent.intellij.logger.AbstractLogger
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.logger.Logger.Level

@ScriptIgnore("processLog", "log")
@ScriptTypeName("logger")
class ConfigurableLogger : AbstractLogger() {

    @Inject(optional = true)
    @Named("delegate.logger")
    private var delegateLogger: Logger? = null

    @Inject(optional = true)
    private val settingBinder: SettingBinder? = null

    private var currentLogLevel: Level? = null

    @PostConstruct
    @ScriptIgnore
    fun init() {
        val logLevel: Int? = settingBinder?.read()?.logLevel
        currentLogLevel = logLevel?.let { CoarseLogLevel.toLevel(it, CoarseLogLevel.LOW) } ?: CoarseLogLevel.LOW
    }

    @ScriptUnIgnore
    override fun log(msg: String) {
        super.log(CoarseLogLevel.EMPTY, msg)
    }

    @ScriptIgnore
    override fun currentLogLevel(): Level {
        return currentLogLevel ?: CoarseLogLevel.LOW
    }

    @ScriptIgnore
    override fun processLog(level: Level, msg: String) {
        delegateLogger!!.log(level, msg)
    }

    @ScriptIgnore
    override fun processLog(logData: String?) {
        //This method will not be called
        throw IllegalArgumentException("ConfigurableLogger#processLog not be implemented")
    }

    enum class CoarseLogLevel : Level {
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

            fun toLevel(level: Int): Level {
                return toLevel(level, LOW)
            }

            fun toLevel(level: Int, defaultLevel: Level): Level {
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