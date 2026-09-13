package com.itangcent.easyapi.core.ai.agent

/**
 * Result a sub-agent reports back to the orchestrator for one [Task].
 *
 * Sub-agents stage a [TaskResult] via `report_findings` (see
 * [com.itangcent.easyapi.core.ai.tools.ReportFindingsTool]); the
 * orchestrator's `run_sub_agent` tool awaits it and serialises it back to the
 * orchestrator's LLM as a `ToolResult.Text`. After every task closes the
 * orchestrator merges the collected [TaskResult]s via
 * [mergeTaskResults] (concatenate-with-tags, design §3.9 / D3) and calls
 * `propose_rule_content` exactly once with the merged findings.
 *
 * **Draft-and-report contract.** The sub-agent perceives the PSI and also
 * drafts the concrete rule proposals for what it found: it is the role that
 * holds the per-key value-format guides (`get_rule_detail`) and the
 * existing-rule lookup (`get_existing_rules_for_key`), so it can honour the
 * no-duplicates quality rule before proposing. The orchestrator's registry has
 * **no perception tools** and its terminal tool merges the collected results
 * deterministically, so composition happens in the sub-agent, not the
 * orchestrator.
 *
 * @property detected `true` if the detection pattern was found in the
 *   project's PSI; `false` if the sub-agent searched and found nothing.
 *   Drives the orchestrator's [TaskStatus] decision: `COMPLETED` in both
 *   cases (the sub-agent ran successfully); `FAILED` only when the
 *   sub-agent errored (FR-3.5 / FR-3.6). "Nothing detected" is a valid
 *   finding, not a skip.
 * @property findings Free-form markdown the sub-agent produced — search
 *   evidence, located classes, why the pattern applies. Rendered into the
 *   merged payload as a `#`-prefixed comment block above the task's rules.
 * @property proposedRules Concrete rule proposals the sub-agent drafted
 *   from its findings. Empty when `detected=false`. Each entry is a
 *   [RuleProposal] carrying the complete rule text.
 */
data class TaskResult(
    val detected: Boolean,
    val findings: String,
    val proposedRules: List<RuleProposal> = emptyList()
)

/**
 * One concrete rule proposal a sub-agent stages via [TaskResult].
 *
 * [rules] is the **complete, ready-to-append rule text** — not a summary.
 * The orchestrator's merge concatenates it verbatim into the proposed rule
 * file, so a truncated or paraphrased preview would corrupt the proposal.
 * It may span several lines when the value needs a guard block (e.g. a
 * `###set resolveProperty=false … true` wrapper when a placeholder in the
 * value must stay literal instead of resolving as a config property).
 *
 * @property key The rule key this proposal targets (e.g.
 *   `method.additional.header`). Matches a key surfaced by `list_rule_keys`;
 *   emitted as a `#`-prefixed label above the rule text.
 * @property rules The complete rule line(s) to append to the proposed file,
 *   verbatim — `<key>[<filter>]=<value>`, or a multi-line block when the
 *   value needs one.
 */
data class RuleProposal(
    val key: String,
    val rules: String
)

/**
 * Merge a list of `(Task, TaskResult)` pairs into the **rule-file content**
 * the orchestrator stages via `propose_rule_content` (design §3.9 / D3).
 *
 * The merged text is valid EasyApi rule content, not a findings report: each
 * task whose `detected=true` contributes
 *
 * ```
 * # ── <title> ──
 * # source: detection:<id>
 * # <the sub-agent's findings, one comment line each>
 *
 * # <rule key>
 * <the complete rule text, verbatim>
 * ```
 *
 * `#`-prefixed lines are comments in the EasyApi rule format
 * (`ConfigTextParser` skips them, `RuleProposalValidator` ignores them for
 * its line-oriented checks), so the file parses to exactly the drafted rules
 * while keeping the reasoning visible next to them. This is what makes the
 * staged proposal saveable as a `.rules` file — before, the merge emitted
 * markdown headings and `source:` lines, which the loader tried to read as
 * rules.
 *
 * Non-detected tasks are filtered out — they contribute nothing (their
 * `COMPLETED` status is already recorded in the task list). A task that
 * detected a pattern but drafted no rule contributes comments only, so a
 * merge in which no task drafted a rule contains no rule lines at all; use
 * [hasRuleLines] to tell that case apart (the tool then stages no proposal,
 * FR-2.5).
 *
 * No deduplication, no LLM round-trip. Revisit if concatenated output proves
 * noisy (D3 options b/c, deferred).
 */
fun mergeTaskResults(results: List<Pair<Task, TaskResult>>): String =
    results.filter { it.second.detected }
        .joinToString("\n\n") { (task, r) -> mergeBlock(task, r) }

private fun mergeBlock(task: Task, result: TaskResult): String = buildString {
    appendLine("# ── ${task.title} ──")
    appendLine("# source: detection:${task.id}")
    result.findings.trim().lines().forEach { line ->
        appendLine(if (line.isBlank()) "#" else "# $line")
    }
    result.proposedRules.forEach { proposal ->
        appendLine()
        appendLine("# ${proposal.key}")
        appendLine(proposal.rules.trim())
    }
}

/**
 * `true` when [merged] contains at least one line the rule parser would read
 * as a rule (i.e. a non-blank line that is not a `#` comment).
 *
 * Lets the orchestrator distinguish "detections found but nothing to write"
 * from "rules drafted" — in the former case it must stage no proposal rather
 * than hand the user an empty comment-only file.
 */
fun hasRuleLines(merged: String): Boolean =
    merged.lines().any { it.isNotBlank() && !it.trimStart().startsWith("#") }
