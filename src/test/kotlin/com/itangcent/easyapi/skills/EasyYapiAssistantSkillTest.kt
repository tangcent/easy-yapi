package com.itangcent.easyapi.skills

import com.itangcent.easyapi.core.rule.ImplicitConfigKeys
import com.itangcent.easyapi.core.rule.RuleKeyRegistry
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.tooling.RuleKeySchemeExporter
import com.itangcent.easyapi.tooling.RuleContextExporter
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.io.File

/**
 * Asserts that the external EasyYapi assistant skill ships in the
 * repository and mirrors the built-in agent's knowledge base and
 * rule-key catalog. If a future doc move breaks any pointer, this test
 * fails before the release.
 *
 * The `docs/knowledge-base/` folder at the repo root is the single source
 * of truth; the skill's bundled `docs/` folder and the plugin resources
 * should both carry verbatim copies (kept in sync by the `syncKnowledgeBase`
 * Gradle task).
 */
class EasyYapiAssistantSkillTest : EasyApiLightCodeInsightFixtureTestCase() {

    private val repoRoot: File by lazy { File(System.getProperty("user.dir")) }

    private val skillDir: File by lazy { repoRoot.resolve("skills/easy-yapi-assistant") }

    /** Bundled knowledge-base docs live under `docs/` next to SKILL.md. */
    private val skillDocsDir: File by lazy { skillDir.resolve("docs") }

    /** Bundled per-recipe catalog mirror (R3-C3). */
    private val skillAiDir: File by lazy { skillDir.resolve("ai") }

    private val skillScriptsDir: File by lazy { skillDir.resolve("scripts") }

    private val skillFile: File by lazy { skillDir.resolve("SKILL.md") }

    fun testSkillFileExists() {
        assertTrue(
            "skill file must ship at skills/easy-yapi-assistant/SKILL.md",
            skillFile.isFile
        )
    }

    fun testSkillBodyReferencesRuleGuide() {
        val body = skillBody()
        assertTrue(
            "skill must reference the bundled rule-guide.md (the authoritative rule guide)",
            body.contains("rule-guide.md")
        )
        assertTrue(
            "skill must document the syncKnowledgeBase Gradle task that keeps the bundled copy in sync",
            body.contains("syncKnowledgeBase")
        )
    }

    fun testSkillBodyReferencesRuleKeysCatalog() {
        val body = skillBody()
        assertTrue(
            "skill must reference the auto-generated rule-keys.json (scheme catalog pointer)",
            body.contains("rule-keys.json")
        )
        assertTrue(
            "skill must document the syncRuleKeySchemes Gradle task that regenerates the catalog",
            body.contains("syncRuleKeySchemes")
        )
    }

    fun testRuleKeyCatalogFilesExist() {
        assertTrue(
            "skill must ship the auto-generated rule-keys.json (run ./gradlew syncRuleKeySchemes)",
            skillDir.resolve("rule-keys.json").isFile
        )
        assertTrue(
            "skill must ship the auto-generated rule-keys.md (run ./gradlew syncRuleKeySchemes)",
            skillDir.resolve("rule-keys.md").isFile
        )
    }

    /**
     * The committed `rule-keys.json`/`rule-keys.md` are generated from the
     * code by [RuleKeySchemeExporter]. Guard them against drift: if they were
     * not regenerated after a rule-key change, this fails and forces a
     * `./gradlew syncRuleKeySchemes` re-run before commit.
     */
    fun testRuleKeyCatalogIsFreshFromExporter() {
        val keys = RuleKeySchemeExporter.collectKeys()
        assertEquals(
            "skills/easy-yapi-assistant/rule-keys.json is stale — run ./gradlew syncRuleKeySchemes",
            RuleKeySchemeExporter.toJson(keys),
            ruleKeyJson()
        )
        assertEquals(
            "skills/easy-yapi-assistant/rule-keys.md is stale — run ./gradlew syncRuleKeySchemes",
            RuleKeySchemeExporter.toMarkdown(keys),
            ruleKeyMarkdown()
        )
    }

    /**
     * The committed JSON catalog must cover every rule key the runtime
     * registry knows (all channels + frameworks + implicit). A brand-new
     * channel/framework registered in the registry but absent from
     * [RuleKeyCatalog.SOURCES] fails here — add it to SOURCES and
     * re-run `syncRuleKeySchemes`.
     */
    fun testRuleKeyJsonCoversEveryRegistrySource() {
        val registryKeys = RuleKeyRegistry.getInstance(project).allKeys()
        assertTrue("registry must resolve at least the general keys", registryKeys.isNotEmpty())
        val exported = ruleKeyJsonBySource()
        val missing = registryKeys.map { it.key.name }.filterNot { it in exported }
        assertTrue(
            "rule-keys.json must list every registered rule key; missing: $missing",
            missing.isEmpty()
        )
        registryKeys.forEach { info ->
            exported[info.key.name]?.let { jsonSource ->
                assertEquals(
                    "source mismatch for ${info.key.name} between registry and rule-keys.json",
                    info.source, jsonSource
                )
            }
        }
    }

    /**
     * The "implicit" source of `rule-keys.json` must list exactly
     * [ImplicitConfigKeys.all] — the keys read by name via
     * `ConfigReader.getFirst(...)` with no RuleKey constant. This pins the
     * ConfigReader.getFirst audit to the generated catalog.
     */
    fun testRuleKeyJsonImplicitSourceMatchesImplicitKeys() {
        val implicitNames = ruleKeyJsonBySource().filterValues { it == "implicit" }.keys
        val expected = ImplicitConfigKeys.all.map { it.name }.toSet()
        assertEquals(
            "rule-keys.json implicit source must list exactly the ImplicitConfigKeys",
            expected, implicitNames
        )
    }

    fun testSkillBodyReferencesEasyapiFolder() {
        val body = skillBody()
        assertTrue(
            "skill must reference the .easyapi/ folder model",
            body.contains(".easyapi/")
        )
    }

    fun testFrontmatterHasNameAndDescription() {
        val (name, description) = parseFrontmatter(skillBody())
        assertTrue("frontmatter `name` must be non-empty", name.isNotBlank())
        assertTrue("frontmatter `description` must be non-empty", description.isNotBlank())
    }

    /**
     * Every knowledge-base page must be bundled verbatim under the skill's
     * `docs/` folder so the external skill mirrors the built-in agent's
     * `get_plugin_doc` surface.
     */
    fun testSkillBundlesFullKnowledgeBase() {
        val canonicalDir = repoRoot.resolve("docs/knowledge-base")
        assertTrue("canonical docs/knowledge-base/ must exist", canonicalDir.isDirectory)
        val expected = canonicalDir.listFiles { f -> f.isFile && f.name.endsWith(".md") }
            ?.map { it.name } ?: emptyList()
        assertTrue("knowledge-base must contain at least rule-guide.md", expected.contains("rule-guide.md"))
        assertTrue("skill docs/ subfolder must exist", skillDocsDir.isDirectory)
        expected.forEach { name ->
            val bundled = skillDocsDir.resolve(name)
            assertTrue("skill must bundle knowledge-base page in docs/: $name", bundled.isFile)
            assertEquals(
                "skill's docs/$name must match the canonical copy verbatim (run ./gradlew syncKnowledgeBase)",
                canonicalDir.resolve(name).readText(),
                bundled.readText()
            )
        }
    }

    /**
     * The plugin JAR resources must also carry the verbatim knowledge base.
     */
    fun testPluginResourcesMatchCanonicalKnowledgeBase() {
        val canonicalDir = repoRoot.resolve("docs/knowledge-base")
        val resourceDir = repoRoot.resolve("src/main/resources/docs/knowledge-base")
        assertTrue("plugin resource dir must exist: $resourceDir", resourceDir.isDirectory)
        canonicalDir.listFiles { f -> f.isFile && f.name.endsWith(".md") }
            ?.forEach { canonical ->
                val res = resourceDir.resolve(canonical.name)
                assertTrue("plugin resources must include ${canonical.name}", res.isFile)
                assertEquals(
                    "plugin resource ${canonical.name} must match canonical (run ./gradlew syncKnowledgeBase)",
                    canonical.readText(),
                    res.readText()
                )
            }
    }

    // -------------------------------------------------------------------------
    // R3-C3 — per-recipe catalog mirror (ai/detection/ + ai/key-guides/)
    // -------------------------------------------------------------------------

    /**
     * Every `src/main/resources/ai/detection/` markdown file must be bundled
     * verbatim under `skills/easy-yapi-assistant/ai/detection/` so the external
     * skill mirrors the in-plugin agent's `get_detection_prompt` surface.
     */
    fun testSkillBundlesDetectionCatalog() {
        val canonicalDir = repoRoot.resolve("src/main/resources/ai/detection")
        assertTrue("canonical ai/detection/ must exist: $canonicalDir", canonicalDir.isDirectory)
        val expected = canonicalDir.listFiles { f -> f.isFile && f.name.endsWith(".md") }
            ?.map { it.name } ?: emptyList()
        assertTrue("ai/detection/ must contain at least one .md file", expected.isNotEmpty())
        val skillDetectionDir = skillAiDir.resolve("detection")
        assertTrue("skill ai/detection/ subfolder must exist: $skillDetectionDir", skillDetectionDir.isDirectory)
        expected.forEach { name ->
            val bundled = skillDetectionDir.resolve(name)
            assertTrue("skill must bundle detection catalog file: ai/detection/$name", bundled.isFile)
            assertEquals(
                "skill's ai/detection/$name must match the canonical copy verbatim (run ./gradlew syncAgentCatalog)",
                canonicalDir.resolve(name).readText(),
                bundled.readText()
            )
        }
    }

    /**
     * Every `src/main/resources/ai/key-guides/` markdown file must be bundled
     * verbatim under `skills/easy-yapi-assistant/ai/key-guides/` so the external
     * skill mirrors the in-plugin agent's `get_rule_detail` surface.
     */
    fun testSkillBundlesKeyGuideCatalog() {
        val canonicalDir = repoRoot.resolve("src/main/resources/ai/key-guides")
        assertTrue("canonical ai/key-guides/ must exist: $canonicalDir", canonicalDir.isDirectory)
        val expected = canonicalDir.listFiles { f -> f.isFile && f.name.endsWith(".md") }
            ?.map { it.name } ?: emptyList()
        assertTrue("ai/key-guides/ must contain at least one .md file", expected.isNotEmpty())
        val skillKeyGuidesDir = skillAiDir.resolve("key-guides")
        assertTrue("skill ai/key-guides/ subfolder must exist: $skillKeyGuidesDir", skillKeyGuidesDir.isDirectory)
        expected.forEach { name ->
            val bundled = skillKeyGuidesDir.resolve(name)
            assertTrue("skill must bundle key-guide catalog file: ai/key-guides/$name", bundled.isFile)
            assertEquals(
                "skill's ai/key-guides/$name must match the canonical copy verbatim (run ./gradlew syncAgentCatalog)",
                canonicalDir.resolve(name).readText(),
                bundled.readText()
            )
        }
    }

    /**
     * The R3-C3 catalog CLI scripts must ship and be executable.
     */
    fun testSkillBundlesCatalogCliScripts() {
        val expected = listOf(
            "get_detection_prompt.sh",
            "get_key_guide.sh",
            "list_detections.sh",
            "get_key_context.sh"
        )
        assertTrue("skill scripts/ folder must exist: $skillScriptsDir", skillScriptsDir.isDirectory)
        expected.forEach { name ->
            val script = skillScriptsDir.resolve(name)
            assertTrue("skill must bundle catalog script: scripts/$name", script.isFile)
            assertTrue(
                "scripts/$name must be executable (chmod +x) — run: chmod +x skills/easy-yapi-assistant/scripts/$name",
                script.canExecute()
            )
        }
    }

    /**
     * SKILL.md must document the catalog scripts and the syncAgentCatalog
     * Gradle task that keeps the mirror in sync.
     */
    fun testSkillBodyDocumentsCatalogScriptsAndSyncTask() {
        val body = skillBody()
        assertTrue(
            "skill must document get_detection_prompt.sh",
            body.contains("get_detection_prompt.sh")
        )
        assertTrue(
            "skill must document get_key_guide.sh (the single-key guide fetcher)",
            body.contains("get_key_guide.sh")
        )
        assertTrue(
            "skill must document list_detections.sh",
            body.contains("list_detections.sh")
        )
        assertTrue(
            "skill must document get_key_guide.sh --list (the key-guide index)",
            body.contains("get_key_guide.sh --list")
        )
        assertTrue(
            "skill must document the syncAgentCatalog Gradle task that keeps the bundled catalog in sync",
            body.contains("syncAgentCatalog")
        )
        assertTrue(
            "skill must document the aggregated syncSkill Gradle task",
            body.contains("syncSkill")
        )
    }

    // -------------------------------------------------------------------------
    // Script-context catalog mirror (rule-contexts.json/.md + get_key_context.sh)
    // -------------------------------------------------------------------------

    fun testSkillBodyReferencesRuleContextsCatalog() {
        val body = skillBody()
        assertTrue(
            "skill must reference the auto-generated rule-contexts.json (script-context catalog pointer)",
            body.contains("rule-contexts.json")
        )
        assertTrue(
            "skill must document the syncRuleContexts Gradle task that regenerates the catalog",
            body.contains("syncRuleContexts")
        )
        assertTrue(
            "skill must document get_key_context.sh (the get_rule_context mirror)",
            body.contains("get_key_context.sh")
        )
    }

    fun testRuleContextCatalogFilesExist() {
        assertTrue(
            "skill must ship the auto-generated rule-contexts.json (run ./gradlew syncRuleContexts)",
            skillDir.resolve("rule-contexts.json").isFile
        )
        assertTrue(
            "skill must ship the auto-generated rule-contexts.md (run ./gradlew syncRuleContexts)",
            skillDir.resolve("rule-contexts.md").isFile
        )
    }

    /**
     * The committed `rule-contexts.json`/`rule-contexts.md` are generated from
     * the code by [RuleContextExporter] (which reflects the same key assembly as
     * [RuleKeySchemeExporter]). Guard them against drift: a rule-key or
     * script-wrapper change without a `./gradlew syncRuleContexts` re-run fails
     * here.
     */
    fun testRuleContextCatalogIsFreshFromExporter() {
        val profiles = RuleContextExporter.collectProfiles()
        assertEquals(
            "skills/easy-yapi-assistant/rule-contexts.json is stale — run ./gradlew syncRuleContexts",
            RuleContextExporter.toJson(profiles),
            ruleContextJson()
        )
        assertEquals(
            "skills/easy-yapi-assistant/rule-contexts.md is stale — run ./gradlew syncRuleContexts",
            RuleContextExporter.toMarkdown(profiles),
            ruleContextMarkdown()
        )
    }

    /**
     * The committed JSON's per-key profiles must cover every rule key the
     * runtime registry knows (all channels + frameworks + implicit) — proven
     * transitively by the scheme guard above, but re-asserted here so a key
     * added to `RuleKeyCatalog.SOURCES` but missing from the
     * script-context catalog fails loudly.
     */
    fun testRuleContextJsonCoversEveryRegistrySource() {
        val registryNames = RuleKeyRegistry.getInstance(project).allKeys().map { it.key.name }.toSet()
        val profileNames = ruleContextProfiles().map { profile -> profile.get("key").asString }.toSet()
        val missing = registryNames.filterNot { it in profileNames }
        assertTrue(
            "rule-contexts.json must profile every registered rule key; missing: $missing",
            missing.isEmpty()
        )
    }

    /**
     * Every `objectRefs` id a profile references must resolve to the
     * shared `objects` dictionary — the external skill can reconstruct the
     * exact `get_rule_context` method signatures from the deduped dictionary.
     */
    fun testRuleContextObjectRefsAllResolve() {
        val json = ruleContextJsonElement()
        val dict = json.asJsonObject.get("objects").asJsonObject
        val dictIds = dict.keySet()
        json.asJsonObject.get("profiles").asJsonArray.forEach { profileEl ->
            val profile = profileEl.asJsonObject
            profile.get("objectRefs").asJsonArray.forEach { ref ->
                assertTrue(
                    "rule-contexts.json objectRef '${ref.asString}' (key=${profile.get("key").asString}) must exist in the shared objects dictionary",
                    ref.asString in dictIds
                )
            }
        }
        assertTrue(
            "rule-contexts.json must ship at least one shared script-object API",
            dictIds.isNotEmpty()
        )
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun skillBody(): String {
        assertTrue("skill file must exist before reading", skillFile.isFile)
        return skillFile.readText(Charsets.UTF_8)
    }

    /** Parses a simple YAML frontmatter block (--- delimited). */
    private fun parseFrontmatter(body: String): Pair<String, String> {
        val start = body.indexOf("---")
        assertTrue("frontmatter must start with ---", start >= 0)
        val end = body.indexOf("---", startIndex = start + 3)
        assertTrue("frontmatter must end with ---", end > start)
        val yaml = body.substring(start + 3, end)
        val name = regexFind(yaml, """(?m)^name:\s*"?(.+?)"?\s*$""") ?: ""
        val description = regexFind(yaml, """(?m)^description:\s*"?(.+?)"?\s*$""") ?: ""
        return name to description
    }

    private fun regexFind(input: String, pattern: String): String? {
        val m = Regex(pattern).find(input)
        return m?.groupValues?.getOrNull(1)
    }

    private fun ruleKeyJson(): String =
        skillDir.resolve("rule-keys.json").readText(Charsets.UTF_8)

    private fun ruleKeyMarkdown(): String =
        skillDir.resolve("rule-keys.md").readText(Charsets.UTF_8)

    /** `name -> source` parsed from the generated `rule-keys.json`. */
    private fun ruleKeyJsonBySource(): Map<String, String> =
        JsonParser.parseString(ruleKeyJson()).asJsonArray
            .map { it.asJsonObject.let { o -> o.get("name").asString to o.get("source").asString } }
            .toMap()

    private fun ruleContextJson(): String =
        skillDir.resolve("rule-contexts.json").readText(Charsets.UTF_8)

    private fun ruleContextMarkdown(): String =
        skillDir.resolve("rule-contexts.md").readText(Charsets.UTF_8)

    private fun ruleContextJsonElement(): JsonElement =
        JsonParser.parseString(ruleContextJson())

    private fun ruleContextProfiles(): List<com.google.gson.JsonObject> =
        ruleContextJsonElement().asJsonObject.get("profiles").asJsonArray
            .map { it.asJsonObject }
}
