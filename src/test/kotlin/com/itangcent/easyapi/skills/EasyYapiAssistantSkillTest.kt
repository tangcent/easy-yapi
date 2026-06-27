package com.itangcent.easyapi.skills

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import java.io.File

/**
 * Asserts that the external EasyYapi assistant skill ships in the
 * repository and points at the authoritative documentation and the rule
 * key catalog. If a future doc move breaks any pointer, this test fails
 * before the release.
 */
class EasyYapiAssistantSkillTest : EasyApiLightCodeInsightFixtureTestCase() {

    private val skillFile: File by lazy {
        File(System.getProperty("user.dir")).resolve("skills/easy-yapi-assistant/SKILL.md")
    }

    fun testSkillFileExists() {
        assertTrue(
            "skill file must ship at skills/easy-yapi-assistant/SKILL.md",
            skillFile.isFile
        )
    }

    fun testSkillBodyReferencesRuleGuide() {
        val body = skillBody()
        assertTrue(
            "skill must reference docs/knowledge-base/rule-guide.md (the authoritative rule guide)",
            body.contains("docs/knowledge-base/rule-guide.md")
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
