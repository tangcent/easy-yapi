package com.itangcent.idea.utils

import com.google.inject.Inject
import com.google.inject.name.Named
import com.itangcent.annotation.script.ScriptIgnore
import com.itangcent.annotation.script.ScriptTypeName
import com.itangcent.annotation.script.ScriptUnIgnore
import com.itangcent.idea.plugin.settings.helper.CommonSettingsHelper
import com.itangcent.intellij.logger.AbstractLogger
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.logger.Logger.Level

@ScriptIgnore("processLog", "log")
@ScriptTypeName("logger")
class ConfigurableLogger : AbstractLogger() {

    @Inject(optional = true)
    @Named("delegate.logger")
    private var delegateLogger: Logger? = null

    @Inject
    private lateinit var commonSettingsHelper: CommonSettingsHelper

    private val currentLogLevel: Level by lazy {
        commonSettingsHelper.currentLogLevel()
    }

    @ScriptUnIgnore
    override fun log(msg: String) {
        super.log(CommonSettingsHelper.VerbosityLevel.EMPTY, msg)
    }

    @ScriptIgnore
    override fun currentLogLevel(): Level {
        return currentLogLevel
    }

    @ScriptIgnore
    override fun processLog(level: Level, msg: String) {
        delegateLogger!!.log(level, msg)
    }

    @ScriptIgnore
    override fun processLog(logData: String?) {
        //This method will not be called
        throw NotImplementedError("ConfigurableLogger#processLog not be implemented")
    }
}