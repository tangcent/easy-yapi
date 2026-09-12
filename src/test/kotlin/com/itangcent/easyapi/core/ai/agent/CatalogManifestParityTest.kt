package com.itangcent.easyapi.core.ai.agent

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards `ai/catalog-manifest.txt` against drifting from the files on disk.
 *
 * The manifest is the catalog's index: [PromptCatalog] only exposes entries
 * that are listed in it, and it loads each listed path blindly — a path that
 * does not exist is skipped with a `LOG.warn`, never an error. A stale index
 * therefore degrades the agent **silently** instead of failing loudly, which
 * is why this needs a tripwire.
 *
 * Both directions are asserted:
 *  - **forward** — every path listed in the manifest exists under
 *    `src/main/resources/` (no ghost ids).
 *  - **reverse** — every markdown file under `ai/detection/` and
 *    `ai/key-guides/` is listed (no orphan guide the agent can never fetch).
 *
 * No IDE / PSI dependency — Pattern A (simple JUnit 4).
 */
class CatalogManifestParityTest {

    private val repoRoot: File by lazy { File(System.getProperty("user.dir")) }
    private val resourcesDir: File by lazy { repoRoot.resolve("src/main/resources") }
    private val aiDir: File by lazy { resourcesDir.resolve("ai") }
    private val manifest: File by lazy { aiDir.resolve("catalog-manifest.txt") }

    @Test
    fun everyManifestEntryExistsOnDisk() {
        assertTrue("manifest must exist: ${manifest.path}", manifest.isFile)

        val missing = manifestEntries().filterNot { resourcesDir.resolve(it).isFile }
        assertTrue(
            "catalog-manifest.txt lists paths that do not exist on disk. " +
                "PromptCatalog skips them silently, so the agent loses those " +
                "entries without any error. Missing: $missing",
            missing.isEmpty()
        )
    }

    @Test
    fun everyCatalogFileOnDiskIsListedInTheManifest() {
        val listed = manifestEntries().toSet()
        val onDisk = CATALOG_DIRS.flatMap { dir ->
            aiDir.resolve(dir).listFiles()
                ?.filter { it.isFile && it.extension == "md" }
                ?.map { "ai/$dir/${it.name}" }
                .orEmpty()
        }
        assertTrue(
            "expected at least one catalog file under $CATALOG_DIRS (found none " +
                "— has the layout changed?)",
            onDisk.isNotEmpty()
        )

        val unlisted = onDisk.filterNot { it in listed }.sorted()
        assertTrue(
            "these catalog files exist on disk but are not listed in " +
                "catalog-manifest.txt, so PromptCatalog never loads them and " +
                "the agent can never fetch them: $unlisted",
            unlisted.isEmpty()
        )
    }

    /** Manifest lines minus blanks and `#` comments. */
    private fun manifestEntries(): List<String> =
        manifest.readLines(Charsets.UTF_8)
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }

    private companion object {
        val CATALOG_DIRS = listOf("detection", "key-guides")
    }
}
