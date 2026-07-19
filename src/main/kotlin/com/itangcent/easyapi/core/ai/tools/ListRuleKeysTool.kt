package com.itangcent.easyapi.core.ai.tools

import com.itangcent.easyapi.core.rule.RuleKeyRegistry
import com.itangcent.easyapi.core.util.json.GsonUtils

/**
 * Perception tool that lists all known rule keys.
 *
 * Delegates to [RuleKeyRegistry] so the catalog reflects the general/shared
 * [com.itangcent.easyapi.core.rule.RuleKeys] plus every registered channel's
 * channel-specific keys plus the implicit keys read by name via
 * `configReader.getFirst(…)`. Returns a JSON array of `{name, type, source}`
 * objects:
 * - `source` is `"general"`, `"implicit"`, or the channel id (e.g.
 *   `"hoppscotch"`, `"yapi"`).
 *
 * Keys are de-duplicated by name by [RuleKeyRegistry] — general keys take
 * precedence over channel/implicit keys with the same name.
 *
 * The `RuleKeys.kt` source file groups keys by Kotlin comment sections; those
 * comments aren't visible to reflection, so this tool returns a flat list
 * (the `source` field is the closest thing to grouping).
 */
class ListRuleKeysTool : AiTool {

    override val name: String = "list_rule_keys"

    override val description: String =
        "List all known EasyAPI rule keys. Returns a JSON array of " +
            "{name, type, source}. Use this to discover what can be configured."

    override val kind: ToolKind = ToolKind.PERCEPTION

    override val parametersSchema: Map<String, Any?> = emptyMap()

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val keys = RuleKeyRegistry.getInstance(ctx.project).allKeys().map { info ->
            mapOf(
                "name" to info.key.name,
                "type" to info.key::class.simpleName,
                "source" to info.source
            )
        }
        return ToolResult.Text(GsonUtils.toJson(keys))
    }
}
