package com.itangcent.easyapi.core.ai.tools

import com.itangcent.easyapi.core.ai.agent.Proposal
import com.itangcent.easyapi.core.ai.agent.hasRuleLines
import com.itangcent.easyapi.core.ai.agent.mergeTaskResults
import com.itangcent.easyapi.core.rule.CompositeRuleValidator

/**
 * The orchestrator's terminal staging action — merges collected sub-agent
 * [com.itangcent.easyapi.core.ai.agent.TaskResult]s and stages the merged
 * rule content (Phase 3 — design §3.9 / D3 / FR-3.8).
 *
 * This tool has the **same name** (`propose_rule_content`) as the Reactive
 * path's [ProposeRuleContentTool] so the orchestrator LLM uses it the same
 * way, but its behaviour differs: when the orchestrator has collected
 * sub-agent results (via [RunSubAgentTool] writing into
 * [com.itangcent.easyapi.core.ai.agent.AgentMemory.collectedSubAgentResults]),
 * this tool **ignores the LLM-provided `content` parameter** and uses
 * [mergeTaskResults] to compose the merged payload deterministically.
 *
 * Rationale (design §3.9 / D3): "No LLM round-trip" for the merge — the
 * merge is pure computation (concatenate-with-tags), so the LLM does not
 * have to compose the merged content itself. This keeps the merge
 * reproducible and avoids the LLM dropping or rewriting sub-agent findings.
 *
 * When `collectedSubAgentResults` is empty (e.g. all sub-agents failed
 * before staging a result, or the orchestrator was invoked without
 * spawning any sub-agent), the tool falls back to the LLM-provided
 * `content` parameter so the orchestrator can still surface a manual
 * proposal.
 *
 * **The merged content is rule-file syntax.** [mergeTaskResults] interleaves
 * the drafted rule lines with `#` comment blocks carrying each detection's
 * findings (`#` is the rule format's comment prefix — the parser skips it), so
 * the proposal is a file the user can actually save as `.rules`: it parses to
 * exactly the drafted rules, with the reasoning kept alongside as comments.
 *
 * **Validated like the Reactive path.** The merged content goes through
 * [CompositeRuleValidator.defaultPipeline] (static review + dry-run of every
 * `groovy:` value); hard errors block staging so the orchestrator's LLM sees
 * them, and soft warnings are prepended as a `# Reviewer notes:` comment block.
 *
 * **Empty merged content (FR-2.5).** When the merged content is blank —
 * either because every sub-agent returned `detected=false`, or because no
 * sub-agent results were collected and no fallback content was provided — the
 * tool **stages no `Proposal`** (leaves `ctx.workingMemory.proposal` `null`),
 * returns a `ToolResult.Text` explaining "no detections found", and the
 * orchestrator's turn ends via
 * [com.itangcent.easyapi.core.ai.agent.RuleAuthoringAgent.finish] with no
 * `ProposalReady` emitted and `TurnOutcome.Answered`. The user is never
 * prompted to apply an empty proposition.
 *
 * The same holds when detections were found but **no rule was drafted** (a
 * sub-agent can report `detected=true` with an empty `proposedRules`): the
 * merge then carries comments only, [hasRuleLines] is `false`, and again no
 * proposal is staged rather than handing the user a comment-only `.rules` file.
 *
 * Like [ProposeRuleContentTool], this tool:
 * - `kind = ACTION`, `requiresApproval = false` (staging only — no disk write).
 * - Stages the proposal in `ctx.workingMemory.proposal`.
 * - Is terminal: the agent loop exits after this tool returns (see
 *   [com.itangcent.easyapi.core.ai.agent.RuleAuthoringAgent.PROPOSE_RULE_CONTENT]).
 */
class OrchestratorProposeRuleContentTool : AiTool {

    override val name: String = "propose_rule_content"

    override val description: String =
        "Stage the merged rule content from all sub-agent findings. " +
            "Call this ONCE after every task is closed (completed or failed). " +
            "The tool automatically merges the collected sub-agent findings " +
            "and their drafted rules — you do NOT need to compose the " +
            "merged content yourself. Pass a suggestedFileName for the " +
            "proposed rule file. If no sub-agent findings were collected " +
            "(all failed or none spawned), pass a fallback content as well. " +
            "If the merged content has no rules (no pattern detected, or " +
            "nothing drafted), the tool stages no proposal and the turn ends " +
            "without prompting the user."

    override val kind: ToolKind = ToolKind.ACTION

    override val requiresApproval: Boolean = false

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "content" to mapOf(
                "type" to "string",
                "description" to "Fallback content — used ONLY when no " +
                    "sub-agent findings were collected (all sub-agents " +
                    "failed or none spawned). Ignored when sub-agent " +
                    "findings are available; the tool merges them " +
                    "deterministically."
            ),
            "suggestedFileName" to mapOf(
                "type" to "string",
                "description" to "Suggested file name (e.g. \"custom.rules\")."
            )
        ),
        "required" to listOf("suggestedFileName")
    )

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val suggestedFileName = (args["suggestedFileName"] as? String)?.trim()
        if (suggestedFileName.isNullOrBlank()) {
            return ToolResult.Error("missing required parameter: suggestedFileName")
        }

        val collected = ctx.workingMemory.collectedSubAgentResults
        val mergedContent = if (collected.isNotEmpty()) {
            // Deterministic merge — "no LLM round-trip" (design §3.9 / D3).
            mergeTaskResults(collected)
        } else {
            // Fallback: no sub-agent results collected (all failed or none
            // spawned). Use the LLM-provided content so the orchestrator
            // can still surface a manual proposal. Blank fallback is
            // handled by the empty-content guard below (FR-2.5).
            (args["content"] as? String)?.trim().orEmpty()
        }

        if (mergedContent.isBlank()) {
            // FR-2.5 — no detections found. Either every sub-agent returned
            // detected=false (mergeTaskResults filtered them all out), or
            // no sub-agent results were collected and no fallback content
            // was provided. Stage NO proposal so the user is never prompted
            // to apply an empty proposition. The orchestrator's turn ends
            // via finish() with no ProposalReady and TurnOutcome.Answered.
            return ToolResult.Text(
                "No detections found for $suggestedFileName — nothing to propose. " +
                    if (collected.isNotEmpty())
                        "All ${collected.size} sub-agent(s) returned detected=false."
                    else
                        "No sub-agent findings were collected."
            )
        }

        // FR-2.5 (detections without rules) — the merge renders each detected
        // task's findings as a `#` comment block, so a non-blank merge can
        // still contain no rule at all (a task may detect the pattern yet
        // draft nothing). Staging that would hand the user a comment-only
        // ".rules" file. Stage no proposal instead.
        if (collected.isNotEmpty() && !hasRuleLines(mergedContent)) {
            val detectedCount = collected.count { it.second.detected }
            return ToolResult.Text(
                "Detections produced no rules for $suggestedFileName — nothing to " +
                    "propose ($detectedCount sub-agent(s) detected a pattern but " +
                    "drafted no rule)."
            )
        }

        // The merged content IS rule-file syntax: rule lines interleaved with
        // `#` comment blocks carrying each detection's findings (the parser
        // skips `#`). Both paths therefore go through the same deterministic
        // review the Reactive path uses — a proposal the user can save never
        // contains a rule the validator rejects.
        val review = CompositeRuleValidator.defaultPipeline().validate(mergedContent, ctx.project)
        if (!review.ok) {
            return ToolResult.Error(
                "Proposal rejected by review — fix these and retry:\n" +
                    review.errors.joinToString("\n")
            )
        }

        val stagedContent = if (review.warnings.isEmpty()) mergedContent
        else buildString {
            append("# Reviewer notes:\n")
            review.warnings.forEach { append("# - ").append(it).append('\n') }
            append("# (Non-blocking warnings — review before saving.)\n")
            append(mergedContent)
        }

        ctx.workingMemory.proposal = Proposal(
            content = stagedContent,
            suggestedFileName = suggestedFileName
        )
        val provenance = if (collected.isEmpty()) ""
        else " from ${collected.size} sub-agent result(s)"
        return ToolResult.Text(
            "Staged proposal for $suggestedFileName (${mergedContent.length} chars$provenance). " +
                "Awaiting user confirmation to save." +
                if (review.warnings.isEmpty()) ""
                else " ${review.warnings.size} reviewer warning(s) attached."
        )
    }
}
