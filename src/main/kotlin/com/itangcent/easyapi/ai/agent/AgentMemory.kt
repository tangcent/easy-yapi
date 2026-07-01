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
 */
data class Ambient(
    val projectName: String,
    val editingRuleFile: String?,
    val existingRuleFiles: List<String>,
    val userLanguage: String? = null
)
