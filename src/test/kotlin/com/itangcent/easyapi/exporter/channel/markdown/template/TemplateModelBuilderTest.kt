package com.itangcent.easyapi.exporter.channel.markdown.template

import com.itangcent.easyapi.exporter.channel.curl.CurlBuilder
import com.itangcent.easyapi.exporter.channel.curl.CurlFormatOptions
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
 * consume. Pure JUnit: no `Project`, no PSI/VFS. Built test-first
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

        val model = TemplateModelBuilder.build(endpoints, moduleName = "API")

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

        val model = TemplateModelBuilder.build(listOf(endpoint), moduleName = "API")

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

        val model = TemplateModelBuilder.build(endpoints, moduleName = "API")

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

        val model = TemplateModelBuilder.build(listOf(endpoint), moduleName = "API")
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

        val model = TemplateModelBuilder.build(listOf(endpoint), moduleName = "API")
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

        val model = TemplateModelBuilder.build(listOf(endpoint), moduleName = "API")
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

        val model = TemplateModelBuilder.build(listOf(endpoint), moduleName = "API")
        val p = model.groups[0].endpoints[0].http!!.pathParams[0]

        assertEquals("", p.defaultValue)
        assertEquals("", p.description)
    }

    // ---- Body: fields, asDemo ----

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

        val model = TemplateModelBuilder.build(listOf(endpoint), moduleName = "API")
        val bodyView = model.groups[0].endpoints[0].http!!.body!!

        assertEquals(2, bodyView.fields.size)
        assertEquals("name", bodyView.fields[0].name)
        assertEquals("string", bodyView.fields[0].type)
        assertEquals("user name", bodyView.fields[0].desc)
        assertEquals("age", bodyView.fields[1].name)
        assertEquals("int", bodyView.fields[1].type)
        assertEquals("user age", bodyView.fields[1].desc)
        // model is the original ObjectModel
        assertSame(body, bodyView.model)
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

        val model = TemplateModelBuilder.build(listOf(endpoint), moduleName = "API")
        val bodyView = model.groups[0].endpoints[0].http!!.response!!

        // top-level field has no indent
        assertEquals("data", bodyView.fields[0].name)
        assertEquals("", bodyView.fields[0].indent)
        assertEquals(0, bodyView.fields[0].depth)
        assertEquals("object", bodyView.fields[0].type)
        assertEquals("response data", bodyView.fields[0].desc)
        // nested field has the indent prefix (separate from name)
        assertEquals("id", bodyView.fields[1].name)
        assertEquals("&ensp;&ensp;&#124;─", bodyView.fields[1].indent)
        assertEquals(1, bodyView.fields[1].depth)
        assertEquals("long", bodyView.fields[1].type)
        assertEquals("user id", bodyView.fields[1].desc)
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

        val model = TemplateModelBuilder.build(listOf(endpoint), moduleName = "API")
        val field = model.groups[0].endpoints[0].http!!.response!!.fields[0]

        assertEquals("data", field.name)
        assertEquals("object[]", field.type)
        assertEquals("user list", field.desc)
        assertEquals(FieldStructuralKind.ARRAY, field.structuralKind)
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

        val model = TemplateModelBuilder.build(listOf(endpoint), moduleName = "API")
        val bodyView = model.groups[0].endpoints[0].http!!.body!!

        // asDemo() is evaluated lazily; equals ObjectModelJsonConverter.toJson(body)
        assertEquals(ObjectModelJsonConverter.toJson(body), bodyView.asDemo())
        // asJson is an alias of asDemo
        assertEquals(bodyView.asDemo(), bodyView.asJson())
        // asJson5 is JSON5 with comments
        assertEquals(ObjectModelJsonConverter.toJson5(body), bodyView.asJson5())
    }

    @Test
    fun testResponseBodyViewNullWhenAbsent() {
        val endpoint = ApiEndpoint(
            name = "Delete User",
            metadata = httpMetadata(path = "/api/users/{id}", method = HttpMethod.DELETE)
        )

        val model = TemplateModelBuilder.build(listOf(endpoint), moduleName = "API")

        assertNull(model.groups[0].endpoints[0].http!!.response)
    }

    @Test
    fun testBodyNullWhenBodyAbsent() {
        val endpoint = ApiEndpoint(
            name = "Get User",
            metadata = httpMetadata(path = "/api/users/{id}", method = HttpMethod.GET)
        )

        val model = TemplateModelBuilder.build(listOf(endpoint), moduleName = "API")

        assertNull(model.groups[0].endpoints[0].http!!.body)
    }

    // ---- Primitive body (Single) — parity with legacy Row(name="", type=model.type, desc="") ----

    @Test
    fun testPrimitiveBodyProducesOneSyntheticField() {
        // Review finding F5: Single body → one FieldView with name="", type=model.type, desc="",
        // structuralKind=PRIMITIVE, depth=0, indent="" — byte-identical to legacy Row.
        val body = ObjectModel.single(JsonType.STRING)
        val endpoint = ApiEndpoint(
            name = "Echo",
            metadata = httpMetadata(path = "/api/echo", method = HttpMethod.POST, body = body)
        )

        val model = TemplateModelBuilder.build(listOf(endpoint), moduleName = "API")
        val bodyView = model.groups[0].endpoints[0].http!!.body!!

        assertEquals(1, bodyView.fields.size)
        val field = bodyView.fields[0]
        assertEquals("", field.name)
        assertEquals("string", field.type)
        assertEquals("", field.desc)
        assertEquals("", field.indent)
        assertEquals(0, field.depth)
        assertFalse(field.required)
        assertNull(field.defaultValue)
        assertFalse(field.hasChildren)
        assertEquals(0, field.childrenCount)
        assertEquals(FieldStructuralKind.PRIMITIVE, field.structuralKind)
    }

    // ---- Map body (MapModel) — parity with legacy two rows (key + value) ----

    @Test
    fun testMapBodyProducesTwoSyntheticFields() {
        // Review finding F6: MapModel body → two FieldViews at depth 0:
        // {name="key", type=formatType(keyType), structuralKind=MAP} and {name="value", ...}.
        val body = ObjectModel.MapModel(
            keyType = ObjectModel.single(JsonType.STRING),
            valueType = ObjectModel.single(JsonType.INT),
        )
        val endpoint = ApiEndpoint(
            name = "MapResource",
            metadata = httpMetadata(path = "/api/map", method = HttpMethod.POST, body = body)
        )

        val model = TemplateModelBuilder.build(listOf(endpoint), moduleName = "API")
        val bodyView = model.groups[0].endpoints[0].http!!.body!!

        assertEquals(2, bodyView.fields.size)
        val keyField = bodyView.fields[0]
        assertEquals("key", keyField.name)
        assertEquals("string", keyField.type)
        assertEquals("", keyField.desc)
        assertEquals("", keyField.indent)
        assertEquals(0, keyField.depth)
        assertEquals(FieldStructuralKind.MAP, keyField.structuralKind)
        val valueField = bodyView.fields[1]
        assertEquals("value", valueField.name)
        assertEquals("int", valueField.type)
        assertEquals("", valueField.desc)
        assertEquals("", valueField.indent)
        assertEquals(0, valueField.depth)
        assertEquals(FieldStructuralKind.MAP, valueField.structuralKind)
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
        val model = TemplateModelBuilder.build(listOf(endpoint), moduleName = "API")
        val fieldsList = model.groups[0].endpoints[0].http!!.response!!.fields

        assertTrue("top-level name row present", fieldsList.any { it.name == "name" })
        assertTrue("top-level children row present", fieldsList.any { it.name == "children" })
        // At least one nested field exists (DEFAULT_MAX_VISITS=2 allows one expansion)
        assertTrue("a nested field with indent prefix present", fieldsList.any { it.depth > 0 && it.indent.startsWith("&ensp;&ensp;") })
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

        val model = TemplateModelBuilder.build(listOf(endpoint), moduleName = "API")
        val fieldsList = model.groups[0].endpoints[0].http!!.response!!.fields

        assertTrue(fieldsList.any { it.name == "id" })
        assertTrue(fieldsList.any { it.name == "parent" })
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

        val model = TemplateModelBuilder.build(listOf(endpoint), moduleName = "API")
        val fieldsList = model.groups[0].endpoints[0].http!!.response!!.fields

        assertTrue(fieldsList.any { it.name == "name" })
        assertTrue(fieldsList.any { it.name == "refB" })
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

        val model = TemplateModelBuilder.build(listOf(endpoint), moduleName = "API")
        val fieldsList = model.groups[0].endpoints[0].http!!.response!!.fields

        assertTrue("middle row present", fieldsList.any { it.name == "middle" && it.depth == 0 })
        assertTrue("inner row present (depth 1)", fieldsList.any { it.name == "inner" && it.depth == 1 && it.indent == "&ensp;&ensp;&#124;─" })
        assertTrue("value row present (depth 2)", fieldsList.any { it.name == "value" && it.depth == 2 && it.indent == "&ensp;&ensp;&ensp;&ensp;&#124;─" })
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

        val model = TemplateModelBuilder.build(listOf(endpoint), moduleName = "gRPC API")
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

        val model = TemplateModelBuilder.build(listOf(endpoint), moduleName = "API")

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

        val model = TemplateModelBuilder.build(listOf(endpoint), moduleName = "API")
        val grpc = model.groups[0].endpoints[0].grpc!!

        assertNotNull(grpc.body)
        assertEquals(1, grpc.body!!.fields.size)
        assertEquals("user_id", grpc.body!!.fields[0].name)
        // asDemo() is the lazy JSON render
        assertEquals(ObjectModelJsonConverter.toJson(body), grpc.body!!.asDemo())
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

        val model = TemplateModelBuilder.build(listOf(endpoint), moduleName = "API")
        val desc = model.groups[0].endpoints[0].http!!.body!!.fields[0].desc

        // Mirrors DefaultMarkdownFormatter.buildFieldDescription:
        // "<comment><br><value1 :desc1><br><value2>"
        assertEquals("user status<br>ACTIVE :active user<br>INACTIVE", desc)
    }

    // ---- HttpView.curl() ----

    @Test
    fun testHttpViewCurlDefaultHostContainsPlaceholderAndMethod() {
        // Default host = CurlBuilder.DEFAULT_HOST = "{{host}}". curl() must be non-null and
        // contain the cURL banner + the method + the placeholder host.
        val endpoint = ApiEndpoint(
            name = "Get User",
            metadata = httpMetadata(path = "/api/users/{id}", method = HttpMethod.GET)
        )

        val model = TemplateModelBuilder.build(listOf(endpoint), moduleName = "API")
        val curl = model.groups[0].endpoints[0].http!!.curl()

        assertNotNull("curl() must be non-null with default host", curl)
        val c = curl!!
        assertTrue("curl should contain 'curl' banner: $c", c.contains("curl"))
        assertTrue("curl should contain '-X GET' method: $c", c.contains("-X GET"))
        assertTrue("curl should contain default '{{host}}' placeholder: $c", c.contains("{{host}}"))
    }

    @Test
    fun testHttpViewCurlWithExplicitHost() {
        val endpoint = ApiEndpoint(
            name = "Get User",
            metadata = httpMetadata(path = "/api/users/{id}", method = HttpMethod.GET)
        )

        val model = TemplateModelBuilder.build(
            listOf(endpoint),
            moduleName = "API",
            host = "https://api.example.com",
        )
        val curl = model.groups[0].endpoints[0].http!!.curl()!!

        assertTrue("curl should contain explicit host: $curl", curl.contains("https://api.example.com"))
        assertFalse(
            "curl should NOT contain '{{host}}' placeholder when explicit host given: $curl",
            curl.contains("{{host}}"),
        )
    }

    @Test
    fun testHttpViewCurlWithBlankHostFallsBackToDefault() {
        val endpoint = ApiEndpoint(
            name = "Get User",
            metadata = httpMetadata(path = "/api/users/{id}", method = HttpMethod.GET)
        )

        val model = TemplateModelBuilder.build(
            listOf(endpoint),
            moduleName = "API",
            host = "   ", // blank
        )
        val curl = model.groups[0].endpoints[0].http!!.curl()!!

        assertTrue("blank host should fall back to default placeholder: $curl", curl.contains("{{host}}"))
    }

    @Test
    fun testHttpViewCurlRespectsFormatOptions() {
        val endpoint = ApiEndpoint(
            name = "Get User",
            metadata = httpMetadata(path = "/api/users/{id}", method = HttpMethod.GET)
        )

        val model = TemplateModelBuilder.build(
            listOf(endpoint),
            moduleName = "API",
            host = "https://api.example.com",
            formatOptions = CurlFormatOptions(longFlags = true),
        )
        val curl = model.groups[0].endpoints[0].http!!.curl()!!

        assertTrue("long-flags option should produce --request: $curl", curl.contains("--request"))
        assertFalse(
            "long-flags option should NOT produce short -X: $curl",
            curl.contains("-X GET"),
        )
    }

    @Test
    fun testGrpcEndpointHasNoHttpViewSoCurlNotApplicable() {
        // gRPC endpoints have http=null → curl() is unreachable. This pins that the curlProvider
        // is only wired on HttpView, never on GrpcView.
        val endpoint = ApiEndpoint(
            name = "GetUser",
            metadata = GrpcMetadata(
                path = "/user.UserService/GetUser",
                serviceName = "UserService",
                methodName = "GetUser",
                packageName = "user",
                streamingType = GrpcStreamingType.UNARY,
            )
        )

        val model = TemplateModelBuilder.build(listOf(endpoint), moduleName = "gRPC API")
        val ep = model.groups[0].endpoints[0]

        assertNull("gRPC endpoint has no HttpView", ep.http)
        assertNotNull("gRPC endpoint has GrpcView", ep.grpc)
    }

    @Test
    fun testHttpViewCurlMatchesCurlBuilderFormatDirectly() {
        // Cross-check: the provider's output must equal a direct CurlBuilder.format call
        // with the same inputs — pins that the provider is a thin delegate.
        val endpoint = ApiEndpoint(
            name = "Create User",
            metadata = httpMetadata(
                path = "/api/users",
                method = HttpMethod.POST,
                contentType = "application/json",
                body = ObjectModel.Object(
                    mapOf("name" to FieldModel(ObjectModel.single(JsonType.STRING), comment = "user name"))
                ),
            ),
        )

        val host = "https://api.example.com"
        val options = CurlFormatOptions(includeComments = false)
        val model = TemplateModelBuilder.build(
            listOf(endpoint),
            moduleName = "API",
            host = host,
            formatOptions = options,
        )
        val providerCurl = model.groups[0].endpoints[0].http!!.curl()!!
        val directCurl = CurlBuilder.format(
            endpoint,
            host,
            com.itangcent.easyapi.exporter.channel.curl.CurlBuildOptions(format = options),
        )

        assertEquals(directCurl, providerCurl)
    }
}
