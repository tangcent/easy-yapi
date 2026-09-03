package com.itangcent.easyapi.core.ai.tools

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.ai.AiRuntimeConfig
import com.itangcent.easyapi.core.ai.agent.AgentEvent
import com.itangcent.easyapi.core.ai.agent.AgentMemory
import com.itangcent.easyapi.core.ai.agent.ApprovalGate
import com.itangcent.easyapi.core.ai.agent.ClarificationGate
import com.itangcent.easyapi.core.ai.agent.KnowledgeState
import com.itangcent.easyapi.core.ai.agent.TaskResult
import com.itangcent.easyapi.core.config.ConfigReader
import com.itangcent.easyapi.core.config.source.RuleFileResolver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow

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
     * [com.itangcent.easyapi.core.internal.threading.read].
     */
    suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult
}

/**
 * Per-conversation context passed to every tool invocation.
 */
data class ToolContext(
    val project: Project,
    val configReader: ConfigReader,
    val aiSettings: AiRuntimeConfig,
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
    val readConsents: com.itangcent.easyapi.core.ai.agent.FileReadConsentGate =
        com.itangcent.easyapi.core.ai.agent.FileReadConsentGate.NOOP,
    /**
     * Sink for [AgentEvent]s emitted directly from tool bodies — currently
     * used only by the Task-List-path tools (`create_task_list`,
     * `update_task`) so they can signal `TaskListCreated` / `Task*`
     * lifecycle events to the UI without going through the agent loop's
     * per-call `Acting`/`Observed` cards.
     *
     * No default — every context MUST wire this to the same
     * `MutableSharedFlow` the owning [com.itangcent.easyapi.core.ai.ConversationSession]
     * uses so tool-emitted events reach the chat panel. Tests that don't
     * care about events pass a no-op `MutableSharedFlow(extraBufferCapacity = 64)`.
     */
    val events: MutableSharedFlow<AgentEvent>,
    /**
     * Sub-agent result slot — `null` for orchestrator / Reactive contexts;
     * non-`null` for sub-agent contexts created by `RunSubAgentTool`.
     *
     * `ReportFindingsTool` completes this deferred with the sub-agent's
     * [TaskResult] when the sub-agent calls `report_findings`; the
     * orchestrator's `RunSubAgentTool` awaits it. Defaults to `null` so
     * non-sub-agent contexts (orchestrator, Reactive, tests that don't
     * exercise sub-agents) are unaffected. Completing a `null` slot is a
     * no-op — `ReportFindingsTool` returns an Error in that case.
     */
    val subAgentResult: CompletableDeferred<TaskResult>? = null
)

/**
 * Outcome of a tool execution.
 */
sealed class ToolResult {
    /** Successful text output fed back to the LLM. */
    data class Text(val value: String) : ToolResult()

    /** Recoverable error — the agent may retry or choose another approach. */
    data class Error(val message: String) : ToolResult()

    /**
     * Stateful output that feeds into [KnowledgeState] instead of appending
     * full content to the conversation history.
     *
     * The agent loop calls `memory.knowledgeState.upsert(section, entries)`
     * and replaces the full result with a short receipt JSON in the transcript.
     * The rendered state block is injected as a system message at request time.
     *
     * @param section The [KnowledgeState] section name (e.g. `"§keys"`).
     * @param entries Entries to upsert into the section.
     * @param receiptNote Human-readable note for the receipt (e.g. "n keys catalogued").
     */
    data class Stateful(
        val section: String,
        val entries: List<KnowledgeState.Entry>,
        val receiptNote: String
    ) : ToolResult()
}
