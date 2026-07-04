package com.itangcent.easyapi.skills

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import java.io.File

/**
 * Asserts that the external EasyApi assistant skill ships in the
 * repository and mirrors the built-in agent's knowledge base and
 * rule-key catalog. If a future doc move breaks any pointer, this test
 * fails before the release.
 *
 * The `docs/knowledge-base/` folder at the repo root is the single source
 * of truth; the skill's bundled `docs/` folder and the plugin resources
 * should both carry verbatim copies (kept in sync by the `syncKnowledgeBase`
 * Gradle task).
 */
class EasyApiAssistantSkillTest : EasyApiLightCodeInsightFixtureTestCase() {

    private val repoRoot: File by lazy { File(System.getProperty("user.dir")) }

    private val skillDir: File by lazy { repoRoot.resolve("skills/easy-yapi-assistant") }

    /** Bundled knowledge-base docs live under `docs/` next to SKILL.md. */
    private val skillDocsDir: File by lazy { skillDir.resolve("docs") }

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
            "skill must reference RuleKeys.kt (the rule key catalog pointer)",
            body.contains("RuleKeys.kt")
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
}
