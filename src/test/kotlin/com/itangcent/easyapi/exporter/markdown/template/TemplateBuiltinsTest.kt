package com.itangcent.easyapi.exporter.markdown.template

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Pins the contract of [TemplateBuiltins] + [RenderContext] before the engine exists
 * (test-first, design.md § Test Strategy point 2).
 *
 * Pure JUnit (Pattern A, write-test-case SKILL.md) — no `Project`, no PSI (NFR-4).
 * The fixed [Clock] + fixed username make every built-in output assertable with
 * `assertEquals` (Decision 9).
 *
 * Reference instant: `2026-03-15T10:30:45Z` (UTC).
 *  - date     = "2026-03-15"
 *  - time     = "10:30:45"
 *  - datetime = "2026-03-15 10:30:45"
 *  - timestamp (epoch ms) = derived from the fixed instant (see `testTimestampIsEpochMillisDigits`)
 *  - now      = ISO-8601 "2026-03-15T10:30:45Z"
 */
class TemplateBuiltinsTest {

    private val fixedInstant: Instant = Instant.parse("2026-03-15T10:30:45Z")
    private val zone: ZoneId = ZoneId.of("UTC")
    private val fixedClock: Clock = Clock.fixed(fixedInstant, zone)

    private val ctx: RenderContext = RenderContext(
        clock = fixedClock,
        zone = zone,
        username = "test-user",
        projectName = "test-project",
        pluginVersion = "1.2.3",
    )

    // ---- date / time / datetime: default patterns ----

    @Test
    fun testDateDefaultPattern() {
        assertEquals("2026-03-15", TemplateBuiltins.resolve("date", ctx, arg = null))
    }

    @Test
    fun testTimeDefaultPattern() {
        assertEquals("10:30:45", TemplateBuiltins.resolve("time", ctx, arg = null))
    }

    @Test
    fun testDateTimeDefaultPattern() {
        assertEquals("2026-03-15 10:30:45", TemplateBuiltins.resolve("datetime", ctx, arg = null))
    }

    // ---- date / time / datetime: custom patterns ----

    @Test
    fun testDateCustomPattern() {
        assertEquals("202603", TemplateBuiltins.resolve("date", ctx, arg = "yyyyMM"))
    }

    @Test
    fun testTimeCustomPattern() {
        assertEquals("103045", TemplateBuiltins.resolve("time", ctx, arg = "HHmmss"))
    }

    @Test
    fun testDateTimeCustomPattern() {
        assertEquals(
            "2026-03-15 10:30:45",
            TemplateBuiltins.resolve("datetime", ctx, arg = "yyyy-MM-dd HH:mm:ss"),
        )
    }

    @Test
    fun testDateCustomPatternWithSpaces() {
        // Spaces inside the parentheses are part of the pattern (CONTRACT § Built-in variables).
        assertEquals("2026 03", TemplateBuiltins.resolve("date", ctx, arg = "yyyy MM"))
    }

    // ---- timestamp / now / username / projectName / pluginVersion ----

    @Test
    fun testTimestampIsEpochMillisDigits() {
        // Derived from the same fixed instant — the contract is "epoch ms as decimal digits".
        assertEquals(
            fixedInstant.toEpochMilli().toString(),
            TemplateBuiltins.resolve("timestamp", ctx, arg = null),
        )
    }

    @Test
    fun testNowIsIso8601() {
        // `now` is the legacy ISO-8601 form — `Instant.toString()` for an ISO-8601 instant is
        // "2026-03-15T10:30:45Z" (the trailing Z is the UTC designator).
        assertEquals(
            fixedInstant.toString(),
            TemplateBuiltins.resolve("now", ctx, arg = null),
        )
    }

    @Test
    fun testUsername() {
        assertEquals("test-user", TemplateBuiltins.resolve("username", ctx, arg = null))
    }

    @Test
    fun testProjectName() {
        assertEquals("test-project", TemplateBuiltins.resolve("projectName", ctx, arg = null))
    }

    @Test
    fun testPluginVersion() {
        assertEquals("1.2.3", TemplateBuiltins.resolve("pluginVersion", ctx, arg = null))
    }

    // ---- invalid patterns: empty + never throws  ----

    @Test
    fun testInvalidDatePatternReturnsEmptyAndDoesNotThrow() {
        assertEquals("", TemplateBuiltins.resolve("date", ctx, arg = "INVALID!@#"))
    }

    @Test
    fun testInvalidTimePatternReturnsEmptyAndDoesNotThrow() {
        assertEquals("", TemplateBuiltins.resolve("time", ctx, arg = "INVALID!@#"))
    }

    @Test
    fun testInvalidDateTimePatternReturnsEmptyAndDoesNotThrow() {
        assertEquals("", TemplateBuiltins.resolve("datetime", ctx, arg = "INVALID!@#"))
    }

    @Test
    fun testEmptyPatternTreatedAsDefault() {
        // An empty pattern string is treated as default-pattern (CONTRACT: empty arg → default).
        assertEquals("2026-03-15", TemplateBuiltins.resolve("date", ctx, arg = ""))
    }

    // ---- unknown meta.* → empty + no throw  ----

    @Test
    fun testUnknownMetaNameReturnsEmpty() {
        assertEquals("", TemplateBuiltins.resolve("nonexistent", ctx, arg = null))
    }

    @Test
    fun testUnknownMetaNameWithArgReturnsEmpty() {
        assertEquals("", TemplateBuiltins.resolve("nonexistent", ctx, arg = "anything"))
    }

    // ---- isBuiltin: registry membership ----

    @Test
    fun testIsBuiltinForKnownNames() {
        listOf(
            "date", "time", "datetime", "timestamp", "now",
            "username", "projectName", "pluginVersion",
        ).forEach { name ->
            assertTrue("'$name' should be a known built-in", TemplateBuiltins.isBuiltin(name))
        }
    }

    @Test
    fun testIsBuiltinForUnknownName() {
        assertFalse("unknown name should not be a built-in", TemplateBuiltins.isBuiltin("nonexistent"))
    }

    // ---- RenderContext: production defaults (smoke) ----

    @Test
    fun testRenderContextProductionDefaultsPopulateUsernameAndClock() {
        val prod = RenderContext.production(projectName = "prod", pluginVersion = "9.9.9")
        // Username is non-null when run under a real JVM (System.getProperty("user.name")).
        assertTrue("production username should not be blank", prod.username.isNotBlank())
        assertEquals("prod", prod.projectName)
        assertEquals("9.9.9", prod.pluginVersion)
        // Clock must be live (not fixed) so wall-clock built-ins advance in production.
        val t1 = prod.clock.millis()
        Thread.sleep(2)
        val t2 = prod.clock.millis()
        assertTrue("production clock should advance", t2 > t1)
    }

    @Test
    fun testRenderContextProductionUsesSystemDefaultZone() {
        val prod = RenderContext.production(projectName = "p", pluginVersion = "v")
        assertEquals(java.time.ZoneId.systemDefault(), prod.zone)
    }
}
