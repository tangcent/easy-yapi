package com.itangcent.easyapi.core.ai.tools

import com.itangcent.easyapi.core.ai.AIService
import com.itangcent.easyapi.core.ai.agent.AgentEvent
import com.itangcent.easyapi.core.ai.agent.AgentMemory
import com.itangcent.easyapi.core.ai.agent.EntryPath
import com.itangcent.easyapi.core.ai.agent.PromptCatalog
import com.itangcent.easyapi.core.ai.agent.RuleAuthoringAgent
import com.itangcent.easyapi.core.ai.agent.Task
import com.itangcent.easyapi.core.ai.agent.TaskList
import com.itangcent.easyapi.core.ai.agent.TaskResult
import com.itangcent.easyapi.core.ai.agent.TaskStatus
import com.itangcent.easyapi.core.logging.IdeaLog
import kotlinx.coroutines.CompletableDeferred

/**
 * Orchestrator-only action — spawns a sub-agent for one [Task] and awaits
 * its [TaskResult] (design §3.6 / FR-3.1, FR-3.2, FR-3.4).
 *
 * - `name = "run_sub_agent"`, `kind = ACTION`, `requiresApproval = false`.
 * - Schema: `{ taskId: string }`.
 * - On execute: looks up the [Task] by id in `ctx.workingMemory.taskList`;
 *   builds the sub-agent seed (detection recipe fetched in-process from
 *   [PromptCatalog] — **not** via the LLM-facing `get_detection_prompt`
 *   tool, which isn't in the orchestrator's set); constructs a fresh
 *   [AgentMemory] and a sub-[ToolContext] carrying the same sinks
 *   (project / configReader / aiSettings / ruleFileResolver / approvals /
 *   events) but the new memory and a new [CompletableDeferred] for the
 *   result; runs [RuleAuthoringAgent.runTurn] under the step ceiling
 *   (D4 — defaults to `aiSettings.maxRequests`, no per-detection override
 *   in v1); awaits the [TaskResult] the sub-agent stages via
 *   `report_findings`; serialises it to [ToolResult.Text].
 *
 * **Auto-marks task status (FR-3.5 / FR-3.6 post-ship fix-up).** When the
 * sub-agent finishes, this tool **automatically** updates the task's
 * status in `ctx.workingMemory.taskList` and emits the matching
 * [AgentEvent] (`TaskCompleted` / `TaskFailed`) — the UI checkbox updates
 * deterministically, without depending on the orchestrator LLM remembering
 * to call `update_task` afterwards:
 * - Sub-agent called `report_findings` (deferred completed) → `COMPLETED`,
 *   regardless of `detected`. "Nothing detected" is a valid finding, not
 *   a skip (FR-3.5).
 * - Sub-agent ended without `report_findings` (step limit / loop / plain
 *   answer) → `FAILED` with a reason.
 * - Sub-agent threw an exception → `FAILED` with a reason.
 *
 * The orchestrator's `update_task` call is therefore **optional** after
 * `run_sub_agent` — the status is already recorded. The orchestrator may
 * still call it to add a `reason` or to mark a deliberately-skipped task
 * (one for which `run_sub_agent` was never called).
 *
 * Orchestrator-only — not registered in any sub-agent's [ToolRegistry], so
 * sub-agents cannot recurse. The sub-agent's tool set (perception tools +
 * `report_findings`) is supplied at construction time via [subAgentTools].
 *
 * Shares the orchestrator's [MutableSharedFlow] of
 * [com.itangcent.easyapi.core.ai.agent.AgentEvent]s — the chat panel sees
 * each sub-agent's `Thinking` / `Perceiving` / `Acting` / `Observed`
 * events as they happen, giving the user live visibility into each
 * detection's progress.
 *
 * @param aiService Provider-neutral LLM backend shared with the
 *   orchestrator. `AIService` is stateless (an HTTP client), so sharing
 *   one instance across orchestrator + sub-agents is safe.
 * @param subAgentTools The sub-agent's [ToolRegistry] — perception tools
 *   + [ReportFindingsTool]. Built once per Magic turn via
 *   `subAgentToolRegistry()` (T3.5) and reused for every sub-agent spawn
 *   in that turn.
 */
class RunSubAgentTool(
    private val aiService: AIService,
    private val subAgentTools: ToolRegistry
) : AiTool, IdeaLog {

    override val name: String = "run_sub_agent"

    override val description: String =
        "Spawn a sub-agent to run one detection task in isolation, and wait " +
            "for its findings. Pass the task id from the seeded task list. " +
            "The sub-agent perceives the project's PSI, decides whether the " +
            "pattern is present, and reports back via report_findings. " +
            "This tool automatically records the task status (completed if " +
            "the sub-agent ran successfully — whether or not it detected " +
            "the pattern; failed on error) and ticks the checklist card " +
            "in the UI, so you do NOT need to call update_task afterwards " +
            "for the status. Orchestrator-only — never call from a " +
            "sub-agent or Reactive chat."

    override val kind: ToolKind = ToolKind.ACTION

    override val requiresApproval: Boolean = false

    /**
     * Disable the per-tool timeout — the sub-agent has its own step ceiling
     * (`aiSettings.maxRequests`, D4) and may legitimately run multiple LLM
     * round-trips. The orchestrator's [ToolRegistry.dispatch] timeout would
     * otherwise cut a long detection short.
     */
    override val timeoutMs: Long = 0L

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
                "taskId" to mapOf(
                "type" to "string",
                "description" to "The id of the PENDING task to run (from the " +
                    "seeded task list, e.g. \"detect_spring_filters_interceptors\"). " +
                    "Use the exact ids listed in the seeded task manifest; a wrong " +
                    "id is rejected with \"unknown task id\"."
            )
        ),
        "required" to listOf("taskId")
    )

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val taskId = (args["taskId"] as? String)?.trim()
            ?: return ToolResult.Error("missing required parameter: taskId (string)")
        if (taskId.isEmpty()) {
            return ToolResult.Error("taskId must not be empty")
        }

        val taskList = ctx.workingMemory.taskList
            ?: return ToolResult.Error(
                "no task list in working memory — run_sub_agent is only valid " +
                    "in a Magic detection turn"
            )
        val task = taskList.byId(taskId)
            ?: return ToolResult.Error(
                "unknown task id: $taskId (not in seeded task list). " +
                    "Valid ids: ${taskList.tasks.joinToString(", ") { it.id }}"
            )

        // Build the sub-agent's user-message instruction = detection recipe.
        // Fetched in-process from PromptCatalog (not via get_detection_prompt
        // — that tool isn't in the orchestrator's set, design §3.6).
        val instruction = buildSubAgentInstruction(task)
            ?: return ToolResult.Error(
                "could not resolve detection recipe for task ${task.id} " +
                    "(task id must start with '$DETECT_PREFIX' and map to a " +
                    "catalog entry under ai/detection/)"
            )

        // Fresh memory per sub-agent (FR-3.1 / G3 — no carry-over from
        // sibling sub-agents). The sub-agent's transcript starts empty.
        val subMemory = AgentMemory()

        // The deferred the sub-agent completes via report_findings. Null
        // for orchestrator / Reactive contexts; non-null here.
        val subAgentResult = CompletableDeferred<TaskResult>()

        // Sub-context: same sinks, new memory + result slot. The events
        // flow is shared so the UI sees the sub-agent's activity.
        val subCtx = ctx.copy(
            workingMemory = subMemory,
            subAgentResult = subAgentResult
        )

        // Construct the sub-agent. Reuses the orchestrator's AIService
        // (stateless HTTP client) + the shared sub-agent tool registry.
        val subAgent = RuleAuthoringAgent(aiService, subAgentTools, subCtx, ctx.events)

        LOG.info("sub-agent start: ${task.id} (${task.title})")
        val outcome = try {
            subAgent.runTurn(
                userMessage = instruction,
                memory = subMemory,
                // Reuse the orchestrator's ambient (editing file, enabled
                // features, framework hints) — the sub-agent needs this
                // context and re-capturing would re-do PSI work.
                ambient = ctx.workingMemory.ambient,
                // SUB_AGENT seeds the sub-agent-specific base prompt
                // (`sub-agent-base.md`), which advertises ONLY this registry's
                // tools (perception + report_findings). The orchestrator/
                // Reactive tools it cannot call (list_project_endpoints,
                // get_plugin_doc, find_classes_by_name,
                // get_existing_rules_for_key, propose_rule_content, ...) are
                // omitted so the model never invokes a tool not in its
                // registry — otherwise it trusts the shared prompt's tool
                // index over the 6-entry tools schema and calls unknown tools.
                entryPath = EntryPath.SUB_AGENT
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("sub-agent '${task.id}' threw during runTurn", e)
            // FR-3.5/FR-3.6 fix-up — auto-mark FAILED so the UI checkbox
            // updates deterministically, without depending on the
            // orchestrator LLM calling update_task afterwards.
            markTaskStatus(ctx, task, TaskStatus.FAILED, "sub-agent threw: ${e.message}")
            return ToolResult.Error(
                "sub-agent '${task.id}' failed: ${e::class.simpleName}: ${e.message}"
            )
        }

        // The sub-agent's runTurn has returned. If it called report_findings,
        // the deferred is completed; otherwise (step limit / loop / plain
        // answer) it is not — synthesise a not-detected result so the
        // orchestrator can mark the task skipped/failed and continue.
        val result: TaskResult = if (subAgentResult.isCompleted) {
            subAgentResult.await()
        } else {
            LOG.warn(
                "sub-agent '${task.id}' ended without report_findings " +
                    "(outcome=${outcome::class.simpleName})"
            )
            TaskResult(
                detected = false,
                findings = "(sub-agent ended without calling report_findings; " +
                    "outcome=${outcome::class.simpleName})",
                proposedRules = emptyList()
            )
        }

        LOG.info(
            "sub-agent done: ${task.id} detected=${result.detected} " +
                "findingsLen=${result.findings.length} " +
                "proposedRules=${result.proposedRules.size}"
        )

        // FR-3.5/FR-3.6 post-ship fix-up — auto-mark the task status so the
        // UI checkbox updates deterministically. The sub-agent ran to
        // completion (called report_findings) → COMPLETED, regardless of
        // `detected`. "Nothing detected" is a valid finding, not a skip.
        // (The not-completed case is handled below — it's FAILED.)
        if (subAgentResult.isCompleted) {
            markTaskStatus(ctx, task, TaskStatus.COMPLETED, null)
        } else {
            markTaskStatus(
                ctx, task, TaskStatus.FAILED,
                "sub-agent ended without calling report_findings " +
                    "(outcome=${outcome::class.simpleName})"
            )
        }

        // Collect the result so the orchestrator's propose_rule_content
        // can merge all sub-agent findings deterministically (design §3.9 /
        // D3 — "no LLM round-trip" for the merge). The merge is done by
        // mergeTaskResults, wired into OrchestratorProposeRuleContentTool.
        ctx.workingMemory.collectedSubAgentResults.add(task to result)

        return ToolResult.Text(serialize(task, result))
    }

    /**
     * Update the task's status in `ctx.workingMemory.taskList` and emit the
     * matching [AgentEvent] — mirrors [UpdateTaskTool]'s status-swap logic
     * (build a fresh [TaskList] with the updated row, emit
     * [AgentEvent.TaskCompleted] / [AgentEvent.TaskFailed]).
     *
     * Called by [execute] when the sub-agent finishes, so the UI checkbox
     * updates deterministically without depending on the orchestrator LLM
     * calling `update_task` afterwards (FR-3.5 / FR-3.6 post-ship fix-up).
     * If the orchestrator does call `update_task` with the same status
     * afterwards, the second call is idempotent (the UI just re-sets the
     * same checkbox state).
     */
    private suspend fun markTaskStatus(
        ctx: ToolContext,
        task: Task,
        newStatus: TaskStatus,
        reason: String?
    ) {
        val taskList = ctx.workingMemory.taskList ?: return
        val updated = task.copy(status = newStatus)
        val newTasks = taskList.tasks.map { if (it.id == task.id) updated else it }
        ctx.workingMemory.taskList = TaskList(newTasks)
        val event = when (newStatus) {
            TaskStatus.COMPLETED -> AgentEvent.TaskCompleted(task.id)
            TaskStatus.FAILED -> AgentEvent.TaskFailed(task.id, reason ?: "")
            TaskStatus.IN_PROGRESS -> AgentEvent.TaskStarted(task.id)
            TaskStatus.SKIPPED -> AgentEvent.TaskSkipped(task.id)
            TaskStatus.PENDING -> null
        }
        if (event != null) {
            ctx.events.emit(event)
        }
    }

    /**
     * Build the sub-agent's user-message instruction from the detection
     * recipe in [PromptCatalog].
     *
     * Task ids follow the pattern `detect_<detection-id>` (see
     * [com.itangcent.easyapi.core.ai.agent.MagicTaskListBuilder]); the
     * detection id is the catalog entry's id under `ai/detection/`.
     *
     * [MagicTaskListBuilder] replaces non-alphanumeric chars in the detection
     * id with `_` (e.g. `spring-filters-interceptors` becomes
     * `spring_filters_interceptors`) to keep `update_task`'s `taskId` stable.
     * We invert that here by converting `_` back to `-`, since every catalog
     * detection id uses hyphens (verified across the detection markdown
     * files under `ai/detection/`).
     *
     * @return the instruction body, or `null` if the task id doesn't
     *   follow the pattern or the detection recipe can't be found.
     */
    private fun buildSubAgentInstruction(task: Task): String? {
        if (!task.id.startsWith(DETECT_PREFIX)) return null
        val detectionId = task.id.removePrefix(DETECT_PREFIX).replace('_', '-')
        val body = PromptCatalog.body("detection", detectionId) ?: return null
        return buildString {
            appendLine("You are a sub-agent running one detection task: ${task.title}.")
            if (!task.detail.isNullOrBlank()) {
                appendLine("Cue: ${task.detail}")
            }
            appendLine()
            appendLine("Detection recipe:")
            appendLine(body)
            appendLine()
            appendLine(
                "Run the suggested searches to confirm whether the pattern is " +
                    "present in this project's PSI. If it is, draft the concrete " +
                    "rule proposals for it: confirm the key name with " +
                    "list_rule_keys, fetch its value-format guide with " +
                    "get_rule_detail(key=...), and check for existing rules with " +
                    "get_existing_rules_for_key — never propose a duplicate. " +
                    "When done, call report_findings with detected=true — put " +
                    "the evidence (located classes, signatures, why the pattern " +
                    "applies) in findings and the drafted rules in proposedRules " +
                    "(each {key, rules}, where rules is the COMPLETE rule line to " +
                    "append verbatim — not a summary) — or detected=false with " +
                    "your search notes in findings if nothing matched. Do NOT call " +
                    "propose_rule_content — the orchestrator merges the " +
                    "sub-agents' findings and proposals and stages the final " +
                    "proposal once."
            )
        }
    }

    /**
     * Serialise a [TaskResult] to the text fed back to the orchestrator's
     * LLM. The orchestrator uses this to decide the task's final status
     * (via update_task) and to compose the merged propose_rule_content
     * payload.
     */
    private fun serialize(task: Task, result: TaskResult): String = buildString {
        appendLine("taskId: ${task.id}")
        appendLine("detected: ${result.detected}")
        appendLine("findings:")
        appendLine(result.findings.trim())
        if (result.proposedRules.isNotEmpty()) {
            appendLine()
            appendLine("proposedRules:")
            result.proposedRules.forEach { rp ->
                appendLine("- ${rp.key}")
                rp.rules.trim().lines().forEach { appendLine("    $it") }
            }
        }
    }

    companion object {
        /** Prefix for detection-task ids (see [MagicTaskListBuilder]). */
        private const val DETECT_PREFIX = "detect_"
    }
}
