package com.itangcent.easyapi.core.mcp

import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.export.EndpointScanResult
import com.itangcent.easyapi.core.export.ExportRejection
import com.itangcent.easyapi.core.export.ExportTargets
import com.itangcent.easyapi.core.export.ProjectSummary
import com.itangcent.easyapi.core.export.TargetedExportResult
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
     * Exports the endpoints of the targets the caller names to a channel that can export without user
     * interaction ([DEFAULT_CHANNEL] when [channel] is omitted), in the project [projectPath] names. The
     * targets follow the rules of [scanEndpoints]; [options] are the channel's options for this call only.
     */
    @McpTool(name = EXPORT)
    @McpDescription(description = EXPORT_DESCRIPTION)
    suspend fun exportEndpoints(
        @McpDescription(description = CLASSES_DESCRIPTION)
        classes: List<String>? = null,
        @McpDescription(description = METHODS_DESCRIPTION)
        methods: List<String>? = null,
        @McpDescription(description = FILES_DESCRIPTION)
        files: List<String>? = null,
        @McpDescription(description = PROJECT_PATH_DESCRIPTION)
        projectPath: String? = null,
        @McpDescription(description = CHANNEL_DESCRIPTION)
        channel: String? = null,
        @McpDescription(description = OPTIONS_DESCRIPTION)
        options: Map<String, String>? = null,
    ): String = runTool(EXPORT, projectPath) { project, summary ->
        val targets = ExportTargets(classes.orEmpty(), methods.orEmpty(), files.orEmpty())
        // A blank channel counts as none, as a blank projectPath does.
        val channelId = channel?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_CHANNEL
        val channelOptions = options.orEmpty()
        // Option names only: a value may be a secret, such as a token.
        project.console.info("MCP $EXPORT called: $targets channel=$channelId options=${channelOptions.keys}")
        when (val result = TargetedExportService.getInstance(project).export(targets, channelId, channelOptions)) {
            is TargetedExportResult.Exported -> {
                val report = result.report
                val reasons = if (report.skipped.isEmpty()) "" else ":\n" + report.skipped.joinToString("\n") { it.describe() }
                project.console.info(
                    "MCP $EXPORT returned ${report.count} endpoint(s) for channel ${report.channel} " +
                        "(created ${report.created}, updated ${report.updated}, not written ${report.notWritten}, " +
                        "failed ${report.failed}), skipped ${report.skipped.size} target(s)$reasons"
                )
                report.copy(project = summary).toJson()
            }

            is TargetedExportResult.Failed -> {
                val line = "MCP $EXPORT failed: ${result.failure.message}"
                // Nothing written is the export failing; the other failures are about the arguments or the IDE.
                if (result.failure is ExportRejection.ExportFailed) project.console.warn(line) else project.console.info(line)
                McpToolError.of(result.failure, summary).toJson()
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
        const val EXPORT = "easyapi_export"

        /** The channel [exportEndpoints] uses when the call names none. */
        const val DEFAULT_CHANNEL = "yapi"

        /** How a tool reports a failure, up to the list of codes that ends every tool description. */
        private const val ERROR_RESULT =
            "On failure the result is {error: {code, message, ...}} instead, where code is one of "

        /** The codes of the failures every tool that takes targets shares ([EndpointScanResult.Failure]). */
        private const val TARGET_ERROR_CODES =
            "TARGETS_REQUIRED, TARGETS_UNRESOLVED (with failures), NO_ENDPOINTS (with skipped), " +
                "TARGETS_CHANGED, INDEX_NOT_READY, UNSAVED_IN_IDE (with files), SYNC_TIMEOUT, "

        /** The codes [runTool] adds to every tool, last in each list. */
        private const val CALL_ERROR_CODES =
            "PROJECT_NOT_FOUND or PROJECT_AMBIGUOUS (with openProjects), INTERNAL_ERROR."

        private const val SCAN_ENDPOINTS_DESCRIPTION =
            "Scans the named classes, methods and source files and lists the API endpoints EasyYapi would " +
                "export for them, as JSON. Read-only: nothing is exported or changed.\n" +
                "Name at least one target; the whole project is never scanned. Every target must resolve, " +
                "otherwise nothing is scanned. A target that resolves but yields no endpoint (a request or " +
                "response model, a service, an unmapped method) is reported in skipped while the others are " +
                "listed; if no target yields an endpoint, the call fails.\n" +
                "Files created or changed outside the IDE are synced from disk before the targets resolve.\n" +
                "Result: {project: {name, basePath}, targets: [{kind, input, resolved}], " +
                "skipped: [{kind, input, reason}], count, " +
                "endpoints: [{protocol, httpMethod, path, name, folder, className, methodName, module}]}. " +
                "Fields without a value are omitted.\n" +
                ERROR_RESULT + TARGET_ERROR_CODES + CALL_ERROR_CODES

        private const val EXPORT_DESCRIPTION =
            "Exports the API endpoints of the named targets to an EasyYapi channel (only \"yapi\" for now): the " +
                "same export as the IDE's \"Export to YAPI\", without any dialog. Name at least one target; if a " +
                "target cannot be resolved, nothing is exported and the error lists each bad target. A target " +
                "without endpoints is listed in skipped; if no target has any, nothing is exported. Files created " +
                "or changed outside the IDE are synced from disk first; if you also changed request or response " +
                "classes, add their files to files so they are synced too (a class outside the project root: its " +
                "name in classes); they are then listed in skipped, which is expected.\n" +
                "YApi: the server and the export mode come from the IDE settings. An API with the same path and " +
                "method anywhere in the YApi project is overwritten and moved into the target folder, unless the " +
                "export mode keeps it; the export mode looks only into the target folder (\"always ask\" counts as " +
                "yes). The token is options.token when given, otherwise the IDE token table entry for each " +
                "endpoint's destination (its yapi.project rule, else its module).\n" +
                "Result: {project, channel, targets, skipped, count, created, updated, notWritten, failed, " +
                "items: [{status, name, httpMethod, path, folder, movedFrom, destination, id, url, folderUrl, " +
                "reason, className, methodName}]}. status is created, updated, not_written or failed; created " +
                "means no API with this path and method was found before the save, across the YApi project or, if " +
                "the project could not be listed, in the target folder; movedFrom, on an updated item, names the " +
                "folder YApi moved it out of, which may now be empty; reason says why for not_written and failed; " +
                "failed > 0 means a partial export; destination is absent when options.token was passed. Fields " +
                "without a value are omitted.\n" +
                ERROR_RESULT + TARGET_ERROR_CODES +
                "CHANNEL_NOT_FOUND or CHANNEL_NOT_SUPPORTED (with channels), CHANNEL_DISABLED, OPTIONS_INVALID " +
                "(with acceptedOptions), EXPORT_FAILED (nothing was written; with items when known), " +
                CALL_ERROR_CODES

        private const val CHANNEL_DESCRIPTION =
            "Channel to export to. Only \"yapi\" is supported for now, and it is the default."

        private const val OPTIONS_DESCRIPTION =
            "Channel options for this call only, as name-value pairs. yapi accepts \"token\": a YApi project " +
                "token; every endpoint of the call then goes to that project. The token is checked with the server " +
                "first and is never saved to the IDE settings. Unknown names are rejected."

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
