package com.itangcent.easyapi.core.ai.tools

import com.itangcent.easyapi.core.ai.agent.KnowledgeState
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.rule.RuleKeyRegistry
import com.itangcent.easyapi.core.rule.context.RuleKeyScriptProfiler
import com.itangcent.easyapi.core.rule.context.RuleScriptProfile
import com.itangcent.easyapi.core.util.json.GsonUtils

/**
 * Returns the executable script-context contract for one registered rule key.
 *
 * Unlike a prose recipe, this tool describes the actual runtime bindings,
 * the callable script objects, and their ids. It
 * resolves the key through [RuleKeyRegistry], so aliases are accepted and only
 * keys known to the current project/channel/framework mix are returned.
 *
 * ## Aliases are resolved, never propagated
 *
 * An alias (for example `doc.param` for `param.doc`) is a backward-
 * compatibility spelling. The entry is always filed under the **canonical**
 * key name so a later `get_rule_context("param.doc")` does not append a second
 * copy of the same line to `§keyContexts`, and the receipt tells the model to
 * author the rule with the canonical name.
 *
 * ## Shared objects are referenced, not inlined
 *
 * Common objects (logger, session, tool, request, response, class, method, etc.)
 * are only returned as `objectRefs` (ids) when `expand=false` (default), so they
 * are not repeated for every key. `§objects` is written by
 * `get_script_object_api` only — this tool never writes it, so the full method
 * signatures can't be downgraded to a mere method count. Call
 * `get_script_object_api(ids=[...])` once to fetch them.
 *
 * Use `expand=true` to get the full inline profile (legacy mode, for debugging).
 *
 * ## Stateful output
 *
 * When `expand=false` (default), returns [ToolResult.Stateful] targeting
 * `§keyContexts` for the key's profile.
 */
class GetRuleContextTool : AiTool, IdeaLog {

    override val name: String = "get_rule_context"

    override val description: String =
        "Return structured script context for one EasyAPI rule key: execution " +
            "mode, bindings, and object references. Common/shared objects are " +
            "only referenced by id (deduplicated); fetch their full method signatures " +
            "via get_script_object_api([ids]). Use before authoring Groovy or Postman scripts. " +
            "Aliases are accepted but resolved to the canonical key — always write the " +
            "canonical key name in the rule file. " +
            "Pass expand=true to get the full inline profile (debugging only)."

    override val kind: ToolKind = ToolKind.PERCEPTION

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "key" to mapOf(
                "type" to "string",
                "description" to "Known rule key or alias (for example, \"field.ignore\" or \"postman.test\"). " +
                    "Aliases are accepted for compatibility only; the response names the canonical key, " +
                    "which is what you must write in the rule file."
            ),
            "expand" to mapOf(
                "type" to "boolean",
                "description" to "If true, expand all objects inline (legacy debugging). Default false."
            )
        ),
        "required" to listOf("key")
    )

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val keyName = (args["key"] as? String)?.trim()
            ?: return ToolResult.Error("missing required parameter: key")
        if (keyName.isEmpty()) return ToolResult.Error("missing required parameter: key")

        val expand = (args["expand"] as? Boolean) ?: false

        val keyInfo = RuleKeyRegistry.getInstance(ctx.project).findKey(keyName)
            ?: return ToolResult.Error("unknown rule key: $keyName")

        val profile = RuleKeyScriptProfiler.describe(keyInfo.key, keyInfo.source)

        if (expand) {
            // Expand all objects inline (legacy/debugging) — reconstruct the original shape
            val expandedProfile = expandProfile(profile)
            return ToolResult.Text(GsonUtils.toJson(expandedProfile))
        }

        // Always file the entry under the canonical key: an alias would
        // otherwise add a second, textually identical line to §keyContexts.
        val canonicalKey = profile.key
        val resolvedViaAlias = keyName != canonicalKey
        if (resolvedViaAlias) {
            LOG.info("AI agent get_rule_context resolved alias '$keyName' to canonical key '$canonicalKey'")
        }

        val keyContextEntry = KnowledgeState.Entry(
            id = canonicalKey,
            renderedLine = renderKeyContext(profile)
        )

        val refCount = profile.objectRefs.distinct().size

        return ToolResult.Stateful(
            section = KnowledgeState.SECTION_KEY_CONTEXTS,
            entries = listOf(keyContextEntry),
            receiptNote = receiptNote(canonicalKey, keyName.takeIf { resolvedViaAlias }, refCount)
        )
    }

    /**
     * Receipt note for the `§keyContexts` entry.
     *
     * When the model asked for an alias, the note names the canonical key and
     * says so explicitly — aliases exist only for backward compatibility and
     * must not be authored into rule files.
     */
    private fun receiptNote(canonicalKey: String, alias: String?, refCount: Int): String =
        buildString {
            append("key context for '").append(canonicalKey).append("'")
            if (alias != null) {
                append(" (requested as '").append(alias)
                append("', an alias kept only for backward compatibility — write the canonical key '")
                append(canonicalKey).append("' in the rule file)")
            }
            if (refCount > 0) {
                append("; object refs: ").append(refCount)
                append(" — fetch their method signatures with get_script_object_api(ids=[...])")
            }
        }

    /**
     * Reconstructs an expanded profile with all objects inline (for legacy/debugging).
     * This is the shape that was used before the object-deduplication refactoring.
     */
    private fun expandProfile(profile: RuleScriptProfile): Map<String, Any?> {
        val objects = RuleKeyScriptProfiler.collectAllObjects(profile)
            .filter { it.id in profile.objectRefs }
        return mutableMapOf(
            "key" to profile.key,
            "aliases" to profile.aliases,
            "source" to profile.source,
            "executionMode" to profile.executionMode,
            "description" to profile.description,
            "bindings" to profile.bindings,
            "objects" to objects,
            "notes" to profile.notes
        )
    }

    /**
     * Render a key context profile as a compact one-line entry for the KnowledgeState.
     * Format: `key | source | mode | refs: [id1, id2, ...]`
     */
    private fun renderKeyContext(profile: RuleScriptProfile): String {
        val parts = mutableListOf(profile.key, profile.source, profile.executionMode)
        val allRefs = profile.objectRefs.distinct().sorted()
        if (allRefs.isNotEmpty()) {
            parts.add("refs: [${allRefs.joinToString(",")}]")
        }
        return parts.joinToString(" | ")
    }
}
