package com.itangcent.easyapi.exporter.httpclient

import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.ApiHeader
import com.itangcent.easyapi.exporter.model.ApiParameter
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.ParameterBinding
import org.junit.Assert.*
import org.junit.Test

class HttpClientFileFormatterTest {

    @Test
    fun testFormatSimpleGet() {
        val endpoints = listOf(
            ApiEndpoint(
                name = "Get User",
                path = "/api/users/1",
                method = HttpMethod.GET
            )
        )

        val result = HttpClientFileFormatter.format(endpoints, "http://localhost:8080")

        assertTrue(result.contains("GET http://localhost:8080/api/users/1"))
    }

    @Test
    fun testFormatPostWithBody() {
        val endpoints = listOf(
            ApiEndpoint(
                name = "Create User",
                path = "/api/users",
                method = HttpMethod.POST,
                contentType = "application/json",
                parameters = listOf(
                    ApiParameter(name = "name", binding = ParameterBinding.Body, example = "John"),
                    ApiParameter(name = "age", binding = ParameterBinding.Body, example = "25")
                )
            )
        )

        val result = HttpClientFileFormatter.format(endpoints, "http://localhost:8080")

        assertTrue(result.contains("POST http://localhost:8080/api/users"))
        assertTrue(result.contains("Content-Type: application/json"))
        assertTrue(result.contains("name"))
        assertTrue(result.contains("John"))
    }

    @Test
    fun testFormatWithQueryParams() {
        val endpoints = listOf(
            ApiEndpoint(
                name = "List Users",
                path = "/api/users",
                method = HttpMethod.GET,
                parameters = listOf(
                    ApiParameter(name = "page", binding = ParameterBinding.Query, example = "1"),
                    ApiParameter(name = "size", binding = ParameterBinding.Query, example = "10")
                )
            )
        )

        val result = HttpClientFileFormatter.format(endpoints, "http://localhost:8080")

        assertTrue(result.contains("page=1"))
        assertTrue(result.contains("size=10"))
    }

    @Test
    fun testFormatWithHeaders() {
        val endpoints = listOf(
            ApiEndpoint(
                name = "Get User",
                path = "/api/users/1",
                method = HttpMethod.GET,
                headers = listOf(
                    ApiHeader("Authorization", "Bearer token123"),
                    ApiHeader("X-Request-Id", "abc123")
                )
            )
        )

        val result = HttpClientFileFormatter.format(endpoints, "http://localhost:8080")

        assertTrue(result.contains("Authorization: Bearer token123"))
        assertTrue(result.contains("X-Request-Id: abc123"))
    }

    @Test
    fun testFormatMultipleEndpoints() {
        val endpoints = listOf(
            ApiEndpoint(name = "Get User", path = "/api/users/1", method = HttpMethod.GET),
            ApiEndpoint(name = "Create User", path = "/api/users", method = HttpMethod.POST),
            ApiEndpoint(name = "Delete User", path = "/api/users/1", method = HttpMethod.DELETE)
        )

        val result = HttpClientFileFormatter.format(endpoints, "http://localhost:8080")

        assertTrue(result.contains("GET http://localhost:8080/api/users/1"))
        assertTrue(result.contains("POST http://localhost:8080/api/users"))
        assertTrue(result.contains("DELETE http://localhost:8080/api/users/1"))

        val separatorCount = result.windowed(3).count { it == "###" }
        assertEquals(2, separatorCount)
    }

    @Test
    fun testFormatWithFormParams() {
        val endpoints = listOf(
            ApiEndpoint(
                name = "Login",
                path = "/api/login",
                method = HttpMethod.POST,
                contentType = "application/x-www-form-urlencoded",
                parameters = listOf(
                    ApiParameter(name = "username", binding = ParameterBinding.Form, example = "admin"),
                    ApiParameter(name = "password", binding = ParameterBinding.Form, example = "secret")
                )
            )
        )

        val result = HttpClientFileFormatter.format(endpoints, "http://localhost:8080")

        assertTrue(result.contains("POST http://localhost:8080/api/login"))
        assertTrue(result.contains("username=admin"))
        assertTrue(result.contains("password=secret"))
    }

    @Test
    fun testFormatPutRequest() {
        val endpoints = listOf(
            ApiEndpoint(
                name = "Update User",
                path = "/api/users/1",
                method = HttpMethod.PUT,
                contentType = "application/json",
                parameters = listOf(
                    ApiParameter(name = "name", binding = ParameterBinding.Body, example = "Updated Name")
                )
            )
        )

        val result = HttpClientFileFormatter.format(endpoints, "http://localhost:8080")

        assertTrue(result.contains("PUT http://localhost:8080/api/users/1"))
        assertTrue(result.contains("Updated Name"))
    }

    @Test
    fun testFormatDeleteRequest() {
        val endpoints = listOf(
            ApiEndpoint(
                name = "Delete User",
                path = "/api/users/1",
                method = HttpMethod.DELETE
            )
        )

        val result = HttpClientFileFormatter.format(endpoints, "http://localhost:8080")

        assertTrue(result.contains("DELETE http://localhost:8080/api/users/1"))
    }

    @Test
    fun testFormatWithContentType() {
        val endpoints = listOf(
            ApiEndpoint(
                name = "Create User",
                path = "/api/users",
                method = HttpMethod.POST,
                contentType = "application/json;charset=UTF-8"
            )
        )

        val result = HttpClientFileFormatter.format(endpoints, "http://localhost:8080")

        assertTrue(result.contains("Content-Type: application/json;charset=UTF-8"))
    }

    @Test
    fun testFormatWithNoContentType() {
        val endpoints = listOf(
            ApiEndpoint(
                name = "Get User",
                path = "/api/users/1",
                method = HttpMethod.GET
            )
        )

        val result = HttpClientFileFormatter.format(endpoints, "http://localhost:8080")

        assertFalse(result.contains("Content-Type:"))
    }
}
