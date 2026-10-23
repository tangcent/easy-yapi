package com.itangcent.easyapi.core.export

/** What a channel did with one endpoint. [wireName] is the agent-facing value. */
enum class ExportItemStatus(val wireName: String) {
    /** Written as a new item: nothing with this identity was at the destination before. */
    CREATED("created"),

    /** Written over an existing item. */
    UPDATED("updated"),

    /** Deliberately not written (the export mode keeps the existing item, the protocol is unsupported, …). */
    NOT_WRITTEN("not_written"),

    /** Not written because something went wrong; [ExportItemResult.reason] says what. */
    FAILED("failed")
}

/**
 * What a channel did with one endpoint of an export, reported through [ExportContext.itemListener].
 * The fields describe what was written (after rules), so they may differ from the scanned endpoint;
 * a `null` field means "as on the endpoint" or "unknown".
 *
 * @param endpoint the very instance from [ExportContext.endpointsToExport]: callers match by identity
 * @param name the title as written
 * @param path the path as written
 * @param httpMethod the HTTP method as written, upper case
 * @param folder the folder (category) as written
 * @param destination where the channel routed it, when the call does not already decide that: for YApi the
 *   key that picked the token from the IDE token table (rule `yapi.project`, else module, else project);
 *   `null` when the caller passed the token, since every endpoint then goes to that token's project
 * @param id the item's id at the destination, when known
 * @param url a page showing the item, when known
 * @param folderUrl a page showing its folder, when known
 * @param reason for [ExportItemStatus.NOT_WRITTEN] and [ExportItemStatus.FAILED]: why, in English
 * @param movedFrom for [ExportItemStatus.UPDATED]: the folder the destination moved the item out of, when it
 *   found it in another folder (YApi matches path and method across the project); that folder may be empty
 *   afterwards
 */
data class ExportItemResult(
    val endpoint: ApiEndpoint,
    val status: ExportItemStatus,
    val name: String? = null,
    val path: String? = null,
    val httpMethod: String? = null,
    val folder: String? = null,
    val destination: String? = null,
    val id: String? = null,
    val url: String? = null,
    val folderUrl: String? = null,
    val reason: String? = null,
    val movedFrom: String? = null
)

/**
 * Receives [ExportItemResult]s while a channel exports: one call per endpoint, one after another,
 * possibly on different threads.
 */
fun interface ExportItemListener {
    fun onItem(result: ExportItemResult)
}
