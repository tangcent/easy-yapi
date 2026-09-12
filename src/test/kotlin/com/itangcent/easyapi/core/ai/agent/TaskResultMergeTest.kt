package com.itangcent.easyapi.core.ai.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the shape of the orchestrator's merge output.
 *
 * The merged text is what `OrchestratorProposeRuleContentTool` stages, and the
 * user saves it as a `.rules` file — so it must be **rule-file syntax**, not a
 * findings report. `#`-prefixed lines are comments in the rule format
 * (`ConfigTextParser` skips them), which is what lets the merge carry each
 * detection's reasoning without polluting the parsed rules.
 *
 * No IDE / PSI dependency — Pattern A (simple JUnit 4).
 */
class TaskResultMergeTest {

    private fun task(id: String, title: String = "Title of $id") = Task(id = id, title = title)

    @Test
    fun mergeEmitsFindingsAsCommentsAndRulesVerbatim() {
        val ruleLine = "method.additional.header[\$class:com.example.A]=" +
            "{\"name\":\"X-Test\",\"value\":\"1\",\"desc\":\"\",\"required\":true}"
        val merged = mergeTaskResults(
            listOf(
                task("t1") to TaskResult(
                    detected = true,
                    findings = "Found MyJwtFilter.\n\nIt extends OncePerRequestFilter.",
                    proposedRules = listOf(
                        RuleProposal(key = "method.additional.header", rules = ruleLine)
                    )
                )
            )
        )

        assertTrue("title must be a comment header:\n$merged", merged.contains("# ── Title of t1 ──"))
        assertTrue(
            "source tag must survive as a comment:\n$merged",
            merged.contains("# source: detection:t1")
        )
        assertTrue(
            "every findings line must be commented:\n$merged",
            merged.lines().any { it == "# Found MyJwtFilter." } &&
                merged.lines().any { it == "# It extends OncePerRequestFilter." }
        )
        assertTrue(
            "the rule line must appear verbatim, uncommented:\n$merged",
            merged.lines().any { it == ruleLine }
        )
        assertTrue("hasRuleLines must be true:\n$merged", hasRuleLines(merged))
    }

    @Test
    fun nonDetectedTasksContributeNothing() {
        val merged = mergeTaskResults(
            listOf(
                task("t1") to TaskResult(detected = false, findings = "nothing here"),
                task("t2") to TaskResult(
                    detected = true,
                    findings = "found it",
                    proposedRules = listOf(RuleProposal("api.name", "api.name=Demo"))
                )
            )
        )

        assertFalse("a non-detected task must be filtered out:\n$merged", merged.contains("t1"))
        assertTrue(merged.contains("# source: detection:t2"))
    }

    @Test
    fun detectedButNoRulesMeansNoRuleLines() {
        val merged = mergeTaskResults(
            listOf(
                task("t1") to TaskResult(
                    detected = true,
                    findings = "The pattern is present but I could not draft a rule."
                )
            )
        )

        assertTrue("the block is still emitted:\n$merged", merged.contains("# source: detection:t1"))
        assertFalse(
            "a comments-only merge must report no rule lines, so the tool " +
                "stages no proposal instead of a comment-only file:\n$merged",
            hasRuleLines(merged)
        )
    }

    @Test
    fun emptyMergeHasNoRuleLines() {
        assertFalse(hasRuleLines(mergeTaskResults(emptyList())))
    }

    @Test
    fun commentedFindingsCannotBecomeAParserDirective() {
        // `###set …` IS a directive (ConfigTextParser skips `#` lines but not
        // `###`). Findings are model-authored prose, so prefixing must not be
        // able to smuggle one in.
        val merged = mergeTaskResults(
            listOf(
                task("t1") to TaskResult(
                    detected = true,
                    findings = "###set resolveProperty=false\nrule.that.must.not.apply=x",
                    proposedRules = listOf(RuleProposal("api.name", "api.name=Demo"))
                )
            )
        )

        val directiveLines = merged.lines().filter { it.startsWith("###") }
        assertTrue(
            "no findings line may end up as a parser directive:\n$merged",
            directiveLines.isEmpty()
        )
        assertTrue(
            "the findings line must be neutralised as a comment:\n$merged",
            merged.lines().any { it.startsWith("# ###set resolveProperty=false") }
        )
    }
}
