package com.itangcent.easyapi.core.ai.agent

import com.itangcent.easyapi.core.ai.AiMessage
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.rule.RuleKeyCatalog

/**
 * Builds the system-prompt pieces consumed by [RuleAuthoringAgent].
 *
 * Three concerns are split out (design C2):
 *
 * - [build] is the fixed base prompt (role/policy loop contract, tool index,
 *   rule-file format, writing-quality rules). Loaded once from
 *   `resources/ai/agent-base.md` (kept out of `docs/knowledge-base/` so the
 *   `get_plugin_doc` tool does not expose it). Always appended once at the
 *   start of a conversation (and re-asserted after a `reset()`).
 *
 * - [build] with an [EntryPath] composes the entry-path-specific opening
 *   messages. The Reactive path appends two derived indexes (detection +
 *   rule-detail) so the agent has a menu to browse; the Task-List paths
 *   append only the base prompt (detection/rule detail is pulled inside
 *   tasks as needed). The indexes are enablement-aware: a `channel: postman`
 *   rule file is absent from the rule index when Postman is disabled (AC-S7).
 *
 * - [ambient] is the per-turn free perception: the rule file being edited,
 *   the other rule files that exist, the detected modules/frameworks, and the
 *   enabled channels/formats. The agent loop appends it before each
 *   reasoning step.
 *
 * Both are intentionally short — the agent pulls the full guide via the
 * `get_plugin_doc` / `get_detection_prompt` / `get_rule_detail` tools rather
 * than baking it into every request (saves tokens).
 */
object SystemPromptBuilder : IdeaLog {

    private const val BASE_RESOURCE = "/ai/agent-base.md"

    /**
     * Fixed base prompt for a sub-agent (one detection task).
     *
     * Loaded once from `resources/ai/sub-agent-base.md`. It advertises **only**
     * the tools in
     * [com.itangcent.easyapi.core.ai.tools.subAgentToolRegistry] (perception +
     * `report_findings`), so a sub-agent never sees the orchestrator/Reactive
     * tools it cannot call (e.g. `list_project_endpoints`, `get_plugin_doc`,
     * `find_classes_by_name`, `get_existing_rules_for_key`,
     * `propose_rule_content`). Without this, the sub-agent was seeded with the
     * shared [BASE] prompt whose tool index lists ~14 tools — the LLM trusted
     * the prompt over its 6-entry `tools` schema array and called tools not in
     * its registry, surfacing as "Unknown tool: <name>" in `ToolRegistry`.
     */
    private const val SUB_AGENT_BASE_RESOURCE = "/ai/sub-agent-base.md"

    /** Fixed base prompt (loaded once from the classpath resource). */
    private val BASE: String by lazy { loadBase() }

    /** Fixed sub-agent base prompt (loaded once from the classpath resource). */
    private val SUB_AGENT_BASE: String by lazy { loadSubAgentBase() }

    /** Fixed base prompt. */
    fun build(): AiMessage.System = AiMessage.System(BASE)

    /**
     * Fixed base prompt for a sub-agent (one detection task).
     *
     * @see SUB_AGENT_BASE_RESOURCE
     */
    fun buildSubAgent(): AiMessage.System = AiMessage.System(SUB_AGENT_BASE)

    /**
     * Compose opening system messages based on [entryPath].
     *
     * - [EntryPath.REACTIVE] → base prompt + detection index + rule index +
     *   L0 rule-key name menu. Agent has a menu to browse; pulls detail on
     *   demand.
     * - [EntryPath.TASK_LIST_MAGIC] / [EntryPath.TASK_LIST_PROGRAMMATIC]
     *   → base prompt only. Detection/rule detail is pulled inside tasks
     *   as needed.
     * - [EntryPath.SUB_AGENT] → the sub-agent base prompt only. It advertises
     *   only the sub-agent's registered tools (perception + `report_findings`)
     *   and omits the orchestrator/Reactive tools it cannot call, so the model
     *   never invokes a tool not in its registry. No detection/rule index —
     *   the assigned detection's recipe is embedded in the sub-agent's user
     *   instruction by [com.itangcent.easyapi.core.ai.tools.RunSubAgentTool],
     *   and per-key guides are pulled via `get_rule_detail` as needed.
     *
     * The indexes are derived from [PromptCatalog] and filtered by the
     * ambient-enabled features (channels/formats/frameworks). The body of an
     * index message is `(none for the currently enabled features)` when no
     * catalog entries match.
     *
     * @param entryPath the entry path selecting prompt shape.
     * @param amb the ambient observation carrying the enabled-feature sets
     *   used to filter the indexes.
     */
    fun build(entryPath: EntryPath, amb: Ambient): List<AiMessage.System> = when (entryPath) {
        EntryPath.REACTIVE -> listOf(
            build(),
            indexMessage("detection", amb),
            indexMessage("key-guides", amb),
            keyIndexMessage()
        )
        EntryPath.TASK_LIST_MAGIC, EntryPath.TASK_LIST_PROGRAMMATIC -> listOf(build())
        EntryPath.SUB_AGENT -> listOf(buildSubAgent())
    }

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
        // Surface the IntelliJ Modules that contain API-bearing PSI so the
        // agent can detect multi-app workspaces cheaply (on-demand fetch for
        // the full recipe via `get_plugin_doc`). Empty list → no hint.
        if (amb.moduleNames.isNotEmpty()) {
            parts += "modules: ${amb.moduleNames.joinToString(", ")}"
        }
        // Surface the detected web frameworks so the agent knows which
        // frameworks are active without a list_project_endpoints round-trip.
        // Derived from the same PSI scan as moduleNames. Empty list → no hint.
        if (amb.frameworkHints.isNotEmpty()) {
            parts += "frameworks active: ${amb.frameworkHints.joinToString(", ")}"
        }
        // Surface the enabled channels so the agent knows which export
        // destinations are turned on (AC-S7). Cheap settings read resolved in
        // AmbientPerception.capture. Empty list → no hint.
        if (amb.enabledChannels.isNotEmpty()) {
            parts += "enabled channels: ${amb.enabledChannels.joinToString(", ")}"
        }
        // Surface the enabled field-format channels so the agent knows which
        // serialization formats are turned on. Cheap settings read resolved in
        // AmbientPerception.capture. Empty list → no hint.
        if (amb.enabledFormats.isNotEmpty()) {
            parts += "enabled formats: ${amb.enabledFormats.joinToString(", ")}"
        }
        // Surface the detected Markdown template locale so the agent can decide
        // whether to propose `markdown.template.language=<tag>`. `en`/null
        // mean "default (English) template" — no hint, no proposal.
        val lang = amb.userLanguage
        if (lang != null && lang != "en") {
            parts += "user language: $lang"
        }
        return AiMessage.System(parts.joinToString("; ") + ".")
    }

    /**
     * Enablement-aware index message for one catalog [category]
     * (`"detection"` or `"key-guides"`).
     *
     * Lists each matching catalog entry as `- `id` — title: cue`, prefixed
     * by a header that names the tool used to fetch the full guide. When no
     * entries match the ambient-enabled features, the body becomes
     * `(none for the currently enabled features)` so the agent knows the
     * catalog is empty for this turn rather than missing entirely.
     */
    private fun indexMessage(category: String, amb: Ambient): AiMessage.System {
        val entries = PromptCatalog.listFor(
            category,
            activeChannels = amb.enabledChannels.toSet(),
            activeFormats = amb.enabledFormats.toSet(),
            activeFrameworks = amb.frameworkHints.toSet()
        )
        val header = when (category) {
            "detection" -> "Available detection prompts (fetch with `get_detection_prompt(id)` when relevant):"
            "key-guides" -> "Available key guides (fetch with `get_rule_detail(key=...)` when relevant):"
            else -> "Available $category prompts:"
        }
        val body = if (entries.isEmpty()) {
            "(none for the currently enabled features)"
        } else {
            entries.joinToString("\n") { "- `${it.id}` — ${it.title}: ${it.cue}" }
        }
        return AiMessage.System("$header\n$body")
    }

    /**
     * The L0 rule-key name menu (spec D2.5) — a compact, source-grouped list
     * of every known rule key, rendered by [RuleKeyCatalog.renderKeyMenu].
     *
     * Unlike the detection/key-guide indexes, this menu is **not**
     * enablement-filtered: it enumerates the full static catalog ([RuleKeyCatalog.SOURCES],
     * also used to build the external skill's `rule-keys.json`), because the
     * agent needs to know which keys *could* apply before deciding what to
     * fetch. The full self-describing scheme is pulled on demand via
     * `list_rule_keys` (which files entries into the Knowledge State block),
     * so this menu stays cheap (~1K tokens).
     *
     * [RuleKeyCatalog.SOURCES] is pure-static (reflection over `RuleKey`
     * objects; `RuleKeyCatalog.FRAMEWORK_NAME` is a `const val` inlined at
     * compile time), so this adds no IntelliJ [Project] dependency to the
     * builder.
     */
    private fun keyIndexMessage(): AiMessage.System {
        val infos = RuleKeyCatalog.SOURCES.flatMap { (source, supplier) ->
            supplier().map { RuleKeyCatalog.RuleKeyInfo(it, source) }
        }
        val entries = RuleKeyCatalog.schemeEntries(infos)
        return AiMessage.System(RuleKeyCatalog.renderKeyMenu(entries))
    }

    private fun loadBase(): String {
        return javaClass.getResourceAsStream(BASE_RESOURCE)?.use { stream ->
            stream.readBytes().toString(Charsets.UTF_8).trim()
        } ?: run {
            // Should never happen — the resource ships in the JAR.
            LOG.warn("agent-base.md resource not found on classpath; falling back to empty base prompt")
            ""
        }
    }

    private fun loadSubAgentBase(): String {
        return javaClass.getResourceAsStream(SUB_AGENT_BASE_RESOURCE)?.use { stream ->
            stream.readBytes().toString(Charsets.UTF_8).trim()
        } ?: run {
            // Should never happen — the resource ships in the JAR. Falling back
            // to the orchestrator's base prompt keeps the sub-agent runnable,
            // but re-introduces the "unknown tool" mismatch this split fixes.
            LOG.warn(
                "sub-agent-base.md resource not found on classpath; " +
                    "falling back to agent-base.md (sub-agent may call unknown tools)"
            )
            BASE
        }
    }
}
