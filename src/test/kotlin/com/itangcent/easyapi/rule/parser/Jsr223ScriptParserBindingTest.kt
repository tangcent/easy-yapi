package com.itangcent.easyapi.rule.parser

import com.itangcent.easyapi.http.HttpClient
import com.itangcent.easyapi.http.HttpRequest
import com.itangcent.easyapi.http.HttpResponse
import com.itangcent.easyapi.http.ScriptHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import javax.script.ScriptEngineManager
import kotlinx.coroutines.runBlocking

/**
 * Binding lock-in test for [Jsr223ScriptParser] (Spec: ai-workflow-patterns, T2.3 / review Issue #2, #7).
 *
 * Verifies that the `httpClient` binding in a `groovy:` evaluation context is a
 * [ScriptHttpClient] (the sync adapter that bridges the suspend-only [HttpClient]
 * to the blocking JSR-223 boundary), NOT the raw [HttpClient].
 *
 * `Jsr223ScriptParser.bind()` is private and tightly coupled to [RuleContext.project]
 * (it calls `HttpClientProvider.getInstance(context.project).getClient()`), so a
 * pure behavioral test through `parse()` would require a full IntelliJ fixture.
 * Instead this test combines:
 *
 * 1. **Source-level drift tripwire** — reads `Jsr223ScriptParser.kt` and asserts the
 *    `ScriptHttpClient(it)` binding line is present (regression protection).
 * 2. **Behavioral test** — binds a `ScriptHttpClient` as `httpClient` in a groovy
 *    engine and verifies a script can call `httpClient.executeSync(...)` and receive
 *    the delegate's response (proves the binding is usable, not just present).
 *
 * Run with: `./gradlew test --tests "*.Jsr223ScriptParserBindingTest*"`
 */
class Jsr223ScriptParserBindingTest {

    private val parserSourceFile = File("src/main/kotlin/com/itangcent/easyapi/rule/parser/Jsr223ScriptParser.kt")

    private fun hasGroovyEngine(): Boolean =
        ScriptEngineManager().getEngineByName("groovy") != null

    // ========== Source-level drift tripwire ==========

    @Test
    fun `Jsr223ScriptParser source binds ScriptHttpClient not raw HttpClient`() {
        assertTrue(
            "Test must run from project root; could not find ${parserSourceFile.path}",
            parserSourceFile.exists()
        )
        val source = parserSourceFile.readText()

        assertTrue(
            "Jsr223ScriptParser must import ScriptHttpClient (binding scoping: T2.3)",
            source.contains("import com.itangcent.easyapi.http.ScriptHttpClient")
        )
        assertTrue(
            "Jsr223ScriptParser must bind httpClient as ScriptHttpClient(it), not the raw HttpClient " +
                "(the suspend HttpClient.execute is not callable from the blocking JSR-223 boundary)",
            source.contains("ScriptHttpClient(it)")
        )
        // Lock-in: the binding must be conditional on rawHttpClient being non-null
        // (HttpClientProvider can fail in headless/test envs — the runCatching guard
        // prevents NPEs). A regression to `bindings["httpClient"] = httpClient` (raw,
        // unconditional) would re-introduce the suspend-callable-from-blocking bug.
        assertTrue(
            "Jsr223ScriptParser must guard the httpClient binding with runCatching + null-check " +
                "(HttpClientProvider can fail; the binding must degrade gracefully to null)",
            source.contains("rawHttpClient?.let { ScriptHttpClient(it) }")
        )
    }

    // ========== Behavioral test: groovy script can call httpClient.executeSync ==========

    @Test
    fun `groovy script calling httpClient executeSync receives the delegate response`() {
        if (!hasGroovyEngine()) return

        val request = HttpRequest(
            url = "https://refresh.example.com/token",
            method = "POST",
            body = "grant_type=refresh_token"
        )
        val expectedResponse = HttpResponse(
            code = 200,
            body = """{"access_token":"newTokFromGroovy"}"""
        )
        val delegate = mock<HttpClient>()
        runBlocking { whenever(delegate.execute(request)).thenReturn(expectedResponse) }

        val scriptClient = ScriptHttpClient(delegate)

        val engine = ScriptEngineManager().getEngineByName("groovy")!!
        val bindings = engine.createBindings()
        // Mirror what Jsr223ScriptParser.bind() does for the httpClient slot:
        // bindings["httpClient"] = ScriptHttpClient(rawClient)
        bindings["httpClient"] = scriptClient
        // Bind the pre-built request so the groovy script passes the exact same instance
        // to executeSync (avoids data-class equals() mismatch between Kotlin emptyList()
        // and Java Collections.emptyList() if the request were reconstructed in Groovy).
        bindings["refreshReq"] = request

        // Groovy script mirroring the 401-refresh recipe's sub-request call.
        val script = """
            def resp = httpClient.executeSync(refreshReq)
            return resp.body
        """.trimIndent()

        val result = engine.eval(script, bindings)

        assertNotNull("Groovy script must return a result (not null)", result)
        assertEquals(
            "Groovy script calling httpClient.executeSync must receive the delegate's response body",
            expectedResponse.body,
            result
        )
    }

    @Test
    fun `groovy script sees httpClient as non-null when ScriptHttpClient is bound`() {
        if (!hasGroovyEngine()) return

        val delegate = mock<HttpClient>()
        val scriptClient = ScriptHttpClient(delegate)

        val engine = ScriptEngineManager().getEngineByName("groovy")!!
        val bindings = engine.createBindings()
        bindings["httpClient"] = scriptClient

        val script = """
            return httpClient != null
        """.trimIndent()

        val result = engine.eval(script, bindings)
        assertEquals(
            "httpClient binding must be non-null when ScriptHttpClient is bound",
            java.lang.Boolean.TRUE,
            result
        )
    }
}
