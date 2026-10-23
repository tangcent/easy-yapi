package com.itangcent.easyapi.core.mcp

import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.export.EndpointScanResult
import com.itangcent.easyapi.core.export.ExportTargets
import com.itangcent.easyapi.core.export.ProjectSummary
import com.itangcent.easyapi.core.export.TargetedExportService
import com.itangcent.easyapi.core.internal.threading.background
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.logging.console
import kotlinx.coroutines.CancellationException

/**
 * EasyYapi tools exposed through the IDE's built-in MCP server.
 *
 * Loaded only when the MCP Server plugin is present (optional descriptor `easy-api-mcp.xml`); the only
 * class allowed to reference `com.intellij.mcpserver.*`, and only its three version-stable pieces:
 * [McpToolset], [McpTool] and [McpDescription]. The rest of that API changed shape after 2025.2.1, the
 * version the plugin compiles against (the project lookup moved to another file class, `mcpFail` and
 * `McpExpectedError` gained a parameter), so calling it would fail with `NoSuchMethodError` on newer
 * IDEs; `McpApiCompatGuardTest` checks the compiled classes of the whole plugin. Hence:
 * - the project comes from the tool's own `projectPath` argument, via [McpProjectResolver];
 * - failures are returned as `{"error":{…}}` text ([McpToolError]), never thrown.
 *
 * Boundary class: the MCP server calls it on its own coroutines, so each tool moves to
 * `IdeDispatchers.Background` first and never lets an unexpected exception escape — see [runTool].
 */
class EasyApiMcpToolset : McpToolset, IdeaLog {

    /**
     * Scans the targets the caller names and lists the endpoints EasyYapi would export for them, in the
     * project [projectPath] names. Read-only; at least one target is required.
     */
    @McpTool(name = SCAN_ENDPOINTS)
    @McpDescription(description = SCAN_ENDPOINTS_DESCRIPTION)
    suspend fun scanEndpoints(
        @McpDescription(description = CLASSES_DESCRIPTION)
        classes: List<String>? = null,
        @McpDescription(description = METHODS_DESCRIPTION)
        methods: List<String>? = null,
        @McpDescription(description = FILES_DESCRIPTION)
        files: List<String>? = null,
        @McpDescription(description = PROJECT_PATH_DESCRIPTION)
        projectPath: String? = null,
    ): String = runTool(SCAN_ENDPOINTS, projectPath) { project, summary ->
        val targets = ExportTargets(classes.orEmpty(), methods.orEmpty(), files.orEmpty())
        project.console.info("MCP $SCAN_ENDPOINTS called: $targets")
        when (val result = TargetedExportService.getInstance(project).scanEndpoints(targets)) {
            is EndpointScanResult.Scanned -> {
                val skipped = result.report.skipped
                val reasons = if (skipped.isEmpty()) "" else ":\n" + skipped.joinToString("\n") { it.describe() }
                project.console.info(
                    "MCP $SCAN_ENDPOINTS returned ${result.report.count} endpoint(s), " +
                        "skipped ${skipped.size} target(s)$reasons"
                )
                result.report.copy(project = summary).toJson()
            }

            is EndpointScanResult.Failure -> {
                project.console.info("MCP $SCAN_ENDPOINTS failed: ${result.message}")
                McpToolError.of(result, summary).toJson()
            }
        }
    }

    /**
     * Runs a tool body on `IdeDispatchers.Background`, for the project [projectPath] names, and turns every
     * unexpected exception into an [McpToolError.INTERNAL_ERROR] result. Shared by every tool of this toolset.
     *
     * - the project comes from [McpProjectResolver]; when no single open project fits, the body does not
     *   run and the result is the error;
     * - cancellation is rethrown untouched;
     * - any other `Exception` is logged with its stack trace and returned as `{"error":{…}}` text;
     *   an `Error` propagates.
     *
     * @param block the tool body, given the project and the [ProjectSummary] its results name
     */
    internal suspend fun runTool(
        toolName: String,
        projectPath: String?,
        block: suspend (Project, ProjectSummary) -> String
    ): String =
        try {
            background {
                when (val resolution = McpProjectResolver.resolve(projectPath)) {
                    is ProjectResolution.Found -> {
                        val project = resolution.project
                        block(project, ProjectSummary(project.name, project.basePath))
                    }

                    is ProjectResolution.Failed -> {
                        LOG.info("MCP $toolName failed: ${resolution.message}")
                        McpToolError(resolution.code, resolution.message, openProjects = resolution.openProjects)
                            .toJson()
                    }
                }
            }
        } catch (e: ProcessCanceledException) {
            throw e // platform control flow: rethrow, never log
        } catch (e: CancellationException) {
            throw e // never swallow coroutine cancellation
        } catch (e: Exception) {
            LOG.warn("MCP tool $toolName failed", e)
            McpToolError(
                McpToolError.INTERNAL_ERROR,
                "$toolName failed: ${e.message ?: e.javaClass.simpleName}. See idea.log for details."
            ).toJson()
        }

    companion object {
        const val SCAN_ENDPOINTS = "easyapi_scan_endpoints"

        private const val SCAN_ENDPOINTS_DESCRIPTION =
            "Scans the named classes, methods and source files and lists the API endpoints EasyYapi would " +
                "export for them, as JSON. Read-only: nothing is exported or changed.\n" +
                "Name at least one target; the whole project is never scanned. Every target must resolve, " +
                "otherwise nothing is scanned. A target that resolves but yields no endpoint (a request or " +
                "response model, a service, an unmapped method) is reported in skipped while the others are " +
                "listed; if no target yields an endpoint, the call fails.\n" +
                "Files created or changed outside the IDE are seen only after the IDE syncs them from disk, so a " +
                "call right after such an edit may show old content or report a new class or method as not " +
                "found (TARGETS_UNRESOLVED); call again shortly.\n" +
                "Result: {project: {name, basePath}, targets: [{kind, input, resolved}], " +
                "skipped: [{kind, input, reason}], count, " +
                "endpoints: [{protocol, httpMethod, path, name, folder, className, methodName, module}]}. " +
                "Fields without a value are omitted.\n" +
                "On failure the result is {error: {code, message, ...}} instead, where code is one of " +
                "TARGETS_REQUIRED, TARGETS_UNRESOLVED (with failures), NO_ENDPOINTS (with skipped), " +
                "TARGETS_CHANGED, INDEX_NOT_READY, PROJECT_NOT_FOUND or PROJECT_AMBIGUOUS (with openProjects), " +
                "INTERNAL_ERROR."

        private const val CLASSES_DESCRIPTION =
            "Classes to scan: fully qualified names (com.example.web.UserController) or simple names " +
                "(UserController). Project sources only; a simple name matching several classes fails with candidates."

        private const val METHODS_DESCRIPTION =
            "Methods to scan, each as ClassName#methodName without a parameter list, e.g. " +
                "com.example.web.UserController#getUser. All overloads with that name declared in the class are " +
                "included; name an inherited method on the class that declares it."

        private const val FILES_DESCRIPTION =
            "Source files to scan, as paths relative to the project root, e.g. " +
                "user-service/src/main/java/com/example/web/UserController.java. Each file must declare at least one class."

        private const val PROJECT_PATH_DESCRIPTION =
            "Absolute path of the project to work on: its root directory, or a directory or file inside it " +
                "(the innermost open project that contains it is used). Pass it on every call; it is required " +
                "when several projects are open in the IDE."
    }
}
