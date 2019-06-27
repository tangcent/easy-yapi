package com.itangcent.idea.plugin.rule

import javax.script.ScriptEngine
import javax.script.ScriptEngineManager

/**
 * see @{link jdk.nashorn.api.scripting.NashornScriptEngineFactory}
 *
 */
class JsRuleParser : ScriptRuleParser() {
    private var scriptEngine: ScriptEngine? = null

    private fun buildScriptEngine(): ScriptEngine? {
        val manager = ScriptEngineManager()
        return manager.getEngineByName("JavaScript")
    }

    override fun getScriptEngine(): ScriptEngine {
        if (scriptEngine != null) return scriptEngine!!
        synchronized(this) {
            if (scriptEngine != null) return scriptEngine!!
            scriptEngine = buildScriptEngine()
        }
        return scriptEngine!!
    }

}

