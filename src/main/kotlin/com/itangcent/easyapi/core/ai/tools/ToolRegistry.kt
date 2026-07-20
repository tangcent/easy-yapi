package com.itangcent.easyapi.core.ai.tools

import com.itangcent.easyapi.core.ai.AiToolSpec
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.util.json.GsonUtils
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds

/**
 * Holds the agent's capabilities and routes tool invocations
 *.
 *
 * Built once per conversation. [schemas] is passed as the `tools` field of
 * every `AiChatRequest`. [dispatch] consults [kindOf]: for ACTION tools it
 * first awaits `ctx.approvals.await(name, args)` (unless the tool overrides
 * [AiTool.requiresApproval] to `false`), then runs [AiTool.execute].
 *
 * Every dispatch is wrapped in a per-tool timeout ([AiTool.timeoutMs],
 * default 30s); tools that suspend on user input override it.
 */
class ToolRegistry(private val tools: List<AiTool>) : IdeaLog {

    private val byName: Map<String, AiTool> = tools.associateBy { it.name }

    /** Tool specs for the LLM — name + description + parameters JSON schema. */
    fun schemas(): List<AiToolSpec> = tools.map { tool ->
        AiToolSpec(
            name = tool.name,
            description = tool.description,
            parametersJsonSchema = GsonUtils.toJson(tool.parametersSchema)
        )
    }

    /** The kind of a named tool, or `null` if unknown. */
    fun kindOf(name: String): ToolKind? = byName[name]?.kind

    /**
     * Whether dispatching [name] will consult `ctx.approvals` before running.
     * `true` for ACTION tools that don't override [AiTool.requiresApproval]
     * to `false`; `false` for perception tools, staging actions, and unknown
     * tools (unknown tools fail in dispatch, not at the gate).
     */
    fun requiresApproval(name: String): Boolean =
        byName[name]?.let { it.kind == ToolKind.ACTION && it.requiresApproval } ?: false

    /**
     * Dispatch [name] with [args] under [ctx].
     *
     * - Unknown tool → [ToolResult.Error].
     * - ACTION tool with `requiresApproval = true` → awaits
     * `ctx.approvals.await(...)` first; if rejected, returns [ToolResult.Error].
     * - Each tool is capped at its own [AiTool.timeoutMs] (30s by default).
     */
    suspend fun dispatch(name: String, args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val tool = byName[name]
        if (tool == null) {
            LOG.info("tool dispatch: unknown tool '$name'")
            return ToolResult.Error("Unknown tool: $name")
        }

        // Log the call (args as compact JSON) at INFO level for diagnostics.
        // (Tool arguments are diagnostics-only, never persisted secrets.)
        LOG.info("tool call: $name args=${GsonUtils.toJson(args)}")

        if (tool.kind == ToolKind.ACTION && tool.requiresApproval) {
            val approved = ctx.approvals.await(name, args)
            if (!approved) {
                LOG.info("tool dispatch: '$name' rejected by user")
                return ToolResult.Error("User rejected action: $name")
            }
        }

        val started = System.currentTimeMillis()
        val timeoutMs = tool.timeoutMs
        return runCatching {
            if (timeoutMs <= 0L) {
                tool.execute(args, ctx)
            } else {
                withTimeout(timeoutMs.milliseconds) {
                    tool.execute(args, ctx)
                }
            }
        }.getOrElse { e ->
            // Recoverable tool failure — warn (never error) with
            // the throwable so the stack is in idea.log for post-mortem.
            LOG.warn("tool '$name' failed after ${System.currentTimeMillis() - started}ms", e)
            ToolResult.Error("Tool '$name' failed: ${e.message}")
        }.also { result ->
            // Log the result body at DEBUG level. Text results are truncated
            // to avoid flooding idea.log with huge tool outputs; errors are
            // logged in full (they're short and diagnostic).
            val elapsed = System.currentTimeMillis() - started
            when (result) {
                is ToolResult.Text -> LOG.info(
                    "tool result: $name -> text(len=${result.value.length}) in ${elapsed}ms " +
                        "body=${result.value.take(MAX_LOGGED_BODY_CHARS)}" +
                        if (result.value.length > MAX_LOGGED_BODY_CHARS) "…" else ""
                )
                is ToolResult.Error -> LOG.info(
                    "tool result: $name -> error in ${elapsed}ms msg=${result.message}"
                )
            }
        }
    }

    companion object {
        /** Default per-tool timeout (30s). Tools may override via [AiTool.timeoutMs]. */
        const val DEFAULT_TOOL_TIMEOUT_MS = 30_000L

        /** Max chars of a Text result body to log at DEBUG level. */
        const val MAX_LOGGED_BODY_CHARS = 500
    }
}

/**
 * Body-free label for a [ToolResult] used in diagnostic logs.
 * Mirrors [RuleAuthoringAgent]'s `resultKind()` but lives next to the
 * registry's own dispatch trace for cohesion.
 */
private fun ToolResult.kindLabel(): String = when (this) {
    is ToolResult.Text -> "text(len=${value.length})"
    is ToolResult.Error -> "error"
}
