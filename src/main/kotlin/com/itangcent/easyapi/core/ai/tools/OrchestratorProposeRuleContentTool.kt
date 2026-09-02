package com.itangcent.easyapi.core.ai.tools

import com.itangcent.easyapi.core.ai.agent.Proposal
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
 * **Empty merged content (FR-2.5).** When the merged content is blank —
 * either because every sub-agent returned `detected=false`, or because no
 * sub-agent results were collected and no fallback content was provided —
 * the tool **stages no `Proposal`** (leaves `ctx.workingMemory.proposal`
 * `null`), returns a `ToolResult.Text` explaining "no detections found",
 * and the orchestrator's turn ends via
 * [com.itangcent.easyapi.core.ai.agent.RuleAuthoringAgent.finish] with no
 * `ProposalReady` emitted and `TurnOutcome.Answered`. The user is never
 * prompted to apply an empty proposition.
 *
 * Like [ProposeRuleContentTool], this tool:
 * - `kind = ACTION`, `requiresApproval = false` (staging only — no disk write).
 * - Validates the fallback content via
 *   [CompositeRuleValidator.defaultPipeline] (static review + dry-run
 *   script execution) before staging.
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
            "via concatenate-with-tags — you do NOT need to compose the " +
            "merged content yourself. Pass a suggestedFileName for the " +
            "proposed rule file. If no sub-agent findings were collected " +
            "(all failed or none spawned), pass a fallback content as well. " +
            "If the merged content is blank (no pattern detected), the tool " +
            "stages no proposal and the turn ends without prompting the user."

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

        // The merged content is intentionally markdown findings (design §3.9:
        // `## <title>`, `source: detection:<id>`, `Proposed rules:` bullets),
        // NOT rule-file syntax — [RuleProposalValidator] would reject the
        // `source:` / `Proposed rules:` lines as unknown rule keys. Validation
        // applies only to the LLM-authored fallback content, which IS
        // rule-file syntax.
        if (collected.isEmpty()) {
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
            return ToolResult.Text(
                "Staged proposal for $suggestedFileName (${mergedContent.length} chars). " +
                    "Awaiting user confirmation to save." +
                    if (review.warnings.isEmpty()) ""
                    else " ${review.warnings.size} reviewer warning(s) attached."
            )
        }

        ctx.workingMemory.proposal = Proposal(
            content = mergedContent,
            suggestedFileName = suggestedFileName
        )
        return ToolResult.Text(
            "Staged merged proposal for $suggestedFileName " +
                "(${mergedContent.length} chars from ${collected.size} sub-agent result(s))."
        )
    }
}
