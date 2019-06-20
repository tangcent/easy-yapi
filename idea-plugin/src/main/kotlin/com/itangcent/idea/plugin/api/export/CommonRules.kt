package com.itangcent.idea.plugin.api.export

import com.google.inject.Inject
import com.itangcent.idea.utils.traceError
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.config.rule.BooleanRule
import com.itangcent.intellij.config.rule.RuleParser
import com.itangcent.intellij.config.rule.StringRule
import com.itangcent.intellij.logger.Logger
import java.util.*

class CommonRules {

    @Inject
    protected val logger: Logger? = null

    @Inject
    protected val configReader: ConfigReader? = null

    @Inject
    protected val ruleParser: RuleParser? = null

    //region moduleRules--------------------------------------------------------
    var moduleRules: ArrayList<StringRule>? = null

    fun readModuleRules(): List<StringRule> {
        if (moduleRules != null) return moduleRules!!
        moduleRules = ArrayList()

        configReader!!.foreach({ key ->
            key.startsWith("module")
        }) { key, value ->
            try {
                moduleRules!!.addAll(ruleParser!!.parseStringRule(value))
            } catch (e: Exception) {
                logger!!.error("error to parse module rule:$key=$value")
                logger.traceError(e)
            }
        }

        return moduleRules!!
    }

    //endregion moduleRules--------------------------------------------------------

    //region ignoreRules--------------------------------------------------------
    var ignoreRules: ArrayList<BooleanRule>? = null

    fun readIgnoreRules(): List<BooleanRule> {
        if (ignoreRules != null) return ignoreRules!!
        ignoreRules = ArrayList()

        configReader!!.foreach({ key ->
            key.startsWith("ignore")
        }) { key, value ->
            try {
                ignoreRules!!.addAll(ruleParser!!.parseBooleanRule(value))
            } catch (e: Exception) {
                logger!!.error("error to parse module rule:$key=$value")
                logger.traceError(e)
            }
        }

        return ignoreRules!!
    }

    //endregion ignoreRules--------------------------------------------------------


    //region moduleRules--------------------------------------------------------
    var methodDocRules: ArrayList<StringRule>? = null

    fun readMethodReadRules(): List<StringRule> {
        if (methodDocRules != null) return methodDocRules!!
        methodDocRules = ArrayList()

        configReader!!.foreach({ key ->
            key.startsWith("doc.method")
        }) { key, value ->
            try {
                methodDocRules!!.addAll(ruleParser!!.parseStringRule(value))
            } catch (e: Exception) {
                logger!!.error("error to parse doc.method rule:$key=$value")
                logger.traceError(e)
            }
        }

        return methodDocRules!!
    }

    //endregion moduleRules--------------------------------------------------------

}