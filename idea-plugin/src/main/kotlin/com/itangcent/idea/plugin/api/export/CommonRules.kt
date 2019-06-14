package com.itangcent.idea.plugin.api.export

import com.google.inject.Inject
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.config.SimpleBooleanRule
import com.itangcent.intellij.config.SimpleRuleParser
import com.itangcent.intellij.config.SimpleStringRule
import com.itangcent.intellij.logger.Logger
import java.util.*

class CommonRules {

    @Inject
    protected val logger: Logger? = null

    @Inject
    protected val configReader: ConfigReader? = null

    @Inject
    protected val simpleRuleParser: SimpleRuleParser? = null

    //region moduleRules--------------------------------------------------------
    var moduleRules: ArrayList<SimpleStringRule>? = null

    fun readModuleRules(): List<SimpleStringRule> {
        if (moduleRules != null) return moduleRules!!
        moduleRules = ArrayList()

        configReader!!.foreach({ key ->
            key.startsWith("module")
        }, { key, value ->
            try {
                moduleRules!!.addAll(simpleRuleParser!!.parseStringRule(value))
            } catch (e: Exception) {
                logger!!.error("error to parse module rule:$key=$value")
            }
        })

        return moduleRules!!
    }

    //endregion moduleRules--------------------------------------------------------

    //region ignoreRules--------------------------------------------------------
    var ignoreRules: ArrayList<SimpleBooleanRule>? = null

    fun readIgnoreRules(): List<SimpleBooleanRule> {
        if (ignoreRules != null) return ignoreRules!!
        ignoreRules = ArrayList()

        configReader!!.foreach({ key ->
            key.startsWith("ignore")
        }, { key, value ->
            try {
                ignoreRules!!.addAll(simpleRuleParser!!.parseBooleanRule(value))
            } catch (e: Exception) {
                logger!!.error("error to parse module rule:$key=$value")
            }
        })

        return ignoreRules!!
    }

    //endregion ignoreRules--------------------------------------------------------
}