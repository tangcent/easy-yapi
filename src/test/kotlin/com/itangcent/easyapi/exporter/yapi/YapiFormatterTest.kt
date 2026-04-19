package com.itangcent.easyapi.exporter.yapi

import com.itangcent.easyapi.exporter.model.*
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
        assertTrue(doc.reqBodyOther.contains("email"))
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
}
