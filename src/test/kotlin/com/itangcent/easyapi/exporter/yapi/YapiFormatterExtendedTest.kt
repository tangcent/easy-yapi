package com.itangcent.easyapi.exporter.yapi

import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.ApiHeader
import com.itangcent.easyapi.exporter.model.ApiParameter
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.ParameterBinding
import com.itangcent.easyapi.exporter.yapi.model.YapiApiDoc
import com.itangcent.easyapi.psi.model.FieldModel
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.type.JsonType
import org.junit.Assert.*
import org.junit.Test

class YapiFormatterExtendedTest {

    private val formatter = YapiFormatter()

    @Test
    fun testFormatWithMultipleUrls() {
        val endpoint = ApiEndpoint(
            name = "Multi-Environment API",
            path = "/api/users",
            method = HttpMethod.GET,
            alternativePaths = listOf(
                "http://dev.example.com/api/users",
                "http://staging.example.com/api/users",
                "http://prod.example.com/api/users"
            )
        )

        val docs = formatter.formatWithUrls(endpoint)

        assertEquals(3, docs.size)
        assertEquals("http://dev.example.com/api/users", docs[0].path)
        assertEquals("http://staging.example.com/api/users", docs[1].path)
        assertEquals("http://prod.example.com/api/users", docs[2].path)
    }

    @Test
    fun testFormatWithJsonSchema() {
        val endpoint = ApiEndpoint(
            name = "Create User",
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

        val doc = formatter.format(endpoint)

        assertNotNull(doc.reqBodyOther)
        assertTrue("Should contain properties", doc.reqBodyOther!!.contains("properties"))
        assertTrue("Should contain name", doc.reqBodyOther!!.contains("name"))
        assertTrue("Should contain age", doc.reqBodyOther!!.contains("age"))
        assertTrue("Should contain email", doc.reqBodyOther!!.contains("email"))
    }

    @Test
    fun testFormatWithJson5Body() {
        val json5Formatter = YapiFormatter(useJson5 = true)
        val endpoint = ApiEndpoint(
            name = "Create User",
            path = "/api/users",
            method = HttpMethod.POST,
            body = ObjectModel.Object(
                mapOf(
                    "name" to FieldModel(ObjectModel.Single(JsonType.STRING), "User name"),
                    "age" to FieldModel(ObjectModel.Single(JsonType.INT), "User age")
                )
            )
        )

        val doc = json5Formatter.format(endpoint)

        assertNotNull(doc.reqBodyOther)
        assertTrue("Should contain comment", doc.reqBodyOther!!.contains("//") || doc.reqBodyOther!!.contains("User name"))
    }

    @Test
    fun testFormatWithMockData() {
        val endpoint = ApiEndpoint(
            name = "Get User",
            path = "/api/users/{id}",
            method = HttpMethod.GET,
            parameters = listOf(
                ApiParameter(
                    name = "id",
                    binding = ParameterBinding.Path,
                    type = "integer",
                    description = "User ID"
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
            path = "/api/users/{id}",
            method = HttpMethod.GET,
            tags = listOf("user", "admin")
        )

        val doc = formatter.format(endpoint)

        assertEquals(2, doc.tags?.size)
        assertTrue(doc.tags!!.contains("user"))
        assertTrue(doc.tags!!.contains("admin"))
    }

    @Test
    fun testFormatWithStatus() {
        val endpoint = ApiEndpoint(
            name = "Get User",
            path = "/api/users/{id}",
            method = HttpMethod.GET,
            status = "deprecated"
        )

        val doc = formatter.format(endpoint)

        assertEquals("deprecated", doc.status)
    }

    @Test
    fun testFormatWithRequiredParameters() {
        val endpoint = ApiEndpoint(
            name = "Create User",
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

        val doc = formatter.format(endpoint)

        assertNotNull(doc.reqBodyOther)
        assertTrue("Should contain required array", doc.reqBodyOther!!.contains("required"))
    }

    @Test
    fun testFormatWithResponseSchema() {
        val endpoint = ApiEndpoint(
            name = "Get User",
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

        val doc = formatter.format(endpoint)

        assertNotNull(doc.resBody)
        assertTrue("Should contain schema", doc.resBody!!.contains("type") || doc.resBody!!.contains("properties"))
    }

    @Test
    fun testFormatWithEnumValues() {
        val endpoint = ApiEndpoint(
            name = "Update User",
            path = "/api/users/{id}",
            method = HttpMethod.PUT,
            parameters = listOf(
                ApiParameter(
                    name = "status",
                    binding = ParameterBinding.Body,
                    type = "string",
                    description = "User status",
                    enumValues = listOf("active", "inactive", "deleted")
                )
            )
        )

        val doc = formatter.format(endpoint)

        assertNotNull(doc.reqBodyOther)
        assertTrue("Should contain enum", doc.reqBodyOther!!.contains("enum"))
    }

    @Test
    fun testFormatWithDefaultValues() {
        val endpoint = ApiEndpoint(
            name = "List Users",
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

        val doc = formatter.format(endpoint)

        assertEquals(2, doc.reqQuery?.size)
        assertEquals("1", doc.reqQuery?.get(0)?.value)
        assertEquals("10", doc.reqQuery?.get(1)?.value)
    }
}
