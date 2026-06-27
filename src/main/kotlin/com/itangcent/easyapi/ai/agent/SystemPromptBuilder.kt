package com.itangcent.easyapi.ai.agent

import com.itangcent.easyapi.ai.AiMessage
import com.itangcent.easyapi.logging.IdeaLog

/**
 * Builds the two system-prompt pieces consumed by [RuleAuthoringAgent]
 *.
 *
 * - [build] is the fixed role/policy preamble appended once at the start of
 * a conversation (and re-asserted after a `reset()`). It frames the
 * Perception→Reasoning→Action loop, names the tools available to the agent,
 * and identifies `propose_rule_content` as the single state-changing action.
 * The text lives in `resources/ai/agent-preamble.md` (kept out of
 * `docs/knowledge-base/` so the `get_plugin_doc` tool does not expose it).
 *
 * - [ambient] is the per-turn free perception: the rule file being edited and
 * the other rule files that exist. The agent loop appends it before each
 * reasoning step.
 *
 * Both are intentionally short — the agent pulls the full guide via the
 * `get_plugin_doc` tool rather than baking it into every request (saves
 * tokens).
 */
object SystemPromptBuilder : IdeaLog {

    private const val PREAMBLE_RESOURCE = "/ai/agent-preamble.md"

    /** Fixed role/policy preamble (loaded once from the classpath resource). */
    private val PREAMBLE: String by lazy { loadPreamble() }

    /** Fixed role/policy preamble. */
    fun build(): AiMessage.System = AiMessage.System(PREAMBLE)

    /**
     * Per-turn ambient context message.
     *
     * @param amb The ambient observation captured for this turn.
     */
    fun ambient(amb: Ambient): AiMessage.System {
        val parts = mutableListOf<String>()
        parts += "Context: project `${amb.projectName}`"
        amb.editingRuleFile?.let { parts += "editing rule file `$it`" }
        if (amb.existingRuleFiles.isNotEmpty()) {
            parts += "other rule files: ${amb.existingRuleFiles.joinToString(", ")}"
        }
        return AiMessage.System(parts.joinToString("; ") + ".")
    }

    private fun loadPreamble(): String {
        return javaClass.getResourceAsStream(PREAMBLE_RESOURCE)?.use { stream ->
            stream.readBytes().toString(Charsets.UTF_8).trim()
        } ?: run {
            // Should never happen — the resource ships in the JAR.
            LOG.warn("agent-preamble.md resource not found on classpath; falling back to empty preamble")
            ""
        }
    }
}
