package com.itangcent.easyapi.core.ai.agent

import com.itangcent.easyapi.core.rule.RuleKeyCatalog
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards that every per-key guide is parseable, documents a key that actually
 * exists, and is addressable by exactly one id.
 *
 * `get_rule_detail(key=…)` resolves a guide **by catalog id, before it ever
 * consults the rule-key registry**:
 *
 * ```
 * val guide = PromptCatalog.body("key-guides", key)   // by id — no registry
 * if (guide != null) return ToolResult.Text(guide)
 * val info = RuleKeyRegistry.findKey(key) ?: return ToolResult.Error(...)
 * ```
 *
 * So the guide is served to the model as authoritative, and the same id is
 * advertised in the L0 index (`- id — title: cue`) that tells the agent the
 * guide exists. Three failure modes follow, none of which another guard covers
 * (`CatalogManifestParityTest` checks the file↔manifest relation,
 * `KeyGuideSharedSectionParityTest` the Postman pair's shared prose, and
 * `EasyYapiAssistantSkillTest` the skill mirror — none of them looks at the id):
 *
 * - **unparseable** — the file is on disk *and* listed in `catalog-manifest.txt`,
 *   but `PromptCatalog.buildEntry` returns `null` (missing/malformed `id`,
 *   `title`, or `cue`), so the agent can never fetch it.
 * - **dangling id** — the guide describes a key that is not registered, so the
 *   agent is invited to author a rule with an unknown key.
 * - **duplicate id** — `entry()` is `firstOrNull { it.id == id }`, so a second
 *   file carrying the same id is silently unreachable.
 *
 * No IDE / PSI dependency — Pattern A (simple JUnit 4). The registered-name set
 * is read from [RuleKeyCatalog.SOURCES], which is pure reflection and includes
 * the implicit keys declared in `ImplicitConfigKeys` (e.g. `markdown.curl.host`).
 */
class KeyGuideKeyResolvesTest {

    private val repoRoot: File by lazy { File(System.getProperty("user.dir")) }

    private val catalogDir: File by lazy {
        repoRoot.resolve("src/main/resources/ai/key-guides")
    }

    private val guideFiles: List<File> by lazy {
        catalogDir.listFiles { f -> f.isFile && f.name.endsWith(".md") }
            ?.sortedBy { it.name }
            .orEmpty()
    }

    /** Every name a registered key answers to — primary name plus aliases. */
    private val registeredKeyNames: Set<String> by lazy {
        RuleKeyCatalog.assembledKeyInfos().flatMap { it.first.allNames }.toSet()
    }

    /** `(file name, catalog entry)` for every guide the catalog can parse. */
    private val parsedEntries: List<Pair<String, CatalogEntry>> by lazy {
        guideFiles.mapNotNull { file ->
            val parsed = PromptCatalog.parseFrontMatter(file.readText(Charsets.UTF_8))
                ?: return@mapNotNull null
            PromptCatalog.buildEntry("ai/key-guides/${file.name}", parsed)
                ?.let { file.name to it }
        }
    }

    @Test
    fun everyGuideIsParsedIntoACatalogEntry() {
        assertTrue(
            "expected at least one key-guide under ${catalogDir.path}",
            guideFiles.isNotEmpty()
        )

        val parseable = parsedEntries.map { it.first }.toSet()
        val unparseable = guideFiles.map { it.name }.filterNot { it in parseable }

        assertTrue(
            "these key-guides are on disk and listed in catalog-manifest.txt, but " +
                "PromptCatalog cannot build an entry from them (missing or malformed " +
                "`id` / `title` / `cue` front matter). PromptCatalog skips such a file " +
                "with a LOG.warn, so get_rule_detail can never fetch it and the agent " +
                "never sees it: $unparseable",
            unparseable.isEmpty()
        )
    }

    @Test
    fun everyGuideIdIsARegisteredKeyName() {
        assertTrue(
            "RuleKeyCatalog.assembledKeyInfos() returned no names at all — the runtime " +
                "lookup would resolve nothing and this guard would pass vacuously",
            registeredKeyNames.isNotEmpty()
        )

        val dangling = parsedEntries
            .filterNot { (_, entry) -> entry.id in registeredKeyNames }
            .map { (file, entry) -> "$file (id=${entry.id})" }

        assertTrue(
            "these key-guides document a rule key that is not registered anywhere, so the " +
                "agent is invited to author a rule with an unknown key. Declare the key in a " +
                "`*RuleKeys` object or in ImplicitConfigKeys, or delete the guide: $dangling",
            dangling.isEmpty()
        )
    }

    @Test
    fun everyGuideRepeatsItsIdInTheKeyField() {
        val mismatched = parsedEntries
            .filter { (_, entry) -> entry.key != entry.id }
            .map { (file, entry) -> "$file (id=${entry.id}, key=${entry.key})" }

        assertTrue(
            "the `key` front-matter field must repeat the id: the by-key lookup and the L0 " +
                "index both address a guide by its **id**, so a differing `key` describes a " +
                "key the reader cannot fetch: $mismatched",
            mismatched.isEmpty()
        )
    }

    @Test
    fun guideIdsAreUnique() {
        val duplicated = parsedEntries
            .map { it.second.id }
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys

        assertTrue(
            "these key-guide ids are claimed by more than one file, and " +
                "PromptCatalog.entry() is `firstOrNull { it.id == id }` — one of them is " +
                "silently unreachable: $duplicated",
            duplicated.isEmpty()
        )
    }
}
