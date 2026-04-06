package com.itangcent.easyapi.exporter.markdown

import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.ApiHeader
import com.itangcent.easyapi.exporter.model.ApiParameter
import com.itangcent.easyapi.exporter.model.HttpMetadata
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.ParameterBinding
import com.itangcent.easyapi.exporter.model.ParameterType
import com.itangcent.easyapi.psi.model.FieldModel
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.type.JsonType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DefaultMarkdownFormatterTest {

    private val formatter = DefaultMarkdownFormatter(outputDemo = true)

    @Test
    fun testFormatSimpleEndpoint() = runBlocking {
        val endpoint = ApiEndpoint(
            name = "Get User",
            metadata = HttpMetadata(
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
            metadata = HttpMetadata(
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
            metadata = HttpMetadata(
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
            metadata = HttpMetadata(
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
                metadata = HttpMetadata(
                    path = "/api/users/{id}",
                    method = HttpMethod.GET
                )
            ),
            ApiEndpoint(
                name = "Create User",
                folder = "Users",
                metadata = HttpMetadata(
                    path = "/api/users",
                    method = HttpMethod.POST
                )
            ),
            ApiEndpoint(
                name = "Get Order",
                folder = "Orders",
                metadata = HttpMetadata(
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
            metadata = HttpMetadata(
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
            metadata = HttpMetadata(
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
            metadata = HttpMetadata(
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
            metadata = HttpMetadata(
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
            ApiEndpoint(name = "Get User", metadata = HttpMetadata(path = "/api/users/{id}", method = HttpMethod.GET)),
            ApiEndpoint(name = "Create User", metadata = HttpMetadata(path = "/api/users", method = HttpMethod.POST)),
            ApiEndpoint(name = "Update User", metadata = HttpMetadata(path = "/api/users/{id}", method = HttpMethod.PUT)),
            ApiEndpoint(name = "Delete User", metadata = HttpMetadata(path = "/api/users/{id}", method = HttpMethod.DELETE))
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
            metadata = HttpMetadata(
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
}
