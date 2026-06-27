package com.itangcent.easyapi.ai.tools

import java.nio.file.Files
import java.nio.file.Path

/**
 * Reserved write-action stub.
 *
 * v1 does NOT register this in the agent's tool set — the disk write happens
 * only through the user-confirmed "Save…" UI flow. The class exists so
 * a later version can hand the agent write-with-confirmation: when enabled,
 * `dispatch` would await `ctx.approvals.await(...)` before executing, and the
 * `requiresApproval = true` flag already reflects that intent.
 *
 * The implementation is functional but unused — kept here so a future version
 * only has to register it in the ToolRegistry, not rewrite the file write.
 */
class WriteRuleFileTool : AiTool {

    override val name: String = "write_rule_file"

    override val description: String =
        "(Reserved) Write rule content to a file. v1 does NOT expose this to " +
            "the agent — saving is done through the user-confirmed 'Save…' UI " +
            "flow. A later version may enable it with explicit approval gating."

    override val kind: ToolKind = ToolKind.ACTION

    override val requiresApproval: Boolean = true

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "path" to mapOf(
                "type" to "string",
                "description" to "Absolute path to the rule file to write."
            ),
            "content" to mapOf(
                "type" to "string",
                "description" to "Content to write."
            )
        ),
        "required" to listOf("path", "content")
    )

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val pathStr = args["path"] as? String
        val content = args["content"] as? String
        if (pathStr.isNullOrBlank() || content.isNullOrBlank()) {
            return ToolResult.Error("missing required parameter(s): path, content")
        }
        val resolved: Path = ctx.ruleFileResolver.resolve(pathStr)
            ?: return ToolResult.Error("path outside allowed rule directories: $pathStr")
        return runCatching {
            Files.writeString(resolved, content)
            // Clear any staged proposal now that the write has landed.
            ctx.workingMemory.proposal = null
            ToolResult.Text("Wrote ${content.length} chars to $resolved")
        }.getOrElse { ToolResult.Error("failed to write $pathStr: ${it.message}") }
    }
}
