package com.itangcent.idea.plugin.rule

import com.intellij.psi.PsiElement
import com.itangcent.annotation.script.ScriptTypeName
import com.itangcent.idea.plugin.utils.RegexUtils
import com.itangcent.intellij.config.rule.RuleContext
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

    override fun initScriptContext(scriptContext: ScriptContext, context: RuleContext) {
        val engineBindings = scriptContext.getBindings(ScriptContext.ENGINE_SCOPE)
        engineBindings.putAll(toolBindings)
        engineBindings["logger"] = logger
        engineBindings["helper"] = Helper(context.getPsiContext())
    }

    @ScriptTypeName("helper")
    inner class Helper(val context: PsiElement?) {
        fun findClass(canonicalText: String): ScriptPsiTypeContext? {
            return context?.let { duckTypeHelper!!.findType(canonicalText, it)?.let { type -> ScriptPsiTypeContext(type) } }
        }
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