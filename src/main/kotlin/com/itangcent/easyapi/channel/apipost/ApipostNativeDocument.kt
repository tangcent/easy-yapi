package com.itangcent.easyapi.channel.apipost

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName

/**
 * Kotlin model of the **ApiPost native import envelope**.
 *
 * ## Provenance
 *
 * Every field name, default value and nesting level below was read off a real
 * request body captured on 2026-09-16 from the ApiPost web client (v8.2.7)
 * importing an easy-yapi OpenAPI document — see
 * `.spec/apipost-export/reference/apipost-native-payload.md` and the scrubbed
 * `apipost-native-skeleton.json`. The server never accepts OpenAPI directly:
 * the browser converts the document to this shape first, so this is the payload
 * an IDE plugin must produce.
 *
 * ## Field notes worth knowing before editing
 *
 * - `model_type` is **`"model"`** for a data model and `"folder"` for a model
 *   folder. (An earlier draft of the reference doc said `"schema"`; the capture
 *   says otherwise.)
 * - A folder node carries no `tags` / `url` / `method` / `response`. Those are
 *   nullable and simply omitted by Gson, which is exactly what the capture shows.
 * - A folder's `request.body` is the *bare* `{"parameter":[]}` — no `mode`,
 *   `raw`, `raw_parameter` or `raw_schema`. [ApipostRequestBody]'s optional
 *   fields being null (and therefore omitted) reproduces that.
 * - `expect.code` is a **string** (`"200"`), not a number.
 * - `not_null` is `1` when the parameter is required and `-1` otherwise.
 * - `param_id` / `target_id` / `model_id` are client-generated 14-hex ids;
 *   `parent_id` and `$ref` chains only resolve because the caller assigns them.
 *
 * Serialization uses Gson (nulls are omitted), so a null field means "not on the
 * wire". That is relied on throughout — do not add `serializeNulls()`.
 */

/** Root of the import envelope. */
data class ApipostNativeDocument(
    val global: ApipostGlobal = ApipostGlobal(),
    val config: ApipostImportConfig,
    val name: String,
    @SerializedName("project_id") val projectId: String? = null,
    val intro: String = "",
    val apis: List<ApipostNode> = emptyList(),
    val models: List<ApipostModel> = emptyList(),
    val samples: List<Any> = emptyList(),
    @SerializedName("import_type") val importType: String = IMPORT_TYPE_APIPOST,
) {
    companion object {
        /** Native (already-converted) document — what this package emits. */
        const val IMPORT_TYPE_APIPOST = "apipost"

        /** OpenAPI-semantics document — what the browser sends the server. */
        const val IMPORT_TYPE_SWAGGER = "swagger"
    }
}

data class ApipostGlobal(val envs: List<Any> = emptyList())

/**
 * Import policy for the receiving project.
 *
 * [apiCoverModal] is the server-side dedup key: `url_and_folder` means an
 * incoming API overwrites an existing one with the same URL inside the same
 * folder — which is the upsert semantics this channel wants, and the same key
 * the client-side `path` + `method` matching implements for the per-API push
 * path.
 */
data class ApipostImportConfig(
    val mode: String = "append",
    @SerializedName("folder_id") val folderId: String = ROOT_ID,
    val host: String = "",
    @SerializedName("base_path") val basePath: String = "",
    @SerializedName("api_cover_modal") val apiCoverModal: String = "url_and_folder",
    @SerializedName("sample_cover_modal") val sampleCoverModal: String = "name",
    @SerializedName("model_cover_modal") val modelCoverModal: String = "name",
    @SerializedName("testing_cover_modal") val testingCoverModal: String = "name",
    @SerializedName("env_cover_modal") val envCoverModal: String = "both",
) {
    companion object {
        /** `parent_id` value meaning "root". */
        const val ROOT_ID = "0"
    }
}

/**
 * One entry in `apis[]` — either a folder or an API.
 *
 * Folders and APIs share 21 fields; an API adds `tags` / `url` / `method` /
 * `response`. Modelled as one type rather than a sealed hierarchy because the
 * wire shape is one flat record and the extra fields are optional.
 */
data class ApipostNode(
    @SerializedName("target_type") val targetType: String = TARGET_TYPE_API,
    val description: String = "",
    @SerializedName("mark_id") val markId: String = "1",
    val request: ApipostRequest = ApipostRequest(),
    @SerializedName("ai_expect_enable") val aiExpectEnable: Int = -1,
    @SerializedName("is_check_result") val isCheckResult: Int = 1,
    @SerializedName("is_socket") val isSocket: Int = 1,
    @SerializedName("is_locked") val isLocked: Int = -1,
    @SerializedName("is_force") val isForce: Int = -1,
    @SerializedName("attribute_info") val attributeInfo: JsonObject = JsonObject(),
    val protocol: String = "http/1.1",
    @SerializedName("ai_expect") val aiExpect: ApipostAiExpect = ApipostAiExpect(),
    @SerializedName("project_id") val projectId: String? = null,
    @SerializedName("target_id") val targetId: String,
    @SerializedName("parent_id") val parentId: String = ApipostImportConfig.ROOT_ID,
    val sort: Int = 0,
    val version: Int = 0,
    @SerializedName("server_id") val serverId: String = "0",
    val status: Int = 1,
    @SerializedName("is_changed") val isChanged: Int = -1,
    val name: String,
    // ── API-only (absent on folders) ──────────────────────────────────────
    val tags: List<String>? = null,
    val url: String? = null,
    val method: String? = null,
    val response: ApipostResponse? = null,
) {
    companion object {
        const val TARGET_TYPE_FOLDER = "folder"
        const val TARGET_TYPE_API = "api"

        /** Builds a folder node (no `tags`/`url`/`method`/`response`). */
        fun folder(
            targetId: String,
            name: String,
            description: String = "",
            parentId: String = ApipostImportConfig.ROOT_ID,
            projectId: String? = null,
            sort: Int = 0,
        ): ApipostNode = ApipostNode(
            targetType = TARGET_TYPE_FOLDER,
            description = description,
            projectId = projectId,
            targetId = targetId,
            parentId = parentId,
            sort = sort,
            name = name,
        )

        /** Builds an API node. */
        fun api(
            targetId: String,
            name: String,
            url: String,
            method: String,
            description: String = "",
            parentId: String = ApipostImportConfig.ROOT_ID,
            projectId: String? = null,
            request: ApipostRequest = ApipostRequest(),
            response: ApipostResponse? = null,
            tags: List<String>? = null,
            sort: Int = 0,
        ): ApipostNode = ApipostNode(
            targetType = TARGET_TYPE_API,
            description = description,
            request = request,
            projectId = projectId,
            targetId = targetId,
            parentId = parentId,
            sort = sort,
            name = name,
            tags = tags,
            url = url,
            method = method,
            response = response,
        )
    }
}

data class ApipostAiExpect(
    val list: List<Any> = emptyList(),
    @SerializedName("none_math_expect_id") val noneMathExpectId: String = "1",
)

/**
 * A node's request block.
 *
 * [auth] stays a [JsonObject] rather than a typed model: it is a fixed ~1.9KB
 * template of 14 keys whose every value is a default (empty string, `false`, or
 * a protocol default such as `HS256`). Modelling it as data classes would mean
 * ~60 guessed property types for a block we never populate — the template comes
 * verbatim from the capture instead (see [ApipostAuthTemplate]).
 */
data class ApipostRequest(
    val auth: JsonObject = ApipostAuthTemplate.create(),
    @SerializedName("pre_tasks") val preTasks: List<Any> = emptyList(),
    @SerializedName("post_tasks") val postTasks: List<Any> = emptyList(),
    val header: ApipostParameterList = ApipostParameterList(),
    val query: ApipostParameterList = ApipostParameterList(),
    val cookie: ApipostParameterList = ApipostParameterList(),
    val restful: ApipostParameterList = ApipostParameterList(),
    val body: ApipostRequestBody = ApipostRequestBody(),
)

/** The `{"parameter": […]}` wrapper ApiPost uses for each parameter source. */
data class ApipostParameterList(val parameter: List<ApipostParameter> = emptyList())

/**
 * Request body.
 *
 * All fields are nullable so that a folder's bare `{"parameter":[]}` and an
 * API's populated `{"mode":"json","parameter":[…],"raw":"","raw_parameter":[],"raw_schema":{}}`
 * are both expressible without a second type.
 */
data class ApipostRequestBody(
    val mode: String? = null,
    val parameter: List<ApipostParameter> = emptyList(),
    val raw: String? = null,
    @SerializedName("raw_parameter") val rawParameter: List<Any>? = null,
    @SerializedName("raw_schema") val rawSchema: JsonObject? = null,
    /** Unused for JSON/XML bodies; omitted from the wire when null. */
    val binary: Any? = null,
)

/** One parameter row under `header` / `query` / `cookie` / `restful`. */
data class ApipostParameter(
    @SerializedName("is_checked") val isChecked: Int = 1,
    val key: String,
    val value: String = "",
    /** `1` when required, `-1` otherwise (ApiPost's tri-state convention). */
    @SerializedName("not_null") val notNull: Int = NOT_NULL,
    val description: String = "",
    /** Java-style type name, first letter upper-cased — e.g. `String`, `Integer`. */
    @SerializedName("field_type") val fieldType: String = "String",
    @SerializedName("param_id") val paramId: String,
    val schema: JsonObject = JsonObject(),
) {
    companion object {
        const val REQUIRED = 1
        const val NOT_NULL = -1
    }
}

data class ApipostResponse(
    @SerializedName("is_check_result") val isCheckResult: Int = 1,
    val example: List<ApipostExample> = emptyList(),
)

data class ApipostExample(
    @SerializedName("example_id") val exampleId: String,
    val raw: String = "",
    @SerializedName("raw_parameter") val rawParameter: List<Any> = emptyList(),
    val headers: List<Any> = emptyList(),
    val expect: ApipostExpect,
)

data class ApipostExpect(
    val name: String,
    /** `1` marks the default example; `-1` otherwise. */
    @SerializedName("is_default") val isDefault: Int = -1,
    /** **A string** on the wire — `"200"`, `"404"`. Not a number. */
    val code: String,
    val sleep: Int = 0,
    @SerializedName("content_type") val contentType: String = "json",
    @SerializedName("verify_type") val verifyType: String = "schema",
    val mock: String = "",
    val schema: JsonObject = JsonObject(),
)

/**
 * One entry in `models[]` — either a model folder or a data model.
 *
 * [schema] holds a **JSON Schema** document, and references to it elsewhere use
 * ApiPost's OAS-flavoured form `#/components/schemas/{model_id}` — the same
 * prefix OpenAPI uses, with the model's server id in place of its name.
 */
data class ApipostModel(
    @SerializedName("model_id") val modelId: String,
    /** For a model: `#/components/schemas/{modelId}`; empty for a folder. */
    @SerializedName("old_model_id") val oldModelId: String = "",
    @SerializedName("model_type") val modelType: String,
    val description: String = "",
    @SerializedName("parent_id") val parentId: String = ApipostImportConfig.ROOT_ID,
    @SerializedName("project_id") val projectId: String? = null,
    val sort: Int = 0,
    val schema: JsonObject = JsonObject(),
    val name: String,
    @SerializedName("display_name") val displayName: String = "",
    @SerializedName("created_at") val createdAt: Long = 0,
    @SerializedName("updated_at") val updatedAt: Long = 0,
    val version: Int = 0,
    @SerializedName("is_changed") val isChanged: Int = -1,
    val status: Int = 1,
    @SerializedName("created_user_info") val createdUserInfo: ApipostUserInfo = ApipostUserInfo(),
    @SerializedName("updated_user_info") val updatedUserInfo: ApipostUserInfo = ApipostUserInfo(),
) {
    companion object {
        const val MODEL_TYPE_FOLDER = "folder"
        const val MODEL_TYPE_MODEL = "model"

        /** Prefix used by ApiPost model references — OpenAPI's, not ours. */
        const val REF_PREFIX = "#/components/schemas/"

        /** The reference string pointing at [modelId]. */
        fun ref(modelId: String): String = "$REF_PREFIX$modelId"
    }
}

data class ApipostUserInfo(
    @SerializedName("nick_name") val nickName: String = "",
    val portrait: String = "",
)

/**
 * The fixed, all-defaults `request.auth` block ApiPost expects on every node.
 *
 * Taken verbatim from the captured request (14 keys, 13 of them nested
 * auth schemes) and parsed once. It accounts for roughly 46% of a single API
 * node's payload, so it is kept as a single JSON literal rather than ~60
 * hand-written properties — fewer places to get a default wrong.
 */
internal object ApipostAuthTemplate {

    private val template: JsonObject by lazy { JsonParser.parseString(TEMPLATE).asJsonObject }

    /** Returns a fresh, independently mutable copy of the template. */
    fun create(): JsonObject = template.deepCopy()

    private const val TEMPLATE = """{
 "type": "inherit",
 "kv": { "key": "", "value": "", "in": "header" },
 "bearer": { "key": "" },
 "basic": { "username": "", "password": "" },
 "digest": {
  "username": "", "password": "", "realm": "", "nonce": "", "algorithm": "MD5",
  "qop": "", "nc": "", "cnonce": "", "opaque": "", "disableRetryRequest": false
 },
 "oauth1": {
  "consumerKey": "", "consumerSecret": "", "signatureMethod": "HMAC-SHA1",
  "addEmptyParamsToSign": true, "includeBodyHash": true, "addParamsToHeader": false,
  "realm": "", "version": "1.0", "nonce": "", "timestamp": "", "verifier": "",
  "callback": "", "tokenSecret": "", "token": "", "disableHeaderEncoding": false
 },
 "oauth2": {
  "addTokenTo": "header", "headerPrefix": "Bearer", "access_token": "",
  "grant_type": "authorization_code", "redirect_uri": "", "authUrl": "",
  "accessTokenUrl": "", "clientId": "", "clientSecret": "", "username": "",
  "password": "", "challengeAlgorithm": "S256", "code_verifier": "Bearer",
  "scope": "", "state": "", "client_authentication": "header", "refreshTokenUrl": "",
  "authRequestParams": [], "tokenRequestParams": [], "refreshRequestParams": []
 },
 "hawk": {
  "authId": "", "authKey": "", "algorithm": "", "user": "", "nonce": "",
  "extraData": "", "app": "", "delegation": "", "timestamp": "",
  "includePayloadHash": false
 },
 "awsv4": {
  "accessKey": "", "secretKey": "", "region": "", "service": "",
  "sessionToken": "", "addAuthDataToQuery": false
 },
 "ntlm": {
  "username": "", "password": "", "domain": "", "workstation": "",
  "disableRetryRequest": false
 },
 "edgegrid": {
  "accessToken": "", "clientToken": "", "clientSecret": "", "nonce": "",
  "timestamp": "", "baseURi": "", "headersToSign": ""
 },
 "noauth": {},
 "jwt": {
  "addTokenTo": "header", "algorithm": "HS256", "secret": "",
  "isSecretBase64Encoded": false, "payload": "", "headerPrefix": "Bearer",
  "queryParamKey": "token", "header": ""
 },
 "asap": {
  "alg": "HS256", "iss": "", "aud": "", "kid": "", "privateKey": "",
  "sub": "", "claims": "", "exp": ""
 }
}"""
}
