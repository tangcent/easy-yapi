package com.itangcent.easyapi.core.export

import com.itangcent.easyapi.channel.spi.ChannelOption

/**
 * Outcome of [TargetedExportService.export]. Pure.
 */
sealed class TargetedExportResult {
    /** Handed to the channel; [report] lists every endpoint, possibly with failed ones. */
    data class Exported(val report: ExportReport) : TargetedExportResult()

    /**
     * Nothing was exported, for a reason the scan shares ([EndpointScanResult.Failure]) or one of the export
     * alone ([ExportRejection]).
     */
    data class Failed(val failure: CallFailure) : TargetedExportResult()
}

/**
 * An expected failure of a [TargetedExportService] call: one the scan and the export share
 * ([EndpointScanResult.Failure]), or one of the export alone ([ExportRejection]).
 */
sealed interface CallFailure {
    /** What went wrong and what to do about it, for the caller verbatim (English). */
    val message: String
}

/** Why an export was refused or failed as a whole, for a reason of the export alone. */
sealed class ExportRejection : CallFailure {

    /** No channel has this id. [headlessChannels] are the channels that can export without user interaction. */
    data class ChannelNotFound(val channelId: String, val headlessChannels: List<String>) : ExportRejection() {
        override val message: String
            get() = "Unknown channel '$channelId'. Channels that can export without user interaction: " +
                "${listed(headlessChannels)}."
    }

    /** The channel can export without user interaction, but is disabled in the settings. */
    data class ChannelDisabled(val channelId: String) : ExportRejection() {
        override val message: String
            get() = "Channel '$channelId' is disabled. Ask the user to enable it under Settings → EasyYapi → " +
                "Features → Export Channels, then retry."
    }

    /** The channel does not declare [com.itangcent.easyapi.channel.spi.Channel.supportsHeadless]. */
    data class ChannelNotSupported(val channelId: String, val headlessChannels: List<String>) : ExportRejection() {
        override val message: String
            get() = "Channel '$channelId' cannot export without user interaction. Channels that can: " +
                "${listed(headlessChannels)}."
    }

    /**
     * Options the channel [channelId] does not accept, or blank values ([ExportOptions.problems]); [problems]
     * never holds an option value.
     */
    data class OptionsInvalid(
        val channelId: String,
        val problems: List<String>,
        val accepted: List<ChannelOption>
    ) : ExportRejection() {
        override val message: String
            get() = "Invalid options for channel '$channelId': ${problems.joinToString("; ")}. " +
                if (accepted.isEmpty()) "It accepts no options." else "Accepted: ${accepted.joinToString(", ") { it.name }}."
    }

    /** Nothing was written. [items] explains each endpoint when the channel reported any. */
    data class ExportFailed(val reason: String, val items: List<ExportItemSummary>) : ExportRejection() {
        override val message: String get() = "Nothing was exported: $reason"
    }

    private companion object {
        fun listed(channelIds: List<String>): String = channelIds.joinToString(", ").ifEmpty { "none" }
    }
}

/**
 * The result of the MCP export tool; [toJson] gives its wire format.
 *
 * @param project the project the call ran against; filled in by the MCP toolset, like [ScanReport.project]
 * @param channel the channel id
 * @param targets how each target resolved, in input order
 * @param skipped the targets that resolved but yield no endpoint, as the scan tool reports them
 * @param count the number of [items]
 * @param created the items with status `created`
 * @param updated the items with status `updated`
 * @param notWritten the items with status `not_written`
 * @param failed the items with status `failed`; more than 0 means a partial export
 * @param items one per exported endpoint, in scan order
 */
data class ExportReport(
    val project: ProjectSummary? = null,
    val channel: String,
    val targets: List<TargetSummary>,
    val skipped: List<SkippedTarget>,
    val count: Int,
    val created: Int,
    val updated: Int,
    val notWritten: Int,
    val failed: Int,
    val items: List<ExportItemSummary>
) {
    /** Compact JSON, null fields omitted: the exact text the MCP tool returns. */
    fun toJson(): String = TOOL_RESULT_GSON.toJson(this)
}

/**
 * One exported endpoint, flattened to plain strings. A `null` field is omitted from the JSON.
 *
 * @param status [ExportItemStatus.wireName]
 * @param className the fully qualified name of the source class
 * @param methodName the name of the source method
 * @see ExportItemResult for the other fields
 */
data class ExportItemSummary(
    val status: String,
    val name: String?,
    val httpMethod: String?,
    val path: String,
    val folder: String?,
    val movedFrom: String? = null,
    val destination: String?,
    val id: String?,
    val url: String?,
    val folderUrl: String?,
    val reason: String?,
    val className: String?,
    val methodName: String?
)

/**
 * Checks the options of an export call against those the channel accepts
 * ([com.itangcent.easyapi.channel.spi.Channel.exportOptions]). Pure.
 */
object ExportOptions {

    /**
     * Why [options] cannot go to a channel accepting [accepted], one line per option: a name it does not
     * accept, a name given twice, a blank value. Names and values are compared trimmed; a line names the
     * option, never its value.
     */
    fun problems(options: Map<String, String>, accepted: List<ChannelOption>): List<String> {
        val names = accepted.mapTo(HashSet()) { it.name }
        val seen = HashSet<String>()
        return options.mapNotNull { (rawName, value) ->
            val name = rawName.trim()
            when {
                name !in names -> "unknown option '$name'"
                !seen.add(name) -> "option '$name' is given more than once"
                value.isBlank() -> "option '$name' has an empty value"
                else -> null
            }
        }
    }

    /** [options] with names and values trimmed: what the channel's `buildConfig` receives once [problems] is empty. */
    fun trimmed(options: Map<String, String>): Map<String, String> =
        options.entries.associate { (name, value) -> name.trim() to value.trim() }
}
