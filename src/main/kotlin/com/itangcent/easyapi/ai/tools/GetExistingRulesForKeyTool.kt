package com.itangcent.easyapi.ai.tools

import com.itangcent.easyapi.util.json.GsonUtils

/**
 * Perception tool that returns the existing rule values for one or more keys
 *.
 *
 * `ctx.configReader.sourcesForKey(key)` returns `SourceValue`s ordered by
 * source priority descending; this tool surfaces them with sourceId + priority
 * so the agent can reason about precedence.
 *
 * Supports batch: pass `keys` (array) to look up multiple keys in one call,
 * or `key` (string) for a single lookup. When `keys` is used, the result is a
 * JSON object mapping each key to its values array.
 */
class GetExistingRulesForKeyTool : AiTool {

    override val name: String = "get_existing_rules_for_key"

    override val description: String =
        "Get all configured values for one or more rule keys, with their source " +
            "and priority. Pass `key` (string) for a single key or `keys` (array " +
            "of strings) to batch-check multiple keys. Returns a JSON array " +
            "{sourceId, priority, value} for a single key, or a JSON object " +
            "mapping each key to its array for batch mode."

    override val kind: ToolKind = ToolKind.PERCEPTION

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "key" to mapOf(
                "type" to "string",
                "description" to "A single rule key (e.g. \"api.name\", \"field.ignore\")."
            ),
            "keys" to mapOf(
                "type" to "array",
                "items" to mapOf("type" to "string"),
                "description" to "Multiple rule keys to look up in one call (batch mode)."
            )
        )
    )

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val keys = PsiNameResolver.extractStringList(args, "key", "keys")
        if (keys.isEmpty()) return ToolResult.Error("missing parameter: provide `key` (string) or `keys` (array)")

        if (keys.size == 1) {
            return ToolResult.Text(GsonUtils.toJson(lookupOne(keys[0], ctx)))
        }
        val result = keys.associateWith { lookupOne(it, ctx) }
        return ToolResult.Text(GsonUtils.toJson(result))
    }

    private fun lookupOne(key: String, ctx: ToolContext): List<Map<String, Any?>> {
        val sourced = ctx.configReader.sourcesForKey(key)
        if (sourced.isNotEmpty()) {
            return sourced.map {
                mapOf("sourceId" to it.sourceId, "priority" to it.priority, "value" to it.value)
            }
        }
        // M-9 fallback: a fake/mock ConfigReader in tests doesn't override
        // sourcesForKey, so degrade to plain values without source metadata
        // rather than looking broken.
        return ctx.configReader.getAll(key).mapIndexed { idx, value ->
            mapOf("sourceId" to "unknown", "priority" to -idx, "value" to value)
        }
    }
}
