package com.itangcent.easyapi.core.ai.tools

import com.itangcent.easyapi.core.ai.agent.FileReadConsentGate
import com.itangcent.easyapi.core.logging.IdeaLog
import java.nio.file.Files

/**
 * Perception tool that reads a **rule file** by name or path.
 *
 * Resolution order:
 * 1. A name (`security.properties`) or scope-prefixed name
 * (`global:jwt.rules` / `project:custom.rules`) — resolved via
 * [ToolContext.ruleFileResolver.resolveByName] against the tracked rule
 * directories. This is the preferred form: the agent does not know the
 * user's home directory, so addressing files by name avoids guessing
 * absolute paths.
 * 2. An absolute path inside a tracked rule directory — validated by
 * [ToolContext.ruleFileResolver.resolve]. Accepted as a fallback.
 *
 * Paths that fall outside the tracked directories are **not** silently read.
 * For clearly-foreign paths (the `/etc` directory, `.java`/`.kt` files,
 * `..` escapes) the tool refuses outright and points the agent at
 * `get_psi_class_info`. For other out-of-scope paths it asks the user for
 * one-time consent via [FileReadConsentGate]; only an approved read proceeds.
 *
 * **This tool is for rule files only.** To inspect Java/Kotlin source code,
 * use `get_psi_class_info` (pass the FQN) or `get_psi_method_info` instead.
 */
class ReadRuleFileTool : AiTool, IdeaLog {

    override val name: String = "read_rule_file"

    override val description: String =
        "Read the UTF-8 contents of a RULE FILE. Pass a bare filename " +
            "(e.g. \"security.properties\") or a scope-prefixed name " +
            "(\"global:jwt.rules\" / \"project:custom.rules\"); the tool " +
            "resolves it against the tracked.easyapi/ rule folders. An " +
            "absolute path inside a tracked folder is also accepted. You do " +
            "NOT know the user's home directory — never hard-code " +
            "\"/Users/<name>\" or a literal \"~\"; address files by name. " +
            "An out-of-scope path asks the user for one-time consent. NOT " +
            "for source code — to inspect a Java/Kotlin class, use " +
            "get_psi_class_info with its FQN instead."

    override val kind: ToolKind = ToolKind.PERCEPTION

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "path" to mapOf(
                "type" to "string",
                "description" to "A rule file NAME (e.g. \"security.properties\", " +
                    "\"global:jwt.rules\"), or an absolute path inside a tracked " +
                    ".easyapi/ folder. Do NOT pass a guessed absolute path or a " +
                    "Java/Kotlin source path."
            )
        ),
        "required" to listOf("path")
    )

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val path = args["path"] as? String
        if (path.isNullOrBlank()) return ToolResult.Error("missing required parameter: path")

        // 1) Name resolution (preferred), then 2) absolute-path resolution.
        val resolved = ctx.ruleFileResolver.resolveByName(path)
            ?: ctx.ruleFileResolver.resolve(path)

        if (resolved != null) {
            return readPath(resolved, path)
        }

        // Outside the allowed rule directories. Refuse outright for paths that
        // are clearly not rule files; for everything else, ask the user.
        if (isNeverConsentable(path)) {
            return buildOutsideAllowedError(path)
        }
        val granted = ctx.readConsents.await(path)
        if (!granted) {
            LOG.info("read_rule_file: user denied consent for $path")
            return buildOutsideAllowedError(path)
        }
        // One-time consent granted — read the requested path directly.
        LOG.info("read_rule_file: user granted consent for $path")
        val target = runCatching {
            java.nio.file.Paths.get(path).toAbsolutePath().normalize()
        }.getOrNull() ?: return buildOutsideAllowedError(path)
        return readPath(target, path)
    }

    private fun readPath(target: java.nio.file.Path, displayPath: String): ToolResult {
        return runCatching {
            ToolResult.Text(Files.readString(target))
        }.getOrElse {
            ToolResult.Error("failed to read $displayPath: ${it.message}")
        }
    }

    /**
     * Paths that should never trigger a consent prompt: source files (the
     * agent has dedicated PSI tools for those), system directories, and
     * `..` escapes. These are refused outright with a corrective hint.
     */
    private fun isNeverConsentable(requestedPath: String): Boolean {
        val lower = requestedPath.lowercase()
        if (lower.endsWith(".java") || lower.endsWith(".kt")) return true
        if (lower.startsWith("/etc/") || lower == "/etc/passwd") return true
        if (requestedPath.contains("..")) return true
        return false
    }

    /**
     * Build a helpful error when the path is outside allowed rule directories.
     *
     * The AI sometimes mistakes this tool for a general file reader and passes
     * Java/Kotlin source paths (e.g. `.../src/main/java/Foo.java`). The error
     * points it at `get_psi_class_info` so the next turn converges.
     */
    private fun buildOutsideAllowedError(requestedPath: String): ToolResult.Error {
        val hint = when {
            requestedPath.endsWith(".java") || requestedPath.endsWith(".kt") ->
                " To inspect a source class, use get_psi_class_info with its " +
                    "fully qualified name (e.g. \"com.example.MyFilter\") instead."
            else -> ""
        }
        return ToolResult.Error(
            "path outside allowed rule directories: $requestedPath. " +
                "Allowed dirs are the project's.easyapi/ folder and the " +
                "global ~/.easyapi/ folder.$hint"
        )
    }
}
