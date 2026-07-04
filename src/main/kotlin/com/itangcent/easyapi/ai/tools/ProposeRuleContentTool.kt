package com.itangcent.easyapi.ai.tools

import com.itangcent.easyapi.ai.agent.Proposal
import com.itangcent.easyapi.rule.RuleProposalValidator

/**
 * Terminal staging action — fills working memory with a proposed rule file
 *.
 *
 * `requiresApproval = false`: staging is harmless — it only writes to
 * `ctx.workingMemory.proposal`. The agent loop treats this tool name as
 * terminal: it stops looping and the chat UI surfaces the proposal with a
 * "Save…" button. The disk write happens only through the user-confirmed
 * "Save…" UI flow; the agent never writes to disk directly in v1.
 *
 * Before staging, every proposal passes through [RuleProposalValidator]
 * (the v1 deterministic "review agent"). Hard errors (unknown keys, invalid
 * filters, malformed JSON values) block staging and are returned to the
 * drafter so it can correct and retry. Soft warnings are prepended to the
 * staged content as a `# Reviewer notes:` block so the user sees them on
 * the proposal card.
 */
class ProposeRuleContentTool : AiTool {

    override val name: String = "propose_rule_content"

    override val description: String =
        "Stage a proposed rule file. The agent calls this when it has produced " +
            "final rule content for the user. Does NOT write to disk — only " +
            "stages the proposal. The chat UI surfaces a 'Save…' button for " +
            "the user to confirm. Content is validated against the rule key " +
            "catalog before staging; invalid proposals are rejected with " +
            "specific errors so you can correct and retry."

    override val kind: ToolKind = ToolKind.ACTION

    override val requiresApproval: Boolean = false

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "content" to mapOf(
                "type" to "string",
                "description" to "The proposed rule file content."
            ),
            "suggestedFileName" to mapOf(
                "type" to "string",
                "description" to "Suggested file name (e.g. \"custom.rules\")."
            )
        ),
        "required" to listOf("content", "suggestedFileName")
    )

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val content = args["content"] as? String
        val suggestedFileName = args["suggestedFileName"] as? String
        if (content.isNullOrBlank() || suggestedFileName.isNullOrBlank()) {
            return ToolResult.Error("missing required parameter(s): content, suggestedFileName")
        }

        // Deterministic review pass (v1 review agent).
        val review = RuleProposalValidator.validate(content, ctx.project)
        if (!review.ok) {
            return ToolResult.Error(
                "Proposal rejected by review — fix these and retry:\n" +
                    review.errors.joinToString("\n")
            )
        }

        val stagedContent = if (review.warnings.isEmpty()) content
        else buildString {
            append("# Reviewer notes:\n")
            review.warnings.forEach { append("# - ").append(it).append('\n') }
            append("# (Non-blocking warnings — review before saving.)\n")
            append(content)
        }

        ctx.workingMemory.proposal = Proposal(content = stagedContent, suggestedFileName = suggestedFileName)
        return ToolResult.Text(
            "Staged proposal for $suggestedFileName (${content.length} chars). " +
                "Awaiting user confirmation to save." +
                if (review.warnings.isEmpty()) ""
                else " ${review.warnings.size} reviewer warning(s) attached."
        )
    }
}
