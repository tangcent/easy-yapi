package com.itangcent.easyapi.ai.tools

import com.itangcent.easyapi.rule.RuleKey
import com.itangcent.easyapi.rule.RuleKeys
import com.itangcent.easyapi.util.json.GsonUtils
import kotlin.reflect.full.memberProperties

/**
 * Perception tool that lists all known rule keys.
 *
 * Reflects over [RuleKeys] via Kotlin reflection and returns a JSON array of
 * `{name, type}` objects. No PSI / project context needed.
 *
 * The `RuleKeys.kt` source file groups keys by Kotlin comment sections; those
 * comments aren't visible to reflection, so this tool returns a flat list. A
 * later revision can annotate the fields to preserve grouping.
 */
class ListRuleKeysTool : AiTool {

    override val name: String = "list_rule_keys"

    override val description: String =
        "List all known EasyAPI rule keys. Returns a JSON array of " +
            "{name, type}. Use this to discover what can be configured."

    override val kind: ToolKind = ToolKind.PERCEPTION

    override val parametersSchema: Map<String, Any?> = emptyMap()

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val keys = RuleKeys::class.memberProperties
.mapNotNull { prop ->
                runCatching { prop.get(RuleKeys) as? RuleKey<*> }
.getOrNull()
                    ?.let { mapOf("name" to it.name, "type" to it::class.simpleName) }
            }
        return ToolResult.Text(GsonUtils.toJson(keys))
    }
}
