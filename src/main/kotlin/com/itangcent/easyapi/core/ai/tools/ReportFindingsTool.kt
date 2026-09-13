package com.itangcent.easyapi.core.ai.tools

import com.itangcent.easyapi.core.ai.agent.RuleProposal
import com.itangcent.easyapi.core.ai.agent.TaskResult
import com.itangcent.easyapi.core.logging.IdeaLog

/**
 * Sub-agent terminal action — stages the sub-agent's [TaskResult] for the
 * orchestrator to consume via `run_sub_agent` (design §3.7 / FR-3.4).
 *
 * - `name = "report_findings"`, `kind = ACTION`, `requiresApproval = false`.
 * - Schema: `{ detected: bool, findings: string, proposedRules: array }`.
 * - On execute: builds a [TaskResult] from the args, completes
 *   `ctx.subAgentResult` (the [kotlinx.coroutines.CompletableDeferred] the
 *   orchestrator's `RunSubAgentTool` is awaiting), and returns
 *   `ToolResult.Text("findings reported")`.
 *
 * **Draft-and-report contract.** The sub-agent reports evidence *and* the
 * concrete rule proposals it drafted from that evidence. It is the only role
 * holding the per-key value-format guides (`get_rule_detail`) and the
 * existing-rule lookup (`get_existing_rules_for_key`), so it can honour the
 * no-duplicates quality rule before proposing. The orchestrator only merges
 * the collected proposals deterministically — it has no perception tools.
 *
 * Terminal for sub-agents: the agent loop's terminal-action detection must
 * treat `report_findings` as terminal in sub-agent contexts, mirroring how
 * `propose_rule_content` is terminal for the orchestrator. The orchestrator's
 * tool registry does NOT include this tool, and the sub-agent's registry
 * does NOT include `propose_rule_content` — each role has its own terminal
 * action.
 *
 * Calling `report_findings` from a non-sub-agent context (where
 * `ctx.subAgentResult == null`) is a recoverable error — the tool returns
 * `ToolResult.Error("report_findings is only available in sub-agent contexts")`
 * so a misbehaving Reactive-path agent gets a clear correction.
 */
class ReportFindingsTool : AiTool, IdeaLog {

    override val name: String = "report_findings"

    override val description: String =
        "Report the detection findings for this sub-agent's task and end the " +
            "sub-agent turn. Pass `detected=true` if the pattern was found in " +
            "the project's PSI (with evidence in `findings` and any concrete " +
            "rule proposals in `proposedRules`), or `detected=false` if the " +
            "search came up empty. Sub-agent-only — never call from the " +
            "orchestrator or Reactive chat."

    override val kind: ToolKind = ToolKind.ACTION

    override val requiresApproval: Boolean = false

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "detected" to mapOf(
                "type" to "boolean",
                "description" to "Whether the detection pattern was found " +
                    "in the project's PSI."
            ),
            "findings" to mapOf(
                "type" to "string",
                "description" to "Free-form markdown — search evidence, " +
                    "located classes, why the pattern applies (or doesn't). " +
                    "Rendered as a `#` comment block above this task's rules " +
                    "in the merged proposal."
            ),
            "proposedRules" to mapOf(
                "type" to "array",
                "description" to "Concrete rule proposals drafted from the " +
                    "findings. Empty when detected=false. Each entry has a " +
                    "`key` (rule key, e.g. method.additional.header) and the " +
                    "complete rule text in `rules`.",
                "items" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "key" to mapOf(
                            "type" to "string",
                            "description" to "Rule key this proposal targets " +
                                "(matches a key from list_rule_keys)."
                        ),
                        "rules" to mapOf(
                            "type" to "string",
                            "description" to "The complete rule line(s) to " +
                                "append to the proposed file, verbatim — " +
                                "`<key>[<filter>]=<value>`. Not a summary: it " +
                                "is concatenated as-is, so a truncated or " +
                                "paraphrased value corrupts the proposal. May " +
                            "span lines when the value needs a guard block " +
                            "(e.g. ###set resolveProperty=false … true to keep " +
                            "a placeholder literal)."
                        )
                    ),
                    "required" to listOf("key", "rules")
                )
            )
        ),
        "required" to listOf("detected", "findings")
    )

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val slot = ctx.subAgentResult
            ?: return ToolResult.Error(
                "report_findings is only available in sub-agent contexts"
            )

        val detected = args["detected"] as? Boolean
            ?: return ToolResult.Error("missing required parameter: detected (boolean)")
        val findings = (args["findings"] as? String)?.trim()
            ?: return ToolResult.Error("missing required parameter: findings (string)")

        @Suppress("UNCHECKED_CAST")
        val rawRules = args["proposedRules"] as? List<Map<String, Any?>>
        val proposedRules = mutableListOf<RuleProposal>()
        for ((index, raw) in (rawRules ?: emptyList()).withIndex()) {
            val key = (raw["key"] as? String)?.trim()
            if (key.isNullOrBlank()) {
                return ToolResult.Error("proposedRules[$index].key is missing or blank")
            }
            val rules = (raw["rules"] as? String)?.trim()
            if (rules.isNullOrBlank()) {
                return ToolResult.Error("proposedRules[$index].rules is missing or blank")
            }
            proposedRules += RuleProposal(key = key, rules = rules)
        }

        val result = TaskResult(
            detected = detected,
            findings = findings,
            proposedRules = proposedRules
        )
        // Complete the deferred — `RunSubAgentTool` is awaiting it. Use
        // `complete` (not `await`); if the slot is already completed (e.g.
        // the sub-agent called report_findings twice) the second call is a
        // no-op and the tool returns a recoverable Error.
        val firstWriter = slot.complete(result)
        if (!firstWriter) {
            LOG.info("report_findings: subAgentResult already completed — duplicate call ignored")
            return ToolResult.Error(
                "findings already reported for this sub-agent — do not call report_findings twice"
            )
        }
        LOG.info(
            "report_findings: detected=$detected findingsLen=${findings.length} " +
                "proposedRules=${proposedRules.size}"
        )
        return ToolResult.Text("findings reported")
    }
}
