package com.itangcent.easyapi.channel.apipost

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.itangcent.easyapi.core.http.HttpClient
import com.itangcent.easyapi.core.http.HttpRequest
import com.itangcent.easyapi.core.http.HttpResponse
import com.itangcent.easyapi.core.http.name
import com.itangcent.easyapi.core.http.value
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [DefaultApipostApiClient] — the wire contract, pinned.
 *
 * Every assertion here encodes something that was measured against the live API
 * on 2026-09-17 (`REVIEW.md §6.9`). The point is that a future refactor cannot
 * quietly undo a fact that took a real token to discover:
 *
 * - snake_case is the only accepted naming (`targetType` reads as "missing").
 * - `project_code` is **not** required, despite the earlier reading of the
 *   error message — which lists every known required field, not just the
 *   missing one.
 * - `update` requires `method` and `name`; `create` does not.
 * - success is decided by the body's `code`, never the HTTP status.
 * - `createModel` must not fall back to the client id: the server regenerates
 *   `model_id`, and the fallback would ship a dangling `$ref`.
 *
 * Plain JUnit — the client takes its [HttpClient] by constructor and touches no
 * IntelliJ or PSI machinery.
 */
class DefaultApipostApiClientTest {

    // ─── create ─────────────────────────────────────────────────────────

    @Test
    fun `createApi sends snake_case fields and never project_code`() {
        val http = FakeHttpClient(ok("""{"target_id":"srv-api"}"""))

        runBlocking { client(http).createApi(PROJECT_ID, apiNode()) }

        val request = http.requests.single()
        assertEquals("POST", request.method)
        assertEquals("$BASE_URL/open/apis/create", request.url)
        assertEquals(TOKEN, request.headers.single { it.name == TOKEN_HEADER }.value)

        val body = json(request.body!!)
        assertEquals(PROJECT_ID, body.get("project_id").asString)
        assertEquals("api", body.get("target_type").asString)
        assertEquals("GET", body.get("method").asString)
        assertEquals("/users", body.get("url").asString)
        assertFalse(
            "project_code is optional — the error message lists all known required " +
                "fields, not only the missing one",
            body.has("project_code"),
        )
        assertFalse("camelCase is reported as a missing field by the server", body.has("targetType"))
    }

    @Test
    fun `createApi returns the id the server reports`() {
        val http = FakeHttpClient(ok("""{"target_id":"srv-api"}"""))

        val result = runBlocking { client(http).createApi(PROJECT_ID, apiNode()) }

        assertEquals("srv-api", result.getOrNull())
    }

    @Test
    fun `createApi falls back to the node id when the response omits one`() {
        // Safe only for APIs and folders: the server adopts a client-supplied
        // target_id, so the node's own id is a truthful answer.
        val http = FakeHttpClient(ok("{}"))

        val result = runBlocking { client(http).createApi(PROJECT_ID, apiNode(targetId = "client-id")) }

        assertEquals("client-id", result.getOrNull())
    }

    // ─── update ─────────────────────────────────────────────────────────

    @Test
    fun `updateApi carries the server target id plus method and name`() {
        val http = FakeHttpClient(ok("{}"))

        runBlocking { client(http).updateApi(PROJECT_ID, "srv-api", apiNode(targetId = "client-id")) }

        assertEquals("$BASE_URL/open/apis/update", http.requests.single().url)
        val body = json(http.requests.single().body!!)
        assertEquals("the node's own id must not be reused for an in-place update",
            "srv-api", body.get("target_id").asString)
        assertEquals("update requires method", "GET", body.get("method").asString)
        assertEquals("update requires name", "List users", body.get("name").asString)
    }

    @Test
    fun `updateModel sends original_name because it is not a model property`() {
        val http = FakeHttpClient(ok("{}"))

        runBlocking { client(http).updateModel(PROJECT_ID, "srv-model", model()) }

        assertEquals("$BASE_URL/open/models/update", http.requests.single().url)
        val body = json(http.requests.single().body!!)
        assertEquals("srv-model", body.get("model_id").asString)
        assertEquals("User", body.get("original_name").asString)
        assertEquals("model", body.get("model_type").asString)
        assertEquals(PROJECT_ID, body.get("project_id").asString)
    }

    // ─── models ─────────────────────────────────────────────────────────

    @Test
    fun `createModel returns the server-generated id`() {
        val http = FakeHttpClient(ok("""{"model_id":"srv-model"}"""))

        val result = runBlocking { client(http).createModel(PROJECT_ID, model(modelId = "client-model")) }

        assertEquals("srv-model", result.getOrNull())
    }

    @Test
    fun `createModel fails rather than echoing the client id`() {
        // The server ignores a client-supplied model_id, so a fallback here would
        // plant a $ref that resolves to nothing — silently. Fail instead.
        val http = FakeHttpClient(ok("{}"))

        val result = runBlocking { client(http).createModel(PROJECT_ID, model(modelId = "client-model")) }

        assertFalse("a missing model id is not a success", result.isSuccess)
        assertTrue(result.errorMessage().orEmpty().contains("id"))
    }

    // ─── listing ────────────────────────────────────────────────────────

    @Test
    fun `listTeams reads a bare array in data`() {
        // The shape trap: `team/list` and `project/list` put a bare array in
        // `data`, while `apis/list` and `models/list` put {"list":[…]} there. A
        // reader that only looked for `data.list` would return an empty list
        // here — silently (reference/apipost-list-shapes.md).
        val http = FakeHttpClient(ok("""[{"team_id":"t1","name":"Team One","created_id":"u9"}]"""))

        val result = runBlocking { client(http).listTeams() }

        assertEquals("$BASE_URL/open/team/list", http.requests.single().url)
        val teams = result.getOrNull()
        assertEquals(1, teams?.size)
        assertEquals("t1", teams!!.single().teamId)
        assertEquals("Team One", teams.single().name)
    }

    @Test
    fun `listProjects sends the required team_id and maps every row`() {
        val http = FakeHttpClient(
            ok("""[{"project_id":"p1","team_id":"t1","name":"Alpha"},
                   {"project_id":"p2","team_id":"t1","name":"Beta"}]"""),
        )

        val result = runBlocking { client(http).listProjects("t1") }

        assertEquals("$BASE_URL/open/project/list", http.requests.single().url)
        assertEquals(
            "team_id is required — omitting it answers 10001 TeamId为必填字段",
            "t1",
            http.requests.single().query.single { it.name == "team_id" }.value,
        )
        val projects = result.getOrNull()
        assertEquals(listOf("p1", "p2"), projects?.map { it.projectId })
        assertEquals("Alpha", projects!!.first().name)
        assertEquals("the row echoes its team, so no request state is needed", "t1", projects.first().teamId)
    }

    @Test
    fun `an apis list that answers a bare array is still read`() {
        // Same route, other envelope: the reader must accept both rather than
        // tie itself to one measured response.
        val http = FakeHttpClient(ok("""[{"target_id":"a1","name":"List users","method":"GET","url":"/users"}]"""))

        val result = runBlocking { client(http).listApis(PROJECT_ID) }

        assertEquals("a1", result.getOrNull()?.single()?.targetId)
    }

    @Test
    fun `a team the token cannot reach is reported as code 12000`() {
        val http = FakeHttpClient(jsonResponse(200, """{"code":12000,"msg":"该团队不存在或已被团队管理员解散"}"""))

        val result = runBlocking { client(http).listProjects("ghost") }

        assertEquals(ApipostCodes.TEAM_NOT_FOUND, (result as ApipostSyncResult.Failure).code)
    }

    @Test
    fun `listApis sends project_id as a query parameter and maps folders too`() {
        val http = FakeHttpClient(
            ok(
                """{"list":[
                     {"target_id":"f1","target_type":"folder","name":"Users","method":"","url":""},
                     {"target_id":"a1","target_type":"api","name":"List users","method":"GET","url":"/users"}
                   ]}""",
            ),
        )

        val result = runBlocking { client(http).listApis(PROJECT_ID) }

        assertEquals("$BASE_URL/open/apis/list", http.requests.single().url)
        assertEquals(
            "project_id travels as a query parameter, not in the body",
            PROJECT_ID,
            http.requests.single().query.single { it.name == "project_id" }.value,
        )

        val nodes = result.getOrNull()
        assertEquals(2, nodes?.size)
        assertEquals(ApipostNode.TARGET_TYPE_FOLDER, nodes!![0].targetType)
        assertEquals("the folder's name is what folder reuse matches on", "Users", nodes[0].name)
        assertEquals("GET", nodes[1].method)
        assertEquals("/users", nodes[1].path)
    }

    @Test
    fun `listModels reads the nested list payload`() {
        val http = FakeHttpClient(
            ok("""{"list":[{"model_id":"m1","model_type":"model","name":"User"}]}"""),
        )

        val result = runBlocking { client(http).listModels(PROJECT_ID) }

        assertEquals("m1", result.getOrNull()?.single()?.modelId)
        assertEquals("User", result.getOrNull()?.single()?.name)
    }

    @Test
    fun `a list entry without an id is skipped rather than crashing the export`() {
        val http = FakeHttpClient(ok("""{"list":[{"name":"broken"},{"model_id":"m1","name":"User"}]}"""))

        val result = runBlocking { client(http).listModels(PROJECT_ID) }

        assertEquals(1, result.getOrNull()?.size)
    }

    // ─── response classification ────────────────────────────────────────

    @Test
    fun `a business code failure is reported with its code`() {
        // 14000 means the node already exists. It is a failure of *this call*, and
        // the push relies on the dedup index to have prevented it — carrying the
        // code is what lets a caller tell it apart from a real error.
        val http = FakeHttpClient(jsonResponse(200, """{"code":14000,"msg":"接口已存在"}"""))

        val result = runBlocking { client(http).createApi(PROJECT_ID, apiNode()) }

        val failure = result as ApipostSyncResult.Failure
        assertEquals(ApipostCodes.API_ALREADY_EXISTS, failure.code)
        assertEquals("接口已存在", failure.message)
    }

    @Test
    fun `a bad token is reported as code 30001 over HTTP 200`() {
        // An existing route answers HTTP 200 even on failure, which is why the
        // status cannot be used to decide success (REVIEW.md §6.8.2).
        val http = FakeHttpClient(jsonResponse(200, """{"code":30001,"msg":"api_token错误或已被删除！"}"""))

        val result = runBlocking { client(http).listApis(PROJECT_ID) }

        assertEquals(ApipostCodes.TOKEN_INVALID, (result as ApipostSyncResult.Failure).code)
    }

    @Test
    fun `an unknown route is reported with its status because the body is not JSON`() {
        val http = FakeHttpClient(jsonResponse(404, "404 page not found"))

        val result = runBlocking { client(http).listApis(PROJECT_ID) }

        val failure = result as ApipostSyncResult.Failure
        assertEquals(404, failure.httpCode)
        assertTrue("the status must survive into the message", failure.message.contains("404"))
    }

    @Test
    fun `an empty body is a failure, not an exception`() {
        val http = FakeHttpClient(jsonResponse(200, ""))

        val result = runBlocking { client(http).listApis(PROJECT_ID) }

        assertFalse(result.isSuccess)
    }

    // ─── fixtures ───────────────────────────────────────────────────────

    private fun client(http: HttpClient) = DefaultApipostApiClient(BASE_URL, TOKEN, http)

    private fun apiNode(targetId: String = "api-1") = ApipostNode.api(
        targetId = targetId,
        name = "List users",
        url = "/users",
        method = "GET",
    )

    private fun model(modelId: String = "model-1") = ApipostModel(
        modelId = modelId,
        modelType = ApipostModel.MODEL_TYPE_MODEL,
        name = "User",
    )

    private fun json(body: String): JsonObject = JsonParser.parseString(body).asJsonObject

    private fun ok(data: String) = jsonResponse(200, """{"code":0,"msg":"成功","data":$data}""")

    private fun jsonResponse(code: Int, body: String) = HttpResponse(code = code, body = body)

    /** Records every request so the payload can be asserted on. */
    private class FakeHttpClient(private val response: HttpResponse) : HttpClient {

        val requests = mutableListOf<HttpRequest>()

        override suspend fun execute(request: HttpRequest): HttpResponse {
            requests += request
            return response
        }

        override fun close() = Unit
    }

    private companion object {
        const val BASE_URL = "https://open.apipost.net"
        const val TOKEN = "test-token"
        const val TOKEN_HEADER = "api-token"
        const val PROJECT_ID = "project-1"
    }
}
