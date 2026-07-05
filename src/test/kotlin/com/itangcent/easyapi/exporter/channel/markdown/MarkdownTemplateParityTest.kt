package com.itangcent.easyapi.exporter.channel.markdown

import com.itangcent.easyapi.exporter.channel.markdown.template.DefaultMarkdownTemplate
import com.itangcent.easyapi.exporter.channel.markdown.template.MarkdownTemplateRenderer
import com.itangcent.easyapi.exporter.channel.markdown.template.RenderContext
import com.itangcent.easyapi.exporter.channel.markdown.template.TemplateModelBuilder
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.ApiHeader
import com.itangcent.easyapi.exporter.model.ApiParameter
import com.itangcent.easyapi.exporter.model.GrpcMetadata
import com.itangcent.easyapi.exporter.model.GrpcStreamingType
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.ParameterBinding
import com.itangcent.easyapi.exporter.model.ParameterType
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.psi.model.FieldModel
import com.itangcent.easyapi.psi.model.FieldOption
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.type.JsonType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * The load-bearing parity gate .
 *
 * Asserts **full-string** `assertEquals` that the default template rendered via
 * [MarkdownTemplateRenderer] reproduces [DefaultMarkdownFormatter] output byte-for-byte,
 * for every shape exercised by [DefaultMarkdownFormatterTest].
 *
 * The default template does not reference any `meta.*` built-ins (it must stay byte-identical
 * to the legacy formatter), so the [RenderContext] values are irrelevant to the output — fixed
 * values are used for determinism only.
 *
 * Pure JUnit: no `Project`, no PSI/VFS.
 */
class MarkdownTemplateParityTest {

    private val oldFormatter = DefaultMarkdownFormatter(outputDemo = true)
    private val oldNoDemoFormatter = DefaultMarkdownFormatter(outputDemo = false)

    private val ctx = RenderContext(
        clock = Clock.fixed(Instant.parse("2026-03-15T10:30:45Z"), ZoneId.of("UTC")),
        zone = ZoneId.of("UTC"),
        username = "testuser",
        projectName = "test-project",
        pluginVersion = "1.0.0-test",
    )

    private val defaultTemplate = DefaultMarkdownTemplate.get()

    /**
     * Runs both the old formatter and the new template renderer on the same input and
     * asserts byte-for-byte equality.
     */
    private fun assertParity(
        endpoints: List<ApiEndpoint>,
        moduleName: String,
        outputDemo: Boolean = true,
    ) {
        val expected = runBlocking {
            (if (outputDemo) oldFormatter else oldNoDemoFormatter).format(endpoints, moduleName)
        }
        assertTrue("Sanity: old formatter must produce non-empty output", expected.isNotEmpty())

        val model = TemplateModelBuilder.build(endpoints, outputDemo, moduleName)
        val actual = MarkdownTemplateRenderer.render(defaultTemplate, model, ctx)

        assertEquals(
            "Parity mismatch (moduleName=$moduleName outputDemo=$outputDemo)",
            expected,
            actual,
        )
    }

    // ---------- HTTP: basic, params, headers, form ----------

    @Test
    fun testSimpleEndpoint() {
        val endpoint = ApiEndpoint(
            name = "Get User",
            metadata = httpMetadata(
                path = "/api/users/{id}",
                method = HttpMethod.GET,
                parameters = listOf(
                    ApiParameter(name = "id", binding = ParameterBinding.Path, required = true, description = "User ID")
                )
            )
        )
        assertParity(listOf(endpoint), "User API")
    }

    @Test
    fun testEndpointWithDescription() {
        val endpoint = ApiEndpoint(
            name = "Get User",
            description = "Retrieve user information by ID",
            metadata = httpMetadata(
                path = "/api/users/{id}",
                method = HttpMethod.GET
            )
        )
        assertParity(listOf(endpoint), "User API")
    }

    @Test
    fun testEndpointWithQueryParameters() {
        val endpoint = ApiEndpoint(
            name = "List Users",
            metadata = httpMetadata(
                path = "/api/users",
                method = HttpMethod.GET,
                parameters = listOf(
                    ApiParameter(name = "page", binding = ParameterBinding.Query, required = false, description = "Page number"),
                    ApiParameter(name = "size", binding = ParameterBinding.Query, required = false, description = "Page size")
                )
            )
        )
        assertParity(listOf(endpoint), "User API")
    }

    @Test
    fun testEndpointWithHeaders() {
        val endpoint = ApiEndpoint(
            name = "Create User",
            metadata = httpMetadata(
                path = "/api/users",
                method = HttpMethod.POST,
                headers = listOf(
                    ApiHeader(name = "Content-Type", value = "application/json"),
                    ApiHeader(name = "Authorization", value = "Bearer token", required = true)
                )
            )
        )
        assertParity(listOf(endpoint), "User API")
    }

    @Test
    fun testEndpointWithFolders() {
        val endpoints = listOf(
            ApiEndpoint(
                name = "Get User",
                folder = "Users",
                metadata = httpMetadata(path = "/api/users/{id}", method = HttpMethod.GET)
            ),
            ApiEndpoint(
                name = "Create User",
                folder = "Users",
                metadata = httpMetadata(path = "/api/users", method = HttpMethod.POST)
            ),
            ApiEndpoint(
                name = "Get Order",
                folder = "Orders",
                metadata = httpMetadata(path = "/api/orders/{id}", method = HttpMethod.GET)
            )
        )
        assertParity(endpoints, "API")
    }

    @Test
    fun testEndpointWithRequestBody() {
        val bodyModel = ObjectModel.Object(
            mapOf(
                "name" to FieldModel(ObjectModel.single(JsonType.STRING), comment = "user name"),
                "age" to FieldModel(ObjectModel.single(JsonType.INT), comment = "user age")
            )
        )
        val endpoint = ApiEndpoint(
            name = "Create User",
            metadata = httpMetadata(
                path = "/api/users",
                method = HttpMethod.POST,
                body = bodyModel,
                headers = listOf(ApiHeader(name = "Content-Type", value = "application/json"))
            )
        )
        assertParity(listOf(endpoint), "User API")
    }

    @Test
    fun testEndpointWithResponseBody() {
        val responseModel = ObjectModel.Object(
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
        )
        val endpoint = ApiEndpoint(
            name = "Get User",
            metadata = httpMetadata(
                path = "/api/users/{id}",
                method = HttpMethod.GET,
                responseBody = responseModel
            )
        )
        assertParity(listOf(endpoint), "User API")
    }

    @Test
    fun testEndpointWithArrayResponseBody() {
        val itemModel = ObjectModel.Object(
            mapOf(
                "id" to FieldModel(ObjectModel.single(JsonType.LONG)),
                "name" to FieldModel(ObjectModel.single(JsonType.STRING))
            )
        )
        val responseModel = ObjectModel.Object(
            mapOf(
                "code" to FieldModel(ObjectModel.single(JsonType.INT)),
                "data" to FieldModel(ObjectModel.array(itemModel), comment = "user list")
            )
        )
        val endpoint = ApiEndpoint(
            name = "List Users",
            metadata = httpMetadata(
                path = "/api/users",
                method = HttpMethod.GET,
                responseBody = responseModel
            )
        )
        assertParity(listOf(endpoint), "User API")
    }

    @Test
    fun testEndpointWithNoResponseBody() {
        val endpoint = ApiEndpoint(
            name = "Delete User",
            metadata = httpMetadata(
                path = "/api/users/{id}",
                method = HttpMethod.DELETE
            )
        )
        assertParity(listOf(endpoint), "User API")
    }

    @Test
    fun testMultipleEndpoints() {
        val endpoints = listOf(
            ApiEndpoint(name = "Get User", metadata = httpMetadata(path = "/api/users/{id}", method = HttpMethod.GET)),
            ApiEndpoint(name = "Create User", metadata = httpMetadata(path = "/api/users", method = HttpMethod.POST)),
            ApiEndpoint(name = "Update User", metadata = httpMetadata(path = "/api/users/{id}", method = HttpMethod.PUT)),
            ApiEndpoint(name = "Delete User", metadata = httpMetadata(path = "/api/users/{id}", method = HttpMethod.DELETE))
        )
        assertParity(endpoints, "User API")
    }

    @Test
    fun testEndpointWithFormParams() {
        val endpoint = ApiEndpoint(
            name = "Upload File",
            metadata = httpMetadata(
                path = "/api/upload",
                method = HttpMethod.POST,
                parameters = listOf(
                    ApiParameter(name = "file", type = ParameterType.FILE, binding = ParameterBinding.Form, required = true, description = "File to upload"),
                    ApiParameter(name = "description", binding = ParameterBinding.Form, required = false, description = "File description")
                )
            )
        )
        assertParity(listOf(endpoint), "Upload API")
    }

    // ---------- Cycle safety ----------

    @Test
    fun testCircularReference() {
        val fields = mutableMapOf<String, FieldModel>()
        val treeNode = ObjectModel.Object(fields)
        fields["name"] = FieldModel(ObjectModel.single(JsonType.STRING), comment = "node name")
        fields["children"] = FieldModel(ObjectModel.array(treeNode), comment = "child nodes")

        val endpoint = ApiEndpoint(
            name = "Get Tree",
            metadata = httpMetadata(
                path = "/api/tree",
                method = HttpMethod.GET,
                responseBody = treeNode
            )
        )
        assertParity(listOf(endpoint), "Tree API")
    }

    @Test
    fun testDirectCircularReference() {
        val fields = mutableMapOf<String, FieldModel>()
        val selfRef = ObjectModel.Object(fields)
        fields["id"] = FieldModel(ObjectModel.single(JsonType.INT), comment = "id")
        fields["parent"] = FieldModel(selfRef, comment = "parent reference")

        val endpoint = ApiEndpoint(
            name = "Get Node",
            metadata = httpMetadata(
                path = "/api/node",
                method = HttpMethod.GET,
                responseBody = selfRef
            )
        )
        assertParity(listOf(endpoint), "Node API")
    }

    @Test
    fun testMutualCircularReference() {
        val fieldsA = mutableMapOf<String, FieldModel>()
        val fieldsB = mutableMapOf<String, FieldModel>()
        val objectA = ObjectModel.Object(fieldsA)
        val objectB = ObjectModel.Object(fieldsB)
        fieldsA["name"] = FieldModel(ObjectModel.single(JsonType.STRING))
        fieldsA["refB"] = FieldModel(objectB, comment = "reference to B")
        fieldsB["value"] = FieldModel(ObjectModel.single(JsonType.INT))
        fieldsB["refA"] = FieldModel(objectA, comment = "reference to A")

        val endpoint = ApiEndpoint(
            name = "Get Mutual",
            metadata = httpMetadata(
                path = "/api/mutual",
                method = HttpMethod.GET,
                responseBody = objectA
            )
        )
        assertParity(listOf(endpoint), "Mutual API")
    }

    @Test
    fun testNonCircularDeepNesting() {
        val inner = ObjectModel.Object(
            mapOf("value" to FieldModel(ObjectModel.single(JsonType.STRING), comment = "inner value"))
        )
        val middle = ObjectModel.Object(
            mapOf("inner" to FieldModel(inner, comment = "inner object"))
        )
        val outer = ObjectModel.Object(
            mapOf("middle" to FieldModel(middle, comment = "middle object"))
        )

        val endpoint = ApiEndpoint(
            name = "Get Nested",
            metadata = httpMetadata(
                path = "/api/nested",
                method = HttpMethod.GET,
                responseBody = outer
            )
        )
        assertParity(listOf(endpoint), "Nested API")
    }

    // ---------- gRPC ----------

    @Test
    fun testGrpcEndpoint() {
        val endpoint = ApiEndpoint(
            name = "GetUser",
            metadata = GrpcMetadata(
                path = "/user.UserService/GetUser",
                serviceName = "UserService",
                methodName = "GetUser",
                packageName = "user",
                streamingType = GrpcStreamingType.UNARY
            )
        )
        assertParity(listOf(endpoint), "gRPC API")
    }

    @Test
    fun testGrpcEndpointWithDescription() {
        val endpoint = ApiEndpoint(
            name = "GetUser",
            description = "Retrieves a user by ID",
            metadata = GrpcMetadata(
                path = "/user.UserService/GetUser",
                serviceName = "UserService",
                methodName = "GetUser",
                packageName = "user",
                streamingType = GrpcStreamingType.UNARY
            )
        )
        assertParity(listOf(endpoint), "gRPC API")
    }

    @Test
    fun testGrpcEndpointWithRequestBody() {
        val bodyModel = ObjectModel.Object(
            mapOf(
                "user_id" to FieldModel(ObjectModel.single(JsonType.STRING), comment = "user ID")
            )
        )
        val endpoint = ApiEndpoint(
            name = "GetUser",
            metadata = GrpcMetadata(
                path = "/user.UserService/GetUser",
                serviceName = "UserService",
                methodName = "GetUser",
                packageName = "user",
                streamingType = GrpcStreamingType.UNARY,
                body = bodyModel
            )
        )
        assertParity(listOf(endpoint), "gRPC API")
    }

    @Test
    fun testGrpcEndpointWithResponseBody() {
        val responseModel = ObjectModel.Object(
            mapOf(
                "name" to FieldModel(ObjectModel.single(JsonType.STRING), comment = "user name")
            )
        )
        val endpoint = ApiEndpoint(
            name = "GetUser",
            metadata = GrpcMetadata(
                path = "/user.UserService/GetUser",
                serviceName = "UserService",
                methodName = "GetUser",
                packageName = "user",
                streamingType = GrpcStreamingType.UNARY,
                responseBody = responseModel
            )
        )
        assertParity(listOf(endpoint), "gRPC API")
    }

    @Test
    fun testGrpcServerStreaming() {
        val endpoint = ApiEndpoint(
            name = "ListUsers",
            metadata = GrpcMetadata(
                path = "/user.UserService/ListUsers",
                serviceName = "UserService",
                methodName = "ListUsers",
                packageName = "user",
                streamingType = GrpcStreamingType.SERVER_STREAMING
            )
        )
        assertParity(listOf(endpoint), "gRPC API")
    }

    @Test
    fun testGrpcBidiStreaming() {
        val endpoint = ApiEndpoint(
            name = "Chat",
            metadata = GrpcMetadata(
                path = "/chat.ChatService/Chat",
                serviceName = "ChatService",
                methodName = "Chat",
                packageName = "chat",
                streamingType = GrpcStreamingType.BIDIRECTIONAL
            )
        )
        assertParity(listOf(endpoint), "gRPC API")
    }

    // ---------- No-demo mode ----------

    @Test
    fun testNoDemoMode() {
        val bodyModel = ObjectModel.Object(
            mapOf(
                "name" to FieldModel(ObjectModel.single(JsonType.STRING), comment = "user name")
            )
        )
        val endpoint = ApiEndpoint(
            name = "Create User",
            metadata = httpMetadata(
                path = "/api/users",
                method = HttpMethod.POST,
                body = bodyModel,
                headers = listOf(ApiHeader(name = "Content-Type", value = "application/json"))
            )
        )
        assertParity(listOf(endpoint), "User API", outputDemo = false)
    }

    // ---------- Edge cases ----------

    @Test
    fun testEndpointWithoutName() {
        val endpoint = ApiEndpoint(
            metadata = httpMetadata(
                path = "/api/users",
                method = HttpMethod.POST
            )
        )
        assertParity(listOf(endpoint), "API")
    }

    @Test
    fun testEndpointWithBlankDescription() {
        val endpoint = ApiEndpoint(
            name = "Get User",
            description = "   ",
            metadata = httpMetadata(
                path = "/api/users/{id}",
                method = HttpMethod.GET
            )
        )
        assertParity(listOf(endpoint), "API")
    }

    @Test
    fun testEndpointWithAllParamTypes() {
        val endpoint = ApiEndpoint(
            name = "Update User",
            metadata = httpMetadata(
                path = "/api/users/{id}",
                method = HttpMethod.PUT,
                parameters = listOf(
                    ApiParameter(name = "id", binding = ParameterBinding.Path, required = true, description = "User ID"),
                    ApiParameter(name = "page", binding = ParameterBinding.Query, required = false, description = "Page number"),
                    ApiParameter(name = "X-Token", binding = ParameterBinding.Header, required = true, description = "Auth token"),
                    ApiParameter(name = "avatar", type = ParameterType.FILE, binding = ParameterBinding.Form, required = false, description = "Avatar file")
                ),
                headers = listOf(
                    ApiHeader(name = "Content-Type", value = "multipart/form-data")
                )
            )
        )
        assertParity(listOf(endpoint), "API")
    }

    @Test
    fun testMixedHttpAndGrpcEndpoints() {
        val endpoints = listOf(
            ApiEndpoint(
                name = "Get User",
                metadata = httpMetadata(path = "/api/users/{id}", method = HttpMethod.GET)
            ),
            ApiEndpoint(
                name = "GetUserGrpc",
                metadata = GrpcMetadata(
                    path = "/user.UserService/GetUser",
                    serviceName = "UserService",
                    methodName = "GetUser",
                    packageName = "user",
                    streamingType = GrpcStreamingType.UNARY
                )
            )
        )
        assertParity(endpoints, "Mixed API")
    }

    @Test
    fun testEndpointWithMapBody() {
        val mapModel = ObjectModel.MapModel(
            keyType = ObjectModel.single(JsonType.STRING),
            valueType = ObjectModel.single(JsonType.INT)
        )
        val bodyModel = ObjectModel.Object(
            mapOf(
                "metadata" to FieldModel(mapModel, comment = "key-value metadata")
            )
        )
        val endpoint = ApiEndpoint(
            name = "Create Resource",
            metadata = httpMetadata(
                path = "/api/resources",
                method = HttpMethod.POST,
                body = bodyModel,
                headers = listOf(ApiHeader(name = "Content-Type", value = "application/json"))
            )
        )
        assertParity(listOf(endpoint), "API")
    }

    @Test
    fun testEndpointWithArrayOfPrimitives() {
        val bodyModel = ObjectModel.Object(
            mapOf(
                "tags" to FieldModel(ObjectModel.array(ObjectModel.single(JsonType.STRING)), comment = "tag list")
            )
        )
        val endpoint = ApiEndpoint(
            name = "Create Post",
            metadata = httpMetadata(
                path = "/api/posts",
                method = HttpMethod.POST,
                body = bodyModel,
                headers = listOf(ApiHeader(name = "Content-Type", value = "application/json"))
            )
        )
        assertParity(listOf(endpoint), "API")
    }

    @Test
    fun testEndpointWithFieldOptions() {
        val bodyModel = ObjectModel.Object(
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
        )
        val endpoint = ApiEndpoint(
            name = "Update Status",
            metadata = httpMetadata(
                path = "/api/status",
                method = HttpMethod.PUT,
                body = bodyModel,
                headers = listOf(ApiHeader(name = "Content-Type", value = "application/json"))
            )
        )
        assertParity(listOf(endpoint), "API")
    }
}
