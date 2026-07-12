package com.itangcent.easyapi.ai.agent

import com.itangcent.easyapi.ai.AiMessage

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

    /** Clear all state — invoked on "New Conversation". */
    fun reset() {
        messages.clear()
        proposal = null
        ambient = null
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
     * `frameworkName` of each [com.itangcent.easyapi.exporter.core.CompositeApiClassRecognizer]
     * implementation that recognized at least one API class. Computed once per
     * [AmbientPerception.capture] in the same PSI scan as [moduleNames].
     *
     * Privacy: carries only short framework labels — never env-var keys or
     * values from the Environments panel.
     */
    val frameworkHints: List<String> = emptyList()
)
