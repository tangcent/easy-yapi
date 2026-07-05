package com.itangcent.easyapi.exporter.channel.markdown.template

import com.itangcent.easyapi.logging.IdeaLog
import java.time.format.DateTimeFormatter

/**
 * Registry of built-in template variables surfaced under the reserved `meta.*` root.
 *
 * Each entry is a pure `(RenderContext, arg: String?) -> String` function — the engine
 * passes the literal text between the parentheses (e.g. `"yyyyMM"` in `{{meta.date(yyyyMM)}}`)
 * as `arg`, or `null` when called with no parentheses (`{{meta.date}}`).
 *
 * Contract rules implemented here:
 *  - `date` / `time` / `datetime` default to `yyyy-MM-dd` / `HH:mm:ss` / `yyyy-MM-dd HH:mm:ss`
 *    when `arg` is `null` or blank.
 *  - An invalid [DateTimeFormatter] pattern resolves to the empty string + an `IdeaLog.warn`
 *    naming the bad pattern ; the engine never throws.
 *  - Unknown built-in names resolve to the empty string + an `IdeaLog.debug`  — no
 *    fallback, no throw.
 *  - Adding a built-in is a single map entry.
 *
 * The registry is namespaced under `meta.*` so it never collides with a model/loop variable
 * named `date` / `username` / etc. — `{{meta.x}}` is always a built-in, `{{x}}` is always
 * model/loop data.
 *
 * **Pure**: touches no PSI/VFS. The [IdeaLog] warn/info are the only side effect,
 * and they go to `idea.log` without requiring a `Project` instance.
 */
object TemplateBuiltins : IdeaLog {

    /** Default patterns. */
    private val defaultPatterns: Map<String, String> = mapOf(
        "date" to "yyyy-MM-dd",
        "time" to "HH:mm:ss",
        "datetime" to "yyyy-MM-dd HH:mm:ss",
    )

    /** The built-in registry. Adding a built-in is one entry. */
    private val registry: Map<String, (RenderContext, String?) -> String> = mapOf(
        "date" to { ctx, arg -> formatDateTime(ctx, arg, default = defaultPatterns.getValue("date")) },
        "time" to { ctx, arg -> formatDateTime(ctx, arg, default = defaultPatterns.getValue("time")) },
        "datetime" to { ctx, arg -> formatDateTime(ctx, arg, default = defaultPatterns.getValue("datetime")) },
        "timestamp" to { ctx, _ -> ctx.clock.millis().toString() },
        "now" to { ctx, _ -> ctx.clock.instant().toString() },
        "username" to { ctx, _ -> ctx.username },
        "projectName" to { ctx, _ -> ctx.projectName },
        "pluginVersion" to { ctx, _ -> ctx.pluginVersion },
    )

    /** Returns `true` when [name] is a registered built-in (used by the engine to short-circuit). */
    fun isBuiltin(name: String): Boolean = registry.containsKey(name)

    /**
     * Resolves the built-in [name] against [ctx], passing [arg] (the literal pattern text or `null`).
     *
     * - Unknown name → empty string + `LOG.info` .
     * - Invalid pattern / formatter failure → empty string + `LOG.warn` naming the bad pattern
     *   ; never throws.
     * - Any other throwable → empty string + `LOG.warn` with the throwable.
     */
    fun resolve(name: String, ctx: RenderContext, arg: String?): String {
        val fn = registry[name]
        if (fn == null) {
            LOG.info("Unknown built-in 'meta.$name' — resolved to empty")
            return ""
        }
        return try {
            fn(ctx, arg)
        } catch (t: Throwable) {
            LOG.warn("Failed to resolve built-in 'meta.$name' (arg=$arg): ${t.message}", t)
            ""
        }
    }

    /** Formats `ctx.clock.instant()` with the given [pattern] (or [default] when blank). */
    private fun formatDateTime(ctx: RenderContext, pattern: String?, default: String): String {
        val actualPattern = if (pattern.isNullOrBlank()) default else pattern
        return try {
            val formatter = DateTimeFormatter.ofPattern(actualPattern).withZone(ctx.zone)
            formatter.format(ctx.clock.instant())
        } catch (t: Throwable) {
            LOG.warn("Invalid DateTimeFormatter pattern '$actualPattern' for meta built-in: ${t.message}", t)
            ""
        }
    }
}
