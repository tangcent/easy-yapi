package com.itangcent.easyapi.channel.yapi

import com.itangcent.easyapi.channel.yapi.model.YapiApiDoc
import com.itangcent.easyapi.channel.yapi.model.YapiResponse
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.ExportItemListener
import com.itangcent.easyapi.core.export.ExportItemResult
import com.itangcent.easyapi.core.export.ExportItemStatus
import com.itangcent.easyapi.core.logging.IdeaConsole

/**
 * Remembers whether the wrapped confirmation let the upload through: the only way to tell "kept by
 * the export mode" from "saved, id unknown" without touching the client.
 */
internal class RecordingUpdateConfirmation(private val delegate: UpdateConfirmation) : UpdateConfirmation {
    var declined: Boolean = false
        private set

    override suspend fun confirm(doc: YapiApiDoc, catId: String): Boolean =
        delegate.confirm(doc, catId).also { declined = !it }
}

/**
 * Where an API sits in the whole YApi project, for the endpoints missing from their own folder: YApi
 * matches a save by path and method across the project and moves the match into the target folder.
 * Lists each YApi project at most once per export, and only when asked. A project that cannot be listed
 * is warned about once on [console]; its endpoints are then judged by their own folder only.
 */
internal class ProjectApiLookup(private val console: IdeaConsole) {
    private val byProject = mutableMapOf<String, List<ProjectApiInfo>>()

    /** The API with [path] and [method] anywhere in the YApi project of [client], if it can be listed. */
    suspend fun find(client: YapiApiClient, path: String, method: String): ProjectApiInfo? {
        val projectId = client.getProjectId().getOrNull() ?: return null // cached: the folder was just resolved
        val apis = byProject.getOrPut(projectId) {
            val listed = client.listProjectApis()
            listed.getOrNull() ?: emptyList<ProjectApiInfo>().also {
                console.warn(
                    "YApi: could not list the APIs of project $projectId: ${listed.errorMessage()}. This export " +
                        "judges created or updated by the target folder only, so an API YApi moves in from " +
                        "another folder is reported as created."
                )
            }
        }
        return apis.firstOrNull { isSameApi(it.path, it.method, path, method) }
    }
}

/**
 * The [ExportItemResult]s [YapiExporter] reports to [listener] when someone listens
 * ([com.itangcent.easyapi.core.export.ExportContext.itemListener]): one [Item] per endpoint. Made only then,
 * so an export nobody listens to sends no request for them.
 */
internal class YapiExportItems(
    private val listener: ExportItemListener,
    private val console: IdeaConsole,
    private val serverUrl: String,
    private val mode: YapiExportMode
) {
    private val projectApis = ProjectApiLookup(console)

    /** The item of [endpoint]: the very instance the exporter was handed, since callers match by identity. */
    fun item(endpoint: ApiEndpoint): Item = Item(endpoint)

    /**
     * One endpoint, reported once: by [uploaded], or by [failed] with what was known by then. Until
     * [beforeUpload] it is reported as scanned, then as it was to be uploaded: the doc after the
     * `yapi.save.before` rules, its method in upper case.
     */
    inner class Item(private val endpoint: ApiEndpoint) {

        /** The token-table key the endpoint is routed by, once known; null when the call passed the token. */
        var destination: String? = null

        private var upload: Upload? = null

        /**
         * Before [doc] is uploaded through [client] into [folder] ([catId]): looks up the API with its path and
         * method, in the folder, else across the YApi project ([ProjectApiLookup]), since YApi matches a save
         * across the project and moves the match into the folder. Returns [confirmation], recording whether it
         * lets the upload through.
         */
        suspend fun beforeUpload(
            client: YapiApiClient,
            doc: YapiApiDoc,
            folder: String,
            catId: String,
            confirmation: UpdateConfirmation
        ): UpdateConfirmation {
            val inFolder = client.findExistingApi(catId, doc.path, doc.method)
            val elsewhere = if (inFolder == null) projectApis.find(client, doc.path, doc.method) else null
            val recorder = RecordingUpdateConfirmation(confirmation)
            upload = Upload(
                client, doc, folder, catId, recorder,
                existingId = inFolder ?: elsewhere?.id,
                movedFrom = elsewhere?.takeIf { it.catId != catId }?.let { it.catName ?: it.catId }
            )
            return recorder
        }

        /**
         * Reports the upload [result]: failed; else kept by the export mode when the confirmation declined, also
         * said on the console; updated when an API with this path and method was there before; created otherwise.
         * Standard YApi answers a new API with `data: []`, so without [ApiUploadResult.apiId] the new id is looked
         * up once in the folder, for the link.
         */
        suspend fun uploaded(result: YapiResponse<ApiUploadResult>) {
            val upload = checkNotNull(upload) { "beforeUpload comes first" }
            if (!result.isSuccess) return failed(result.errorMessage())
            val declined = upload.recorder.declined
            val status = when {
                declined -> ExportItemStatus.NOT_WRITTEN
                upload.existingId != null -> ExportItemStatus.UPDATED
                else -> ExportItemStatus.CREATED
            }
            val savedId = result.getOrNull()?.apiId
            val id = when (status) {
                ExportItemStatus.NOT_WRITTEN -> upload.existingId
                ExportItemStatus.UPDATED -> savedId ?: upload.existingId
                else -> savedId ?: upload.client.findExistingApi(upload.catId, upload.doc.path, upload.doc.method)
            }
            // Cached on the client: the exporter has just asked for it.
            val projectId = upload.client.getProjectId().getOrNull()
            val reason = if (declined) keptBy(upload.folder) else null
            reason?.let { console.info("${endpoint.name}: not written: $it") }
            listener.onItem(
                ExportItemResult(
                    endpoint = endpoint,
                    status = status,
                    name = upload.doc.title,
                    path = upload.doc.path,
                    httpMethod = upload.doc.method.uppercase(),
                    folder = upload.folder,
                    destination = destination,
                    id = id,
                    url = if (projectId != null && id != null) YapiUrls.apiUrl(serverUrl, projectId, id) else null,
                    folderUrl = projectId?.let { YapiUrls.cartUrl(serverUrl, it, upload.catId) },
                    reason = reason,
                    movedFrom = upload.movedFrom.takeIf { status == ExportItemStatus.UPDATED }
                )
            )
        }

        /** Reports the endpoint as failed for [reason]; what the doc does not tell is left to the endpoint. */
        fun failed(reason: String?) {
            listener.onItem(
                ExportItemResult(
                    endpoint = endpoint,
                    status = ExportItemStatus.FAILED,
                    name = upload?.doc?.title,
                    path = upload?.doc?.path,
                    httpMethod = upload?.doc?.method?.uppercase(),
                    folder = upload?.folder,
                    destination = destination,
                    reason = reason
                )
            )
        }
    }

    /**
     * One upload as [Item.beforeUpload] found it: [existingId] is the API it writes over, and [movedFrom] the
     * folder that API is in when not the target one.
     */
    private class Upload(
        val client: YapiApiClient,
        val doc: YapiApiDoc,
        val folder: String,
        val catId: String,
        val recorder: RecordingUpdateConfirmation,
        val existingId: String?,
        val movedFrom: String?
    )

    private fun keptBy(folder: String): String = when (mode) {
        YapiExportMode.NEVER_UPDATE ->
            "an API with this path and method is already in folder '$folder'; export mode NEVER_UPDATE keeps it"

        YapiExportMode.UPDATE_IF_CHANGED -> "no change found; export mode UPDATE_IF_CHANGED keeps the existing API"
        else -> "the existing API was kept (export mode ${mode.name})"
    }
}
