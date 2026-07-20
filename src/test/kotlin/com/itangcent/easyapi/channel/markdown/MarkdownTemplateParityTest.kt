package com.itangcent.easyapi.channel.markdown

import com.itangcent.easyapi.channel.markdown.template.DefaultMarkdownTemplate
import com.itangcent.easyapi.channel.markdown.template.MarkdownTemplateRenderer
import com.itangcent.easyapi.channel.markdown.template.RenderContext
import com.itangcent.easyapi.channel.markdown.template.TemplateModelBuilder
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.ApiHeader
import com.itangcent.easyapi.core.export.ApiParameter
import com.itangcent.easyapi.core.export.GrpcMetadata
import com.itangcent.easyapi.core.export.GrpcStreamingType
import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.core.export.ParameterBinding
import com.itangcent.easyapi.core.export.ParameterType
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.core.psi.model.FieldModel
import com.itangcent.easyapi.core.psi.model.FieldOption
import com.itangcent.easyapi.core.psi.model.ObjectModel
import com.itangcent.easyapi.core.psi.type.JsonType
import com.itangcent.easyapi.testFramework.ResourceLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * The load-bearing parity gate.
 *
 * Asserts that the default template rendered via [MarkdownTemplateRenderer]
 * matches a **static committed golden file** byte-for-byte. The golden is
 * captured at `src/test/resources/com/itangcent/easyapi/channel/markdown/MarkdownTemplateParityTest.expected.md`.
 *
 * To regenerate the golden (deliberate, reviewed change):
 *   `./gradlew test --tests *.MarkdownTemplateParityTest -Dupdate.golden=true`
 *
 * The fixture matrix covers: object/array/primitive/map/recursive bodies,
 * gRPC (all streaming types), params (path/query/header/form), field options,
 * descriptions, folders, and edge cases (no name, blank description).
 *
 * Pure JUnit: no `Project`, no PSI/VFS.
 */
class MarkdownTemplateParityTest {

    private val ctx = RenderContext(
        clock = Clock.fixed(Instant.parse("2026-03-15T10:30:45Z"), ZoneId.of("UTC")),
        zone = ZoneId.of("UTC"),
        username = "testuser",
        projectName = "test-project",
        pluginVersion = "1.0.0-test",
    )

    private val defaultTemplate = DefaultMarkdownTemplate.get()

    companion object {
        private const val GOLDEN_RESOURCE =
            "/com/itangcent/easyapi/channel/markdown/MarkdownTemplateParityTest.expected.md"
        private const val GOLDEN_PATH =
            "src/test/resources/com/itangcent/easyapi/channel/markdown/MarkdownTemplateParityTest.expected.md"
    }

    /** The comprehensive fixture matrix — mirrors the shapes from DefaultMarkdownFormatterTest. */
    private fun comprehensiveEndpoints(): List<ApiEndpoint> = listOf(
        // --- HTTP: basic GET with path param ---
        ApiEndpoint(
            name = "Get User",
            folder = "Users",
            metadata = httpMetadata(
                path = "/api/users/{id}",
                method = HttpMethod.GET,
                parameters = listOf(
                    ApiParameter(name = "id", binding = ParameterBinding.Path, required = true, description = "User ID")
                )
            )
        ),
        // --- HTTP: POST with object request body + nested object response ---
        ApiEndpoint(
            name = "Create User",
            folder = "Users",
            metadata = httpMetadata(
                path = "/api/users",
                method = HttpMethod.POST,
                body = ObjectModel.Object(
                    mapOf(
                        "name" to FieldModel(ObjectModel.single(JsonType.STRING), comment = "user name"),
                        "age" to FieldModel(ObjectModel.single(JsonType.INT), comment = "user age")
                    )
                ),
                responseBody = ObjectModel.Object(
                    mapOf(
                        "code" to FieldModel(ObjectModel.single(JsonType.INT), comment = "response code"),
                        "msg" to FieldModel(ObjectModel.single(JsonType.STRING), comment = "message"),
                        "data" to FieldModel(
                            ObjectModel.Object(
                                mapOf(
                                    "id" to FieldModel(ObjectModel.single(JsonType.LONG), comment = "user id"),
                                    "name" to FieldModel(ObjectModel.single(JsonType.STRING), comment = "user name")
                                )
                            ),
                            comment = "response data"
                        )
                    )
                ),
                headers = listOf(ApiHeader(name = "Content-Type", value = "application/json"))
            )
        ),
        // --- HTTP: array response body ---
        ApiEndpoint(
            name = "List Users",
            folder = "Users",
            metadata = httpMetadata(
                path = "/api/users",
                method = HttpMethod.GET,
                parameters = listOf(
                    ApiParameter(name = "page", binding = ParameterBinding.Query, required = false, description = "Page number"),
                    ApiParameter(name = "size", binding = ParameterBinding.Query, required = false, description = "Page size")
                ),
                responseBody = ObjectModel.Object(
                    mapOf(
                        "code" to FieldModel(ObjectModel.single(JsonType.INT)),
                        "data" to FieldModel(
                            ObjectModel.array(
                                ObjectModel.Object(
                                    mapOf(
                                        "id" to FieldModel(ObjectModel.single(JsonType.LONG)),
                                        "name" to FieldModel(ObjectModel.single(JsonType.STRING))
                                    )
                                )
                            ),
                            comment = "user list"
                        )
                    )
                )
            )
        ),
        // --- HTTP: primitive (Single) body ---
        ApiEndpoint(
            name = "Echo String",
            folder = "Misc",
            metadata = httpMetadata(
                path = "/api/echo/string",
                method = HttpMethod.POST,
                body = ObjectModel.single(JsonType.STRING),
                headers = listOf(ApiHeader(name = "Content-Type", value = "application/json"))
            )
        ),
        // --- HTTP: map body ---
        ApiEndpoint(
            name = "Update Metadata",
            folder = "Misc",
            metadata = httpMetadata(
                path = "/api/resources/{id}/metadata",
                method = HttpMethod.PUT,
                parameters = listOf(
                    ApiParameter(name = "id", binding = ParameterBinding.Path, required = true, description = "Resource ID")
                ),
                body = ObjectModel.Object(
                    mapOf(
                        "metadata" to FieldModel(
                            ObjectModel.MapModel(
                                keyType = ObjectModel.single(JsonType.STRING),
                                valueType = ObjectModel.single(JsonType.INT)
                            ),
                            comment = "key-value metadata"
                        )
                    )
                ),
                headers = listOf(ApiHeader(name = "Content-Type", value = "application/json"))
            )
        ),
        // --- HTTP: array of primitives body ---
        ApiEndpoint(
            name = "Create Post",
            folder = "Misc",
            metadata = httpMetadata(
                path = "/api/posts",
                method = HttpMethod.POST,
                body = ObjectModel.Object(
                    mapOf(
                        "tags" to FieldModel(ObjectModel.array(ObjectModel.single(JsonType.STRING)), comment = "tag list")
                    )
                ),
                headers = listOf(ApiHeader(name = "Content-Type", value = "application/json"))
            )
        ),
        // --- HTTP: field options ---
        ApiEndpoint(
            name = "Update Status",
            folder = "Misc",
            metadata = httpMetadata(
                path = "/api/status",
                method = HttpMethod.PUT,
                body = ObjectModel.Object(
                    mapOf(
                        "status" to FieldModel(
                            ObjectModel.single(JsonType.STRING),
                            comment = "status",
                            options = listOf(
                                FieldOption(value = "active", desc = "Active user"),
                                FieldOption(value = "inactive", desc = "Inactive user")
                            )
                        )
                    )
                ),
                headers = listOf(ApiHeader(name = "Content-Type", value = "application/json"))
            )
        ),
        // --- HTTP: form params ---
        ApiEndpoint(
            name = "Upload File",
            folder = "Misc",
            metadata = httpMetadata(
                path = "/api/upload",
                method = HttpMethod.POST,
                parameters = listOf(
                    ApiParameter(name = "file", type = ParameterType.FILE, binding = ParameterBinding.Form, required = true, description = "File to upload"),
                    ApiParameter(name = "description", binding = ParameterBinding.Form, required = false, description = "File description")
                ),
                headers = listOf(ApiHeader(name = "Content-Type", value = "multipart/form-data"))
            )
        ),
        // --- HTTP: DELETE with no response body ---
        ApiEndpoint(
            name = "Delete User",
            folder = "Users",
            metadata = httpMetadata(
                path = "/api/users/{id}",
                method = HttpMethod.DELETE
            )
        ),
        // --- HTTP: endpoint with description ---
        ApiEndpoint(
            name = "Get Profile",
            folder = "Misc",
            description = "Retrieve the current user's profile information",
            metadata = httpMetadata(
                path = "/api/profile",
                method = HttpMethod.GET
            )
        ),
        // --- HTTP: recursive (cycle) body ---
        run {
            val fields = mutableMapOf<String, FieldModel>()
            val treeNode = ObjectModel.Object(fields)
            fields["name"] = FieldModel(ObjectModel.single(JsonType.STRING), comment = "node name")
            fields["children"] = FieldModel(ObjectModel.array(treeNode), comment = "child nodes")
            ApiEndpoint(
                name = "Get Tree",
                folder = "Misc",
                metadata = httpMetadata(
                    path = "/api/tree",
                    method = HttpMethod.GET,
                    responseBody = treeNode
                )
            )
        },
        // --- gRPC: unary with body + response ---
        ApiEndpoint(
            name = "GetUser",
            folder = "gRPC",
            metadata = GrpcMetadata(
                path = "/user.UserService/GetUser",
                serviceName = "UserService",
                methodName = "GetUser",
                packageName = "user",
                streamingType = GrpcStreamingType.UNARY,
                body = ObjectModel.Object(
                    mapOf(
                        "user_id" to FieldModel(ObjectModel.single(JsonType.STRING), comment = "user ID")
                    )
                ),
                responseBody = ObjectModel.Object(
                    mapOf(
                        "name" to FieldModel(ObjectModel.single(JsonType.STRING), comment = "user name")
                    )
                )
            )
        ),
        // --- gRPC: server streaming ---
        ApiEndpoint(
            name = "ListUsers",
            folder = "gRPC",
            metadata = GrpcMetadata(
                path = "/user.UserService/ListUsers",
                serviceName = "UserService",
                methodName = "ListUsers",
                packageName = "user",
                streamingType = GrpcStreamingType.SERVER_STREAMING
            )
        ),
        // --- gRPC: bidi streaming ---
        ApiEndpoint(
            name = "Chat",
            folder = "gRPC",
            metadata = GrpcMetadata(
                path = "/chat.ChatService/Chat",
                serviceName = "ChatService",
                methodName = "Chat",
                packageName = "chat",
                streamingType = GrpcStreamingType.BIDIRECTIONAL
            )
        ),
        // --- HTTP: endpoint without name (edge case) ---
        ApiEndpoint(
            folder = "Misc",
            metadata = httpMetadata(
                path = "/api/webhook",
                method = HttpMethod.POST
            )
        ),
    )

    @Test
    fun testDefaultTemplateMatchesGolden() {
        val endpoints = comprehensiveEndpoints()
        val model = TemplateModelBuilder.build(endpoints, moduleName = "Test API")
        val actual = MarkdownTemplateRenderer.render(defaultTemplate, model, ctx)

        assertTrue("Rendered output must be non-empty", actual.isNotEmpty())

        val goldenFile = File(GOLDEN_PATH)
        val updateGolden = System.getProperty("update.golden") != null ||
            System.getenv("UPDATE_GOLDEN") != null

        if (updateGolden || !goldenFile.exists()) {
            goldenFile.parentFile.mkdirs()
            goldenFile.writeText(actual)
            println("Golden written at $GOLDEN_PATH (${actual.length} chars)")
            return
        }

        // The renderer emits LF (`\n`) on every platform; the golden is committed
        // with LF. readRaw collapses CRLF→LF so the parity gate is stable on
        // Windows checkouts (where git autocrlf may convert the on-disk golden).
        val expected = ResourceLoader.readRaw(GOLDEN_RESOURCE)
        assertEquals(
            "Default template output must match the committed golden byte-for-byte. " +
                "If this is an intentional change, run with -Dupdate.golden=true to update.",
            expected,
            actual,
        )
    }
}
