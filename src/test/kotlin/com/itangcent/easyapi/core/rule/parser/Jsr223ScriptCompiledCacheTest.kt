package com.itangcent.easyapi.core.rule.parser

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager

/**
 * Lock-in test for the compiled-script cache in [Jsr223ScriptParser]
 * (see `.spec/api-scan-performance.md` §5.2).
 *
 * Acceptance criteria being covered:
 *
 * - "同一条 groovy 规则对 N 个类求值，compile 只发生一次" — the same script source
 *   must yield the **same** [javax.script.CompiledScript] instance on every call.
 * - A script that fails to compile must fall back to `null` so the caller evals
 *   the raw source instead of failing the rule.
 *
 * `compiledScript()` is private (it needs a pooled engine), so it is invoked
 * reflectively here. If the method is renamed or its signature changes, these
 * tests fail loudly — which is the intent.
 *
 * Run with: `./gradlew test --tests "*.Jsr223ScriptCompiledCacheTest*"`
 */
class Jsr223ScriptCompiledCacheTest {

    private val parser = GroovyScriptParser()

    private fun hasGroovyEngine(): Boolean =
        ScriptEngineManager().getEngineByName("groovy") != null

    private fun groovyEngine(): ScriptEngine =
        ScriptEngineManager().getEngineByName("groovy")
            ?: error("Groovy JSR-223 engine is not on the test classpath")

    private fun compiledScript(engine: ScriptEngine, script: String): Any? =
        Jsr223ScriptParser::class.java
            .getDeclaredMethod("compiledScript", ScriptEngine::class.java, String::class.java)
            .apply { isAccessible = true }
            .invoke(parser, engine, script)

    @Test
    fun `same script source compiles once and is reused`() {
        if (!hasGroovyEngine()) return

        val engine = groovyEngine()
        val source = "1 + 1"

        val first = compiledScript(engine, source)
        val second = compiledScript(engine, source)

        assertNotNull("Groovy engine should support Compilable", first)
        assertSame(
            "The same script source must reuse one cached CompiledScript, not recompile",
            first,
            second
        )
    }

    @Test
    fun `different script sources get different compiled entries`() {
        if (!hasGroovyEngine()) return

        val engine = groovyEngine()

        val first = compiledScript(engine, "1 + 1")
        val second = compiledScript(engine, "2 + 2")

        assertNotNull(first)
        assertNotNull(second)
        assertNotSame("Different sources must not share a compiled entry", first, second)
    }

    @Test
    fun `uncompilable script falls back to null`() {
        if (!hasGroovyEngine()) return

        val engine = groovyEngine()

        assertNull(
            "A script that fails to compile must return null so the caller evals raw source",
            compiledScript(engine, "def x = (")
        )
    }

    @Test
    fun `invalidating the cache forces a recompile`() {
        if (!hasGroovyEngine()) return

        val engine = groovyEngine()
        val source = "1 + 2"

        val before = compiledScript(engine, source)
        assertNotNull(before)

        parser.invalidateCompiledCache()

        val after = compiledScript(engine, source)
        assertNotNull(after)
        assertNotSame(
            "After a config reload the cached CompiledScript must be dropped: stale " +
                "entries keep their Groovy classloader reachable",
            before,
            after
        )
    }
}
