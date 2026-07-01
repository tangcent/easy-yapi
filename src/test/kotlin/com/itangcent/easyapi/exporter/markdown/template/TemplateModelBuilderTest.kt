package com.itangcent.easyapi.exporter.markdown.template

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
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.model.ObjectModelJsonConverter
import com.itangcent.easyapi.psi.type.JsonType
import org.junit.Assert.*
import org.junit.Test

/**
 * Pins the [TemplateModelBuilder] contract — the data transformation from
 * [ApiEndpoint] / [ObjectModel] into the pure-data [TemplateModel] that templates
 * consume. Pure JUnit (Pattern A): no `Project`, no PSI/VFS (NFR-4). Built test-first
 * (red); the builder is implemented in [TemplateModelBuilder] only after this test
 * exists.
 *
 * Representative inputs are lifted from `DefaultMarkdownFormatterTest` so the model
 * mirrors exactly what the formatter walks today.
 */
class TemplateModelBuilderTest {

    // ---- Grouping & top-level ----

    @Test
    fun testGroupNameAndEndpointCount() {
        val endpoints = listOf(
            ApiEndpoint(
                name = "Get User",
                folder = "Users",
                metadata = httpMetadata(path = "/api/users/{id}", method = HttpMethod.GET)
            ),
            ApiEndpoint(
                name = "Get Order",
                folder = "Orders",
                metadata = httpMetadata(path = "/api/orders/{id}", method = HttpMethod.GET)
            )
        )

        val model = TemplateModelBuilder.build(endpoints, outputDemo = true, moduleName = "API")

        assertEquals("API", model.moduleName)
        assertEquals(2, model.endpointCount)
        assertEquals(2, model.groups.size)
        assertEquals("Users", model.groups[0].folder)
        assertEquals(1, model.groups[0].endpoints.size)
        assertEquals("Orders", model.groups[1].folder)
    }

    @Test
    fun testEndpointsWithoutFolderFormAnEmptyFolderGroup() {
        val endpoint = ApiEndpoint(
            name = "Get User",
            metadata = httpMetadata(path = "/api/users", method = HttpMethod.GET)
        )

        val model = TemplateModelBuilder.build(listOf(endpoint), outputDemo = true, moduleName = "API")

        assertEquals(1, model.groups.size)
        assertEquals("", model.groups[0].folder)
        assertEquals(1, model.groups[0].endpoints.size)
    }

    @Test
    fun testFolderGroupingPreservesInputOrder() {
        val endpoints = listOf(
            ApiEndpoint(name = "B", folder = "B", metadata = httpMetadata(path = "/b", method = HttpMethod.GET)),
            ApiEndpoint(name = "A", folder = "A", metadata = httpMetadata(path = "/a", method = HttpMethod.GET)),
            ApiEndpoint(name = "B2", folder = "B", metadata = httpMetadata(path = "/b2", method = HttpMethod.GET))
        )

        val model = TemplateModelBuilder.build(endpoints, outputDemo = true, moduleName = "API")

        // groupBy preserves first-appearance order of keys
        assertEquals(listOf("B", "A"), model.groups.map { it.folder })
        assertEquals(listOf("B", "B2"), model.groups[0].endpoints.map { it.name })
    }

    // ---- HTTP view: params, headers, hasRequestContent ----

    @Test
    fun testHttpPathParamBucketingAndHasRequestContentTrue() {
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

        val model = TemplateModelBuilder.build(listOf(endpoint), outputDemo = true, moduleName = "API")
        val http = model.groups[0].endpoints[0].http!!

        assertEquals("HTTP", model.groups[0].endpoints[0].protocol)
        assertEquals("/api/users/{id}", model.groups[0].endpoints[0].path)
        assertEquals("GET", model.groups[0].endpoints[0].method)
        assertEquals(1, http.pathParams.size)
        assertEquals("id", http.pathParams[0].name)
        assertTrue(http.pathParams[0].required)
        assertEquals("User ID", http.pathParams[0].description)
        assertEquals("", http.pathParams[0].defaultValue) // null → ""
        assertTrue("hasRequestContent must be true with a path param", http.hasRequestContent)
    }

    @Test
    fun testHttpEmptyRequestHasRequestContentFalse() {
        val endpoint = ApiEndpoint(
            name = "Get Nothing",
            metadata = httpMetadata(path = "/api/nothing", method = HttpMethod.GET)
        )

        val model = TemplateModelBuilder.build(listOf(endpoint), outputDemo = true, moduleName = "API")
        val http = model.groups[0].endpoints[0].http!!

        assertTrue(http.pathParams.isEmpty())
        assertTrue(http.queryParams.isEmpty())
        assertTrue(http.formParams.isEmpty())
        assertTrue(http.headers.isEmpty())
        assertNull(http.body)
        assertFalse("hasRequestContent must be false for an empty request", http.hasRequestContent)
    }

    @Test
    fun testHttpQueryFormHeaderBucketting() {
        val endpoint = ApiEndpoint(
            name = "Upload",
            metadata = httpMetadata(
                path = "/api/upload",
                method = HttpMethod.POST,
                parameters = listOf(
                    ApiParameter(name = "page", binding = ParameterBinding.Query, description = "page"),
                    ApiParameter(name = "file", type = ParameterType.FILE, binding = ParameterBinding.Form, required = true, description = "file"),
                    ApiParameter(name = "ignored", binding = ParameterBinding.Ignored)
                ),
                headers = listOf(ApiHeader(name = "Authorization", value = "Bearer token", required = true))
            )
        )

        val model = TemplateModelBuilder.build(listOf(endpoint), outputDemo = true, moduleName = "API")
        val http = model.groups[0].endpoints[0].http!!

        assertEquals(listOf("page"), http.queryParams.map { it.name })
        assertEquals(listOf("file"), http.formParams.map { it.name })
        assertEquals("file", http.formParams[0].type) // ParameterType.FILE.name.lowercase() == "file"
        assertEquals(listOf("Authorization"), http.headers.map { it.name })
        assertEquals("Bearer token", http.headers[0].value)
        assertTrue(http.headers[0].required)
    }

    @Test
    fun testParamNullDefaultsAndDescriptionRenderAsEmptyString() {
        val endpoint = ApiEndpoint(
            name = "Get User",
            metadata = httpMetadata(
                path = "/api/users/{id}",
                method = HttpMethod.GET,
                parameters = listOf(
                    ApiParameter(name = "id", binding = ParameterBinding.Path, defaultValue = null, description = null)
                )
            )
        )

        val model = TemplateModelBuilder.build(listOf(endpoint), outputDemo = true, moduleName = "API")
        val p = model.groups[0].endpoints[0].http!!.pathParams[0]

        assertEquals("", p.defaultValue)
        assertEquals("", p.description)
    }

    // ---- Body: rows, demo, outputDemo ----

    @Test
    fun testBodyRowsSimpleObject() {
        val body = ObjectModel.Object(
            mapOf(
                "name" to FieldModel(ObjectModel.single(JsonType.STRING), comment = "user name"),
                "age" to FieldModel(ObjectModel.single(JsonType.INT), comment = "user age")
            )
        )
        val endpoint = ApiEndpoint(
            name = "Create User",
            metadata = httpMetadata(path = "/api/users", method = HttpMethod.POST, body = body)
        )

        val model = TemplateModelBuilder.build(listOf(endpoint), outputDemo = true, moduleName = "API")
        val bodyView = model.groups[0].endpoints[0].http!!.body!!

        assertEquals(2, bodyView.rows.size)
        assertEquals("name", bodyView.rows[0].name)
        assertEquals("string", bodyView.rows[0].type)
        assertEquals("user name", bodyView.rows[0].desc)
        assertEquals("age", bodyView.rows[1].name)
        assertEquals("int", bodyView.rows[1].type)
        assertEquals("user age", bodyView.rows[1].desc)
    }

    @Test
    fun testBodyRowsNestedObjectHasIndentPrefix() {
        val inner = ObjectModel.Object(
            mapOf("id" to FieldModel(ObjectModel.single(JsonType.LONG), comment = "user id"))
        )
        val body = ObjectModel.Object(
            mapOf("data" to FieldModel(inner, comment = "response data"))
        )
        val endpoint = ApiEndpoint(
            name = "Get User",
            metadata = httpMetadata(path = "/api/users/{id}", method = HttpMethod.GET, responseBody = body)
        )

        val model = TemplateModelBuilder.build(listOf(endpoint), outputDemo = true, moduleName = "API")
        val bodyView = model.groups[0].endpoints[0].http!!.response!!

        // top-level row has no indent
        assertEquals("data", bodyView.rows[0].name)
        assertEquals("object", bodyView.rows[0].type)
        assertEquals("response data", bodyView.rows[0].desc)
        // nested row has the indent prefix
        assertEquals("&ensp;&ensp;&#124;─id", bodyView.rows[1].name)
        assertEquals("long", bodyView.rows[1].type)
        assertEquals("user id", bodyView.rows[1].desc)
    }

    @Test
    fun testBodyRowsArrayTypeIsItemPlusBrackets() {
        val itemModel = ObjectModel.Object(
            mapOf(
                "id" to FieldModel(ObjectModel.single(JsonType.LONG)),
                "name" to FieldModel(ObjectModel.single(JsonType.STRING))
            )
        )
        val body = ObjectModel.Object(
            mapOf("data" to FieldModel(ObjectModel.array(itemModel), comment = "user list"))
        )
        val endpoint = ApiEndpoint(
            name = "List Users",
            metadata = httpMetadata(path = "/api/users", method = HttpMethod.GET, responseBody = body)
        )

        val model = TemplateModelBuilder.build(listOf(endpoint), outputDemo = true, moduleName = "API")
        val row = model.groups[0].endpoints[0].http!!.response!!.rows[0]

        assertEquals("data", row.name)
        assertEquals("object[]", row.type)
        assertEquals("user list", row.desc)
    }

    @Test
    fun testBodyDemoPresentWhenOutputDemoTrue() {
        val body = ObjectModel.Object(
            mapOf("name" to FieldModel(ObjectModel.single(JsonType.STRING), comment = "user name"))
        )
        val endpoint = ApiEndpoint(
            name = "Create User",
            metadata = httpMetadata(path = "/api/users", method = HttpMethod.POST, body = body)
        )

        val model = TemplateModelBuilder.build(listOf(endpoint), outputDemo = true, moduleName = "API")
        val demo = model.groups[0].endpoints[0].http!!.body!!.demo

        assertNotNull("demo must be present when outputDemo=true", demo)
        assertEquals(ObjectModelJsonConverter.toJson(body), demo)
    }

    @Test
    fun testBodyDemoNullWhenOutputDemoFalse() {
        val body = ObjectModel.Object(
            mapOf("name" to FieldModel(ObjectModel.single(JsonType.STRING), comment = "user name"))
        )
        val endpoint = ApiEndpoint(
            name = "Create User",
            metadata = httpMetadata(path = "/api/users", method = HttpMethod.POST, body = body)
        )

        val model = TemplateModelBuilder.build(listOf(endpoint), outputDemo = false, moduleName = "API")
        val bodyView = model.groups[0].endpoints[0].http!!.body!!

        // rows still present
        assertEquals(1, bodyView.rows.size)
        // demo suppressed 
        assertNull("demo must be null when outputDemo=false", bodyView.demo)
    }

    @Test
    fun testResponseBodyViewNullWhenAbsent() {
        val endpoint = ApiEndpoint(
            name = "Delete User",
            metadata = httpMetadata(path = "/api/users/{id}", method = HttpMethod.DELETE)
        )

        val model = TemplateModelBuilder.build(listOf(endpoint), outputDemo = true, moduleName = "API")

        assertNull(model.groups[0].endpoints[0].http!!.response)
    }

    @Test
    fun testBodyDemoNullWhenBodyAbsent() {
        val endpoint = ApiEndpoint(
            name = "Get User",
            metadata = httpMetadata(path = "/api/users/{id}", method = HttpMethod.GET)
        )

        val model = TemplateModelBuilder.build(listOf(endpoint), outputDemo = true, moduleName = "API")

        assertNull(model.groups[0].endpoints[0].http!!.body)
    }

    // ---- Cycle safety ----

    @Test
    fun testCircularReferenceTerminatesWithoutStackOverflow() {
        // TreeNode { name: String, children: List<TreeNode> }
        val fields = mutableMapOf<String, FieldModel>()
        val treeNode = ObjectModel.Object(fields)
        fields["name"] = FieldModel(ObjectModel.single(JsonType.STRING), comment = "node name")
        fields["children"] = FieldModel(ObjectModel.array(treeNode), comment = "child nodes")

        val endpoint = ApiEndpoint(
            name = "Get Tree",
            metadata = httpMetadata(path = "/api/tree", method = HttpMethod.GET, responseBody = treeNode)
        )

        // Must not stack-overflow
        val model = TemplateModelBuilder.build(listOf(endpoint), outputDemo = true, moduleName = "API")
        val rows = model.groups[0].endpoints[0].http!!.response!!.rows

        assertTrue("top-level name row present", rows.any { it.name == "name" })
        assertTrue("top-level children row present", rows.any { it.name == "children" })
        // At least one nested row exists (DEFAULT_MAX_VISITS=2 allows one expansion)
        assertTrue("a nested row with indent prefix present", rows.any { it.name.startsWith("&ensp;&ensp;&#124;─") })
    }

    @Test
    fun testDirectCircularObjectReferenceTerminates() {
        // Object A { id, parent: A }
        val fields = mutableMapOf<String, FieldModel>()
        val selfRef = ObjectModel.Object(fields)
        fields["id"] = FieldModel(ObjectModel.single(JsonType.INT), comment = "id")
        fields["parent"] = FieldModel(selfRef, comment = "parent reference")

        val endpoint = ApiEndpoint(
            name = "Get Node",
            metadata = httpMetadata(path = "/api/node", method = HttpMethod.GET, responseBody = selfRef)
        )

        val model = TemplateModelBuilder.build(listOf(endpoint), outputDemo = true, moduleName = "API")
        val rows = model.groups[0].endpoints[0].http!!.response!!.rows

        assertTrue(rows.any { it.name == "id" })
        assertTrue(rows.any { it.name == "parent" })
    }

    @Test
    fun testMutualCircularReferenceTerminates() {
        // A { name, refB: B }, B { value, refA: A }
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
            metadata = httpMetadata(path = "/api/mutual", method = HttpMethod.GET, responseBody = objectA)
        )

        val model = TemplateModelBuilder.build(listOf(endpoint), outputDemo = true, moduleName = "API")
        val rows = model.groups[0].endpoints[0].http!!.response!!.rows

        assertTrue(rows.any { it.name == "name" })
        assertTrue(rows.any { it.name == "refB" })
    }

    @Test
    fun testNonCircularDeepNestingRendersAllLevels() {
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
            metadata = httpMetadata(path = "/api/nested", method = HttpMethod.GET, responseBody = outer)
        )

        val model = TemplateModelBuilder.build(listOf(endpoint), outputDemo = true, moduleName = "API")
        val rows = model.groups[0].endpoints[0].http!!.response!!.rows

        assertTrue("middle row present", rows.any { it.name == "middle" })
        assertTrue("inner row present (depth 1)", rows.any { it.name == "&ensp;&ensp;&#124;─inner" })
        assertTrue("value row present (depth 2)", rows.any { it.name == "&ensp;&ensp;&ensp;&ensp;&#124;─value" })
    }

    // ---- gRPC view ----

    @Test
    fun testGrpcEndpointFields() {
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

        val model = TemplateModelBuilder.build(listOf(endpoint), outputDemo = true, moduleName = "gRPC API")
        val ep = model.groups[0].endpoints[0]

        assertEquals("gRPC", ep.protocol)
        assertNull(ep.http)
        assertNotNull(ep.grpc)
        val grpc = ep.grpc!!
        assertEquals("UserService", grpc.serviceName)
        assertEquals("GetUser", grpc.methodName) // path.substringAfterLast('/')
        assertEquals("UNARY", grpc.streamingType)
        assertEquals("/user.UserService/GetUser", grpc.fullPath)
        assertNull(grpc.body)
        assertNull(grpc.response)
        // method at the Endpoint level mirrors gRPC method name
        assertEquals("GetUser", ep.method)
        assertEquals("/user.UserService/GetUser", ep.path)
    }

    @Test
    fun testGrpcServerStreamingType() {
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

        val model = TemplateModelBuilder.build(listOf(endpoint), outputDemo = true, moduleName = "API")

        assertEquals("SERVER_STREAMING", model.groups[0].endpoints[0].grpc!!.streamingType)
    }

    @Test
    fun testGrpcEndpointWithBody() {
        val body = ObjectModel.Object(
            mapOf("user_id" to FieldModel(ObjectModel.single(JsonType.STRING), comment = "user ID"))
        )
        val endpoint = ApiEndpoint(
            name = "GetUser",
            metadata = GrpcMetadata(
                path = "/user.UserService/GetUser",
                serviceName = "UserService",
                methodName = "GetUser",
                packageName = "user",
                streamingType = GrpcStreamingType.UNARY,
                body = body
            )
        )

        val model = TemplateModelBuilder.build(listOf(endpoint), outputDemo = true, moduleName = "API")
        val grpc = model.groups[0].endpoints[0].grpc!!

        assertNotNull(grpc.body)
        assertEquals(1, grpc.body!!.rows.size)
        assertEquals("user_id", grpc.body!!.rows[0].name)
        assertNotNull("demo present when outputDemo=true", grpc.body!!.demo)
    }

    // ---- Field description (options) ----

    @Test
    fun testFieldDescJoinsCommentAndOptions() {
        val body = ObjectModel.Object(
            mapOf(
                "status" to FieldModel(
                    ObjectModel.single(JsonType.STRING),
                    comment = "user status",
                    options = listOf(
                        com.itangcent.easyapi.psi.model.FieldOption(value = "ACTIVE", desc = "active user"),
                        com.itangcent.easyapi.psi.model.FieldOption(value = "INACTIVE")
                    )
                )
            )
        )
        val endpoint = ApiEndpoint(
            name = "Create User",
            metadata = httpMetadata(path = "/api/users", method = HttpMethod.POST, body = body)
        )

        val model = TemplateModelBuilder.build(listOf(endpoint), outputDemo = true, moduleName = "API")
        val desc = model.groups[0].endpoints[0].http!!.body!!.rows[0].desc

        // Mirrors DefaultMarkdownFormatter.buildFieldDescription:
        // "<comment><br><value1 :desc1><br><value2>"
        assertEquals("user status<br>ACTIVE :active user<br>INACTIVE", desc)
    }
}
