package com.itangcent.easyapi.exporter.yapi

import com.google.gson.JsonParser
import com.itangcent.easyapi.exporter.model.*
import com.itangcent.easyapi.exporter.yapi.model.*
import com.itangcent.easyapi.psi.model.FieldModel
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.type.JsonType
import com.itangcent.easyapi.util.markdown.BundledMarkdownRender
import org.junit.Assert.*
import org.junit.Test

class YapiFormatterTest {

    private val formatter = YapiFormatter(markdownRender = BundledMarkdownRender())

    // region Basic formatting

    @Test
    fun testFormatSimpleEndpoint() {
        val endpoint = ApiEndpoint(
            name = "Get User",
            metadata = httpMetadata(
                path = "/api/users/{id}",
                method = HttpMethod.GET,
                parameters = listOf(
                    ApiParameter(name = "id", binding = ParameterBinding.Path, example = "1")
                )
            )
        )

        val doc = formatter.format(endpoint)

        assertEquals("Get User", doc.title)
        assertEquals("/api/users/{id}", doc.path)
        assertEquals("get", doc.method)
    }

    @Test
    fun testFormatEndpointWithQuery() {
        val endpoint = ApiEndpoint(
            name = "List Users",
            metadata = httpMetadata(
                path = "/api/users",
                method = HttpMethod.GET,
                parameters = listOf(
                    ApiParameter(name = "page", binding = ParameterBinding.Query, defaultValue = "1"),
                    ApiParameter(name = "size", binding = ParameterBinding.Query, defaultValue = "10")
                )
            )
        )

        val doc = formatter.format(endpoint)

        assertEquals(2, doc.reqQuery?.size)
        assertEquals("page", doc.reqQuery?.get(0)?.name)
        assertEquals("size", doc.reqQuery?.get(1)?.name)
    }

    @Test
    fun testFormatEndpointWithBody() {
        val endpoint = ApiEndpoint(
            name = "Create User",
            metadata = httpMetadata(
                path = "/api/users",
                method = HttpMethod.POST,
                contentType = "application/json",
                parameters = listOf(
                    ApiParameter(name = "name", binding = ParameterBinding.Body, example = "John"),
                    ApiParameter(name = "email", binding = ParameterBinding.Body, example = "john@example.com")
                )
            )
        )

        val doc = formatter.format(endpoint)

        assertNotNull(doc.reqBodyOther)
        assertTrue(doc.reqBodyOther!!.contains("name"))
        assertTrue(doc.reqBodyOther!!.contains("email"))
    }

    @Test
    fun testFormatEndpointWithHeaders() {
        val endpoint = ApiEndpoint(
            name = "Protected API",
            metadata = httpMetadata(
                path = "/api/protected",
                method = HttpMethod.GET,
                headers = listOf(
                    ApiHeader(name = "Authorization", value = "Bearer token"),
                    ApiHeader(name = "X-Custom-Header", value = "custom-value")
                )
            )
        )

        val doc = formatter.format(endpoint)

        assertEquals(2, doc.reqHeaders?.size)
        assertEquals("Authorization", doc.reqHeaders?.get(0)?.name)
        assertEquals("Bearer token", doc.reqHeaders?.get(0)?.value)
    }

    @Test
    fun testFormatEndpointWithFormParams() {
        val endpoint = ApiEndpoint(
            name = "Login",
            metadata = httpMetadata(
                path = "/api/auth/login",
                method = HttpMethod.POST,
                contentType = "application/x-www-form-urlencoded",
                parameters = listOf(
                    ApiParameter(name = "username", binding = ParameterBinding.Form, example = "admin"),
                    ApiParameter(name = "password", binding = ParameterBinding.Form, example = "secret")
                )
            )
        )

        val doc = formatter.format(endpoint)

        assertEquals(2, doc.reqBodyForm?.size)
        assertEquals("username", doc.reqBodyForm?.get(0)?.name)
        assertEquals("password", doc.reqBodyForm?.get(1)?.name)
    }

    @Test
    fun testFormatEndpointWithDescription() {
        val endpoint = ApiEndpoint(
            name = "Get User",
            description = "Retrieve user information by ID",
            metadata = httpMetadata(
                path = "/api/users/{id}",
                method = HttpMethod.GET
            )
        )

        val doc = formatter.format(endpoint)

        assertNotNull("desc should be rendered HTML", doc.desc)
        assertTrue("desc should contain rendered text", doc.desc!!.contains("Retrieve user information"))
        assertEquals("markdown should preserve raw description", "Retrieve user information by ID", doc.markdown)
    }

    @Test
    fun testFormatEndpointWithMarkdownDescription() {
        val endpoint = ApiEndpoint(
            name = "Get User",
            description = "**Bold** and *italic* description",
            metadata = httpMetadata(
                path = "/api/users/{id}",
                method = HttpMethod.GET
            )
        )

        val doc = formatter.format(endpoint)

        assertNotNull("desc should be rendered HTML", doc.desc)
        assertTrue("desc should contain bold tag", doc.desc!!.contains("<strong>") || doc.desc!!.contains("<b>"))
        assertTrue("desc should contain italic tag", doc.desc!!.contains("<em>") || doc.desc!!.contains("<i>"))
        assertEquals("markdown should preserve raw description", "**Bold** and *italic* description", doc.markdown)
    }

    @Test
    fun testFormatEndpointWithNullDescription() {
        val endpoint = ApiEndpoint(
            name = "Get User",
            description = null,
            metadata = httpMetadata(
                path = "/api/users/{id}",
                method = HttpMethod.GET
            )
        )

        val doc = formatter.format(endpoint)

        assertNull("desc should be null when no description", doc.desc)
        assertNull("markdown should be null when no description", doc.markdown)
    }

    @Test
    fun testFormatEndpointWithBlankDescription() {
        val endpoint = ApiEndpoint(
            name = "Get User",
            description = "   ",
            metadata = httpMetadata(
                path = "/api/users/{id}",
                method = HttpMethod.GET
            )
        )

        val doc = formatter.format(endpoint)

        assertNull("desc should be null for blank description", doc.desc)
        assertNull("markdown should be null for blank description", doc.markdown)
    }

    @Test
    fun testFormatEndpointWithComplexMarkdownDescription() {
        val endpoint = ApiEndpoint(
            name = "Create User",
            description = """
                Creates a new user in the system.
                
                **Request body:**
                - `name`: User name
                - `email`: User email
                
                ```json
                {"name": "John"}
                ```
            """.trimIndent(),
            metadata = httpMetadata(
                path = "/api/users",
                method = HttpMethod.POST
            )
        )

        val doc = formatter.format(endpoint)

        assertNotNull("desc should be rendered HTML", doc.desc)
        assertTrue("desc should contain bold", doc.desc!!.contains("<strong>") || doc.desc!!.contains("<b>"))
        assertTrue("desc should contain code", doc.desc!!.contains("<code>"))
        assertTrue("markdown should preserve raw description", doc.markdown!!.contains("Creates a new user"))
    }

    // endregion

    // region Path formatting

    @Test
    fun testFormatPathPrefixesSlash() {
        val endpoint = ApiEndpoint(
            name = "No Leading Slash",
            metadata = httpMetadata(
                path = "api/users",
                method = HttpMethod.GET
            )
        )

        val doc = formatter.format(endpoint)

        assertEquals("/api/users", doc.path)
    }

    @Test
    fun testFormatPathEmptyBecomesSlash() {
        val endpoint = ApiEndpoint(
            name = "Empty Path",
            metadata = httpMetadata(
                path = "",
                method = HttpMethod.GET
            )
        )

        val doc = formatter.format(endpoint)

        assertEquals("/", doc.path)
    }

    @Test
    fun testFormatPathStripsInvalidCharacters() {
        val endpoint = ApiEndpoint(
            name = "Invalid Chars",
            metadata = httpMetadata(
                path = "/api/用户/list",
                method = HttpMethod.GET
            )
        )

        val doc = formatter.format(endpoint)

        assertEquals("/api/list", doc.path)
    }

    @Test
    fun testFormatPathCollapsesRedundantSlashes() {
        val endpoint = ApiEndpoint(
            name = "Double Slashes",
            metadata = httpMetadata(
                path = "/api//users///list",
                method = HttpMethod.GET
            )
        )

        val doc = formatter.format(endpoint)

        assertEquals("/api/users/list", doc.path)
    }

    @Test
    fun testFormatPathPreservesPathVariables() {
        val endpoint = ApiEndpoint(
            name = "Path Variables",
            metadata = httpMetadata(
                path = "/api/users/{id}/orders/{orderId}",
                method = HttpMethod.GET
            )
        )

        val doc = formatter.format(endpoint)

        assertEquals("/api/users/{id}/orders/{orderId}", doc.path)
    }

    @Test
    fun testFormatPathPreservesAllowedSpecialChars() {
        val endpoint = ApiEndpoint(
            name = "Special Chars",
            metadata = httpMetadata(
                path = "/api/v1.0/users:search",
                method = HttpMethod.GET
            )
        )

        val doc = formatter.format(endpoint)

        assertEquals("/api/v1.0/users:search", doc.path)
    }

    @Test
    fun testFormatPathWithSpaces() {
        val endpoint = ApiEndpoint(
            name = "Spaces In Path",
            metadata = httpMetadata(
                path = "/api/user list/all",
                method = HttpMethod.GET
            )
        )

        val doc = formatter.format(endpoint)

        assertEquals("/api/user/list/all", doc.path)
    }

    @Test
    fun testFormatPathDisabledAutoFormat() {
        val noFormatFormatter = YapiFormatter(autoFormatUrl = false, markdownRender = BundledMarkdownRender())
        val endpoint = ApiEndpoint(
            name = "No Format",
            metadata = httpMetadata(
                path = "api/users list",
                method = HttpMethod.GET
            )
        )

        val doc = noFormatFormatter.format(endpoint)

        assertEquals("api/users list", doc.path)
    }

    // endregion

    // region Multiple URLs

    @Test
    fun testFormatWithMultipleUrls() {
        val endpoint = ApiEndpoint(
            name = "Multi-Environment API",
            metadata = httpMetadata(
                path = "/api/users",
                method = HttpMethod.GET,
                alternativePaths = listOf(
                    "/api/v1/users",
                    "/api/v2/users",
                    "/api/v3/users"
                )
            )
        )

        val docs = formatter.formatWithUrls(endpoint)

        assertEquals(3, docs.size)
        assertEquals("/api/v1/users", docs[0].path)
        assertEquals("/api/v2/users", docs[1].path)
        assertEquals("/api/v3/users", docs[2].path)
    }

    @Test
    fun testFormatWithUrlsAlsoFormatsAlternativePaths() {
        val endpoint = ApiEndpoint(
            name = "Alt Paths",
            metadata = httpMetadata(
                path = "/api/users",
                method = HttpMethod.GET,
                alternativePaths = listOf(
                    "api/v1/users",
                    "/api//v2//users",
                    "/api/用户"
                )
            )
        )

        val docs = formatter.formatWithUrls(endpoint)

        assertEquals(3, docs.size)
        assertEquals("/api/v1/users", docs[0].path)
        assertEquals("/api/v2/users", docs[1].path)
        assertEquals("/api/", docs[2].path)
    }

    // endregion

    // region Body & Schema

    @Test
    fun testFormatWithJsonSchema() {
        val endpoint = ApiEndpoint(
            name = "Create User",
            metadata = httpMetadata(
                path = "/api/users",
                method = HttpMethod.POST,
                body = ObjectModel.Object(
                    mapOf(
                        "name" to FieldModel(ObjectModel.Single(JsonType.STRING), "User name"),
                        "age" to FieldModel(ObjectModel.Single(JsonType.INT), "User age"),
                        "email" to FieldModel(ObjectModel.Single(JsonType.STRING), "User email")
                    )
                )
            )
        )

        val doc = formatter.format(endpoint)

        assertNotNull(doc.reqBodyOther)
        assertTrue("Should contain properties", doc.reqBodyOther!!.contains("properties"))
        assertTrue("Should contain name", doc.reqBodyOther!!.contains("name"))
        assertTrue("Should contain age", doc.reqBodyOther!!.contains("age"))
        assertTrue("Should contain email", doc.reqBodyOther!!.contains("email"))
    }

    @Test
    fun testFormatWithJson5Body() {
        val json5Formatter = YapiFormatter(useJson5 = true, markdownRender = BundledMarkdownRender())
        val endpoint = ApiEndpoint(
            name = "Create User",
            metadata = httpMetadata(
                path = "/api/users",
                method = HttpMethod.POST,
                body = ObjectModel.Object(
                    mapOf(
                        "name" to FieldModel(ObjectModel.Single(JsonType.STRING), "User name"),
                        "age" to FieldModel(ObjectModel.Single(JsonType.INT), "User age")
                    )
                )
            )
        )

        val doc = json5Formatter.format(endpoint)

        assertNotNull(doc.reqBodyOther)
        assertTrue(
            "Should contain comment",
            doc.reqBodyOther!!.contains("//") || doc.reqBodyOther!!.contains("User name")
        )
    }

    @Test
    fun testFormatWithRequiredParameters() {
        val endpoint = ApiEndpoint(
            name = "Create User",
            metadata = httpMetadata(
                path = "/api/users",
                method = HttpMethod.POST,
                parameters = listOf(
                    ApiParameter(
                        name = "name",
                        binding = ParameterBinding.Body,
                        required = true,
                        description = "User name"
                    ),
                    ApiParameter(
                        name = "nickname",
                        binding = ParameterBinding.Body,
                        required = false,
                        description = "User nickname"
                    )
                )
            )
        )

        val doc = formatter.format(endpoint)

        assertNotNull(doc.reqBodyOther)
        assertTrue("Should contain required array", doc.reqBodyOther!!.contains("required"))
    }

    @Test
    fun testFormatWithResponseSchema() {
        val endpoint = ApiEndpoint(
            name = "Get User",
            metadata = httpMetadata(
                path = "/api/users/{id}",
                method = HttpMethod.GET,
                body = null,
                responseBody = ObjectModel.Object(
                    mapOf(
                        "code" to FieldModel(ObjectModel.Single(JsonType.INT), "Response code"),
                        "message" to FieldModel(ObjectModel.Single(JsonType.STRING), "Response message"),
                        "data" to FieldModel(
                            ObjectModel.Object(
                                mapOf(
                                    "id" to FieldModel(ObjectModel.Single(JsonType.INT)),
                                    "name" to FieldModel(ObjectModel.Single(JsonType.STRING))
                                )
                            ),
                            "Response data"
                        )
                    )
                )
            )
        )

        val doc = formatter.format(endpoint)

        assertNotNull(doc.resBody)
        assertTrue("Should contain schema", doc.resBody!!.contains("type") || doc.resBody!!.contains("properties"))
    }

    @Test
    fun testFormatWithEnumValues() {
        val endpoint = ApiEndpoint(
            name = "Update User",
            metadata = httpMetadata(
                path = "/api/users/{id}",
                method = HttpMethod.PUT,
                parameters = listOf(
                    ApiParameter(
                        name = "status",
                        binding = ParameterBinding.Body,
                        description = "User status",
                        enumValues = listOf("active", "inactive", "deleted")
                    )
                )
            )
        )

        val doc = formatter.format(endpoint)

        assertNotNull(doc.reqBodyOther)
        assertTrue("Should contain enum", doc.reqBodyOther!!.contains("enum"))
    }

    // endregion

    // region Metadata

    @Test
    fun testFormatWithMockData() {
        val endpoint = ApiEndpoint(
            name = "Get User",
            metadata = httpMetadata(
                path = "/api/users/{id}",
                method = HttpMethod.GET,
                parameters = listOf(
                    ApiParameter(
                        name = "id",
                        binding = ParameterBinding.Path,
                        description = "User ID"
                    )
                )
            )
        )

        val doc = formatter.formatWithMock(endpoint)

        assertNotNull(doc.reqParams?.get(0)?.example)
    }

    @Test
    fun testFormatWithTags() {
        val endpoint = ApiEndpoint(
            name = "Get User",
            tags = listOf("user", "admin"),
            metadata = httpMetadata(
                path = "/api/users/{id}",
                method = HttpMethod.GET
            )
        )

        val doc = formatter.format(endpoint)

        assertEquals(2, doc.tags?.size)
        assertTrue(doc.tags!!.contains("user"))
        assertTrue(doc.tags!!.contains("admin"))
    }

    @Test
    fun testFormatWithDefaultValues() {
        val endpoint = ApiEndpoint(
            name = "List Users",
            metadata = httpMetadata(
                path = "/api/users",
                method = HttpMethod.GET,
                parameters = listOf(
                    ApiParameter(
                        name = "page",
                        binding = ParameterBinding.Query,
                        defaultValue = "1",
                        description = "Page number"
                    ),
                    ApiParameter(
                        name = "size",
                        binding = ParameterBinding.Query,
                        defaultValue = "10",
                        description = "Page size"
                    )
                )
            )
        )

        val doc = formatter.format(endpoint)

        assertEquals(2, doc.reqQuery?.size)
        assertEquals("1", doc.reqQuery?.get(0)?.value)
        assertEquals("10", doc.reqQuery?.get(1)?.value)
    }

    // endregion

    // region Mock rules

    @Test
    fun testFormatWithMockWithRules() {
        val mockRules = mapOf(
            "*.id|integer" to "@integer",
            "*.email|string" to "@email"
        )
        val mockFormatter = YapiFormatter(mockRules = mockRules, markdownRender = BundledMarkdownRender())
        val endpoint = ApiEndpoint(
            name = "Get User",
            metadata = httpMetadata(
                path = "/api/users/{id}",
                method = HttpMethod.GET,
                parameters = listOf(
                    ApiParameter(
                        name = "id",
                        binding = ParameterBinding.Path,
                        description = "User ID",
                        jsonType = "integer"
                    )
                )
            )
        )

        val doc = mockFormatter.formatWithMock(endpoint)

        assertNotNull(doc.reqParams)
        assertEquals("@integer", doc.reqParams!![0].example)
    }

    @Test
    fun testFormatWithMockRulesForQueryParams() {
        val mockRules = mapOf(
            "*.page|integer" to "@integer(1, 100)",
            "*.size|integer" to "@integer(1, 100)"
        )
        val mockFormatter = YapiFormatter(mockRules = mockRules, markdownRender = BundledMarkdownRender())
        val endpoint = ApiEndpoint(
            name = "List Users",
            metadata = httpMetadata(
                path = "/api/users",
                method = HttpMethod.GET,
                parameters = listOf(
                    ApiParameter(name = "page", binding = ParameterBinding.Query, jsonType = "integer"),
                    ApiParameter(name = "size", binding = ParameterBinding.Query, jsonType = "integer")
                )
            )
        )

        val doc = mockFormatter.formatWithMock(endpoint)

        assertNotNull(doc.reqQuery)
        assertEquals("@integer(1, 100)", doc.reqQuery!![0].example)
        assertEquals("@integer(1, 100)", doc.reqQuery!![1].example)
    }

    @Test
    fun testFormatWithMockRulesFallbackToNameHeuristic() {
        val mockRules = mapOf(
            "*.email|string" to "@email"
        )
        val mockFormatter = YapiFormatter(mockRules = mockRules, markdownRender = BundledMarkdownRender())
        val endpoint = ApiEndpoint(
            name = "Get User",
            metadata = httpMetadata(
                path = "/api/users/{id}",
                method = HttpMethod.GET,
                parameters = listOf(
                    ApiParameter(
                        name = "id",
                        binding = ParameterBinding.Path,
                        description = "User ID",
                        jsonType = "integer"
                    )
                )
            )
        )

        val doc = mockFormatter.formatWithMock(endpoint)

        assertNotNull(doc.reqParams)
        assertEquals("@id", doc.reqParams!![0].example)
    }

    @Test
    fun testFormatWithMockNoRulesUsesNameHeuristic() {
        val noRulesFormatter = YapiFormatter(
            mockGenerator = MockDataGenerator(emptyMap()),
            markdownRender = BundledMarkdownRender()
        )
        val endpoint = ApiEndpoint(
            name = "Get User",
            metadata = httpMetadata(
                path = "/api/users/{id}",
                method = HttpMethod.GET,
                parameters = listOf(
                    ApiParameter(
                        name = "email",
                        binding = ParameterBinding.Query,
                        jsonType = "string"
                    )
                )
            )
        )

        val doc = noRulesFormatter.formatWithMock(endpoint)

        assertNotNull(doc.reqQuery)
        assertEquals("@email", doc.reqQuery!![0].example)
    }

    // endregion

    // region buildApiDocBody

    @Test
    fun testBuildApiDocBodyBasicFields() {
        val doc = MutableYapiApiDoc.create(
            title = "Get User",
            path = "/api/users/{id}",
            method = "get",
            desc = "<p>desc</p>",
            markdown = "desc",
            status = "done"
        )
        val json = formatter.buildApiDocBody(doc, "my-token", "cat123")
        val obj = JsonParser.parseString(json).asJsonObject

        assertEquals("my-token", obj.get("token").asString)
        assertEquals("cat123", obj.get("catid").asString)
        assertEquals("Get User", obj.get("title").asString)
        assertEquals("/api/users/{id}", obj.get("path").asString)
        assertEquals("get", obj.get("method").asString)
        assertEquals("<p>desc</p>", obj.get("desc").asString)
        assertEquals("desc", obj.get("markdown").asString)
        assertEquals("done", obj.get("status").asString)
    }

    @Test
    fun testBuildApiDocBodyWithExistingId() {
        val doc = MutableYapiApiDoc.create(title = "T", path = "/p", method = "get")
        val json = formatter.buildApiDocBody(doc, "tok", "cat1", "existing-123")
        val obj = JsonParser.parseString(json).asJsonObject

        assertEquals("existing-123", obj.get("id").asString)
    }

    @Test
    fun testBuildApiDocBodyWithoutExistingId() {
        val doc = MutableYapiApiDoc.create(title = "T", path = "/p", method = "get")
        val json = formatter.buildApiDocBody(doc, "tok", "cat1")
        val obj = JsonParser.parseString(json).asJsonObject

        assertNull(obj.get("id"))
    }

    @Test
    fun testBuildApiDocBodyWithHeaders() {
        val doc = MutableYapiApiDoc.create(
            title = "T", path = "/p", method = "get",
            reqHeaders = listOf(
                MutableYapiHeader(name = "Authorization", value = "Bearer x", desc = "Auth header", required = 1)
            )
        )
        val json = formatter.buildApiDocBody(doc, "tok", "cat1")
        val obj = JsonParser.parseString(json).asJsonObject
        val headers = obj.getAsJsonArray("req_headers")

        assertEquals(1, headers.size())
        val header = headers[0].asJsonObject
        assertEquals("Authorization", header.get("name").asString)
        assertEquals("Bearer x", header.get("value").asString)
        assertEquals("Auth header", header.get("desc").asString)
        assertEquals(1, header.get("required").asInt)
    }

    @Test
    fun testBuildApiDocBodyWithQueryParams() {
        val doc = MutableYapiApiDoc.create(
            title = "T", path = "/p", method = "get",
            reqQuery = listOf(
                MutableYapiQuery(name = "page", value = "1", desc = "Page number", required = 0)
            )
        )
        val json = formatter.buildApiDocBody(doc, "tok", "cat1")
        val obj = JsonParser.parseString(json).asJsonObject
        val queries = obj.getAsJsonArray("req_query")

        assertEquals(1, queries.size())
        val query = queries[0].asJsonObject
        assertEquals("page", query.get("name").asString)
        assertEquals("1", query.get("value").asString)
        assertEquals("Page number", query.get("desc").asString)
    }

    @Test
    fun testBuildApiDocBodyWithFormParams() {
        val doc = MutableYapiApiDoc.create(
            title = "T", path = "/p", method = "post",
            reqBodyType = "form",
            reqBodyForm = listOf(
                MutableYapiFormParam(name = "username", example = "admin", type = "text", required = 1, desc = "Login name")
            )
        )
        val json = formatter.buildApiDocBody(doc, "tok", "cat1")
        val obj = JsonParser.parseString(json).asJsonObject
        val forms = obj.getAsJsonArray("req_body_form")

        assertEquals(1, forms.size())
        val form = forms[0].asJsonObject
        assertEquals("username", form.get("name").asString)
        assertEquals("admin", form.get("example").asString)
        assertEquals("text", form.get("type").asString)
        assertEquals(1, form.get("required").asInt)
        assertEquals("form", obj.get("req_body_type").asString)
    }

    @Test
    fun testBuildApiDocBodyWithPathParams() {
        val doc = MutableYapiApiDoc.create(
            title = "T", path = "/p/{id}", method = "get",
            reqParams = listOf(
                MutableYapiPathParam(name = "id", example = "42", desc = "User ID")
            )
        )
        val json = formatter.buildApiDocBody(doc, "tok", "cat1")
        val obj = JsonParser.parseString(json).asJsonObject
        val params = obj.getAsJsonArray("req_params")

        assertEquals(1, params.size())
        val param = params[0].asJsonObject
        assertEquals("id", param.get("name").asString)
        assertEquals("42", param.get("example").asString)
        assertEquals("User ID", param.get("desc").asString)
    }

    // endregion

    // region Extension support

    @Test
    fun testExtensionDefaultReturnsEmptyMap() {
        val doc = MutableYapiApiDoc.create(title = "T", path = "/p", method = "get")
        assertTrue(doc.getExts().isEmpty())

        val header = MutableYapiHeader(name = "X-Custom")
        assertTrue(header.getExts().isEmpty())

        val query = MutableYapiQuery(name = "q")
        assertTrue(query.getExts().isEmpty())

        val pathParam = MutableYapiPathParam(name = "id")
        assertTrue(pathParam.getExts().isEmpty())

        val formParam = MutableYapiFormParam(name = "field")
        assertTrue(formParam.getExts().isEmpty())
    }

    @Test
    fun testExtensionWithCustomExts() {
        val doc = MutableYapiApiDoc.create(
            title = "T", path = "/p", method = "get",
            exts = mapOf("custom_field" to "custom_value", "is_custom" to true)
        )
        assertEquals(mapOf("custom_field" to "custom_value", "is_custom" to true), doc.getExts())
    }

    @Test
    fun testBuildApiDocBodyMergesDocExtensions() {
        val doc = MutableYapiApiDoc.create(
            title = "T", path = "/p", method = "get",
            exts = mapOf("custom_field" to "custom_value", "api_opened" to 1)
        )
        val json = formatter.buildApiDocBody(doc, "tok", "cat1")
        val obj = JsonParser.parseString(json).asJsonObject

        assertEquals("custom_value", obj.get("custom_field").asString)
        // Extension value overwrites the standard field
        assertEquals(1, obj.get("api_opened").asInt)
    }

    @Test
    fun testBuildApiDocBodyMergesHeaderExtensions() {
        val doc = MutableYapiApiDoc.create(
            title = "T", path = "/p", method = "get",
            reqHeaders = listOf(
                MutableYapiHeader(
                    name = "Authorization",
                    value = "Bearer x"
                ).also { it.putAllExts(mapOf("custom_header_field" to "extra")) }
            )
        )
        val json = formatter.buildApiDocBody(doc, "tok", "cat1")
        val obj = JsonParser.parseString(json).asJsonObject
        val header = obj.getAsJsonArray("req_headers")[0].asJsonObject

        assertEquals("Authorization", header.get("name").asString)
        assertEquals("extra", header.get("custom_header_field").asString)
    }

    @Test
    fun testBuildApiDocBodyMergesQueryExtensions() {
        val doc = MutableYapiApiDoc.create(
            title = "T", path = "/p", method = "get",
            reqQuery = listOf(
                MutableYapiQuery(
                    name = "page",
                    value = "1"
                ).also { it.putAllExts(mapOf("custom_query_field" to "extra")) }
            )
        )
        val json = formatter.buildApiDocBody(doc, "tok", "cat1")
        val obj = JsonParser.parseString(json).asJsonObject
        val query = obj.getAsJsonArray("req_query")[0].asJsonObject

        assertEquals("page", query.get("name").asString)
        assertEquals("extra", query.get("custom_query_field").asString)
    }

    @Test
    fun testBuildApiDocBodyMergesPathParamExtensions() {
        val doc = MutableYapiApiDoc.create(
            title = "T", path = "/p/{id}", method = "get",
            reqParams = listOf(
                MutableYapiPathParam(
                    name = "id",
                    example = "1"
                ).also { it.putAllExts(mapOf("custom_param_field" to "extra")) }
            )
        )
        val json = formatter.buildApiDocBody(doc, "tok", "cat1")
        val obj = JsonParser.parseString(json).asJsonObject
        val param = obj.getAsJsonArray("req_params")[0].asJsonObject

        assertEquals("id", param.get("name").asString)
        assertEquals("extra", param.get("custom_param_field").asString)
    }

    @Test
    fun testBuildApiDocBodyMergesFormParamExtensions() {
        val doc = MutableYapiApiDoc.create(
            title = "T", path = "/p", method = "post",
            reqBodyForm = listOf(
                MutableYapiFormParam(
                    name = "file",
                    type = "file"
                ).also { it.putAllExts(mapOf("custom_form_field" to "extra")) }
            )
        )
        val json = formatter.buildApiDocBody(doc, "tok", "cat1")
        val obj = JsonParser.parseString(json).asJsonObject
        val form = obj.getAsJsonArray("req_body_form")[0].asJsonObject

        assertEquals("file", form.get("name").asString)
        assertEquals("file", form.get("type").asString)
        assertEquals("extra", form.get("custom_form_field").asString)
    }

    @Test
    fun testBuildApiDocBodyExtensionOverwritesStandardField() {
        val doc = MutableYapiApiDoc.create(
            title = "Original Title",
            path = "/p",
            method = "get",
            exts = mapOf("title" to "Overridden Title")
        )
        val json = formatter.buildApiDocBody(doc, "tok", "cat1")
        val obj = JsonParser.parseString(json).asJsonObject

        // Extension value should overwrite the standard field
        assertEquals("Overridden Title", obj.get("title").asString)
    }

    @Test
    fun testBuildApiDocBodyWithNoExtensions() {
        val doc = MutableYapiApiDoc.create(title = "T", path = "/p", method = "get")
        val json = formatter.buildApiDocBody(doc, "tok", "cat1")
        val obj = JsonParser.parseString(json).asJsonObject

        // Should have standard fields but no extra extension fields
        assertEquals("T", obj.get("title").asString)
        assertEquals("/p", obj.get("path").asString)
        assertFalse(obj.has("custom_field"))
    }

    // endregion

    // region YapiApiDoc interface

    @Test
    fun testYapiApiDocInterface() {
        val doc: YapiApiDoc = MutableYapiApiDoc.create(title = "T", path = "/p", method = "get")
        assertEquals("T", doc.title)
        assertEquals("/p", doc.path)
        assertEquals("get", doc.method)
        assertNull(doc.desc)
        assertNull(doc.markdown)
        assertNull(doc.status)
        assertNull(doc.tag)
        assertNull(doc.reqHeaders)
        assertNull(doc.reqQuery)
        assertNull(doc.reqParams)
        assertNull(doc.reqBodyForm)
        assertNull(doc.reqBodyOther)
        assertNull(doc.reqBodyType)
        assertFalse(doc.reqBodyIsJsonSchema)
        assertNull(doc.resBody)
        assertEquals("json", doc.resBodyType)
        assertTrue(doc.resBodyIsJsonSchema)
        assertNull(doc.tags)
        assertNull(doc.open)
        assertTrue(doc.getExts().isEmpty())
    }

    @Test
    fun testYapiApiDocPolymorphism() {
        val doc: YapiApiDoc = MutableYapiApiDoc.create(title = "A", path = "/a", method = "get")
        val copy: YapiApiDoc = MutableYapiApiDoc.from(doc)

        assertEquals("A", doc.title)
        assertEquals("A", copy.title)
    }

    @Test
    fun testBuildApiDocBodyAcceptsYapiApiDoc() {
        val info: YapiApiDoc = MutableYapiApiDoc.create(title = "T", path = "/p", method = "get")
        val json = formatter.buildApiDocBody(info, "tok", "cat1")
        val obj = JsonParser.parseString(json).asJsonObject

        assertEquals("T", obj.get("title").asString)
        assertEquals("/p", obj.get("path").asString)
    }

    @Test
    fun testBuildApiDocBodyAcceptsMutableYapiApiDoc() {
        val mutable = MutableYapiApiDoc.from(
            MutableYapiApiDoc.create(title = "T", path = "/p", method = "get")
        )
        mutable.title = "Changed"
        mutable.setExt("custom", "value")

        val json = formatter.buildApiDocBody(mutable, "tok", "cat1")
        val obj = JsonParser.parseString(json).asJsonObject

        assertEquals("Changed", obj.get("title").asString)
        assertEquals("value", obj.get("custom").asString)
    }

    // endregion

    // region MutableYapiApiDoc

    @Test
    fun testMutableYapiApiDocFromYapiApiDoc() {
        val doc = MutableYapiApiDoc.create(
            title = "Get User",
            path = "/api/users/{id}",
            method = "get",
            desc = "desc",
            markdown = "md",
            status = "done",
            tag = listOf("user"),
            reqHeaders = listOf(MutableYapiHeader(name = "Auth")),
            reqQuery = listOf(MutableYapiQuery(name = "page")),
            reqParams = listOf(MutableYapiPathParam(name = "id")),
            reqBodyForm = listOf(MutableYapiFormParam(name = "name")),
            reqBodyOther = "{}",
            reqBodyType = "json",
            reqBodyIsJsonSchema = true,
            resBody = "{}",
            resBodyType = "json",
            resBodyIsJsonSchema = false,
            tags = listOf("tag1"),
            open = true,
            exts = mapOf("key1" to "val1")
        )
        val mutable = MutableYapiApiDoc.from(doc)

        assertEquals("Get User", mutable.title)
        assertEquals("/api/users/{id}", mutable.path)
        assertEquals("get", mutable.method)
        assertEquals("desc", mutable.desc)
        assertEquals("md", mutable.markdown)
        assertEquals("done", mutable.status)
        assertEquals(listOf("user"), mutable.tag)
        assertEquals(1, mutable.reqHeaders?.size)
        assertEquals(1, mutable.reqQuery?.size)
        assertEquals(1, mutable.reqParams?.size)
        assertEquals(1, mutable.reqBodyForm?.size)
        assertEquals("{}", mutable.reqBodyOther)
        assertEquals("json", mutable.reqBodyType)
        assertTrue(mutable.reqBodyIsJsonSchema)
        assertEquals("{}", mutable.resBody)
        assertEquals("json", mutable.resBodyType)
        assertFalse(mutable.resBodyIsJsonSchema)
        assertEquals(listOf("tag1"), mutable.tags)
        assertEquals(true, mutable.open)
        assertEquals(mapOf("key1" to "val1"), mutable.getExts())
    }

    @Test
    fun testMutableYapiApiDocFromMutableYapiApiDoc() {
        val original = MutableYapiApiDoc.from(
            MutableYapiApiDoc.create(title = "T", path = "/p", method = "get", exts = mapOf("k" to "v"))
        )
        original.setExt("extra", "data")
        val copy = MutableYapiApiDoc.from(original)

        assertEquals("T", copy.title)
        assertEquals(mapOf("k" to "v", "extra" to "data"), copy.getExts())
    }

    @Test
    fun testMutableYapiApiDocPropertyMutation() {
        val mutable = MutableYapiApiDoc.from(
            MutableYapiApiDoc.create(title = "Original", path = "/original", method = "get")
        )

        mutable.title = "Changed"
        mutable.path = "/changed"
        mutable.method = "post"
        mutable.desc = "new desc"
        mutable.markdown = "new md"
        mutable.status = "undone"
        mutable.tag = listOf("new-tag")
        mutable.reqHeaders = listOf(MutableYapiHeader(name = "X-New"))
        mutable.reqQuery = listOf(MutableYapiQuery(name = "q"))
        mutable.reqParams = listOf(MutableYapiPathParam(name = "p"))
        mutable.reqBodyForm = listOf(MutableYapiFormParam(name = "f"))
        mutable.reqBodyOther = """{"type":"object"}"""
        mutable.reqBodyType = "json"
        mutable.reqBodyIsJsonSchema = true
        mutable.resBody = """{"type":"array"}"""
        mutable.resBodyType = "json"
        mutable.resBodyIsJsonSchema = false
        mutable.tags = listOf("new-tag")
        mutable.open = false

        assertEquals("Changed", mutable.title)
        assertEquals("/changed", mutable.path)
        assertEquals("post", mutable.method)
        assertEquals("new desc", mutable.desc)
        assertEquals("new md", mutable.markdown)
        assertEquals("undone", mutable.status)
        assertEquals(listOf("new-tag"), mutable.tag)
        assertEquals(1, mutable.reqHeaders!!.size)
        assertEquals("X-New", mutable.reqHeaders!![0].name)
        assertEquals(1, mutable.reqQuery!!.size)
        assertEquals(1, mutable.reqParams!!.size)
        assertEquals(1, mutable.reqBodyForm!!.size)
        assertEquals("""{"type":"object"}""", mutable.reqBodyOther)
        assertEquals("json", mutable.reqBodyType)
        assertTrue(mutable.reqBodyIsJsonSchema)
        assertEquals("""{"type":"array"}""", mutable.resBody)
        assertFalse(mutable.resBodyIsJsonSchema)
        assertEquals(listOf("new-tag"), mutable.tags)
        assertEquals(false, mutable.open)
    }

    @Test
    fun testMutableYapiApiDocSetExt() {
        val mutable = MutableYapiApiDoc.from(
            MutableYapiApiDoc.create(title = "T", path = "/p", method = "get")
        )

        assertTrue(mutable.getExts().isEmpty())

        mutable.setExt("custom_field", "custom_value")
        assertEquals("custom_value", mutable.getExts()["custom_field"])

        mutable.setExt("custom_field", "updated")
        assertEquals("updated", mutable.getExts()["custom_field"])

        mutable.setExt("null_field", null)
        assertTrue(mutable.getExts().containsKey("null_field"))
        assertNull(mutable.getExts()["null_field"])
    }

    @Test
    fun testMutableYapiApiDocPutAllExts() {
        val mutable = MutableYapiApiDoc.from(
            MutableYapiApiDoc.create(title = "T", path = "/p", method = "get", exts = mapOf("a" to 1))
        )

        mutable.putAllExts(mapOf("b" to 2, "c" to 3))
        assertEquals(mapOf("a" to 1, "b" to 2, "c" to 3), mutable.getExts())

        mutable.putAllExts(mapOf("a" to 10))
        assertEquals(10, mutable.getExts()["a"])
    }

    @Test
    fun testMutableYapiApiDocExtsInheritedFromSource() {
        val doc = MutableYapiApiDoc.create(
            title = "T", path = "/p", method = "get",
            exts = mapOf("inherited" to true)
        )
        val mutable = MutableYapiApiDoc.from(doc)

        assertEquals(mapOf("inherited" to true), mutable.getExts())

        // Modifying mutable exts should not affect the original
        mutable.setExt("new_key", "new_val")
        assertEquals(mapOf("inherited" to true), doc.getExts())
        assertEquals(mapOf("inherited" to true, "new_key" to "new_val"), mutable.getExts())
    }

    @Test
    fun testMutableYapiApiDocBuildApiDocBody() {
        val mutable = MutableYapiApiDoc.from(
            MutableYapiApiDoc.create(title = "T", path = "/p", method = "get")
        )
        mutable.title = "Modified Title"
        mutable.setExt("custom_ext", "ext_value")

        val json = formatter.buildApiDocBody(mutable, "tok", "cat1")
        val obj = JsonParser.parseString(json).asJsonObject

        assertEquals("Modified Title", obj.get("title").asString)
        assertEquals("ext_value", obj.get("custom_ext").asString)
    }

    @Test
    fun testMutableYapiApiDocExtensionOverwritesStandardField() {
        val mutable = MutableYapiApiDoc.from(
            MutableYapiApiDoc.create(title = "Original", path = "/p", method = "get")
        )
        mutable.setExt("title", "Overridden")

        val json = formatter.buildApiDocBody(mutable, "tok", "cat1")
        val obj = JsonParser.parseString(json).asJsonObject

        assertEquals("Overridden", obj.get("title").asString)
    }

    // endregion
}
