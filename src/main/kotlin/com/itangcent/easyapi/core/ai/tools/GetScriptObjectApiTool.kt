package com.itangcent.easyapi.core.ai.tools

import com.itangcent.easyapi.core.ai.agent.KnowledgeState
import com.itangcent.easyapi.core.rule.context.RuleKeyScriptProfiler
import com.itangcent.easyapi.core.rule.context.ScriptObjectApi

/**
 * Fetches the full method signatures for one or more shared script objects.
 *
 * Shared objects (logger, session, tool, request, response, class, method, etc.)
 * are only returned by id reference in `get_rule_context` (to avoid duplication).
 * Use this tool to fetch their complete callable method signatures once.
 *
 * Ids are resolved against the profiler's static object dictionary (every
 * it-context object, common helper object, and additional binding with a
 * wrapper class) — the dictionary is derived from the code, not from any
 * particular rule key, so all ids resolve in any authoring context.
 *
 * ## Stateful output
 *
 * All results are upserted into `§objects` section of the Knowledge State.
 * The receipt reports the added/updated entry count and names any unknown
 * ids it had to ignore, so the model can correct them in one round trip.
 */
class GetScriptObjectApiTool : AiTool {

    override val name: String = "get_script_object_api"

    override val description: String =
        "Fetch the full method signatures for one or more shared script objects " +
            "by id (e.g. \"logger\", \"session\", \"request\", \"response\", \"class\", \"method\"). " +
            "Returns a JSON object mapping id -> {type, description, methods}. " +
            "Call get_rule_context(key=...) first to discover which objects are referenced."

    override val kind: ToolKind = ToolKind.PERCEPTION

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "ids" to mapOf(
                "type" to "array",
                "items" to mapOf("type" to "string"),
                "description" to "One or more script object ids to fetch (e.g. [\"logger\", \"request\"])."
            )
        ),
        "required" to listOf("ids")
    )

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val rawIds = args["ids"]
        val ids: List<String> = when (rawIds) {
            is List<*> -> rawIds.filterIsInstance<String>()
            is String -> rawIds.split(",").map { it.trim() }.filter { it.isNotBlank() }
            else -> return ToolResult.Error("missing or invalid parameter: ids (expected string array)")
        }

        if (ids.isEmpty()) {
            return ToolResult.Error("ids must not be empty")
        }

        val objectsById = RuleKeyScriptProfiler.allScriptObjects().associateBy { it.id }

        val unknownIds = ids.filter { it !in objectsById }
        if (unknownIds.size == ids.size) {
            // Nothing resolved: tell the model what it could have asked for,
            // otherwise it can only guess ids.
            return ToolResult.Error(
                "all requested object ids are unknown: ${unknownIds.joinToString(",")}. " +
                    "Known object ids: ${objectsById.keys.sorted().joinToString(",")}"
            )
        }

        // Render the resolvable ids into KnowledgeState entries; unknown ids
        // are dropped from the entry set but named in the receipt, so the
        // model can correct them in one round trip.
        // This is the ONLY writer of §objects — get_rule_context only references
        // object ids, so the full signatures can never be downgraded to a count.
        val knownIds = ids.filter { it in objectsById }
        val stateEntries = knownIds.map { id ->
            KnowledgeState.Entry(id = id, renderedLine = renderObject(objectsById[id]!!))
        }

        val receiptNote = buildString {
            append("${stateEntries.size} object APIs added/updated")
            if (unknownIds.isNotEmpty()) {
                append("; unknown ids ignored: ${unknownIds.joinToString(",")}")
            }
        }

        return ToolResult.Stateful(
            section = KnowledgeState.SECTION_OBJECTS,
            entries = stateEntries,
            receiptNote = receiptNote
        )
    }

    /**
     * Render a [ScriptObjectApi] as a compact entry for the KnowledgeState.
     * Format: `id | type | description | method1(...) : returnType, method2(...) : returnType`
     */
    private fun renderObject(obj: ScriptObjectApi): String {
        val methodList = obj.methods.joinToString(", ") { m ->
            val params = m.parameters.joinToString(", ") { p ->
                "${p.name}: ${p.type}"
            }
            "${m.name}($params): ${m.returns}"
        }
        val parts = mutableListOf(obj.id, obj.type)
        if (obj.description.isNotBlank()) parts.add(obj.description)
        if (methodList.isNotBlank()) parts.add("methods: $methodList")
        return parts.joinToString(" | ")
    }
}
