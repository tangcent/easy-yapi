package com.itangcent.easyapi.ai.tools

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.ai.AiSettings
import com.itangcent.easyapi.ai.agent.AgentMemory
import com.itangcent.easyapi.ai.agent.ApprovalGate
import com.itangcent.easyapi.ai.agent.ClarificationGate
import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.config.source.RuleFileResolver

/**
 * Classifies a tool as a sense (read-only) or a hand (state-changing).
 */
enum class ToolKind { PERCEPTION, ACTION }

/**
 * A capability the AI agent can invoke.
 *
 * Perception tools ([ToolKind.PERCEPTION]) run automatically and never mutate
 * state. Action tools ([ToolKind.ACTION]) change state and are gated by
 * [ApprovalGate] unless [requiresApproval] is overridden to `false` (e.g.
 * staging-only tools like `propose_rule_content`).
 */
interface AiTool {
    val name: String
    val description: String
    val kind: ToolKind

    /**
     * Whether [execute] must be preceded by `ctx.approvals.await(...)`.
     * Defaults to `true` for ACTION tools; override to `false` for staging-only
     * actions that write only to [AgentMemory].
     */
    val requiresApproval: Boolean get() = kind == ToolKind.ACTION

    /**
     * Maximum execution time for [execute] in milliseconds.
     *
     * Defaults to 30 seconds. Override with a larger value (or `0` to disable
     * the timeout entirely) for tools that legitimately suspend for long
     * periods — e.g. [AskClarificationTool] waits for the user to answer,
     * which can take minutes.
     */
    val timeoutMs: Long get() = 30_000L

    /** JSON-schema describing the parameters object. */
    val parametersSchema: Map<String, Any?>

    /**
     * Execute the tool. For PSI-touching tools, wrap reads in
     * [com.itangcent.easyapi.core.threading.read].
     */
    suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult
}

/**
 * Per-conversation context passed to every tool invocation.
 */
data class ToolContext(
    val project: Project,
    val configReader: ConfigReader,
    val aiSettings: AiSettings,
    val ruleFileResolver: RuleFileResolver,
    val workingMemory: AgentMemory,
    val approvals: ApprovalGate,
    /**
     * Gate the `ask_clarification` tool suspends on until the user answers
     *. Defaults to a no-op so contexts that never ask for
     * clarification need not wire one.
     */
    val clarifications: ClarificationGate = ClarificationGate.NOOP,
    /**
     * Gate the `read_rule_file` tool suspends on when it wants to read a
     * file outside the allowed rule directories (one-time user consent).
     * Defaults to [FileReadConsentGate.NOOP] (deny) so contexts that never
     * wire a gate preserve the original refuse-outside-allow-list behavior.
     */
    val readConsents: com.itangcent.easyapi.ai.agent.FileReadConsentGate =
        com.itangcent.easyapi.ai.agent.FileReadConsentGate.NOOP
)

/**
 * Outcome of a tool execution.
 */
sealed class ToolResult {
    /** Successful text output fed back to the LLM. */
    data class Text(val value: String) : ToolResult()

    /** Recoverable error — the agent may retry or choose another approach. */
    data class Error(val message: String) : ToolResult()
}
