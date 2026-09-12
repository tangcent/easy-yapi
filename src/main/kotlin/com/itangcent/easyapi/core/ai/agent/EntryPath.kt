package com.itangcent.easyapi.core.ai.agent

/**
 * Entry path selects how [SystemPromptBuilder] composes the opening system
 * messages for a turn. The agent loop is shared across all three paths
 * (design principle "two paths, one loop"); only the seed prompt and the
 * available tools differ.
 *
 * - [REACTIVE] — plain chat. The agent gets the base prompt plus the
 *   derived detection and rule-detail indexes (enablement-aware). It uses
 *   `get_detection_prompt` / `get_rule_detail` to pull full recipes on
 *   demand. Task-list tools are not used.
 * - [TASK_LIST_PROGRAMMATIC] — the Magic detection pass: the caller seeds
 *   the task list (`MagicTaskListBuilder`) and drives the orchestrator
 *   agent. The agent gets the base prompt only; detection/rule detail is
 *   pulled inside each task's sub-agent as needed. `update_task` /
 *   `run_sub_agent` are used here.
 *
 * - [SUB_AGENT] — used by [com.itangcent.easyapi.core.ai.tools.RunSubAgentTool]
 *   when it spawns a sub-agent for one detection task. The sub-agent gets
 *   its own base prompt (`sub-agent-base.md`) that advertises **only** the
 *   tools in [com.itangcent.easyapi.core.ai.tools.subAgentToolRegistry]
 *   (perception + `report_findings`); the orchestrator/Reactive tools it
 *   cannot call are deliberately omitted so the model never invokes a tool
 *   that isn't in its `ToolRegistry` (which would surface as "Unknown tool").
 *
 * Entrance-driven, not agent-detected (design D1): the caller picks the
 * path; the agent never decides "this is complex, switch mode."
 */
enum class EntryPath {
    REACTIVE,
    TASK_LIST_PROGRAMMATIC,
    SUB_AGENT
}
