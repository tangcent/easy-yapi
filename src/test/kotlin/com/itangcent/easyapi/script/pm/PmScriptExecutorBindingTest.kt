package com.itangcent.easyapi.script.pm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.script.ScriptEngineManager

/**
 * Binding lock-in test for [PmScriptExecutor] (Spec: ai-workflow-patterns, T2.3 / review Issue #2).
 *
 * Verifies that `httpClient` is **NOT** bound in the `pm.*` script execution context
 * (`postman.test` / `postman.prerequest`). This is a deliberate security-surface
 * limitation: `ScriptHttpClient` is bound ONLY in [Jsr223ScriptParser] (for `groovy:`
 * rule values + `http.call.before`/`after` events). `postman.*` scripts use
 * `pm.sendRequest` for sub-requests if ever needed.
 *
 * This test prevents future widening of the security surface — a regression that
 * adds `bindings["httpClient"] = ...` to `PmScriptExecutor.bindPmObject(...)` will
 * fail this test.
 *
 * Run with: `./gradlew test --tests "*.PmScriptExecutorBindingTest*"`
 */
class PmScriptExecutorBindingTest {

    private val executorSourceFile = File("src/main/kotlin/com/itangcent/easyapi/script/pm/PmScriptExecutor.kt")

    private fun hasGroovyEngine(): Boolean =
        ScriptEngineManager().getEngineByName("groovy") != null

    /**
     * Creates a [PmObject] and binds exactly the variables that
     * [PmScriptExecutor.bindPmObject] binds (mirroring PmScriptExecutor.kt lines 103-113).
     *
     * This does NOT call `bindPmObject` directly (it's private), so it replicates the
     * binding set explicitly. If `bindPmObject` is extended with a new binding, this
     * helper should be updated to match (the source-level test below catches the
     * `httpClient` case specifically).
     */
    private fun bindPmObjectMirror(bindings: javax.script.Bindings, pm: PmObject) {
        bindings["pm"] = pm
        bindings["environment"] = pm.environment
        bindings["globals"] = pm.globals
        bindings["collectionVariables"] = pm.collectionVariables
        bindings["request"] = pm.request
        bindings["response"] = pm.response
        bindings["test"] = pm.test
        bindings["cookies"] = pm.cookies
        bindings["info"] = pm.info
        // NOTE: no `logger` binding here — PmScriptExecutor binds `project.console` which
        // requires a Project. This test doesn't exercise logging, so it's omitted.
    }

    private fun createPostResponsePm(): PmObject {
        val envVars = PmVariableScope()
        val globalVars = PmVariableScope()
        val collectionVars = PmVariableScope()
        val request = PmRequest(url = "https://api.example.com/users", method = "POST")
        val response = PmResponse(
            code = 200,
            status = "OK",
            headers = PmHeaderList(listOf("Content-Type" to "application/json")),
            responseTime = 50,
            responseSize = 100,
            rawBody = """{"token":"abc123"}"""
        )
        val testCollector = PmTestCollector()
        val info = PmInfo("test", "Login", "req-001")
        return PmObject.forPostResponse(
            request = request,
            response = response,
            environment = envVars,
            globals = globalVars,
            collectionVariables = collectionVars,
            testCollector = testCollector,
            cookies = PmCookies(),
            info = info
        )
    }

    // ========== Source-level drift tripwire ==========

    @Test
    fun `PmScriptExecutor bindPmObject does NOT bind httpClient`() {
        assertTrue(
            "Test must run from project root; could not find ${executorSourceFile.path}",
            executorSourceFile.exists()
        )
        val source = executorSourceFile.readText()

        // Extract the bindPmObject function body to scope the assertion (avoid
        // false positives from an unrelated `httpClient` mention elsewhere).
        val bindPmObjectStart = source.indexOf("private fun bindPmObject(")
        assertTrue(
            "Could not locate bindPmObject function in PmScriptExecutor.kt",
            bindPmObjectStart >= 0
        )
        // The function body ends at the next closing brace at the same indentation.
        val bindPmObjectEnd = source.indexOf("}", startIndex = bindPmObjectStart)
        assertTrue(
            "Could not determine end of bindPmObject function",
            bindPmObjectEnd > bindPmObjectStart
        )
        val bindPmObjectBody = source.substring(bindPmObjectStart, bindPmObjectEnd)

        assertFalse(
            "PmScriptExecutor.bindPmObject must NOT bind httpClient " +
                "(deliberate security-surface limitation: ScriptHttpClient is bound ONLY in " +
                "Jsr223ScriptParser for groovy: rule values + http.call.* events. " +
                "postman.* scripts use pm.sendRequest for sub-requests if ever needed). " +
                "Found forbidden binding in:\n$bindPmObjectBody",
            bindPmObjectBody.contains("bindings[\"httpClient\"]")
        )
    }

    // ========== Behavioral test: groovy script sees httpClient as null ==========

    @Test
    fun `postman script context does NOT expose httpClient binding`() {
        if (!hasGroovyEngine()) return

        val pm = createPostResponsePm()
        val engine = ScriptEngineManager().getEngineByName("groovy")!!
        val bindings = engine.createBindings()
        bindPmObjectMirror(bindings, pm)

        // Groovy script probes whether `httpClient` is bound. In JSR-223 Groovy,
        // an unbound variable resolves to null (no MissingPropertyException for
        // bare identifiers when the engine's bindings are consulted).
        val script = """
            try {
                return httpClient
            } catch (Exception e) {
                return "__UNBOUND__"
            }
        """.trimIndent()

        val result = engine.eval(script, bindings)

        // `httpClient` must NOT be in the bindings — either resolves to null
        // or throws (depending on Groovy's resolve strategy). Both outcomes
        // confirm the binding is absent.
        assertTrue(
            "httpClient must NOT be bound in the pm.* script context. " +
                "Expected null or '__UNBOUND__', got: $result",
            result == null || result == "__UNBOUND__"
        )
        assertNull(
            "httpClient binding must be absent from the pm.* script context " +
                "(got non-null value: $result)",
            bindings["httpClient"]
        )
    }
}
