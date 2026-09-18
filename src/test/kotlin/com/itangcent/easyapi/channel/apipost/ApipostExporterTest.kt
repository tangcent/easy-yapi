package com.itangcent.easyapi.channel.apipost

import com.google.gson.JsonObject
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

/**
 * Tests for [ApipostExporter.push] — the ordering and id-wiring contract.
 *
 * These drive the exporter with a **hand-built** [ApipostNativeDocument] instead
 * of one produced from endpoints. That is the point: the rules under test are
 * about the document's *shape* (a folder, a nested folder, a model under a model
 * folder, a `$ref`), and the OpenAPI path cannot produce that shape on demand
 * without a real PSI graph.
 *
 * What the shape costs if it is wrong was measured live (`REVIEW.md §6.9`), which
 * is why each rule has a test:
 *
 * | Rule | Server's response when violated |
 * |---|---|
 * | folders before APIs | `14002 父节点已删除` |
 * | models before APIs | `$ref` silently points at nothing |
 * | `$ref` uses the **server** model id | `$ref` silently points at nothing |
 * | models stay at the root | `16005 父级模型目录不存在` |
 */
class ApipostExporterTest : EasyApiLightCodeInsightFixtureTestCase() {

    private val exporter get() = ApipostExporter.getInstance(project)

    // ─── ordering ───────────────────────────────────────────────────────

    fun testPushesFoldersThenModelsThenApis() {
        val client = FakeApipostApiClient()

        val outcome = push(client, demoDocument())

        assertEquals(
            "folders must be created before the APIs that live in them, and models " +
                "before the APIs that reference them",
            listOf("folder:Users", "model:User", "api:List users"),
            client.order,
        )
        assertEquals(1, outcome.created)
        assertEquals(0, outcome.updated)
        assertEquals(null, outcome.failure)
    }

    fun testFoldersArePushedInDocumentOrderSoNestedOnesResolveTheirParent() {
        val client = FakeApipostApiClient()
        val document = document(
            apis = listOf(
                ApipostNode.folder(targetId = "outer", name = "Users"),
                ApipostNode.folder(targetId = "inner", name = "Admin", parentId = "outer"),
                ApipostNode.api(
                    targetId = "api-1",
                    name = "List",
                    url = "/users",
                    method = "GET",
                    parentId = "inner",
                ),
            ),
        )

        push(client, document)

        assertEquals(
            "a nested folder must point at its parent's id, not at the root",
            "outer",
            client.createdFolders[1].parentId,
        )
        assertEquals(
            "an API in a nested folder must resolve both levels",
            "inner",
            client.createdApis.single().parentId,
        )
    }

    // ─── folder reuse ───────────────────────────────────────────────────

    fun testExistingFolderIsReusedByNameInsteadOfBeingRecreated() {
        val client = FakeApipostApiClient(
            existingApis = listOf(
                // A folder the user already has, with the server's own id.
                ApipostExistingApi(
                    targetId = "server-folder-id",
                    name = "Users",
                    method = "",
                    path = "",
                    targetType = ApipostNode.TARGET_TYPE_FOLDER,
                ),
            ),
        )

        push(client, demoDocument())

        assertTrue(
            "re-creating an existing folder answers 14000 接口已存在, so it must be skipped",
            client.createdFolders.isEmpty(),
        )
        assertEquals(
            "children must be re-pointed at the folder the user already has",
            "server-folder-id",
            client.createdApis.single().parentId,
        )
    }

    // ─── models ─────────────────────────────────────────────────────────

    fun testModelFolderIsSkippedAndModelsAreFlattenedToRoot() {
        val client = FakeApipostApiClient()

        push(client, demoDocument())

        assertTrue(
            "a model folder cannot hold models through the open API (16005), so pushing " +
                "one would only leave an empty directory behind",
            client.createdFolders.none { it.name == "schemas" },
        )
        assertEquals(
            "a model cannot be nested through the open API, so it goes to the root",
            ApipostImportConfig.ROOT_ID,
            client.createdModels.single().parentId,
        )
    }

    fun testExistingModelIsUpdatedInsteadOfDuplicated() {
        val client = FakeApipostApiClient(
            existingModels = listOf(ApipostExistingModel(modelId = "server-model-id", name = "User")),
        )

        push(client, demoDocument())

        assertTrue("an existing model must not be created again", client.createdModels.isEmpty())
        assertEquals(listOf("server-model-id"), client.updatedModels.keys.toList())
    }

    // ─── $ref rewriting ─────────────────────────────────────────────────

    fun testModelRefsAreRewrittenToTheServerModelId() {
        val client = FakeApipostApiClient()

        push(client, demoDocument())

        // The formatter emitted a ref to the *client* id; the server mints a
        // different one, so leaving it alone would ship a dangling reference.
        val pushed = client.createdApis.single()
        assertEquals(
            "${ApipostModel.REF_PREFIX}srv-$MODEL_CLIENT_ID",
            refOf(pushed),
        )
    }

    fun testModelRefsFollowTheIdOfAnUpdatedModel() {
        val client = FakeApipostApiClient(
            existingModels = listOf(ApipostExistingModel(modelId = "existing-model-id", name = "User")),
        )

        push(client, demoDocument())

        assertEquals(
            "an updated model keeps its server id, and refs must follow it there too",
            "${ApipostModel.REF_PREFIX}existing-model-id",
            refOf(client.createdApis.single()),
        )
    }

    fun testModelRefIsLeftAloneWhenNoModelWasPushed() {
        val client = FakeApipostApiClient()
        val document = document(apis = listOf(apiNode()))

        push(client, document)

        assertEquals(
            "with no models in the document there is nothing to remap, and the ref " +
                "(which came from a pre-existing schema) survives untouched",
            "${ApipostModel.REF_PREFIX}$MODEL_CLIENT_ID",
            refOf(client.createdApis.single()),
        )
    }

    // ─── dedup and failure ──────────────────────────────────────────────

    fun testApiAlreadyOnTheServerIsUpdatedWithItsTargetIdAndKeepsItsFolder() {
        val client = FakeApipostApiClient(
            existingApis = listOf(
                ApipostExistingApi("server-folder-id", "Users", "", "", ApipostNode.TARGET_TYPE_FOLDER),
                ApipostExistingApi("server-api-id", "List users", "GET", "/users"),
            ),
        )

        val outcome = push(client, demoDocument())

        assertEquals(
            "update must carry the server's target_id",
            listOf("server-api-id"),
            client.updatedApis.keys.toList(),
        )
        assertEquals(
            "an updated API keeps its folder, resolved through the reused folder's id",
            "server-folder-id",
            client.updatedApis.values.single().parentId,
        )
        assertEquals(0, outcome.created)
        assertEquals(1, outcome.updated)
    }

    fun testFailureShortCircuitsAndReportsWhatWasDoneBefore() {
        val client = FakeApipostApiClient(
            apiFailure = ApipostSyncResult.Failure("api_token错误或已被删除！", ApipostCodes.TOKEN_INVALID),
        )

        val outcome = push(client, demoDocument())

        assertEquals("api_token错误或已被删除！", outcome.failure)
        assertEquals("nothing succeeded, so nothing is counted", 0, outcome.created)
    }

    fun testModelFailureShortCircuitsBeforeAnyApiIsPushed() {
        val client = FakeApipostApiClient(
            modelFailure = ApipostSyncResult.Failure("模型名称已存在", ApipostCodes.MODEL_NAME_EXISTS),
        )

        val outcome = push(client, demoDocument())

        assertEquals("模型名称已存在", outcome.failure)
        assertTrue("no API may be pushed once a model failed", client.createdApis.isEmpty())
    }

    // ─── fixtures ───────────────────────────────────────────────────────

    private fun push(client: ApipostApiClient, document: ApipostNativeDocument): ApipostPushOutcome =
        runBlocking { exporter.push(client, PROJECT_ID, document) }

    /** Reads the `$ref` out of the single response example of [node]. */
    private fun refOf(node: ApipostNode): String? {
        val example = node.response?.example?.single()
        assertNotNull("the node under test should carry one response example", example)
        return (example!!.expect.schema.get("\$ref"))?.asString
    }

    private fun demoDocument(): ApipostNativeDocument = document(
        apis = listOf(
            ApipostNode.folder(targetId = FOLDER_CLIENT_ID, name = "Users"),
            apiNode(),
        ),
        models = listOf(
            ApipostModel(
                modelId = MODEL_FOLDER_CLIENT_ID,
                modelType = ApipostModel.MODEL_TYPE_FOLDER,
                name = "schemas",
            ),
            ApipostModel(
                modelId = MODEL_CLIENT_ID,
                modelType = ApipostModel.MODEL_TYPE_MODEL,
                name = "User",
                parentId = MODEL_FOLDER_CLIENT_ID,
                schema = JsonObject().apply { addProperty("type", "object") },
            ),
        ),
    )

    /** An API in the `Users` folder whose response refs the client-side model id. */
    private fun apiNode(): ApipostNode = ApipostNode.api(
        targetId = "api-client-id",
        name = "List users",
        url = "/users",
        method = "GET",
        parentId = FOLDER_CLIENT_ID,
        response = ApipostResponse(
            example = listOf(
                ApipostExample(
                    exampleId = "1",
                    expect = ApipostExpect(
                        name = "OK",
                        code = "200",
                        schema = JsonObject().apply {
                            addProperty("\$ref", ApipostModel.REF_PREFIX + MODEL_CLIENT_ID)
                        },
                    ),
                ),
            ),
        ),
    )

    private fun document(
        apis: List<ApipostNode>,
        models: List<ApipostModel> = emptyList(),
    ): ApipostNativeDocument = ApipostNativeDocument(
        config = ApipostImportConfig(),
        name = "Demo",
        projectId = PROJECT_ID,
        apis = apis,
        models = models,
    )

    private companion object {
        const val PROJECT_ID = "p1"
        const val FOLDER_CLIENT_ID = "folder-client-id"
        const val MODEL_CLIENT_ID = "model-client-id"
        const val MODEL_FOLDER_CLIENT_ID = "model-folder-client-id"
    }
}
