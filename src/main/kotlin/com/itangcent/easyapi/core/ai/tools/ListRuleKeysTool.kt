package com.itangcent.easyapi.core.ai.tools

import com.itangcent.easyapi.core.ai.agent.KnowledgeState
import com.itangcent.easyapi.core.rule.RuleKeyCatalog
import com.itangcent.easyapi.core.rule.RuleKeyRegistry

/**
 * Perception tool that lists all known rule keys.
 *
 * Delegates to [RuleKeyCatalog] so the catalog reflects the general/shared
 * [com.itangcent.easyapi.core.rule.RuleKeys] plus every registered channel's
 * channel-specific keys plus the implicit config keys read by name via
 * `configReader.getFirst(…)`. Returns a JSON array of self-describing [RuleKeyCatalog.SchemeEntry]
 * objects, which is the same shape as the external `easy-yapi-assistant` skill's
 * `rule-keys.json`. Every entry also includes a compact `scriptContext` summary
 * with the execution mode summary. Call `get_rule_context(key=…)` before writing
 * a Groovy or Postman script; it returns the complete binding definitions and
 * references to shared script objects.
 *
 * `source` is `"general"`, `"implicit"`, or a channel/framework id (e.g.
 * `"hoppscotch"`, `"yapi"`).
 *
 * Keys are de-duplicated by name — general keys take precedence over
 * channel/implicit keys with the same name.
 *
 * ## Self-describing loop (design C4 / task A5)
 *
 * Every key carries its own one-line semantics in its
 * [com.itangcent.easyapi.core.rule.RuleKeyScheme] (`summary`), so this field
 * is always included — no separate description source needed. Whether a key
 * also has a per-key guide file is discovered separately via
 * `get_rule_detail(key=…)` (which returns the self-describing scheme profile
 * for a key with no guide).
 *
 * ## Stateful output
 *
 * Returns [ToolResult.Stateful] targeting `§keys`. The rendered entries are
 * compact one-line summaries per key — one line per canonical key name.
 *
 * Aliases are deliberately **not** rendered: they exist for backward
 * compatibility with older rule files, and the agent must author canonical
 * names only. A key requested by alias is resolved by `get_rule_context`, which
 * files the entry under the canonical name and says so in its receipt.
 */
class ListRuleKeysTool : AiTool {

    override val name: String = "list_rule_keys"

    override val description: String =
        "List all known EasyAPI rule keys (canonical names only — aliases are " +
            "compatibility-only and must not be written into rule files). Entries: " +
            "name, source, summary, outputShape, contexts. " +
            "Call get_rule_context(key=…) for the complete bindings and script object references, " +
            "and get_rule_detail(key=…) for the full guide when one exists."

    override val kind: ToolKind = ToolKind.PERCEPTION

    override val parametersSchema: Map<String, Any?> = emptyMap()

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val registry = RuleKeyRegistry.getInstance(ctx.project)
        val catalogEntries = RuleKeyCatalog.schemeEntries(
            registry.enabledKeys().map { RuleKeyCatalog.RuleKeyInfo(it.key, it.source) }
        )

        val stateEntries = catalogEntries.map { entry ->
            KnowledgeState.Entry(id = entry.name, renderedLine = buildKeyLine(entry))
        }

        return ToolResult.Stateful(
            section = KnowledgeState.SECTION_KEYS,
            entries = stateEntries,
            receiptNote = "${catalogEntries.size} keys catalogued"
        )
    }

    /**
     * Build a compact one-line directory entry for a key.
     * Format: `name | source | summary | outputShape | contexts`
     */
    private fun buildKeyLine(entry: RuleKeyCatalog.SchemeEntry): String {
        val parts = mutableListOf(entry.name, entry.source)
        entry.summary?.let { parts.add(it) }
        parts.add(entry.outputShape ?: "?")
        if (entry.staticConfiguration) parts.add("static")
        if (entry.contextKinds.isNotEmpty()) {
            parts.add("[${entry.contextKinds.joinToString(",")}]")
        }
        return parts.joinToString(" | ")
    }
}
