package com.itangcent.logger

import com.itangcent.annotation.script.ScriptIgnore
import com.itangcent.annotation.script.ScriptTypeName
import com.itangcent.intellij.logger.Level
import com.itangcent.intellij.logger.Logger

@ScriptIgnore("processLog", "log")
@ScriptTypeName("logger")
class ConfigurableLogger(
    private val delegateLogger: Logger,
    private val loggerLevel: Int
) : Logger {

    override fun log(level: Level, msg: String) {
        if (level.level <= loggerLevel) {
            return
        }
        delegateLogger.log(level, msg)
    }
}