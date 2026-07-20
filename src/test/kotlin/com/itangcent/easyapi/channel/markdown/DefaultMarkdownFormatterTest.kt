package com.itangcent.easyapi.channel.markdown

import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.ApiHeader
import com.itangcent.easyapi.core.export.ApiParameter
import com.itangcent.easyapi.core.export.GrpcMetadata
import com.itangcent.easyapi.core.export.GrpcStreamingType
import com.itangcent.easyapi.core.export.HttpMetadata
import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.core.export.ParameterBinding
import com.itangcent.easyapi.core.export.ParameterType
import com.itangcent.easyapi.core.export.grpcMetadata
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.core.export.isGrpc
import com.itangcent.easyapi.core.export.isHttp
import com.itangcent.easyapi.core.export.path
import com.itangcent.easyapi.core.psi.model.FieldModel
import com.itangcent.easyapi.core.psi.model.FieldOption
import com.itangcent.easyapi.core.psi.model.ObjectModel
import com.itangcent.easyapi.core.psi.type.JsonType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DefaultMarkdownFormatterTest {

    private val formatter = DefaultMarkdownFormatter()

    @Test
    fun testFormatSimpleEndpoint() = runBlocking {
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

        val markdown = formatter.format(listOf(endpoint), "User API")

        assertTrue(markdown.contains("# User API"))
        assertTrue(markdown.contains("### Get User"))
        assertTrue(markdown.contains("/api/users/{id}"))
        assertTrue(markdown.contains("GET"))
        assertTrue(markdown.contains("**Path Params:**"))
        assertTrue(markdown.contains("| id |"))
    }

    @Test
    fun testFormatEndpointWithDescription() = runBlocking {
        val endpoint = ApiEndpoint(
            name = "Get User",
            description = "Retrieve user information by ID",
            metadata = httpMetadata(
                path = "/api/users/{id}",
                method = HttpMethod.GET
            )
        )

        val markdown = formatter.format(listOf(endpoint), "User API")

        assertTrue(markdown.contains("Retrieve user information by ID"))
    }

    @Test
    fun testFormatEndpointWithQueryParameters() = runBlocking {
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

        val markdown = formatter.format(listOf(endpoint), "User API")

        assertTrue(markdown.contains("**Query:**"))
        assertTrue(markdown.contains("page"))
        assertTrue(markdown.contains("size"))
        assertTrue(markdown.contains("Page number"))
        assertTrue(markdown.contains("Page size"))
    }

    @Test
    fun testFormatEndpointWithHeaders() = runBlocking {
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

        val markdown = formatter.format(listOf(endpoint), "User API")

        assertTrue(markdown.contains("**Headers:**"))
        assertTrue(markdown.contains("Content-Type"))
        assertTrue(markdown.contains("application/json"))
        assertTrue(markdown.contains("Authorization"))
    }

    @Test
    fun testFormatEndpointWithFolders() = runBlocking {
        val endpoints = listOf(
            ApiEndpoint(
                name = "Get User",
                folder = "Users",
                metadata = httpMetadata(
                    path = "/api/users/{id}",
                    method = HttpMethod.GET
                )
            ),
            ApiEndpoint(
                name = "Create User",
                folder = "Users",
                metadata = httpMetadata(
                    path = "/api/users",
                    method = HttpMethod.POST
                )
            ),
            ApiEndpoint(
                name = "Get Order",
                folder = "Orders",
                metadata = httpMetadata(
                    path = "/api/orders/{id}",
                    method = HttpMethod.GET
                )
            )
        )

        val markdown = formatter.format(endpoints, "API")

        assertTrue(markdown.contains("## Users"))
        assertTrue(markdown.contains("## Orders"))
    }

    @Test
    fun testFormatEndpointWithRequestBody() = runBlocking {
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

        val markdown = formatter.format(listOf(endpoint), "User API")

        assertTrue(markdown.contains("**Request Body:**"))
        assertTrue(markdown.contains("| name | string | user name |"))
        assertTrue(markdown.contains("| age | int | user age |"))
        assertTrue(markdown.contains("**Request Demo:**"))
        assertTrue(markdown.contains("```json"))
    }

    @Test
    fun testFormatEndpointWithResponseBody() = runBlocking {
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

        val markdown = formatter.format(listOf(endpoint), "User API")

        assertTrue("Should contain RESPONSE section", markdown.contains("> RESPONSE"))
        assertTrue("Should contain Body header", markdown.contains("**Body:**"))
        assertTrue("Should contain code field", markdown.contains("| code | int | response code |"))
        assertTrue("Should contain msg field", markdown.contains("| msg | string | message |"))
        assertTrue("Should contain data field", markdown.contains("| data | object | response data |"))
        assertTrue("Should contain nested id field", markdown.contains("&#124;─id | long | user id |"))
        assertTrue("Should contain nested name field", markdown.contains("&#124;─name | string | user name |"))
        assertTrue("Should contain Response Demo", markdown.contains("**Response Demo:**"))
        assertTrue("Should contain json block", markdown.contains("```json"))
    }

    @Test
    fun testFormatEndpointWithArrayResponseBody() = runBlocking {
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

        val markdown = formatter.format(listOf(endpoint), "User API")

        assertTrue(markdown.contains("> RESPONSE"))
        assertTrue(markdown.contains("| data | object[] | user list |"))
    }

    @Test
    fun testFormatEndpointWithNoResponseBody() = runBlocking {
        val endpoint = ApiEndpoint(
            name = "Delete User",
            metadata = httpMetadata(
                path = "/api/users/{id}",
                method = HttpMethod.DELETE
            )
        )

        val markdown = formatter.format(listOf(endpoint), "User API")

        assertFalse(markdown.contains("> RESPONSE"))
        assertFalse(markdown.contains("**Body:**"))
    }

    @Test
    fun testFormatMultipleEndpoints() = runBlocking {
        val endpoints = listOf(
            ApiEndpoint(name = "Get User", metadata = httpMetadata(path = "/api/users/{id}", method = HttpMethod.GET)),
            ApiEndpoint(name = "Create User", metadata = httpMetadata(path = "/api/users", method = HttpMethod.POST)),
            ApiEndpoint(name = "Update User", metadata = httpMetadata(path = "/api/users/{id}", method = HttpMethod.PUT)),
            ApiEndpoint(name = "Delete User", metadata = httpMetadata(path = "/api/users/{id}", method = HttpMethod.DELETE))
        )

        val markdown = formatter.format(endpoints, "User API")

        assertTrue(markdown.contains("Get User"))
        assertTrue(markdown.contains("Create User"))
        assertTrue(markdown.contains("Update User"))
        assertTrue(markdown.contains("Delete User"))
    }

    @Test
    fun testFormatEndpointWithFormParams() = runBlocking {
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

        val markdown = formatter.format(listOf(endpoint), "Upload API")

        assertTrue(markdown.contains("**Form:**"))
        assertTrue(markdown.contains("file"))
        assertTrue(markdown.contains("description"))
    }

    @Test
    fun testFormatCircularReferenceDoesNotStackOverflow() = runBlocking {
        // Simulate a self-referencing type like TreeNode { name: String, children: List<TreeNode> }
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

        // Should complete without OOM or StackOverflow
        val markdown = formatter.format(listOf(endpoint), "Tree API")

        assertTrue(markdown.contains("name"))
        assertTrue(markdown.contains("children"))
        // Should contain nested fields (maxVisits=2 allows one level of recursion)
        assertTrue("Should contain nested name field", markdown.contains("&#124;─name"))
    }

    @Test
    fun testFormatDirectCircularObjectReference() = runBlocking {
        // Object A has a field of type Object A (direct self-reference)
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

        val markdown = formatter.format(listOf(endpoint), "Node API")

        assertTrue(markdown.contains("| id |"))
        assertTrue(markdown.contains("| parent |"))
        // The nested parent.id should appear (first recursion allowed by maxVisits=2)
        assertTrue("Should contain nested id", markdown.contains("&#124;─id"))
    }

    @Test
    fun testFormatMutualCircularReference() = runBlocking {
        // Object A references Object B, Object B references Object A
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

        val markdown = formatter.format(listOf(endpoint), "Mutual API")

        assertTrue(markdown.contains("name"))
        assertTrue(markdown.contains("refB"))
        assertTrue(markdown.contains("value"))
        assertTrue(markdown.contains("refA"))
    }

    @Test
    fun testFormatNonCircularDeepNesting() = runBlocking {
        // Non-circular deep nesting should render all levels
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

        val markdown = formatter.format(listOf(endpoint), "Nested API")

        assertTrue(markdown.contains("middle"))
        assertTrue(markdown.contains("inner"))
        assertTrue(markdown.contains("value"))
    }

    // ==================== gRPC endpoint tests ====================

    @Test
    fun testFormatGrpcEndpoint() = runBlocking {
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

        val markdown = formatter.format(listOf(endpoint), "gRPC API")

        assertTrue(markdown.contains("# gRPC API"))
        assertTrue(markdown.contains("### GetUser"))
        assertTrue(markdown.contains("**Protocol:** gRPC"))
        assertTrue(markdown.contains("**Service:** UserService"))
        assertTrue(markdown.contains("GetUser"))
        assertTrue(markdown.contains("**Streaming:** UNARY"))
        assertTrue(markdown.contains("**Full Path:** /user.UserService/GetUser"))
    }

    @Test
    fun testFormatGrpcEndpointWithDescription() = runBlocking {
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

        val markdown = formatter.format(listOf(endpoint), "gRPC API")

        assertTrue(markdown.contains("Retrieves a user by ID"))
    }

    @Test
    fun testFormatGrpcEndpointWithRequestBody() = runBlocking {
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

        val markdown = formatter.format(listOf(endpoint), "gRPC API")

        assertTrue(markdown.contains("> REQUEST"))
        assertTrue(markdown.contains("**Request Body:**"))
        assertTrue(markdown.contains("user_id"))
        assertTrue(markdown.contains("**Request Demo:**"))
        assertTrue(markdown.contains("```json"))
    }

    @Test
    fun testFormatGrpcEndpointWithResponseBody() = runBlocking {
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

        val markdown = formatter.format(listOf(endpoint), "gRPC API")

        assertTrue(markdown.contains("> RESPONSE"))
        assertTrue(markdown.contains("**Body:**"))
        assertTrue(markdown.contains("name"))
    }

    @Test
    fun testFormatGrpcServerStreaming() = runBlocking {
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

        val markdown = formatter.format(listOf(endpoint), "gRPC API")

        assertTrue(markdown.contains("**Streaming:** SERVER_STREAMING"))
    }

    @Test
    fun testFormatGrpcBidiStreaming() = runBlocking {
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

        val markdown = formatter.format(listOf(endpoint), "gRPC API")

        assertTrue(markdown.contains("**Streaming:** BIDIRECTIONAL"))
    }

    // ==================== Endpoint without name ====================

    @Test
    fun testFormatEndpointWithoutName() = runBlocking {
        val endpoint = ApiEndpoint(
            metadata = httpMetadata(
                path = "/api/users",
                method = HttpMethod.POST
            )
        )

        val markdown = formatter.format(listOf(endpoint), "API")

        assertTrue(markdown.contains("POST /api/users"))
    }

    // ==================== Endpoint with blank description ====================

    @Test
    fun testFormatEndpointWithBlankDescription() = runBlocking {
        val endpoint = ApiEndpoint(
            name = "Get User",
            description = "   ",
            metadata = httpMetadata(
                path = "/api/users/{id}",
                method = HttpMethod.GET
            )
        )

        val markdown = formatter.format(listOf(endpoint), "API")

        assertFalse(markdown.contains("**Desc:**"))
    }

    // ==================== Endpoint with all param types ====================

    @Test
    fun testFormatEndpointWithAllParamTypes() = runBlocking {
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

        val markdown = formatter.format(listOf(endpoint), "API")

        assertTrue(markdown.contains("**Path Params:**"))
        assertTrue(markdown.contains("**Query:**"))
        assertTrue(markdown.contains("**Headers:**"))
        assertTrue(markdown.contains("**Form:**"))
    }

    // ==================== Mixed HTTP and gRPC endpoints ====================

    @Test
    fun testFormatMixedHttpAndGrpcEndpoints() = runBlocking {
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

        val markdown = formatter.format(endpoints, "Mixed API")

        assertTrue(markdown.contains("### Get User"))
        assertTrue(markdown.contains("### GetUserGrpc"))
        assertTrue(markdown.contains("**Protocol:** gRPC"))
    }

    // ==================== Map type in body ====================

    @Test
    fun testFormatEndpointWithMapBody() = runBlocking {
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

        val markdown = formatter.format(listOf(endpoint), "API")

        assertTrue(markdown.contains("metadata"))
        assertTrue(markdown.contains("map"))
    }

    // ==================== Array of primitives ====================

    @Test
    fun testFormatEndpointWithArrayOfPrimitives() = runBlocking {
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

        val markdown = formatter.format(listOf(endpoint), "API")

        assertTrue(markdown.contains("tags"))
        assertTrue(markdown.contains("string[]"))
    }

    // ==================== FieldModel with options ====================

    @Test
    fun testFormatEndpointWithFieldOptions() = runBlocking {
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

        val markdown = formatter.format(listOf(endpoint), "API")

        assertTrue(markdown.contains("active"))
        assertTrue(markdown.contains("inactive"))
    }

    // ==================== GrpcMetadata properties ====================

    @Test
    fun testGrpcMetadataProtocol() {
        val meta = GrpcMetadata(
            path = "/test.Service/Method",
            serviceName = "Service",
            methodName = "Method",
            packageName = "test",
            streamingType = GrpcStreamingType.UNARY
        )
        assertEquals("gRPC", meta.protocol)
    }

    @Test
    fun testGrpcStreamingTypeValues() {
        assertEquals(4, GrpcStreamingType.values().size)
        assertNotNull(GrpcStreamingType.UNARY)
        assertNotNull(GrpcStreamingType.SERVER_STREAMING)
        assertNotNull(GrpcStreamingType.CLIENT_STREAMING)
        assertNotNull(GrpcStreamingType.BIDIRECTIONAL)
    }

    // ==================== ApiEndpoint gRPC helpers ====================

    @Test
    fun testApiEndpointIsGrpc() {
        val endpoint = ApiEndpoint(
            metadata = GrpcMetadata(
                path = "/test.Service/Method",
                serviceName = "Service",
                methodName = "Method",
                packageName = "test",
                streamingType = GrpcStreamingType.UNARY
            )
        )
        assertTrue(endpoint.isGrpc)
        assertFalse(endpoint.isHttp)
    }

    @Test
    fun testApiEndpointGrpcMetadata() {
        val grpcMeta = GrpcMetadata(
            path = "/test.Service/Method",
            serviceName = "Service",
            methodName = "Method",
            packageName = "test",
            streamingType = GrpcStreamingType.UNARY
        )
        val endpoint = ApiEndpoint(metadata = grpcMeta)
        assertNotNull(endpoint.grpcMetadata)
        assertEquals("Service", endpoint.grpcMetadata?.serviceName)
    }

    @Test
    fun testApiEndpointPathForGrpc() {
        val endpoint = ApiEndpoint(
            metadata = GrpcMetadata(
                path = "/test.Service/Method",
                serviceName = "Service",
                methodName = "Method",
                packageName = "test",
                streamingType = GrpcStreamingType.UNARY
            )
        )
        assertEquals("/test.Service/Method", endpoint.path)
    }
}
