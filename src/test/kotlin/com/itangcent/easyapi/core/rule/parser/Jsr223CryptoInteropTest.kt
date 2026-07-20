package com.itangcent.easyapi.core.rule.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.script.ScriptEngineManager

/**
 * Spike test (Spec: ai-workflow-patterns, T1.1 / review Issue #6).
 *
 * Resolves Requirements Open Question #1: is `javax.crypto.Mac` reachable from
 * a `groovy:` rule value? Req 5.2's HMAC signing recipe depends on this.
 *
 * `GroovyScriptParser.parse()` (see [Jsr223ScriptParser.parse]) evaluates `groovy:`
 * expressions via `enginePool.withEngine { engine -> engine.eval(script, bindings) }`
 * (Jsr223ScriptParser.kt lines 72-79). The `bindings` add `it`, `logger`, `httpClient`,
 * etc. — none of which affect `javax.crypto.Mac` reachability. This spike exercises the
 * exact same JSR-223 engine-eval code path (via [EnginePool.withEngine]) without the
 * heavyweight `RuleContext` mocking, giving the cleanest possible signal for the
 * crypto-interop question.
 *
 * - If this test PASSES → Req 5.2 stays first-class; proceed.
 * - If this test FAILS → demote Req 5.2 to a scaffold in the catalog (T4.1) and record
 *   the failure in `review-design.md`.
 *
 * The Groovy engine comes from the optional `org.intellij.groovy` bundled plugin
 * (declared `optional` in `plugin.xml`); when absent, *all* `groovy:`/`pm.*` scripts
 * no-op. This is a pre-existing precondition, unchanged by this spec. The test guards
 * on engine availability (`hasGroovyEngine`) so it no-ops cleanly in environments
 * without Groovy (mirroring [EnginePoolTest]'s pattern).
 */
class Jsr223CryptoInteropTest {

    private fun hasGroovyEngine(): Boolean =
        ScriptEngineManager().getEngineByName("groovy") != null

    /**
     * Independent JDK computation of `HmacSHA256("msg", "secret")` as a lowercase
     * hex string. Used as the expected value for the Groovy interop result.
     */
    private fun expectedHmacSha256Hex(message: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(message.toByteArray(Charsets.UTF_8))
            .joinToString("") { String.format("%02x", it) }
    }

    @Test
    fun `groovy engine can compute HmacSHA256 via javax crypto Mac interop`() {
        if (!hasGroovyEngine()) return

        val message = "msg"
        val secret = "secret"
        val expectedHex = expectedHmacSha256Hex(message, secret)

        // Groovy script that mirrors the JDK computation via Java interop — exactly the
        // kind of code the Req 5.2 HMAC recipe generates inside a `postman.prerequest`
        // (or `groovy:`) rule value.
        val groovyScript = """
            import javax.crypto.Mac
            import javax.crypto.spec.SecretKeySpec
            def mac = Mac.getInstance("HmacSHA256")
            mac.init(new SecretKeySpec("$secret".getBytes("UTF-8"), "HmacSHA256"))
            def raw = mac.doFinal("$message".getBytes("UTF-8"))
            return raw.collect { String.format("%02x", it) }.join()
        """.trimIndent()

        val pool = EnginePool("groovy")
        val result = pool.withEngine { engine -> engine.eval(groovyScript) }

        assertNotNull("Groovy engine eval must return a result (not null)", result)
        assertTrue(
            "Groovy HmacSHA256 result must be a String (got ${result?.javaClass?.name})",
            result is String
        )
        assertEquals(
            "Groovy-computed HmacSHA256 hex must match the independent JDK computation",
            expectedHex,
            result
        )
    }

    @Test
    fun `groovy engine can compute HmacSHA256 as base64 via java util Base64 interop`() {
        if (!hasGroovyEngine()) return

        val message = "msg"
        val secret = "secret"

        // Independent JDK base64 computation.
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val expectedBase64 = java.util.Base64.getEncoder()
            .encodeToString(mac.doFinal(message.toByteArray(Charsets.UTF_8)))

        val groovyScript = """
            import javax.crypto.Mac
            import javax.crypto.spec.SecretKeySpec
            import java.util.Base64
            def mac = Mac.getInstance("HmacSHA256")
            mac.init(new SecretKeySpec("$secret".getBytes("UTF-8"), "HmacSHA256"))
            def raw = mac.doFinal("$message".getBytes("UTF-8"))
            return Base64.getEncoder().encodeToString(raw)
        """.trimIndent()

        val result = EnginePool("groovy").withEngine { engine -> engine.eval(groovyScript) }

        assertNotNull(result)
        assertEquals(expectedBase64, result)
    }
}
