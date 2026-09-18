package com.itangcent.easyapi.channel.apipost

import com.google.gson.Gson
import com.itangcent.easyapi.testFramework.ResultLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Field-mapping tests for [ApipostNativeFormatter] — OpenAPI 3.0 → ApiPost's
 * native import envelope.
 *
 * The primary fixture is the **real** OpenAPI output of the `openapi` channel
 * (`OpenApiSerializerTest.allFeaturesJson`), so the mapping is exercised against
 * the exact shape this plugin emits rather than a hand-written idealisation of
 * it. That document has 6 operations across 3 tags and one `components.schemas`
 * entry, which covers grouping, parameter routing, request bodies, responses and
 * `$ref` rewriting in one go.
 *
 * Pure JUnit: the formatter takes no `Project` and touches no PSI.
 */
class ApipostNativeFormatterTest {

    private val gson = Gson()

    /**
     * Real `openapi` channel output: `/users` (GET, POST), `/users/{id}`
     * (GET, DELETE), `/forms/{id}` (POST), `/users/{id}/avatar` (POST) —
     * tags Users / Forms / Uploads, one `User` schema.
     */
    private val allFeaturesOas: String = ResultLoader.load(
        Class.forName("com.itangcent.easyapi.channel.openapi.OpenApiSerializerTest"),
        "allFeaturesJson",
    )

    private val defaultOptions = ApipostNativeOptions(projectId = "proj-1", name = "Demo API")

    private fun format(
        oas: String = allFeaturesOas,
        options: ApipostNativeOptions = defaultOptions,
    ): ApipostNativeDocument = ApipostNativeFormatter.format(oas, emptyList(), options)

    private fun ApipostNativeDocument.folders() =
        apis.filter { it.targetType == ApipostNode.TARGET_TYPE_FOLDER }

    private fun ApipostNativeDocument.apiNodes() =
        apis.filter { it.targetType == ApipostNode.TARGET_TYPE_API }

    private fun ApipostNativeDocument.modelsOnly() =
        models.filter { it.modelType == ApipostModel.MODEL_TYPE_MODEL }

    // ─── apis[] ─────────────────────────────────────────────────────────────

    @Test
    fun everyOperationBecomesAnApiGroupedUnderItsTag() {
        val document = format()

        val folders = document.folders()
        assertEquals(
            "one folder per tag in the source document",
            setOf("Users", "Forms", "Uploads"),
            folders.map { it.name }.toSet(),
        )

        val apis = document.apiNodes()
        assertEquals("one API node per path+method", 6, apis.size)
        assertEquals(
            setOf("/users", "/users/{id}", "/forms/{id}", "/users/{id}/avatar"),
            apis.map { it.url }.toSet(),
        )
        assertTrue("methods must be upper-cased", apis.all { it.method == it.method?.uppercase() })
        assertTrue("every API must carry a project id", apis.all { it.projectId == "proj-1" })
    }

    @Test
    fun foldersAreEmittedBeforeTheApisThatReferenceThem() {
        val document = format()

        val lastFolderIndex = document.apis.indexOfLast { it.targetType == ApipostNode.TARGET_TYPE_FOLDER }
        val firstApiIndex = document.apis.indexOfFirst { it.targetType == ApipostNode.TARGET_TYPE_API }

        assertTrue(
            "a folder's target_id must exist before any child references it as parent_id",
            lastFolderIndex in 0..<firstApiIndex,
        )

        val folderIds = document.folders().map { it.targetId }.toSet()
        assertTrue(
            "every folder is a root node",
            document.folders().all { it.parentId == ApipostImportConfig.ROOT_ID },
        )
        assertTrue(
            "every API points at a folder emitted in the same document",
            document.apiNodes().all { it.parentId in folderIds },
        )
    }

    @Test
    fun operationsWithoutTagsBecomeRootLevelApis() {
        val untagged = """
            {
              "openapi": "3.0.3",
              "info": { "title": "T", "version": "1.0.0" },
              "paths": {
                "/ping": { "get": { "summary": "Ping", "operationId": "ping", "responses": { "200": { "description": "OK" } } } }
              }
            }
        """.trimIndent()

        val document = ApipostNativeFormatter.format(
            untagged,
            emptyList(),
            ApipostNativeOptions(name = "T"),
        )

        assertEquals("no tag means no folder", 0, document.apis.count { it.targetType == ApipostNode.TARGET_TYPE_FOLDER })
        val api = document.apis.single()
        assertEquals(ApipostImportConfig.ROOT_ID, api.parentId)
        assertEquals("/ping", api.url)
        assertEquals("GET", api.method)
        assertEquals("Ping", api.name)
    }

    // ─── request mapping ────────────────────────────────────────────────────

    @Test
    fun parametersAreRoutedByTheirLocation() {
        val document = format()

        val listUsers = document.apiNodes().single { it.url == "/users" && it.method == "GET" }
        assertEquals("query params go to query", listOf("status"), listUsers.request.query.parameter.map { it.key })
        assertEquals("headers go to header", listOf("X-Request-Id"), listUsers.request.header.parameter.map { it.key })
        assertEquals("cookies go to cookie", listOf("session"), listUsers.request.cookie.parameter.map { it.key })

        val getUser = document.apiNodes().single { it.url == "/users/{id}" && it.method == "GET" }
        val idParam = getUser.request.restful.parameter.single()
        assertEquals("path params go to restful", "id", idParam.key)
        assertEquals("a required path param maps to not_null=1", ApipostParameter.REQUIRED, idParam.notNull)
        assertEquals("Integer", idParam.fieldType)
        assertTrue("every parameter carries a generated id", idParam.paramId.matches(ID_PATTERN))

        val optional = listUsers.request.query.parameter.single()
        assertEquals("an optional param maps to not_null=-1", ApipostParameter.NOT_NULL, optional.notNull)
        assertEquals("String", optional.fieldType)
    }

    @Test
    fun requestBodyBecomesARawSchemaWithAModeFromTheMediaType() {
        val document = format()

        val createUser = document.apiNodes().single { it.url == "/users" && it.method == "POST" }
        val body = createUser.request.body
        assertEquals("application/json maps to mode=json", "json", body.mode)
        assertNotNull("the body schema must survive", body.rawSchema)
        assertEquals("an API body carries an empty raw_parameter list", emptyList<Any>(), body.rawParameter)

        val listUsers = document.apiNodes().single { it.url == "/users" && it.method == "GET" }
        assertEquals("a GET with no body has a bare body block", null, listUsers.request.body.mode)
    }

    // ─── response mapping ───────────────────────────────────────────────────

    @Test
    fun responsesBecomeExamplesWithStringStatusCodes() {
        val document = format()

        val listUsers = document.apiNodes().single { it.url == "/users" && it.method == "GET" }
        val example = listUsers.response?.example?.single() ?: error("expected one response example")

        // `code` is a String on the wire, not a number.
        assertEquals("200", example.expect.code)
        assertEquals("the first 2xx response is the default example", 1, example.expect.isDefault)
        assertEquals("OK", example.expect.name)
        assertNotNull("a response schema must survive", example.expect.schema)
    }

    // ─── models[] and $ref rewriting ────────────────────────────────────────

    @Test
    fun modelsMirrorComponentsSchemasUnderASchemasFolder() {
        val document = format()

        val folder = document.models.single { it.modelType == ApipostModel.MODEL_TYPE_FOLDER }
        assertEquals("schemas", folder.name)
        assertEquals("the schemas folder is a root node", ApipostImportConfig.ROOT_ID, folder.parentId)

        val schemas = document.modelsOnly()
        assertEquals(setOf("User"), schemas.map { it.name }.toSet())

        val user = schemas.single()
        assertEquals("models hang off the schemas folder", folder.modelId, user.parentId)
        assertEquals("old_model_id mirrors the OAS ref shape", ApipostModel.ref(user.modelId), user.oldModelId)
        assertEquals("proj-1", user.projectId)
        assertTrue("every model carries a generated id", user.modelId.matches(ID_PATTERN))
        assertEquals("the schema body is carried over verbatim", "object", user.schema.get("type")?.asString)
    }

    @Test
    fun everyRefPointsAtAModelIdDefinedInTheSameDocument() {
        val document = format()
        val refs = refsIn(gson.toJson(document))
        val modelIds = document.modelsOnly().map { it.modelId }.toSet()

        assertTrue("the fixture must actually contain references", refs.isNotEmpty())
        assertTrue("no bare schema names may survive as refs: $refs", refs.none { it.endsWith("/User") })
        assertTrue(
            "every \$ref must resolve to a model_id in the same document: $refs vs $modelIds",
            refs.all { it.removePrefix(ApipostModel.REF_PREFIX) in modelIds },
        )
    }

    @Test
    fun repeatedFormatCallsDoNotAccumulateNameSuffixes() {
        val first = format().modelsOnly().map { it.name }
        val second = format().modelsOnly().map { it.name }
        val third = format().modelsOnly().map { it.name }

        assertEquals("model naming must be per-document, not shared across runs", first, second)
        assertEquals(first, third)
        assertTrue("no spurious dedup suffix expected here", first.none { it.contains("_") })
    }

    // ─── envelope ───────────────────────────────────────────────────────────

    @Test
    fun configHostAndBasePathComeFromTheDocumentedApiUrlOnly() {
        val configured = ApipostNativeFormatter.format(
            allFeaturesOas,
            emptyList(),
            ApipostNativeOptions.of("proj-1", "Demo API", "https://api.example.com/v2"),
        )
        assertEquals("https://api.example.com", configured.config.host)
        assertEquals("/v2", configured.config.basePath)

        val unconfigured = ApipostNativeFormatter.format(
            allFeaturesOas,
            emptyList(),
            ApipostNativeOptions.of("proj-1", "Demo API", null),
        )
        assertEquals("no server url means an empty host", "", unconfigured.config.host)
        assertEquals("no server url means an empty base path", "", unconfigured.config.basePath)
    }

    @Test
    fun envelopeCarriesTitleProjectAndNativeImportType() {
        val document = format()

        assertEquals("info.title wins over the fallback name", "Demo API", document.name)
        assertEquals("proj-1", document.projectId)
        assertEquals(ApipostNativeDocument.IMPORT_TYPE_APIPOST, document.importType)
        assertEquals("url_and_folder", document.config.apiCoverModal)
        assertEquals("append", document.config.mode)
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private fun refsIn(json: String): List<String> =
        REF_PATTERN.findAll(json).map { it.groupValues[1] }.toList()

    private companion object {
        val ID_PATTERN = Regex("^[0-9a-f]{14}$")
        val REF_PATTERN = Regex("\"\\\$ref\"\\s*:\\s*\"([^\"]+)\"")
    }
}
