package com.itangcent.easyapi.channel.openapi

import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.ApiHeader
import com.itangcent.easyapi.core.export.ApiParameter
import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.core.export.ParameterBinding
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.core.psi.model.FieldModel
import com.itangcent.easyapi.core.psi.model.ObjectModel
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import io.swagger.v3.parser.OpenAPIV3Parser
import io.swagger.v3.parser.core.models.ParseOptions
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

/**
 * OAS 3.0 compliance verification for the `openapi` channel output (NFR-6 / AC-10).
 *
 * **Why this test exists.** Before it, nothing in the repo proved the emitted
 * document was a *legal* OAS 3.0 document: there was no swagger/openapi
 * dependency anywhere, so "compliant" rested entirely on the literal content of
 * the golden files. A formatter change that emitted, say, a `$ref` alongside
 * sibling keys would keep every golden test green (goldens are regenerated) while
 * producing a document real consumers reject. This test closes that hole by
 * validating against the reference implementation (`swagger-parser`, test-only —
 * see `build.gradle.kts`).
 *
 * Two layers:
 *  - **A. Golden files** — every document the formatter/serializer ever produced
 *    (the `.txt` fixtures under `src/test/resources/result`), validated in bulk.
 *  - **B. In-memory document** — built through the public
 *    [OpenApiDocumentFactory] façade, so the façade and a live formatter run are
 *    covered too, not just frozen snapshots.
 *
 * Scope note: only *error-level* violations matter here. OAS optional fields the
 * formatter never emits (`security`, `externalDocs`, `nullable`, …) are not
 * gaps — they are legal omissions and must not fail this test.
 */
class OpenApiComplianceTest {

    private val project: Project = mock()

    // ─── A. Golden files ────────────────────────────────────────────────────

    @Test
    fun everyOpenApiGoldenFileIsValidOas3() {
        val dir = goldenDir() ?: error("Could not locate the `result` test-resources directory")
        val files = dir.listFiles { f ->
            f.isFile &&
                f.name.endsWith(".txt") &&
                GOLDEN_PREFIXES.any { f.name.startsWith(it) }
        }?.sortedBy { it.name }.orEmpty()

        // Guard against the filter silently matching nothing (which would make
        // this test vacuously green).
        assertTrue(
            "Expected to find the openapi golden files under ${dir.absolutePath}, found ${files.size}",
            files.size >= 15,
        )

        val violations = files.mapNotNull { file ->
            validate(file.readText())?.takeIf { it.isNotEmpty() }?.let { "${file.name} → $it" }
        }

        assertTrue(
            "OAS 3.0 violations in golden output:\n" + violations.joinToString("\n") +
                "\nIf a golden is legitimately obsolete, regenerate it; " +
                "do NOT relax this test to make it pass.",
            violations.isEmpty(),
        )
    }

    // ─── B. In-memory document via the public façade ────────────────────────

    /**
     * Guards the guard: if the validator were ineffective (e.g. a future
     * swagger-parser release stops reporting structural problems through
     * `messages`), both checks above would pass vacuously. This asserts that a
     * document missing the OAS-required `info` and `paths` is actually flagged.
     */
    @Test
    fun theValidatorReportsViolations() {
        val missingEverything = """{"openapi":"3.0.3"}"""
        assertTrue(
            "Expected the OAS validator to flag a document with neither `info` nor `paths`",
            validate(missingEverything).isNotEmpty(),
        )
    }

    @Test
    fun inMemoryDocumentBuiltThroughThePublicFacadeIsValidOas3() {
        val userBody = ObjectModel.Object(
            linkedMapOf(
                "id" to FieldModel(model = ObjectModel.single("long"), required = true),
                "name" to FieldModel(model = ObjectModel.single("string")),
                "status" to FieldModel(model = ObjectModel.single("string")),
            )
        )

        val endpoints = listOf(
            endpoint(
                name = "List users",
                path = "/users",
                method = HttpMethod.GET,
                parameters = listOf(
                    ApiParameter(
                        name = "status",
                        binding = ParameterBinding.Query,
                        description = "Filter by status",
                        example = "ACTIVE",
                    ),
                ),
                headers = listOf(ApiHeader(name = "X-Request-Id", description = "Request ID", example = "abc-123")),
                responseBody = userBody,
                sourceMethod = mockMethod("listUsers"),
            ),
            endpoint(
                name = "Create user",
                path = "/users",
                method = HttpMethod.POST,
                contentType = "application/json",
                bodyAttr = "com.example.User",
                body = userBody,
                responseBody = userBody,
                responseType = "com.example.User",
                sourceMethod = mockMethod("createUser"),
            ),
            endpoint(
                name = "Get user by id",
                path = "/users/{id}",
                method = HttpMethod.GET,
                parameters = listOf(
                    ApiParameter(
                        name = "id",
                        required = true,
                        binding = ParameterBinding.Path,
                        description = "User ID",
                    ),
                ),
                responseBody = userBody,
                sourceMethod = mockMethod("getUser"),
            ),
        )

        val document = OpenApiDocumentFactory.build(
            project = project,
            endpoints = endpoints,
            infoTitle = "Compliance API",
            infoVersion = "1.0.0",
            infoDescription = "Fixture for the OAS compliance check",
            serverUrl = "https://api.example.com/v1",
        )
        val json = OpenApiSerializer.toJson(document)

        // Sanity: the fixture must actually produce substance, otherwise an
        // empty-but-legal document would pass the compliance check vacuously.
        assertTrue("expected an OAS version marker", json.contains("\"openapi\": \"3.0.3\""))
        assertTrue("expected /users in paths", json.contains("\"/users\""))
        assertTrue("expected a component schema", json.contains("\"components\""))
        assertTrue("expected a servers block", json.contains("\"servers\""))

        val messages = validate(json)
        assertTrue(
            "OAS 3.0 violations in the façade-built document:\n$messages",
            messages.isEmpty(),
        )
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    /**
     * Runs the reference OAS 3.0 validator. An empty list means the document is
     * a legal OAS 3.0 document; every returned string is a violation.
     *
     * There is no "validate" switch to flip: `ParseOptions` only exposes
     * `validateExternalRefs` / `validateInternalRefs`, and structural validation
     * (required fields, value domains) runs unconditionally while deserializing.
     * Violations surface through `messages`.
     */
    private fun validate(spec: String): List<String> =
        OpenAPIV3Parser().readContents(spec, null, ParseOptions()).messages.orEmpty()

    private fun goldenDir(): File? {
        val url = javaClass.classLoader.getResource("result") ?: return null
        return File(url.toURI())
    }

    private fun endpoint(
        name: String? = null,
        path: String,
        method: HttpMethod,
        parameters: List<ApiParameter> = emptyList(),
        headers: List<ApiHeader> = emptyList(),
        contentType: String? = null,
        bodyAttr: String? = null,
        body: ObjectModel? = null,
        responseBody: ObjectModel? = null,
        responseType: String? = null,
        sourceMethod: PsiMethod? = null,
    ): ApiEndpoint = ApiEndpoint(
        name = name,
        metadata = httpMetadata(
            path = path,
            method = method,
            parameters = parameters,
            headers = headers,
            contentType = contentType,
            bodyAttr = bodyAttr,
            body = body,
            responseBody = responseBody,
            responseType = responseType,
        ),
        sourceMethod = sourceMethod,
    )

    private fun mockMethod(name: String): PsiMethod {
        val method = mock<PsiMethod>()
        whenever(method.name).thenReturn(name)
        return method
    }

    private companion object {
        /** Golden fixtures that hold an emitted OAS document. */
        val GOLDEN_PREFIXES = listOf(
            "com.itangcent.easyapi.channel.openapi.OpenApiFormatterTest.",
            "com.itangcent.easyapi.channel.openapi.OpenApiSerializerTest.",
        )
    }
}
