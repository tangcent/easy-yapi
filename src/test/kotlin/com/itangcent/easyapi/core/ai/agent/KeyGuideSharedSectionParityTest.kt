package com.itangcent.easyapi.core.ai.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Drift guard for the content the two Postman hook guides deliberately share.
 *
 * `ai/key-guides/postman.test.md` and `ai/key-guides/postman.prerequest.md`
 * overlap heavily. That is **load-bearing, not accidental**: `get_rule_detail(key=…)`
 * returns ONE guide, and swapping the two hooks is the single most common
 * workflow-rule error — a guide that merely linked to its sibling would lose the
 * warning exactly when the agent needs it. So the shared warnings are copied
 * instead of linked.
 *
 * The two files are edited independently, which is how copies drift. Two guards
 * cover the risk in both of its forms:
 *
 * - [sharedSectionsAreByteIdentical] — sections that are verbatim copies today
 *   must stay verbatim. A wording tweak in one file has to be made in both.
 * - [sharedWarningsAppearInBothGuides] — warnings that are *adapted* per guide
 *   (each names its own bundle, its own hook order) cannot be compared verbatim,
 *   so pin the sentence that must survive in both.
 *
 * No IDE / PSI dependency — Pattern A (simple JUnit 4).
 */
class KeyGuideSharedSectionParityTest {

    private val repoRoot: File by lazy { File(System.getProperty("user.dir")) }

    private val postmanTest: File by lazy {
        repoRoot.resolve("src/main/resources/ai/key-guides/postman.test.md")
    }

    private val postmanPrerequest: File by lazy {
        repoRoot.resolve("src/main/resources/ai/key-guides/postman.prerequest.md")
    }

    private val testBody: String by lazy { read(postmanTest) }

    private val prerequestBody: String by lazy { read(postmanPrerequest) }

    @Test
    fun sharedSectionsAreByteIdentical() {
        val testSections = sections(testBody)
        val prerequestSections = sections(prerequestBody)

        VERBATIM_SHARED_HEADINGS.forEach { heading ->
            val fromTest = testSections[heading]
            val fromPrerequest = prerequestSections[heading]
            assertTrue(
                "postman.test.md is missing the shared section '$heading'",
                fromTest != null
            )
            assertTrue(
                "postman.prerequest.md is missing the shared section '$heading'",
                fromPrerequest != null
            )
            assertEquals(
                "Shared section '$heading' drifted between the two Postman hook " +
                    "guides. It is duplicated on purpose (each guide is fetched in " +
                    "isolation), so keep it byte-identical — or update both files in " +
                    "the same change.",
                fromTest,
                fromPrerequest
            )
        }
    }

    /**
     * Each invariant is a sentence that must be present in **both** guides, in
     * any surrounding wording. Removing one from a single guide would leave the
     * agent unprotected whenever it fetches that guide alone.
     */
    @Test
    fun sharedWarningsAppearInBothGuides() {
        val testText = normalized(testBody)
        val prerequestText = normalized(prerequestBody)

        SHARED_INVARIANTS.forEach { invariant ->
            val needle = normalized(invariant)
            assertTrue(
                "postman.test.md must carry the shared warning \"$invariant\" — " +
                    "it is duplicated on purpose because get_rule_detail returns " +
                    "one guide at a time",
                testText.contains(needle)
            )
            assertTrue(
                "postman.prerequest.md must carry the shared warning \"$invariant\"",
                prerequestText.contains(needle)
            )
        }
    }

    private fun read(file: File): String {
        assertTrue("guide must exist: ${file.path} (run from the project root)", file.isFile)
        return file.readText(Charsets.UTF_8)
    }

    /** Case- and whitespace-insensitive so wording reflow doesn't break the guard. */
    private fun normalized(text: String): String =
        text.lowercase().replace(Regex("\\s+"), " ")

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
        /** Sections that are verbatim copies in both guides today. */
        val VERBATIM_SHARED_HEADINGS = listOf(
            "## postman.test vs postman.prerequest (#1 mistake)",
            "## No hardcoded secrets"
        )

        /**
         * Warnings that are adapted per guide (each names its own bundle / hook
         * order) and therefore cannot be compared verbatim.
         */
        val SHARED_INVARIANTS = listOf(
            // Which hook fires when — the #1 mistake this pair guards against.
            "fires AFTER the response",
            "fires BEFORE the",
            // The silent-failure trap: a groovy: prefix routes Postman values
            // to Jsr223ScriptParser, where `pm` is unbound.
            "A `groovy:` prefix routes the value to `Jsr223ScriptParser`",
            "silently swallowed",
            // Chain/bundle integrity: half a bundle is a silent bug.
            "Proposing half a chain is forbidden",
            // Never emit a literal credential.
            "Never emit a literal token, key, or password",
            // Check for existing rules before proposing.
            "get_existing_rules_for_key"
        )
    }
}
