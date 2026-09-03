package com.itangcent.easyapi.core.ai.agent

import com.itangcent.easyapi.core.ai.AiMessage

/**
 * The agent's working memory for one conversation.
 *
 * Holds the full transcript fed to the LLM, the staged proposal (if any),
 * and the last captured ambient perception.
 */
class AgentMemory {
    /** Full transcript fed to the LLM on each reasoning step. */
    val messages: MutableList<AiMessage> = mutableListOf()

    /** Staged by `propose_rule_content`; surfaced to the user with a "Save…" button. */
    @Volatile
    var proposal: Proposal? = null

    /** Last captured ambient perception (editing rule file, existing rule files). */
    @Volatile
    var ambient: Ambient? = null

    /**
     * Ephemeral task list for the Magic path. `null` in Reactive turns —
     * the agent never calls `create_task_list` and the field stays untouched.
     * Cleared by [reset] so a new conversation starts with no task list.
     */
    @Volatile
    var taskList: TaskList? = null

    /**
     * Sub-agent results collected by `RunSubAgentTool` during one Magic
     * detection turn (Phase 3 — design §3.9 / D3). Each entry pairs the
     * [Task] with the [TaskResult] its sub-agent reported via
     * `report_findings`. The orchestrator's `propose_rule_content` tool
     * reads this list and merges it via [mergeTaskResults] (concatenate-
     * with-tags) so the LLM does not have to compose the merged content
     * itself — the merge is deterministic, "no LLM round-trip" (D3).
     *
     * Empty in Reactive turns and in sub-agent contexts (sub-agents don't
     * spawn sub-agents). Cleared by [reset].
     */
    val collectedSubAgentResults: MutableList<Pair<Task, TaskResult>> = mutableListOf()

    /**
     * External knowledge state for rule-key catalogs, script-context profiles,
     * and script-object API signatures. Updated by [ToolResult.Stateful] tool
     * results; rendered as a system message block injected at request time.
     *
     * Reset by [reset] so a new conversation starts fresh.
     */
    val knowledgeState: KnowledgeState = KnowledgeState()

    /**
     * How many leading [AiMessage.System] messages were produced by
     * [SystemPromptBuilder.build] for this conversation. The L0 indexes are
     * enablement-filtered, so [com.itangcent.easyapi.core.ai.agent.RuleAuthoringAgent]
     * replaces exactly these messages when the enabled channel/format/framework
     * sets change mid-conversation.
     */
    var openingSystemCount: Int = 0

    /** Clear all state — invoked on "New Conversation". */
    fun reset() {
        messages.clear()
        proposal = null
        ambient = null
        taskList = null
        openingSystemCount = 0
        collectedSubAgentResults.clear()
        knowledgeState.clear()
    }
}

/** A staged rule proposal waiting for the user to confirm a save. */
data class Proposal(val content: String, val suggestedFileName: String)

/**
 * Compact observation captured before each reasoning step.
 *
 * The AI assistant runs inside the rule-file edit dialog (Settings), not the
 * main editor — so there is no caret/active source file in the editor sense.
 * What the agent actually needs to know is: which rule file is being edited,
 * and what other rule files already exist.
 *
 * [userLanguage] carries the detected Markdown template locale  — a
 * BCP-47 tag (e.g. `zh-CN`, `ja`) when the user appears to want non-English
 * output, or `null` when English / undetermined (no suggestion to surface).
 *
 * Env-var names are intentionally NOT carried here — the agent resolves
 * them from existing rule files (via `get_existing_rules_for_key`) and the
 * source code, not from the Environments panel. See [AmbientPerception].
 */
data class Ambient(
    val projectName: String,
    val editingRuleFile: String?,
    val existingRuleFiles: List<String>,
    val userLanguage: String? = null,
    /**
     * Names of the IntelliJ `Module`s in the workspace that contain API-bearing
     * PSI (e.g. `@RestController` / `@Controller` classes), captured once per
     * [AmbientPerception.capture] so the agent can detect multi-app workspaces
     * cheaply without an `list_project_endpoints` round-trip on every turn.
     *
     * Privacy: carries only module **names** — never env-var keys or values
     * from the Environments panel.
     */
    val moduleNames: List<String> = emptyList(),
    /**
     * Web frameworks detected among the project's API-bearing PSI (e.g.
     * `SpringMVC`, `Feign`, `JAX-RS`, `gRPC`), derived from the
     * `frameworkName` of each [com.itangcent.easyapi.core.export.recognizer.CompositeApiClassRecognizer]
     * implementation that recognized at least one API class. Computed once per
     * [AmbientPerception.capture] in the same PSI scan as [moduleNames].
     *
     * Privacy: carries only short framework labels — never env-var keys or
     * values from the Environments panel.
     */
    val frameworkHints: List<String> = emptyList(),
    /**
     * Ids of the currently-enabled [com.itangcent.easyapi.channel.spi.Channel]s
     * (e.g. `"postman"`, `"markdown"`), resolved from
     * [com.itangcent.easyapi.channel.spi.ChannelRegistry] in
     * [AmbientPerception.capture]. Cheap settings read — no PSI. Used by
     * [SystemPromptBuilder.indexMessage] to filter the detection/rule catalog
     * to only entries whose `channel:` scope matches an enabled channel (AC-S7).
     *
     * Privacy: carries only short channel ids — never env-var keys or values
     * from the Environments panel.
     */
    val enabledChannels: List<String> = emptyList(),
    /**
     * Ids of the currently-enabled
     * [com.itangcent.easyapi.format.spi.FieldFormatChannel]s (e.g. `"json"`,
     * `"yaml"`), resolved from [com.itangcent.easyapi.format.spi.FieldFormatChannelRegistry]
     * in [AmbientPerception.capture]. Cheap settings read — no PSI. Used by
     * [SystemPromptBuilder.indexMessage] to filter the rule catalog to only
     * entries whose `format:` scope matches an enabled format.
     *
     * Privacy: carries only short format ids — never env-var keys or values
     * from the Environments panel.
     */
    val enabledFormats: List<String> = emptyList()
)
