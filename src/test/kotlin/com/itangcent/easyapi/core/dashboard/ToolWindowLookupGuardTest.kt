package com.itangcent.easyapi.core.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the API Explorer tool window id — the same string is both the lookup
 * key production code uses and the label the user reads, and it lives in two
 * files that nothing links together.
 *
 * The platform derives a tool window's visible name from its **id**, not the
 * other way around:
 *
 * ```
 * key = ("toolwindow.stripe." + id).replace(" ", "_")
 * label = bundle.messageOrDefault(key, id)
 * ```
 *
 * This plugin declares no `<resource-bundle>`, so the lookup always falls back
 * to the raw id: `id="API Explorer"` is literally what the stripe button,
 * **View → Tool Windows**, the Switcher and the tool-window widget show.
 *
 * The id therefore has to stay in step with a second, unrelated copy —
 * `ToolWindowManager.getToolWindow("<id>")` in production code:
 *
 * | site | role |
 * |---|---|
 * | `META-INF/plugin.xml`, `<toolWindow id=…>` | registers the window; the IDE keys each user's saved layout/anchor state by it |
 * | `src/main/kotlin`, `getToolWindow("<id>")` | finds the window at runtime |
 *
 * Nothing connects the two at compile time. A one-sided rename makes
 * `getToolWindow` return `null`, whose only symptom is an action that quietly
 * does nothing (or an NPE at a `?.`-less call site) — no compiler error, no log
 * line, no failed export. That silent failure is what this scan catches.
 *
 * No IDE / PSI dependency — `plugin.xml` is read off the classpath and the call
 * sites off the source tree.
 */
class ToolWindowLookupGuardTest {

    /**
     * The explorer's id is asserted rather than derived from the label: with no
     * bundle in play the id *is* the label, so renaming it is a user-visible
     * change that should have to update this line consciously.
     */
    @Test
    fun explorerToolWindowIdIsTheVisibleName() {
        assertEquals(
            "The API Explorer tool window id is user-visible (no resource bundle overrides it) " +
                "and is also what production code looks up. Rename it in plugin.xml and in every " +
                "getToolWindow(...) call site together, then update this test.",
            "API Explorer",
            explorerToolWindowId()
        )
    }

    /**
     * Every hardcoded `getToolWindow("<id>")` must name a window that plugin.xml
     * actually declares. A literal that matches nothing compiles, ships, and
     * fails silently at runtime.
     *
     * Reminder: a `getToolWindow` argument passed as a constant (as the console
     * window does) is not scanned here — that indirection is compiler-checked on
     * its own side. Only the literals can drift unnoticed.
     */
    @Test
    fun hardcodedToolWindowLookupsMatchDeclaredIds() {
        val declared = declaredToolWindowIds()
        val lookups = hardcodedToolWindowLookups()

        assertTrue(
            "No hardcoded getToolWindow(\"…\") call sites were found under src/main/kotlin — " +
                "either the scan regex stopped matching or the lookups moved. " +
                "Declared ids: ${declared.sorted()}",
            lookups.isNotEmpty()
        )

        val unknown = lookups
            .mapValues { (_, literals) -> literals.filterNot { it in declared } }
            .filterValues { it.isNotEmpty() }
        assertTrue(
            "These getToolWindow(\"<id>\") arguments match no <toolWindow id=…> in plugin.xml, " +
                "so they resolve to null at runtime with no error:\n" +
                unknown.entries.joinToString("\n") { "  ${it.key} -> \"${it.value}\"" } +
                "\nDeclared ids: ${declared.sorted()}",
            unknown.isEmpty()
        )
    }

    /**
     * Keeps [hardcodedToolWindowLookupsMatchDeclaredIds] from passing vacuously:
     * the three entries that open the explorer are known to exist, so the scan
     * has to keep seeing them. If one is deleted on purpose, update this list.
     */
    @Test
    fun lookupScanStillReachesTheKnownCallSites() {
        val lookups = hardcodedToolWindowLookups()
        val expectedSuffixes = listOf(
            "ide/action/ApiCallAction.kt",
            "ide/action/OpenApiDashboardAction.kt",
            "ide/linemarker/ApiMethodLineMarkerProvider.kt"
        )

        val missing = expectedSuffixes.filterNot { suffix -> lookups.keys.any { it.endsWith(suffix) } }
        assertTrue(
            "The scan no longer finds a getToolWindow(\"…\") literal in these files, which " +
                "open the explorer: $missing. " +
                "Found: ${lookups.entries.joinToString(", ") { "${it.key} -> \"${it.value}\"" }}",
            missing.isEmpty()
        )
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    private val mainSrcRoot = File("src/main/kotlin")

    /** id of the tool window backed by [ApiDashboardToolWindowFactory]. */
    private fun explorerToolWindowId(): String {
        val matches = declaredToolWindows()
        assertTrue(
            "plugin.xml should declare at least one <toolWindow id=… factoryClass=…>; " +
                "found none — has the extension-point format changed?",
            matches.isNotEmpty()
        )
        val explorer = matches.filter { it.second == EXPLORER_FACTORY_CLASS }
        assertEquals(
            "expected exactly one toolWindow backed by $EXPLORER_FACTORY_CLASS, " +
                "found ${explorer.map { it.first }}. Declared: ${matches.map { it.second }}",
            1,
            explorer.size
        )
        return explorer.single().first
    }

    /** `(id, factoryClass)` of every `<toolWindow …>` declared in plugin.xml. */
    private fun declaredToolWindows(): List<Pair<String, String>> =
        TOOL_WINDOW_REGEX.findAll(loadFromClasspath("META-INF/plugin.xml")).mapNotNull { element ->
            val id = ID_REGEX.find(element.value)?.groupValues?.get(1)
            val factory = FACTORY_REGEX.find(element.value)?.groupValues?.get(1)
            if (id != null && factory != null) id to factory else null
        }.toList()

    private fun declaredToolWindowIds(): Set<String> =
        declaredToolWindows().map { it.first }.toSet()

    /**
     * `path relative to src/main/kotlin -> getToolWindow("<literal>") argument`
     * for every hardcoded lookup in production code. Comments are stripped
     * first, since KDoc quotes these calls.
     */
    private fun hardcodedToolWindowLookups(): Map<String, List<String>> {
        if (!mainSrcRoot.exists()) return emptyMap()
        val lookups = linkedMapOf<String, List<String>>()
        mainSrcRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val stripped = stripComments(file.readText())
            val literals = GET_TOOL_WINDOW_REGEX.findAll(stripped).map { it.groupValues[1] }.toList()
            if (literals.isNotEmpty()) {
                val relativePath = file.path.replace('\\', '/').removePrefix("src/main/kotlin/")
                lookups[relativePath] = literals
            }
        }
        return lookups
    }

    /** Mirrors `AntiPatternGateTest.stripComments`. */
    private fun stripComments(content: String): String =
        Regex("""//[^\n]*""").replace(
            Regex("""/\*[\s\S]*?\*/""").replace(content, ""),
            ""
        )

    private fun loadFromClasspath(resourcePath: String): String {
        val stream = javaClass.getResourceAsStream("/$resourcePath")
            ?: error(
                "/$resourcePath not found on the test classpath — expected the file to " +
                    "live at src/main/resources/$resourcePath"
            )
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private companion object {
        const val EXPLORER_FACTORY_CLASS =
            "com.itangcent.easyapi.core.dashboard.ApiDashboardToolWindowFactory"

        /** Attribute blobs of every `<toolWindow …>` element (no nested elements). */
        val TOOL_WINDOW_REGEX = Regex("""<toolWindow\b([^>]*?)/>""", RegexOption.DOT_MATCHES_ALL)
        val ID_REGEX = Regex("""\bid\s*=\s*"([^"]*)"""")
        val FACTORY_REGEX = Regex("""\bfactoryClass\s*=\s*"([^"]*)"""")

        /** `getToolWindow("API Explorer")` — the literal form only; constants pass through. */
        val GET_TOOL_WINDOW_REGEX = Regex("""getToolWindow\s*\(\s*"([^"]*)"\s*\)""")
    }
}
