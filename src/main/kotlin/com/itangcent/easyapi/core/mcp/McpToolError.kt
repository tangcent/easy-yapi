package com.itangcent.easyapi.core.mcp

import com.itangcent.easyapi.channel.spi.ChannelOption
import com.itangcent.easyapi.core.export.CallFailure
import com.itangcent.easyapi.core.export.EndpointScanResult
import com.itangcent.easyapi.core.export.ExportItemSummary
import com.itangcent.easyapi.core.export.ExportRejection
import com.itangcent.easyapi.core.export.ProjectSummary
import com.itangcent.easyapi.core.export.SkippedTarget
import com.itangcent.easyapi.core.export.TOOL_RESULT_GSON
import com.itangcent.easyapi.core.export.TargetFailure

/**
 * The failure result of an EasyYapi MCP tool: returned as ordinary text, `{"error":{"code":…,"message":…}}`,
 * instead of thrown as the MCP server's error type, whose shape differs between IDE versions
 * (see [EasyApiMcpToolset]). Pure; no MCP types.
 *
 * @param code one of the constants below — stable, for the caller to branch on
 * @param message what went wrong and what to do about it, in English
 * @param project the project the call was resolved to, for the failures that come after that:
 *   [TARGETS_REQUIRED], [TARGETS_UNRESOLVED], [NO_ENDPOINTS], [TARGETS_CHANGED], [INDEX_NOT_READY],
 *   [UNSAVED_IN_IDE], [SYNC_TIMEOUT], and the export's [CHANNEL_NOT_FOUND], [CHANNEL_DISABLED],
 *   [CHANNEL_NOT_SUPPORTED], [OPTIONS_INVALID] and [EXPORT_FAILED]; [INTERNAL_ERROR] leaves it out
 * @param failures for [TARGETS_UNRESOLVED]: every target that did not resolve
 * @param skipped for [NO_ENDPOINTS]: every target, none of which yields an endpoint
 * @param files for [UNSAVED_IN_IDE]: the files with unsaved changes, relative to the project root (a file
 *   outside it keeps its full path)
 * @param openProjects for [PROJECT_NOT_FOUND] and [PROJECT_AMBIGUOUS]: the roots of the open projects
 * @param channels for [CHANNEL_NOT_FOUND] and [CHANNEL_NOT_SUPPORTED]: the channels that can export without
 *   user interaction
 * @param acceptedOptions for [OPTIONS_INVALID]: the options the channel accepts, never a value
 * @param items for [EXPORT_FAILED]: what the channel reported per endpoint, when it reported any
 */
internal data class McpToolError(
    val code: String,
    val message: String,
    val project: ProjectSummary? = null,
    val failures: List<FailureSummary>? = null,
    val skipped: List<SkippedTarget>? = null,
    val files: List<String>? = null,
    val openProjects: List<String>? = null,
    val channels: List<String>? = null,
    val acceptedOptions: List<ChannelOption>? = null,
    val items: List<ExportItemSummary>? = null
) {
    /** Compact JSON, null fields omitted: `{"error":{…}}`. */
    fun toJson(): String = TOOL_RESULT_GSON.toJson(mapOf("error" to this))

    /**
     * One target that did not resolve.
     *
     * @param kind `class`, `method` or `file`
     * @param input the target as passed (trimmed)
     * @param reason why it did not resolve
     * @param candidates the matching classes or files, for an ambiguous name only
     */
    data class FailureSummary(
        val kind: String,
        val input: String,
        val reason: String,
        val candidates: List<String>? = null
    )

    companion object {
        const val TARGETS_REQUIRED = "TARGETS_REQUIRED"
        const val TARGETS_UNRESOLVED = "TARGETS_UNRESOLVED"
        const val NO_ENDPOINTS = "NO_ENDPOINTS"
        const val TARGETS_CHANGED = "TARGETS_CHANGED"
        const val INDEX_NOT_READY = "INDEX_NOT_READY"
        const val UNSAVED_IN_IDE = "UNSAVED_IN_IDE"
        const val SYNC_TIMEOUT = "SYNC_TIMEOUT"
        const val PROJECT_NOT_FOUND = "PROJECT_NOT_FOUND"
        const val PROJECT_AMBIGUOUS = "PROJECT_AMBIGUOUS"
        const val INTERNAL_ERROR = "INTERNAL_ERROR"
        const val CHANNEL_NOT_FOUND = "CHANNEL_NOT_FOUND"
        const val CHANNEL_DISABLED = "CHANNEL_DISABLED"
        const val CHANNEL_NOT_SUPPORTED = "CHANNEL_NOT_SUPPORTED"
        const val OPTIONS_INVALID = "OPTIONS_INVALID"
        const val EXPORT_FAILED = "EXPORT_FAILED"

        /** The error result for an expected failure of a call in [project]: the scan's, or the export's own. */
        fun of(failure: CallFailure, project: ProjectSummary): McpToolError = when (failure) {
            EndpointScanResult.TargetsRequired -> McpToolError(TARGETS_REQUIRED, failure.message, project)

            is EndpointScanResult.TargetsUnresolved -> McpToolError(
                TARGETS_UNRESOLVED, failure.message, project,
                failures = failure.failures.map { target ->
                    FailureSummary(
                        target.kind.wireName, target.input, target.reason,
                        (target as? TargetFailure.Ambiguous)?.candidates
                    )
                }
            )

            is EndpointScanResult.NoEndpoints -> McpToolError(
                NO_ENDPOINTS, failure.message, project, skipped = failure.skipped
            )

            EndpointScanResult.TargetsChanged -> McpToolError(TARGETS_CHANGED, failure.message, project)
            EndpointScanResult.IndexNotReady -> McpToolError(INDEX_NOT_READY, failure.message, project)
            is EndpointScanResult.UnsavedInIde -> McpToolError(
                UNSAVED_IN_IDE, failure.message, project, files = failure.files
            )

            EndpointScanResult.SyncTimeout -> McpToolError(SYNC_TIMEOUT, failure.message, project)
            is ExportRejection.ChannelNotFound -> McpToolError(
                CHANNEL_NOT_FOUND, failure.message, project, channels = failure.headlessChannels
            )

            is ExportRejection.ChannelDisabled -> McpToolError(CHANNEL_DISABLED, failure.message, project)
            is ExportRejection.ChannelNotSupported -> McpToolError(
                CHANNEL_NOT_SUPPORTED, failure.message, project, channels = failure.headlessChannels
            )

            is ExportRejection.OptionsInvalid -> McpToolError(
                OPTIONS_INVALID, failure.message, project, acceptedOptions = failure.accepted
            )

            is ExportRejection.ExportFailed -> McpToolError(
                EXPORT_FAILED, failure.message, project, items = failure.items.ifEmpty { null }
            )
        }
    }
}
