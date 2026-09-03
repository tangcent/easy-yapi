package com.itangcent.easyapi.core.ai.tools

import com.itangcent.easyapi.core.ai.AIService

/**
 * The standard set of tools handed to the [ToolRegistry] for a real
 * conversation.
 *
 * 15 perception tools + 1 staging action (`propose_rule_content`).
 * `write_rule_file` is intentionally NOT registered in v1 — the disk write
 * happens only through the user-confirmed "Save…" UI flow.
 *
 * Detection pairs:
 * - [FindClassesByAnnotationTool] — annotation-declared components
 * (`@RestController`, `@WebFilter`,...).
 * - [FindClassesBySupertypeTool] — inheritance-declared components
 * (filters extending `OncePerRequestFilter`, interceptors implementing
 * `HandlerInterceptor`,...). Without it the agent reports false negatives
 * like "no Filters" for the standard Spring Boot declaration style.
 * - [FindClassesByNameTool] — resolves class simple names to FQNs via
 * `PsiShortNamesCache`, with an FQN short-circuit and batch mode.
 *
 * Catalog detail tools (Phase A — design C3):
 * - [GetDetectionPromptTool] — full detection recipe by family id.
 * - [GetRuleDetailTool] — per-key guide lookup or scope-query concatenation.
 * Both are perception tools; the Reactive path's seed prompt lists the
 * available catalog ids so the agent knows what to fetch.
 */
fun standardRuleTools(): List<AiTool> = listOf(
    ListRuleKeysTool(),
    GetRuleContextTool(),
    GetScriptObjectApiTool(),
    GetPluginDocTool(),
    GetDetectionPromptTool(),
    GetRuleDetailTool(),
    ReadRuleFileTool(),
    ListProjectEndpointsTool(),
    GetPsiClassInfoTool(),
    GetPsiMethodInfoTool(),
    FindClassesByAnnotationTool(),
    FindClassesBySupertypeTool(),
    FindClassesByNameTool(),
    GetExistingRulesForKeyTool(),
    GetModuleDependencyGraphTool(),
    AskClarificationTool(),
    ProposeRuleContentTool(),
    // Phase B — Magic-path task-list tools (design C7). Registered
    // unconditionally; the agent is instructed (agent-base.md) to use them
    // only for Magic tasks with ≥2 distinct steps.
    CreateTaskListTool(),
    UpdateTaskTool()
    // WriteRuleFileTool() is reserved — not registered in v1.
)

/**
 * The orchestrator's tool set for a Magic detection turn (Phase 3 —
 * design §3.5 / FR-3.2, FR-3.3, D6).
 *
 * Three tools, all staging-only (`requiresApproval = false`):
 * - [UpdateTaskTool] — tick each task's status as the orchestrator walks
 *   the seeded list.
 * - [RunSubAgentTool] — spawn a sub-agent for one PENDING task and await
 *   its [com.itangcent.easyapi.core.ai.agent.TaskResult]. Collects each
 *   result into [com.itangcent.easyapi.core.ai.agent.AgentMemory.collectedSubAgentResults]
 *   so the orchestrator's terminal action can merge them deterministically.
 * - [OrchestratorProposeRuleContentTool] — terminal; merges collected
 *   sub-agent findings via [com.itangcent.easyapi.core.ai.agent.mergeTaskResults]
 *   (concatenate-with-tags, design §3.9 / D3) and stages the merged rule
 *   content once after all tasks close. "No LLM round-trip" for the merge
 *   — the LLM does not compose the merged content itself.
 *
 * The orchestrator has **no perception tools** — it never touches PSI
 * directly. All perception happens inside sub-agents (which have their
 * own tool set via [subAgentToolRegistry]). This enforces the role split
 * (FR-3.2/3.3): the orchestrator coordinates, sub-agents perceive.
 *
 * `create_task_list` is intentionally absent — the task list is seeded
 * by the caller (FR-2.4); the orchestrator must not call it.
 *
 * @param aiService The shared LLM backend — passed to [RunSubAgentTool]
 *   so it can construct sub-agents. Stateless (HTTP client), safe to
 *   share across orchestrator + sub-agents.
 * @param subAgentTools The sub-agent [ToolRegistry] (built via
 *   [subAgentToolRegistry]), passed to [RunSubAgentTool] so each
 *   spawned sub-agent gets the perception + report_findings set.
 */
fun orchestratorToolRegistry(
    aiService: AIService,
    subAgentTools: ToolRegistry
): List<AiTool> = listOf(
    UpdateTaskTool(),
    RunSubAgentTool(aiService, subAgentTools),
    OrchestratorProposeRuleContentTool()
)

/**
 * The sub-agent's tool set for a Magic detection turn (Phase 3 —
 * design §3.5 / FR-3.2, FR-3.3).
 *
 * Seven perception tools + one terminal action:
 * - [FindClassesByAnnotationTool] / [FindClassesBySupertypeTool] — locate
 *   candidate classes by annotation or supertype.
 * - [GetPsiClassInfoTool] — drill into a class's methods/fields/signature.
 * - [GetRuleDetailTool] — fetch the per-key guide so the sub-agent
 *   can draft concrete rule proposals.
 * - [GetRuleContextTool] — fetch key-specific bindings and callable script
 *   APIs before drafting a Groovy or Postman rule.
 * - [ListRuleKeysTool] — enumerate known rule keys (for proposal shape).
 * - [ReportFindingsTool] — terminal; stages the sub-agent's
 *   [com.itangcent.easyapi.core.ai.agent.TaskResult] and ends the
 *   sub-agent's turn.
 *
 * The sub-agent has **no** `propose_rule_content`, `run_sub_agent`,
 * `update_task`, or `create_task_list` — it cannot recurse, cannot
 * update the orchestrator's task list, and cannot propose final rule
 * content. It only perceives and reports (FR-3.2/3.3).
 *
 * `get_detection_prompt` is intentionally absent — the sub-agent's
 * detection recipe is already embedded in its seed instruction by
 * [RunSubAgentTool] (fetched in-process from
 * [com.itangcent.easyapi.core.ai.agent.PromptCatalog], design §3.6).
 * Exposing `get_detection_prompt` would let a sub-agent fetch other
 * detections' recipes and wander off-task.
 */
fun subAgentToolRegistry(): List<AiTool> = listOf(
    FindClassesByAnnotationTool(),
    FindClassesBySupertypeTool(),
    GetPsiClassInfoTool(),
    GetRuleDetailTool(),
    GetRuleContextTool(),
    GetScriptObjectApiTool(),
    ListRuleKeysTool(),
    ReportFindingsTool()
)
