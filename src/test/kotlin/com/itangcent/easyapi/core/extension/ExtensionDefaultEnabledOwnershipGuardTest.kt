package com.itangcent.easyapi.core.extension

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards "exactly one class decides whether an extension is on".
 *
 * `ExtensionConfig.defaultEnabled` is an input to the code-list grammar, not a
 * neutral property of a catalogue entry: whoever reads it is one step away from
 * re-implementing the grammar. That is not hypothetical — the Extensions tab and
 * `ExtensionConfigSource` each grew their own three-way filter, and the tab's copy
 * persisted a positive-only list that could not express a deselection, which is
 * issue #1461.
 *
 * The grammar now lives in [ExtensionConfigRegistry] alone, and the flag is read in
 * exactly one expression (`isEnabled`). Two kinds of read survive, and only these
 * two are allowed:
 *
 * - **deciding** ([deciders]) — the grammar owner;
 * - **displaying** ([displayers]) — the settings preview prints the declared
 *   default of the entry it is already rendering, exactly as it renders
 *   `on-class`. It decides nothing.
 *
 * Adding a file to either set has to be a deliberate act, which is the point: it
 * forces the "should this class know that?" question at review time rather than
 * after two copies have drifted apart.
 *
 * Run with: `./gradlew test --tests "*.ExtensionDefaultEnabledOwnershipGuardTest"`
 */
class ExtensionDefaultEnabledOwnershipGuardTest {

    private val mainSrcRoot = File("src/main/kotlin")

    /** The grammar owner. Reads the flag in one expression, to decide. */
    private val deciders = setOf(
        "com/itangcent/easyapi/core/extension/ExtensionConfigRegistry.kt"
    )

    /** Reads the flag to render it, never to decide. */
    private val displayers = setOf(
        "com/itangcent/easyapi/core/settings/ui/SettingsPanels.kt"
    )

    /**
     * `FeatureDescriptor.defaultEnabled` is a different concept that happens to
     * share the name — the feature registry resolves its own defaults inside
     * `core/feature/`, with nothing to do with the extension code list. Out of
     * scope here rather than allowlisted file by file, so that renaming one of the
     * two concepts later does not look like a guard violation.
     */
    private val unrelatedPackages = setOf(
        "com/itangcent/easyapi/core/feature/"
    )

    @Test
    fun onlyTheGrammarOwnerDecidesOnDefaultEnabled() {
        assertTrue("main source root should exist at $mainSrcRoot", mainSrcRoot.exists())

        val offenders = mutableListOf<String>()
        mainSrcRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val path = file.path.replace('\\', '/')
            val relative = path.substringAfter("src/main/kotlin/", path)
            if (relative in deciders || relative in displayers) return@forEach
            if (unrelatedPackages.any { relative.startsWith(it) }) return@forEach

            if (READ.containsMatchIn(stripComments(file.readText()))) offenders.add(relative)
        }

        assertTrue(
            "`ExtensionConfig.defaultEnabled` may only be read by the class that owns the " +
                "code-list grammar (ExtensionConfigRegistry) and by the settings preview that " +
                "displays it. Anywhere else it means a second copy of the selection grammar is " +
                "being written — that is what issue #1461 was.\n" +
                "Delegate to ExtensionConfigRegistry instead: enabledExtensions(codes) to decide, " +
                "selectedCodes(codes) to project onto codes, buildConfig(codes) to project onto " +
                "rule text, encodeSelection(checked) to persist a selection. If a read really is " +
                "legitimate, add the file to `deciders` or `displayers` and say why in the KDoc.\n" +
                "Violations:\n${offenders.joinToString("\n")}",
            offenders.isEmpty()
        )
    }

    /**
     * A guard that silently stops matching would pass forever, so the scanning
     * method itself is asserted: it must see the flag where it is known to be, and
     * it must not be fooled by a mention inside a comment.
     */
    @Test
    fun guardActuallySeesTheFlag() {
        val owner = File(mainSrcRoot, "com/itangcent/easyapi/core/extension/ExtensionConfigRegistry.kt")
        assertTrue("the grammar owner should exist at $owner", owner.exists())
        assertTrue(
            "the guard failed to detect a read it is supposed to guard — the regex or the " +
                "comment stripping has drifted",
            READ.containsMatchIn(stripComments(owner.readText()))
        )

        val commented = "// extension.defaultEnabled stays in the catalogue\n" +
            "/* extension.defaultEnabled likewise here */\n"
        assertFalse(
            "comment mentions must not count as reads",
            READ.containsMatchIn(stripComments(commented))
        )
    }

    private fun stripComments(content: String): String =
        Regex("""/\*[\s\S]*?\*/""").replace(content, "")
            .let { Regex("""//[^\n]*""").replace(it, "") }

    private companion object {
        /** A property read, so a declaration (`val x: Boolean`) does not count. */
        val READ = Regex("""\.defaultEnabled\b""")
    }
}
