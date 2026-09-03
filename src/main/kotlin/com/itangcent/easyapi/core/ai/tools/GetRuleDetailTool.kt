package com.itangcent.easyapi.core.ai.tools

import com.itangcent.easyapi.core.ai.agent.Ambient
import com.itangcent.easyapi.core.ai.agent.AmbientPerception
import com.itangcent.easyapi.core.ai.agent.PromptCatalog
import com.itangcent.easyapi.core.rule.RuleKeyRegistry
import com.itangcent.easyapi.core.rule.context.RuleKeyScriptProfiler
import com.itangcent.easyapi.core.rule.context.RuleScriptProfile

/**
 * Perception tool that fetches per-key rule detail from the key-guide
 * catalog (design C3 / task A4).
 *
 * Two access patterns:
 *
 * - **By key** — `get_rule_detail(key="postman.test")` returns the single
 *   per-key guide. `key` takes precedence over any scope args; this is the
 *   pattern to use when the agent knows which key it is about to set. When no
 *   guide file exists for the key, the key's self-describing scheme profile is
 *   returned instead — every registered key is describable.
 *
 * - **By scope** — `get_rule_detail(channel="postman")` returns the
 *   concatenated guides of every key-guide file whose `CatalogScope` matches
 *   the supplied args **and** the ambient-enabled features. Use this when the
 *   agent wants a tour of what a channel/format/framework supports (e.g.
 *   before proposing a Postman workflow bundle).
 *
 * At least one of `key` / `channel` / `format` / `framework` is required;
 * calling with no args returns `Error`.
 *
 * An empty scope match (e.g. `channel="postman"` when Postman is disabled)
 * returns `Text("(no rule-detail files match the given scope)")` — not
 * `Error` — so the agent can react gracefully.
 */
class GetRuleDetailTool : AiTool {

    override val name: String = "get_rule_detail"

    override val description: String =
        "Fetch the full detail for one rule key (by `key`) or every key " +
            "matching a scope (by `channel` / `format` / `framework`). " +
            "At least one argument is required. `key` takes precedence over " +
            "scope args. Returns Markdown; a key with no guide file returns " +
            "its self-describing scheme profile."

    override val kind: ToolKind = ToolKind.PERCEPTION

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "key" to mapOf(
                "type" to "string",
                "description" to "The rule key whose recipe to fetch " +
                    "(e.g. \"postman.test\"). Takes precedence over scope args."
            ),
            "channel" to mapOf(
                "type" to "string",
                "description" to "Scope query: concatenate recipes of every " +
                    "rule file scoped to this channel id (e.g. \"postman\")."
            ),
            "format" to mapOf(
                "type" to "string",
                "description" to "Scope query: concatenate recipes of every " +
                    "rule file scoped to this field-format id."
            ),
            "framework" to mapOf(
                "type" to "string",
                "description" to "Scope query: concatenate recipes of every " +
                    "rule file scoped to this framework id."
            )
        ),
        "required" to emptyList<String>()
    )

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val key = args["key"] as? String
        val channel = args["channel"] as? String
        val format = args["format"] as? String
        val framework = args["framework"] as? String

        // 1. By-key lookup — key wins over scope args.
        if (!key.isNullOrBlank()) {
            val guide = PromptCatalog.body("key-guides", key)
            if (guide != null) {
                return ToolResult.Text(guide)
            }
            // No guide file → fall back to the key's self-describing scheme
            // profile so every registered key stays describable.
            val info = RuleKeyRegistry.getInstance(ctx.project).findKey(key)
                ?: return ToolResult.Error("unknown rule key: $key")
            return ToolResult.Text(profileToMarkdown(RuleKeyScriptProfiler.describe(info.key, info.source)))
        }

        // 2. Scope query — at least one scope arg is required.
        if (channel.isNullOrBlank() && format.isNullOrBlank() && framework.isNullOrBlank()) {
            return ToolResult.Error("provide at least one of key / channel / format / framework")
        }

        // The ambient-enabled features gate the scope match so a disabled
        // channel's guides are not surfaced (mirrors the index message
        // filtering). Reuse the ambient cached on working memory by the agent
        // loop; fall back to a fresh capture when no turn has run yet (e.g.
        // ad-hoc tool unit tests that build their own ToolContext).
        val amb: Ambient = ctx.workingMemory.ambient
            ?: AmbientPerception.capture(ctx.project)
        val entries = PromptCatalog.listFor(
            "key-guides",
            activeChannels = amb.enabledChannels.toSet(),
            activeFormats = amb.enabledFormats.toSet(),
            activeFrameworks = amb.frameworkHints.toSet()
        ).filter { e ->
            // Apply the supplied scope args on top of the ambient-enabled
            // filter: a `channel=postman` query returns only postman-scoped
            // entries (and only when postman is enabled).
            (channel.isNullOrBlank() || e.scope.channel == channel) &&
                (format.isNullOrBlank() || e.scope.format == format) &&
                (framework.isNullOrBlank() || e.scope.framework == framework)
        }

        if (entries.isEmpty()) {
            return ToolResult.Text("(no rule-detail files match the given scope)")
        }
        // Concatenate bodies with a clear separator so the agent can
        // distinguish multiple guides in one response.
        val sb = StringBuilder()
        for ((idx, entry) in entries.withIndex()) {
            if (idx > 0) sb.append("\n\n---\n\n")
            val body = PromptCatalog.body("key-guides", entry.id)
            if (body != null) {
                sb.append("# ").append(entry.title).append("\n\n")
                sb.append(body)
            }
        }
        return ToolResult.Text(sb.toString())
    }

    private fun profileToMarkdown(profile: RuleScriptProfile): String {
        val sb = StringBuilder()
        sb.append("# ").append(profile.key).append("\n\n")
        sb.append("**Source:** `").append(profile.source).append("`  \n")
        sb.append("**Execution mode:** `").append(profile.executionMode).append("`\n\n")
        sb.append(profile.description).append("\n\n")
        if (profile.aliases.isNotEmpty()) {
            sb.append("**Aliases:** ").append(profile.aliases.joinToString(", ") { "`$it`" }).append("\n\n")
        }
        if (profile.notes.isNotEmpty()) {
            sb.append("**Notes:**\n")
            profile.notes.forEach { sb.append("- ").append(it).append("\n") }
            sb.append("\n")
        }
        if (profile.bindings.isNotEmpty()) {
            sb.append("**Bindings:**\n")
            profile.bindings.forEach { b ->
                sb.append("- `").append(b.name)
                if (b.aliases.isNotEmpty()) {
                    sb.append("` (aliases: ").append(b.aliases.joinToString(", ") { "`$it`" }).append(")")
                } else {
                    sb.append("`")
                }
                sb.append(" — ").append(b.description)
                sb.append(" (availability: ").append(b.availability).append(")\n")
            }
            sb.append("\n")
        }
        if (profile.objectRefs.isNotEmpty()) {
            // Keep this fallback compact: full method signatures are NOT inlined
            // here. They are the §objects knowledge block's content, whose single
            // writer is `get_script_object_api` — re-rendering them per key would
            // duplicate tens of KB into every request. List the referenced object
            // ids and point the model at the dedicated tool instead.
            sb.append("**Object APIs:**\n")
            profile.objectRefs.distinct().forEach { ref ->
                sb.append("- `").append(ref).append("` — fetch method signatures via ")
                    .append("`get_script_object_api(ids=[\"").append(ref).append("\"])`\n")
            }
            sb.append("\n")
        }
        return sb.toString()
    }
}
