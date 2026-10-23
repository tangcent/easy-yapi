package com.itangcent.easyapi.core.export

import com.google.gson.Gson
import com.google.gson.GsonBuilder

/**
 * Outcome of [TargetedExportService.scanEndpoints]. Pure; a [Failure] carries the agent-facing text.
 */
sealed class EndpointScanResult {
    /** At least one target yields endpoints; [ScanReport.skipped] names the targets that do not. */
    data class Scanned(val report: ScanReport) : EndpointScanResult()

    /** Expected failures; [message] goes to the caller verbatim (English). */
    sealed class Failure : EndpointScanResult() {
        abstract val message: String
    }

    /** No target was named. Nothing is scanned: the whole project is never the fallback. */
    data object TargetsRequired : Failure() {
        override val message: String
            get() = "No target was given: pass at least one entry in classes, methods or files. " +
                "The whole project is never scanned."
    }

    /** At least one target did not resolve; nothing was scanned. */
    data class TargetsUnresolved(val failures: List<TargetFailure>, val targetCount: Int) : Failure() {
        override val message: String
            get() = "${failures.size} of $targetCount target(s) could not be resolved, so nothing was scanned:\n" +
                failures.joinToString("\n") { it.describe() }
    }

    /** Every target resolved and was scanned, but none yields an endpoint; [skipped] lists them all. */
    data class NoEndpoints(val skipped: List<SkippedTarget>) : Failure() {
        override val message: String
            get() = "No target has an endpoint, so nothing was listed:\n" +
                skipped.joinToString("\n") { it.describe() }
    }

    /** Indexing did not finish within the wait budget, or ran while the targets were being scanned. */
    data object IndexNotReady : Failure() {
        override val message: String
            get() = "IntelliJ IDEA is indexing the project, or was while the targets were being scanned; " +
                "retry when indexing finishes."
    }

    /** A target changed after it resolved (edited, moved or deleted), so the scan result was not trusted. */
    data object TargetsChanged : Failure() {
        override val message: String
            get() = "A target was edited, moved or deleted while it was being scanned, so the result was " +
                "discarded; retry the call."
    }

    /**
     * Files have unsaved changes in the IDE that may not match the disk ([SyncOutcome.UnsavedInIde]), so
     * nothing was synced or scanned.
     *
     * @param files relative to the project root; a file outside it keeps its full path
     */
    data class UnsavedInIde(val files: List<String>) : Failure() {
        override val message: String
            get() = "These files have unsaved changes in the IDE that may not match the files on disk, so it is " +
                "unclear which version to use; nothing was scanned. Ask the user to save them in " +
                "the IDE, or to drop the editor changes with File | Reload from Disk, then retry: " +
                files.joinToString(", ")
    }

    /** The IDE did not load the files changed outside it in time ([SyncOutcome.TimedOut]); nothing was scanned. */
    data object SyncTimeout : Failure() {
        override val message: String
            get() = "The IDE did not finish loading the files changed outside it within " +
                "${ExternalChangeSync.SYNC_TIMEOUT.inWholeSeconds} seconds, so nothing was scanned. " +
                "Retry the call. If it times out again, ask the user to close any modal dialog open in the IDE, " +
                "such as Settings, and retry a little later."
    }
}

/**
 * The result of the MCP scan tool; [toJson] gives its wire format.
 *
 * @param project the project the call ran against. [TargetedExportService] leaves it `null`; the MCP
 *   toolset, which resolved the project, fills it in so a caller with several projects open can check it
 * @param targets how each target resolved, in input order
 * @param skipped the targets that resolved but yield no endpoint, in input order; empty when every target has one
 * @param count the number of [endpoints]
 * @param endpoints the endpoints, in scan order
 */
data class ScanReport(
    val project: ProjectSummary? = null,
    val targets: List<TargetSummary>,
    val skipped: List<SkippedTarget>,
    val count: Int,
    val endpoints: List<EndpointSummary>
) {
    /** Compact JSON, null fields omitted — the exact text the MCP tool returns. */
    fun toJson(): String = TOOL_RESULT_GSON.toJson(this)
}

/**
 * Writes the results of the MCP tools: compact JSON, null fields omitted, and no HTML escaping, so the
 * quotes around a name in a message stay quotes instead of becoming Unicode escapes. An instance of its
 * own, as `ApipostNativeSerializer` has: `GsonUtils.GSON` also stores the settings.
 */
internal val TOOL_RESULT_GSON: Gson = GsonBuilder().disableHtmlEscaping().create()

/**
 * Identifies the project a result belongs to.
 *
 * @param name the IntelliJ project name
 * @param basePath the project root, as IntelliJ reports it
 */
data class ProjectSummary(
    val name: String,
    val basePath: String?
)

/**
 * How one target resolved.
 *
 * @param kind `class`, `method` or `file` ([TargetKind.wireName])
 * @param input the target as passed (trimmed)
 * @param resolved class FQNs, method signatures, or the FQNs of the classes a file declares
 */
data class TargetSummary(
    val kind: String,
    val input: String,
    val resolved: List<String>
)

/**
 * A target that resolved but yields no endpoint: a request or response model, a service, a method
 * without a request mapping, or one the rules ignore. The other targets are listed as usual.
 * A class whose export failed or timed out lands here too: the scanner only logs that.
 *
 * @param kind `class`, `method` or `file` ([TargetKind.wireName])
 * @param input the target as passed (trimmed)
 * @param reason what was scanned and found empty, and what to pass instead, in English
 */
data class SkippedTarget(
    val kind: String,
    val input: String,
    val reason: String
) {
    /** One line of the agent-facing error: `- class 'UserDto': <reason>`. */
    fun describe(): String = "- $kind '$input': $reason"

    companion object {
        private const val HINT =
            ". If you changed a model or a service, pass the controllers or methods that use it instead."

        /** An exporter error or timeout is only logged, so it also leaves a target without endpoints. */
        private const val EXPORT_FAILED = ", or exporting it failed (see idea.log)"

        /** The entry for a target of [kind], passed as [input], whose scan found no endpoint. */
        fun of(kind: TargetKind, input: String): SkippedTarget = SkippedTarget(
            kind.wireName, input,
            when (kind) {
                TargetKind.CLASS -> "no endpoint in this class: it is not an API class (for example a request " +
                    "or response model, or a service), or rules ignore its endpoints"

                TargetKind.METHOD -> "no endpoint for this method: it is not mapped to a request, or rules ignore it"
                TargetKind.FILE -> "no endpoint in the classes this file declares: none is an API class, or rules " +
                    "ignore their endpoints"
            } + EXPORT_FAILED + HINT
        )
    }
}

/**
 * One endpoint, flattened to plain strings. A `null` field is omitted from the JSON.
 *
 * @param protocol `HTTP` or `gRPC`
 * @param httpMethod the HTTP method; `null` for gRPC
 * @param path the URL path, or `/package.Service/Method` for gRPC
 * @param name the endpoint name
 * @param folder the folder (category) the endpoint is exported under
 * @param className the fully qualified name of the source class
 * @param methodName the name of the source method
 * @param module the IntelliJ module that holds the source class
 */
data class EndpointSummary(
    val protocol: String,
    val httpMethod: String?,
    val path: String,
    val name: String?,
    val folder: String?,
    val className: String?,
    val methodName: String?,
    val module: String?
)
