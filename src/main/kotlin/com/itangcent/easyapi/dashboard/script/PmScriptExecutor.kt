package com.itangcent.easyapi.dashboard.script

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.logging.IdeaConsole
import com.itangcent.easyapi.logging.IdeaConsoleProvider
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.rule.parser.EnginePool
import javax.script.Bindings

/**
 * Executes Groovy pre-request and post-response scripts in the context of a [PmObject].
 *
 * Uses an [EnginePool] to borrow and return reusable [javax.script.ScriptEngine] instances,
 * avoiding the overhead of creating a new engine for every script evaluation.
 * The `pm` object and its sub-properties are bound as script variables so that
 * Groovy scripts can access them directly (e.g., `pm.environment.set("key", "value")`).
 *
 * A [IdeaConsole] is also bound as `logger` for scripts to emit log messages
 * that are routed through the plugin's user-visible console.
 *
 * Usage:
 * ```kotlin
 * val executor = PmScriptExecutor.getInstance(project)
 * val pm = PmObject.forPreRequest(...)
 * executor.executePreRequestScript(scriptText, pm)
 * // After request is sent:
 * val pm2 = PmObject.forPostResponse(...)
 * val testResults = executor.executePostResponseScript(scriptText, pm2)
 * ```
 */
@Service(Service.Level.PROJECT)
class PmScriptExecutor(private val project: Project) : IdeaLog {

    private val enginePool = EnginePool("groovy")
    private val console: IdeaConsole by lazy { IdeaConsoleProvider.getInstance(project).getConsole() }

    /**
     * Executes a pre-request script.
     *
     * The script can modify [pm.request] (URL, headers, body, auth) before the request is sent.
     * If the script is blank, this is a no-op.
     *
     * @param script The Groovy script source code
     * @param pm The [PmObject] context for this execution
     * @throws Exception if the script fails at runtime
     */
    fun executePreRequestScript(
        script: String,
        pm: PmObject
    ) {
        if (script.isBlank()) return
        executeScript(script, pm)
    }

    /**
     * Executes a post-response script and returns the collected test results.
     *
     * The script can inspect [pm.response], run assertions, and record test outcomes.
     * If the script is blank, returns an empty list.
     *
     * @param script The Groovy script source code
     * @param pm The [PmObject] context for this execution
     * @return The list of [TestResult] instances collected during script execution
     * @throws Exception if the script fails at runtime
     */
    fun executePostResponseScript(
        script: String,
        pm: PmObject
    ): List<TestResult> {
        if (script.isBlank()) return emptyList()
        executeScript(script, pm)
        return pm.testCollector.results
    }

    private fun executeScript(script: String, pm: PmObject) {
        try {
            enginePool.withEngine { engine ->
                val bindings = engine.createBindings()
                bindPmObject(bindings, pm)
                LOG.info("Evaluating script (${script.lines().size} lines)...")
                engine.eval(script, bindings)
                LOG.info("Script evaluation completed successfully")
            } ?: run {
                LOG.warn("Groovy script engine not available")
            }
        } catch (e: Exception) {
            LOG.warn("Script execution failed: ${e.message}", e)
            throw e
        }
    }

    /**
     * Binds the [PmObject] and its sub-properties as script variables.
     *
     * Available bindings in scripts:
     * - `pm` — the full [PmObject]
     * - `environment`, `globals`, `collectionVariables` — variable scopes
     * - `request`, `response` — request/response objects
     * - `test`, `cookies`, `info` — test handler, cookies, metadata
     * - `logger` — [IdeaConsole] for emitting log messages
     */
    private fun bindPmObject(bindings: Bindings, pm: PmObject) {
        bindings["pm"] = pm
        bindings["environment"] = pm.environment
        bindings["globals"] = pm.globals
        bindings["collectionVariables"] = pm.collectionVariables
        bindings["request"] = pm.request
        bindings["response"] = pm.response
        bindings["test"] = pm.test
        bindings["cookies"] = pm.cookies
        bindings["info"] = pm.info
        bindings["logger"] = console
    }

    companion object : IdeaLog {
        fun getInstance(project: Project): PmScriptExecutor =
            project.getService(PmScriptExecutor::class.java)
    }
}
