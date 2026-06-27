package com.itangcent.easyapi.ai.tools

/**
 * Perception tool that reads plugin documentation from the bundled knowledge
 * base.
 *
 * Accepts `name ∈ {overview, index, rule-guide, settings-guide, usage-guide}`
 * and returns the Markdown text from `docs/knowledge-base/<name>.md` on the
 * classpath. `overview` resolves to `README.md`, `index` to `index.md`.
 *
 * Contributor internals are not exposed to the agent (they live in
 * `AGENTS.md`); `easyapi-script-reference` is still mirrored so existing
 * prompts keep working.
 */
class GetPluginDocTool : AiTool {

    override val name: String = "get_plugin_doc"

    override val description: String =
        "Read a plugin documentation page from the EasyApi knowledge base. " +
            "Parameter `name` is one of " +
            "overview | index | rule-guide | settings-guide | usage-guide | " +
            "easyapi-script-reference. Returns the doc text (Markdown)."

    override val kind: ToolKind = ToolKind.PERCEPTION

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "name" to mapOf(
                "type" to "string",
                "enum" to listOf(
                    "overview", "index", "rule-guide", "settings-guide",
                    "usage-guide", "easyapi-script-reference"
                ),
                "description" to "Which documentation page to read."
            )
        ),
        "required" to listOf("name")
    )

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val name = args["name"] as? String ?: return ToolResult.Error("missing required parameter: name")
        val resource = when (name) {
            "overview" -> "/docs/knowledge-base/README.md"
            "index" -> "/docs/knowledge-base/index.md"
            "rule-guide" -> "/docs/knowledge-base/rule-guide.md"
            "settings-guide" -> "/docs/knowledge-base/settings-guide.md"
            "usage-guide" -> "/docs/knowledge-base/usage-guide.md"
            // Kept for backward compatibility with older prompts; the script
            // reference is mirrored under knowledge-base/.
            "easyapi-script-reference" -> "/docs/knowledge-base/easyapi-script-reference.md"
            else -> return ToolResult.Error("unknown doc name: $name")
        }
        val text = javaClass.getResourceAsStream(resource)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: return ToolResult.Error("doc not bundled: $name")
        return ToolResult.Text(text)
    }
}
