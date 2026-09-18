package com.itangcent.easyapi.channel.apipost

/**
 * Hand-written [ApipostApiClient] for the push tests.
 *
 * A fake rather than a mockito mock so assertions can inspect **what was pushed**
 * — target ids, `parent_id` links, rewritten `$ref`s — instead of only that a
 * call happened. Calls are recorded in push order, which is what lets a test
 * assert the folders-before-APIs contract.
 *
 * The id behaviour mirrors the live server (`REVIEW.md §6.9.5`):
 *
 * - [createApi] answers with **the node's own id**, because the server adopts a
 *   client-supplied `target_id` verbatim (verified for APIs and folders).
 * - [createModel] answers with a **different** id, because the server ignores a
 *   client-supplied `model_id` and always mints its own. That asymmetry is the
 *   reason `$ref` rewriting exists, so the fake must reproduce it — a fake that
 *   echoed the client id would let a broken remap pass.
 *
 * @param existingApis what `listApis` reports: the dedup index.
 * @param existingModels what `listModels` reports.
 * @param apiFailure when set, every API-side write fails with it (lets a test
 *        drive the short-circuit path).
 * @param modelFailure when set, every model-side write fails with it.
 * @param teams what `listTeams` reports.
 * @param projects what `listProjects` reports, whatever team is asked for.
 * @param serverModelId how the fake mints the server-side model id.
 */
internal class FakeApipostApiClient(
    private val existingApis: List<ApipostExistingApi> = emptyList(),
    private val existingModels: List<ApipostExistingModel> = emptyList(),
    private val apiFailure: ApipostSyncResult.Failure? = null,
    private val modelFailure: ApipostSyncResult.Failure? = null,
    private val teams: List<ApipostTeam> = emptyList(),
    private val projects: List<ApipostProject> = emptyList(),
    private val serverModelId: (ApipostModel) -> String = { "srv-${it.modelId}" },
) : ApipostApiClient {

    /** Every call made, as `kind:name`, in the order the push issued it. */
    val order = mutableListOf<String>()

    /** Folder nodes handed to [createApi], in push order. */
    val createdFolders = mutableListOf<ApipostNode>()

    /** API nodes handed to [createApi], in push order. */
    val createdApis = mutableListOf<ApipostNode>()

    /** API nodes handed to [updateApi], keyed by the target id the push carried. */
    val updatedApis = mutableMapOf<String, ApipostNode>()

    val createdModels = mutableListOf<ApipostModel>()

    /** Model nodes handed to [updateModel], keyed by the model id the push carried. */
    val updatedModels = mutableMapOf<String, ApipostModel>()

    /** Every node handed to [createApi], folders and APIs alike, in push order. */
    val createdNodes: List<ApipostNode> get() = createdFolders + createdApis

    /** Every team id `listProjects` was asked about, in call order. */
    val requestedTeamIds = mutableListOf<String>()

    override suspend fun listTeams(): ApipostSyncResult<List<ApipostTeam>> =
        ApipostSyncResult.Success(teams)

    override suspend fun listProjects(teamId: String): ApipostSyncResult<List<ApipostProject>> {
        requestedTeamIds += teamId
        return ApipostSyncResult.Success(projects)
    }

    override suspend fun listApis(projectId: String): ApipostSyncResult<List<ApipostExistingApi>> =
        ApipostSyncResult.Success(existingApis)

    override suspend fun createApi(projectId: String, node: ApipostNode): ApipostSyncResult<String> {
        apiFailure?.let { return it }
        if (node.targetType == ApipostNode.TARGET_TYPE_FOLDER) {
            createdFolders += node
            order += "folder:${node.name}"
        } else {
            createdApis += node
            order += "api:${node.name}"
        }
        return ApipostSyncResult.Success(node.targetId)
    }

    override suspend fun updateApi(
        projectId: String,
        targetId: String,
        node: ApipostNode,
    ): ApipostSyncResult<Unit> {
        apiFailure?.let { return it }
        updatedApis[targetId] = node
        order += "updateApi:$targetId"
        return ApipostSyncResult.Success(Unit)
    }

    override suspend fun listModels(projectId: String): ApipostSyncResult<List<ApipostExistingModel>> =
        ApipostSyncResult.Success(existingModels)

    override suspend fun createModel(projectId: String, model: ApipostModel): ApipostSyncResult<String> {
        modelFailure?.let { return it }
        createdModels += model
        order += "model:${model.name}"
        return ApipostSyncResult.Success(serverModelId(model))
    }

    override suspend fun updateModel(
        projectId: String,
        modelId: String,
        model: ApipostModel,
    ): ApipostSyncResult<String> {
        modelFailure?.let { return it }
        updatedModels[modelId] = model
        order += "updateModel:$modelId"
        return ApipostSyncResult.Success(modelId)
    }

    companion object {
        /** A client-side model reference, the form [ApipostNativeFormatter] emits. */
        fun modelRef(clientModelId: String): String = ApipostModel.REF_PREFIX + clientModelId
    }
}
