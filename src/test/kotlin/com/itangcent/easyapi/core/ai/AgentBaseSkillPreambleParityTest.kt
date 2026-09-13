package com.itangcent.easyapi.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the T0 rule-authoring block that is hand-written into **both**
 * always-loaded prompts:
 *
 *  - `src/main/resources/ai/agent-base.md` — the built-in agent preamble.
 *  - `skills/easy-yapi-assistant/SKILL.md` — the external assistant skill.
 *
 * These two surfaces are the only ones that author rule values, so they must
 * teach the *same* rule-file syntax, value formats, and quality rules. The
 * T1/T2 tiers (`rule-guide.md`, `ai/detection/`, `ai/key-guides/`) are kept
 * in sync by Gradle tasks; the T0 block is hand-written, so it is the only
 * tier that can silently drift. This test extracts each shared section by its
 * `## ` heading and asserts the bodies are byte-identical.
 *
 * The sub-agent preamble (`ai/sub-agent-base.md`) is intentionally excluded:
 * sub-agents draft per-key proposals from their own per-key sources
 * (`get_rule_detail` for value formats, `get_existing_rules_for_key` for
 * duplicate detection) plus the short rule-file-format reminder inlined in
 * their own preamble, so they carry no general rule-authoring block.
 *
 * No IDE / PSI dependency — Pattern A (simple JUnit 4).
 */
class AgentBaseSkillPreambleParityTest {

    private val repoRoot: File by lazy { File(System.getProperty("user.dir")) }

    private val agentBase: File by lazy {
        repoRoot.resolve("src/main/resources/ai/agent-base.md")
    }

    private val subAgentBase: File by lazy {
        repoRoot.resolve("src/main/resources/ai/sub-agent-base.md")
    }

    private val skill: File by lazy {
        repoRoot.resolve("skills/easy-yapi-assistant/SKILL.md")
    }

    @Test
    fun sharedRuleAuthoringSectionsAreByteIdentical() {
        assertTrue("built-in preamble must exist: ${agentBase.path}", agentBase.isFile)
        assertTrue("skill must exist: ${skill.path}", skill.isFile)

        val agentSections = sections(agentBase.readText(Charsets.UTF_8))
        val skillSections = sections(skill.readText(Charsets.UTF_8))

        SHARED_HEADINGS.forEach { heading ->
            val agentBody = agentSections[heading]
            val skillBody = skillSections[heading]
            assertTrue(
                "agent-base.md is missing shared T0 section '$heading'",
                agentBody != null
            )
            assertTrue(
                "SKILL.md is missing shared T0 section '$heading' — it must inline " +
                    "the built-in preamble's rule-authoring block verbatim",
                skillBody != null
            )
            assertEquals(
                "Shared T0 section '$heading' drifted between agent-base.md and " +
                    "SKILL.md — keep them byte-identical (see AGENTS/T0 tiering)",
                agentBody,
                skillBody
            )
        }
    }

    /**
     * Reverse of the parity assertion — the sub-agent preamble is deliberately
     * excluded from the shared T0 block, so it must NOT carry any of those
     * sections.
     *
     * Without this, copying the block into `sub-agent-base.md` would leave the
     * parity test green (it only inspects the two T0 surfaces) while teaching
     * every sub-agent a rule-authoring contract it is not supposed to own.
     */
    @Test
    fun subAgentPreambleCarriesNoSharedRuleAuthoringBlock() {
        assertTrue(
            "sub-agent preamble must exist: ${subAgentBase.path}",
            subAgentBase.isFile
        )
        val subAgentSections = sections(subAgentBase.readText(Charsets.UTF_8))

        SHARED_HEADINGS.forEach { heading ->
            assertTrue(
                "sub-agent-base.md must NOT carry the shared T0 section '$heading' — " +
                    "sub-agents draft per-key proposals from `get_rule_detail`, not " +
                    "from the general rule-authoring block (see AGENTS/T0 tiering)",
                subAgentSections[heading] == null
            )
        }
    }

    /**
     * Splits a markdown body into `## `-delimited sections: the heading line
     * plus every following line up to (but excluding) the next `## ` heading.
     * `### ` sub-headings stay inside their parent section. Trailing whitespace
     * is trimmed so a file-final section and a mid-file section compare equal.
     */
    private fun sections(body: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        var heading: String? = null
        val buffer = StringBuilder()
        fun flush() {
            val h = heading ?: return
            result[h] = buffer.toString().trimEnd()
        }
        body.lines().forEach { line ->
            if (line.startsWith("## ")) {
                flush()
                heading = line.trimEnd()
                buffer.setLength(0)
                buffer.appendLine(line)
            } else if (heading != null) {
                buffer.appendLine(line)
            }
        }
        flush()
        return result
    }

    private companion object {
        val SHARED_HEADINGS = listOf(
            "## Rule file format (CRITICAL — follow exactly)",
            "## Writing a rule value — supported formats & when to use each (CRITICAL)",
            "## Writing rules — quality rules (CRITICAL — follow exactly)"
        )
    }
}
