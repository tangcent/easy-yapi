package com.itangcent.easyapi.logging

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Anti-pattern gate test.
 *
 * Fails if `src/main` contains:
 * - `LOG.error(` or `Logger.*.error(` (prohibited — triggers intrusive popup; use LOG.warn)
 * - `LOG.debug(`/`LOG.trace(` or `Logger.*.debug/trace(` (prohibited on the LOG channel —
 *   filtered out of idea.log by default; use LOG.info, or the console channel for opt-in trace)
 * - `println(` outside `logging/IdeaConsole*` (the legit API)
 * - `.printStackTrace()`
 * - `Notifications.Bus.notify` / `NotificationGroupManager` outside `NotificationUtils.kt`
 *
 * KDoc-comment occurrences are excluded.
 *
 * Run with: `./gradlew test --tests "*.AntiPatternGateTest"`
 */
class AntiPatternGateTest {

    private val mainSrcRoot = File("src/main/kotlin")

    @Test
    fun noLogErrorInProductionCode() {
        val offenders = scanFiles { content, file ->
            val stripped = stripComments(content)
            val patterns = listOf(
                Regex("""\bLOG\.error\s*\("""),
                Regex("""Logger\.getInstance\(.*\)\.error\s*\(""")
            )
            patterns.flatMap { regex ->
                regex.findAll(stripped).map { it.value }.toList()
            }
        }
        assertTrue(
            "LOG.error is prohibited in production code (use LOG.warn as the error-level fallback). " +
                "Violations:\n${offenders.joinToString("\n")}",
            offenders.isEmpty()
        )
    }

    /**
     * `LOG.debug` / `LOG.trace` are filtered out of `idea.log` by default, so they
     * are invisible when investigating bugs. `LOG.info` is the floor for diagnostics
     * on the `LOG` channel; genuinely opt-in verbose trace belongs on the `console`
     * channel (`project.console.debug/trace`).
     */
    @Test
    fun noDebugTraceOnLogChannel() {
        val offenders = scanFiles { content, file ->
            // Skip the logging infrastructure itself — IdeaLogConsole floors
            // console trace/debug to LOG.info, and IdeaLog/IdeaConsoleProvider
            // document the rule in KDoc. Those are the legitimate exceptions.
            val normalizedPath = file.path.replace('\\', '/')
            if (normalizedPath.contains("logging/IdeaLog") ||
                normalizedPath.contains("logging/IdeaLogConsole") ||
                normalizedPath.contains("logging/IdeaConsoleProvider")
            ) return@scanFiles emptyList()

            val stripped = stripComments(content)
            val patterns = listOf(
                Regex("""\bLOG\.debug\s*\("""),
                Regex("""\bLOG\.trace\s*\("""),
                Regex("""Logger\.getInstance\(.*\)\.debug\s*\("""),
                Regex("""Logger\.getInstance\(.*\)\.trace\s*\(""")
            )
            patterns.flatMap { regex ->
                regex.findAll(stripped).map { it.value }.toList()
            }
        }
        assertTrue(
            "LOG.debug/LOG.trace are prohibited in production code (they are filtered out of idea.log by " +
                "default — use LOG.info, or the console channel for opt-in trace). " +
                "Violations:\n${offenders.joinToString("\n")}",
            offenders.isEmpty()
        )
    }

    @Test
    fun noPrintlnInProductionCode() {
        val offenders = scanFiles { content, file ->
            // Skip the legit IdeaConsole API definitions and implementations
            val normalizedPath = file.path.replace('\\', '/')
            if (normalizedPath.contains("logging/IdeaConsole") ||
                normalizedPath.contains("logging/DefaultIdeaConsole") ||
                normalizedPath.contains("logging/ConfigurableIdeaConsole")
            ) return@scanFiles emptyList()

            // Strip KDoc comments (/** ... */) and line comments (//)
            val stripped = stripComments(content)

            val regex = Regex("""\bprintln\s*\(""")
            regex.findAll(stripped).map { "println(" }.toList()
        }
        assertTrue(
            "Found println() in production code (should use LOG/console instead):\n${offenders.joinToString("\n")}",
            offenders.isEmpty()
        )
    }

    @Test
    fun noPrintStackTraceInProductionCode() {
        val offenders = scanFiles { content, _ ->
            val stripped = stripComments(content)
            val regex = Regex("""\.printStackTrace\s*\(""")
            regex.findAll(stripped).map { ".printStackTrace()" }.toList()
        }
        assertTrue(
            "Found .printStackTrace() in production code (should use LOG.warn(msg, e) instead):\n${offenders.joinToString("\n")}",
            offenders.isEmpty()
        )
    }

    @Test
    fun noDirectNotificationsOutsideNotificationUtils() {
        val offenders = scanFiles { content, file ->
            // NotificationUtils.kt is the one allowed location
            if (file.path.replace('\\', '/').contains("ide/support/NotificationUtils")) return@scanFiles emptyList()

            val stripped = stripComments(content)
            val patterns = listOf(
                Regex("""Notifications\.Bus\.notify"""),
                Regex("""NotificationGroupManager""")
            )
            patterns.flatMap { regex ->
                regex.findAll(stripped).map { it.value }.toList()
            }
        }
        assertTrue(
            "Found direct Notifications.Bus.notify / NotificationGroupManager calls outside NotificationUtils.kt " +
                "(should route through NotificationUtils):\n${offenders.joinToString("\n")}",
            offenders.isEmpty()
        )
    }

    // --- helpers ---

    private fun scanFiles(matcher: (content: String, file: File) -> List<String>): List<String> {
        if (!mainSrcRoot.exists()) return emptyList()
        val offenders = mutableListOf<String>()
        mainSrcRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val content = file.readText()
            val matches = matcher(content, file)
            if (matches.isNotEmpty()) {
                offenders.add("${file.path}: ${matches.joinToString(", ")}")
            }
        }
        return offenders
    }

    private fun stripComments(content: String): String {
        // Remove /* ... */ (including KDoc /** ... */) and // line comments
        var result = content
        result = Regex("""/\*[\s\S]*?\*/""").replace(result, "")
        result = Regex("""//[^\n]*""").replace(result, "")
        return result
    }
}
