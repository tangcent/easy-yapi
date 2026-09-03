package com.itangcent.easyapi.core.ai.tools

/**
 * Perception tool that reads plugin documentation from the bundled knowledge
 * base.
 *
 * Accepts `name ∈ {overview, index, rule-guide, settings-guide, usage-guide,
 * postman-script-reference}` and returns the Markdown text from
 * `docs/knowledge-base/<name>.md` on the classpath. `overview` resolves to
 * `README.md`, `index` to `index.md`.
 *
 * `postman-script-reference` documents the Postman-compatible `pm.*` Groovy
 * API (pre-request / post-response scripts) — the agent should fetch it only
 * when authoring `postman.*` rule keys; the shared Groovy `it`-context object
 * APIs come from `get_script_object_api` instead. The legacy
 * `easyapi-script-reference` id is kept as a non-advertised alias so older
 * prompts keep working.
 */
class GetPluginDocTool : AiTool {

    override val name: String = "get_plugin_doc"

    override val description: String =
        "Read a plugin documentation page from the EasyApi knowledge base. " +
            "Parameter `name` is one of " +
            "overview | index | rule-guide | settings-guide | usage-guide | " +
            "postman-script-reference. postman-script-reference documents the " +
            "Postman-compatible pm.* Groovy API — use it only when authoring " +
            "postman.* scripts. Returns the doc text (Markdown)."

    override val kind: ToolKind = ToolKind.PERCEPTION

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "name" to mapOf(
                "type" to "string",
                "enum" to listOf(
                    "overview", "index", "rule-guide", "settings-guide",
                    "usage-guide", "postman-script-reference"
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
            // Postman-compatible pm.* Groovy API reference — Postman-only.
            "postman-script-reference" -> "/docs/knowledge-base/postman-script-reference.md"
            // Backward-compatible alias for older prompts / persisted
            // conversations. Not listed in the schema, so the model does not
            // discover it for unrelated (non-Postman) tasks.
            "easyapi-script-reference" -> "/docs/knowledge-base/postman-script-reference.md"
            else -> return ToolResult.Error("unknown doc name: $name")
        }
        val text = javaClass.getResourceAsStream(resource)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: return ToolResult.Error("doc not bundled: $name")
        return ToolResult.Text(text)
    }
}
