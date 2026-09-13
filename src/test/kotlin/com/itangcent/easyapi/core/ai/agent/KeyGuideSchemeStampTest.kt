package com.itangcent.easyapi.core.ai.agent

import com.itangcent.easyapi.core.rule.RuleKeyCatalog
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import kotlin.reflect.full.memberProperties

/**
 * Guards that every per-key guide is **stamped** with the scheme of the key it
 * documents, and that the stamp still matches the live scheme.
 *
 * `KeyGuideKeyResolvesTest` pins a guide's *identity* (parseable, id is a
 * registered key, `key` repeats the id, ids unique) but nothing binds a guide
 * to the key's *contents*. That leaves one unguarded failure mode, and it is
 * the common one: **the key's scheme is edited and the guide that describes it
 * is not revisited.** The guide then keeps being served as authoritative by
 * `get_rule_detail` while describing behaviour the key no longer has — a guide
 * is only as good as its last review, and review is the one thing no test can
 * do.
 *
 * So each guide's front matter carries `scheme-stamp`: a short digest of the
 * key's live [RuleKeyCatalog.SchemeEntry] — the *same* entry `list_rule_keys`
 * prints and `rule-keys.json` publishes (`name`, `source`, `summary`, context
 * kinds, output shape, dry-runnable, static-configuration, aliases, type,
 * mode, additional bindings, `jsonValue`, notes).
 *
 * The stamp fires in **both** directions of the pairing, which is why it beats
 * a narrower pin:
 *
 * - the scheme changed → the prose may now be **wrong**;
 * - the scheme gained a field that the guide existed to carry → the guide may
 *   now be **redundant**. `ai.md` §9.3 calls this the tripwire that keeps the
 *   scheme growing instead of collecting a fourth copy of the same guide.
 *
 * The remedy for both is the same and cheap: re-read the guide, then set the
 * stamp to the value this test prints. The stamp covers the *scheme*, never
 * the guide body, so editing prose alone never moves it.
 *
 * No IDE / PSI dependency — Pattern A (simple JUnit 4). The scheme catalog is
 * read from [RuleKeyCatalog], which is pure reflection.
 */
class KeyGuideSchemeStampTest {

    private companion object {
        /** Front-matter field pinning the scheme the guide was reviewed against. */
        const val STAMP_FIELD = "scheme-stamp"

        /** Digest length in bytes (48 bits of SHA-256 — ample for a drift tripwire). */
        const val DIGEST_BYTES = 6
    }

    private val repoRoot: File by lazy { File(System.getProperty("user.dir")) }

    private val catalogDir: File by lazy {
        repoRoot.resolve("src/main/resources/ai/key-guides")
    }

    private val guideFiles: List<File> by lazy {
        catalogDir.listFiles { f -> f.isFile && f.name.endsWith(".md") }
            ?.sortedBy { it.name }
            .orEmpty()
    }

    /** One guide's front matter, reduced to what this guard needs. */
    private data class Guide(
        val file: String,
        val id: String?,
        val key: String?,
        val stamp: String?
    )

    private val guides: List<Guide> by lazy {
        guideFiles.map { file ->
            val header = PromptCatalog
                .parseFrontMatter(file.readText(Charsets.UTF_8))
                ?.header
                .orEmpty()
            Guide(
                file = file.name,
                id = header["id"] as? String,
                key = header["key"] as? String,
                stamp = header[STAMP_FIELD] as? String
            )
        }
    }

    /**
     * Key name → the live published scheme entry, for every registered key.
     *
     * Bridged exactly as `RuleKeySchemeExporter.collectKeys()` does, so the
     * stamp pins the same catalog the external skill's `rule-keys.json` ships.
     */
    private val liveSchemes: Map<String, RuleKeyCatalog.SchemeEntry> by lazy {
        RuleKeyCatalog.schemeEntries(
            RuleKeyCatalog.assembledKeyInfos().map { (key, source) ->
                RuleKeyCatalog.RuleKeyInfo(key, source)
            }
        ).associateBy { it.name }
    }

    @Test
    fun everyGuideStampsTheLiveSchemeOfItsKey() {
        assertTrue(
            "expected at least one key-guide under ${catalogDir.path}",
            guideFiles.isNotEmpty()
        )
        assertTrue(
            "RuleKeyCatalog.schemeEntries(assembledKeyInfos()) produced no entries, so no " +
                "guide's key could be resolved and this guard would report every guide as " +
                "unknown rather than checking any stamp",
            liveSchemes.isNotEmpty()
        )

        val problems = guides.mapNotNull { guide ->
            val entry = guide.key?.let { liveSchemes[it] }
            if (entry == null) {
                return@mapNotNull "${guide.file} — front matter `key: " +
                    "${guide.key ?: "<missing>"}` is not a key in the scheme catalog"
            }
            val expected = stamp(entry)
            if (guide.stamp == expected) {
                null
            } else {
                "${guide.file} — declared=${guide.stamp ?: "<missing>"} expected=$expected\n" +
                    render(entry).prependIndent("      ")
            }
        }

        assertTrue(
            "these key-guides are out of step with the live rule-key scheme. `$STAMP_FIELD` " +
                "pins the exact SchemeEntry of the guide's key at the moment the guide was " +
                "last reviewed. A mismatch or a missing field means the key moved on after " +
                "the guide was written. Re-read the guide against the scheme below, fix the " +
                "prose if the change made it wrong — or delete the guide if a new scheme " +
                "field now carries what the guide carried (ai.md §9.3) — then set " +
                "`$STAMP_FIELD` to the printed `expected` value. Editing the prose alone " +
                "does not move the stamp: it tracks the scheme, not the guide.\n" +
                problems.joinToString("\n"),
            problems.isEmpty()
        )
    }

    /**
     * The stamp is only worth having if it is sensitive to *every* field of the
     * entry it fingerprints. Adding a property to `SchemeEntry` without adding
     * it to [render] would leave that property invisible to the digest and
     * silence this guard for any change that touches only it.
     */
    @Test
    fun theFingerprintCoversEverySchemeEntryField() {
        val sample = liveSchemes.values.firstOrNull()
        assertTrue(
            "no scheme entries to fingerprint — see everyGuideStampsTheLiveSchemeOfItsKey",
            sample != null
        )

        val rendered = render(sample!!)
        val lines = rendered.lineSequence().toList()
        val missing = RuleKeyCatalog.SchemeEntry::class.memberProperties
            .map { it.name }
            .filterNot { name -> lines.any { it.startsWith("$name=") } }

        assertTrue(
            "SchemeEntry carries field(s) that `render(…)` does not fingerprint, so a change " +
                "to them would leave every guide's `$STAMP_FIELD` untouched and this guard " +
                "silent. Add a line for each: $missing",
            missing.isEmpty()
        )
    }

    // -------------------------------------------------------------------
    // Fingerprint (pure — the digest is only as good as this rendering)
    // -------------------------------------------------------------------

    /**
     * Canonical, order-stable rendering of [entry]. Every field of
     * [RuleKeyCatalog.SchemeEntry] appears as its own `name=value` line, and
     * the digest is taken over this text.
     */
    private fun render(entry: RuleKeyCatalog.SchemeEntry): String = buildString {
        appendLine("name=${entry.name}")
        appendLine("source=${entry.source}")
        appendLine("summary=${entry.summary}")
        appendLine("contextKinds=${entry.contextKinds.joinToString(",")}")
        appendLine("outputShape=${entry.outputShape}")
        appendLine("dryRunnable=${entry.dryRunnable}")
        appendLine("staticConfiguration=${entry.staticConfiguration}")
        appendLine("aliases=${entry.aliases.joinToString(",")}")
        appendLine("type=${entry.type}")
        appendLine("mode=${entry.mode}")
        appendLine(
            "additionalBindings=" +
                entry.additionalBindings.joinToString(",") { "${it.name}:${it.kind}" }
        )
        appendLine("jsonValue=${entry.jsonValue}")
        appendLine("notes=${entry.notes.joinToString("\u001F")}")
    }

    /** Short hex digest of [rendered]. */
    private fun digest(rendered: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(rendered.toByteArray(Charsets.UTF_8))
            .take(DIGEST_BYTES)
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private fun stamp(entry: RuleKeyCatalog.SchemeEntry): String = digest(render(entry))
}
