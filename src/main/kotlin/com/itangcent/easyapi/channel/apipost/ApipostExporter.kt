package com.itangcent.easyapi.channel.apipost

import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.rule.engine.RuleEngine
import kotlinx.coroutines.CancellationException

/**
 * Pushes an [ApipostNativeDocument] to an ApiPost project, one node at a time.
 *
 * The open API has no bulk import route — `POST /open/apis/create` and
 * `/open/apis/update` take a single node each — so the whole ordering and dedup
 * burden is on this side (REQ-7, P0).
 *
 * ## Order is a contract: folders → models → APIs
 *
 * Every phase exists because the server rejects a child whose parent is not
 * already there (`14002 父节点已删除`, `16005 父级模型目录不存在` — verified live,
 * `REVIEW.md §6.9.7`):
 *
 * 1. **Folders.** A folder is an API node with `target_type = "folder"`; there is
 *    no separate route. A folder that already exists is **reused by name** rather
 *    than re-created, so a re-export does not answer `14000 接口已存在` and the
 *    children keep pointing at the folder the user actually has. Each folder's
 *    server id is recorded because a child's `parent_id` must reference it.
 * 2. **Models.** Pushed before APIs because an API's `$ref` names a model id.
 *    Deduped by name via `listModels` (OQ-2). The server **regenerates**
 *    `model_id`, so the id it returns is kept in a `client id → server id` map.
 * 3. **APIs.** Each one's `parent_id` and `$ref`s are rewritten through those maps
 *    ([ApipostNativeSerializer.remapRefs]), then created or updated depending on
 *    the dedup index.
 *
 * ## Model folders are deliberately not pushed
 *
 * A model folder *can* be created (`model_type = "folder"` succeeds) but **no
 * model can be nested into it** — the models endpoints reject every non-root
 * `parent_id` with `16005` (`REVIEW.md §6.9.4`). Pushing one would leave an empty
 * directory behind, so model folders are skipped and models go to the root. The
 * nesting still exists in the file product, which ApiPost's own importer builds
 * correctly.
 *
 * ## Failure handling
 *
 * A [ApipostSyncResult.Failure] short-circuits the push and returns the counts
 * accumulated so far, so a partially-applied export is still reported honestly.
 * Cancellation propagates as [CancellationException] (never swallowed) so the
 * orchestrator can treat it as a cancel rather than an error.
 */
@Service(Service.Level.PROJECT)
class ApipostExporter(private val project: Project) : IdeaLog {

    companion object {
        fun getInstance(project: Project): ApipostExporter = project.getService(ApipostExporter::class.java)

        /** Dedup key: ApiPost matches APIs on method + path (`url_cover_modal`). */
        internal fun apiKey(method: String?, path: String?): String =
            "${method?.trim()?.uppercase().orEmpty()} ${path?.trim().orEmpty()}"
    }

    /**
     * Pushes [document] to [projectId] using [client].
     *
     * @return the created/updated counts, plus [ApipostPushOutcome.failure]
     *         when the push stopped early (non-null means "did not finish").
     */
    suspend fun push(
        client: ApipostApiClient,
        projectId: String,
        document: ApipostNativeDocument,
        indicator: ProgressIndicator? = null,
    ): ApipostPushOutcome {
        val engine = RuleEngine.getInstance(project)

        // One list call serves both indices: it returns folders and APIs together.
        val existing = when (val r = client.listApis(projectId)) {
            is ApipostSyncResult.Success -> r.data
            is ApipostSyncResult.Failure -> {
                LOG.warn("ApipostExporter: listApis failed — ${r.message}")
                return ApipostPushOutcome(failure = r.message)
            }
        }
        val apiIndex = existing
            .filter { it.targetType == ApipostNode.TARGET_TYPE_API }
            .associateBy { apiKey(it.method, it.path) }
        // By name, because that is all a folder can be matched on. A duplicated
        // folder name resolves to the last one listed; ApiPost does not keep
        // folder names unique, so there is no better key available.
        val folderIndex = existing
            .filter { it.targetType == ApipostNode.TARGET_TYPE_FOLDER }
            .associateBy { it.name }

        // ─── Phase 1: folders ───────────────────────────────────────────────
        // Document order is the parents-first order the formatter emits, so a
        // nested folder resolves its own parent from the map built so far.
        val folderIds = mutableMapOf<String, String>()
        for (folder in document.apis.filter { it.targetType == ApipostNode.TARGET_TYPE_FOLDER }) {
            indicator?.checkCanceled()

            val reused = folderIndex[folder.name]?.targetId
            if (reused != null) {
                folderIds[folder.targetId] = reused
                continue
            }
            val parent = folderIds[folder.parentId] ?: ApipostImportConfig.ROOT_ID
            when (val r = client.createApi(projectId, folder.copy(parentId = parent))) {
                is ApipostSyncResult.Success -> folderIds[folder.targetId] = r.data
                is ApipostSyncResult.Failure -> {
                    LOG.warn("ApipostExporter: folder '${folder.name}' failed — ${r.message}")
                    return ApipostPushOutcome(failure = r.message)
                }
            }
        }

        // ─── Phase 2: models ────────────────────────────────────────────────
        val modelIndex = when (val r = client.listModels(projectId)) {
            is ApipostSyncResult.Success -> r.data.associateBy { it.name }
            is ApipostSyncResult.Failure -> {
                LOG.warn("ApipostExporter: listModels failed — ${r.message}")
                return ApipostPushOutcome(failure = r.message)
            }
        }
        // Client model id → server model id: the only trustworthy source of the
        // latter, because the server ignores a client-supplied `model_id`.
        val modelIds = mutableMapOf<String, String>()
        for (model in document.models.filter { it.modelType == ApipostModel.MODEL_TYPE_MODEL }) {
            indicator?.checkCanceled()

            // Flattened to the root: a model cannot be nested through the open API.
            val node = model.copy(parentId = ApipostImportConfig.ROOT_ID)
            val existingId = modelIndex[model.name]?.modelId
            val result = if (existingId != null) {
                client.updateModel(projectId, existingId, node)
            } else {
                client.createModel(projectId, node)
            }
            when (result) {
                is ApipostSyncResult.Success -> modelIds[model.modelId] = result.data
                is ApipostSyncResult.Failure -> {
                    LOG.warn("ApipostExporter: model '${model.name}' failed — ${result.message}")
                    return ApipostPushOutcome(failure = result.message)
                }
            }
        }

        // ─── Phase 3: APIs ──────────────────────────────────────────────────
        val apis = document.apis.filter { it.targetType == ApipostNode.TARGET_TYPE_API }
        val total = apis.size.coerceAtLeast(1)
        var created = 0
        var updated = 0

        for ((index, rawNode) in apis.withIndex()) {
            indicator?.checkCanceled()
            indicator?.fraction = index.toDouble() / total

            // Parent comes from the folder map, `$ref`s from the model map; both
            // fall back to the root when the target was skipped.
            val node = ApipostNativeSerializer.remapRefs(
                rawNode.copy(parentId = folderIds[rawNode.parentId] ?: ApipostImportConfig.ROOT_ID),
                modelIds,
            )
            indicator?.text = node.name

            engine.evaluate(ApipostRuleKeys.APIPOST_SAVE_BEFORE) { ctx ->
                ctx.setExt("document", node)
            }

            // `url` is the node's path — ApiPost's own field name for it.
            // The index is keyed on the server's method+path, so a hit means
            // update in place rather than create a duplicate (AC-6).
            val existingId = apiIndex[apiKey(node.method, node.url)]?.targetId
            val result = if (existingId != null) {
                client.updateApi(projectId, existingId, node)
            } else {
                client.createApi(projectId, node)
            }

            engine.evaluate(ApipostRuleKeys.APIPOST_SAVE_AFTER) { ctx ->
                ctx.setExt("content", node)
                ctx.setExt("result", result)
            }

            when (result) {
                is ApipostSyncResult.Failure -> {
                    LOG.warn("ApipostExporter: api '${node.name}' failed — ${result.message}")
                    return ApipostPushOutcome(created = created, updated = updated, failure = result.message)
                }

                is ApipostSyncResult.Success -> if (existingId != null) updated++ else created++
            }
        }

        LOG.info(
            "ApipostExporter.push: done. created=$created updated=$updated " +
                "folders=${folderIds.size} models=${modelIds.size}"
        )
        return ApipostPushOutcome(created = created, updated = updated)
    }
}

/**
 * Outcome of one [ApipostExporter.push] run.
 *
 * @property created APIs newly created on the server.
 * @property updated existing APIs updated in place.
 * @property failure first error message; non-null means the push stopped early
 *           and the counts describe only what was completed.
 */
data class ApipostPushOutcome(
    val created: Int = 0,
    val updated: Int = 0,
    val failure: String? = null,
)
