package com.itangcent.idea.plugin.rule

import com.itangcent.idea.plugin.utils.RegexUtils
import javax.script.*

abstract class StandardJdkRuleParser : ScriptRuleParser() {
    private var scriptEngine: ScriptEngine? = null
    private var unsupported = false

    protected abstract fun scriptType(): String

    private fun buildScriptEngine(): ScriptEngine? {
        val manager = ScriptEngineManager()
        return manager.getEngineByName(scriptType())
    }

    override fun getScriptEngine(): ScriptEngine {
        if (unsupported) {
            throw UnsupportedScriptException(scriptType())
        }
        if (scriptEngine != null) return scriptEngine!!
        synchronized(this) {
            if (scriptEngine != null) return scriptEngine!!
            scriptEngine = buildScriptEngine()
        }
        if (scriptEngine == null) {
            unsupported = true
            throw UnsupportedScriptException(scriptType())
        }
        initScripEngine(scriptEngine!!)
        return scriptEngine!!
    }

    open fun initScripEngine(scriptEngine: ScriptEngine) {
        scriptEngine.setBindings(SimpleBindings(toolBindings), ScriptContext.GLOBAL_SCOPE)
    }

    override fun initScriptContext(scriptContext: ScriptContext) {
        val engineBindings = scriptContext.getBindings(ScriptContext.ENGINE_SCOPE)
        engineBindings.putAll(toolBindings)
        engineBindings["logger"] = logger
    }

    companion object {
        private val toolBindings: Bindings

        init {
            val bindings: Bindings = SimpleBindings()
            bindings["tool"] = RuleToolUtils()
            bindings["regex"] = RegexUtils()
            toolBindings = bindings
        }
    }
}