package com.itangcent.easyapi.exporter.yapi

import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.ApiHeader
import com.itangcent.easyapi.exporter.model.ApiParameter
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.ParameterBinding
import org.junit.Assert.*
import org.junit.Test

class YapiFormatterTest {

    private val formatter = YapiFormatter()

    @Test
    fun testFormatSimpleEndpoint() {
        val endpoint = ApiEndpoint(
            name = "Get User",
            path = "/api/users/{id}",
            method = HttpMethod.GET,
            parameters = listOf(
                ApiParameter(name = "id", binding = ParameterBinding.Path, example = "1")
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
            path = "/api/users",
            method = HttpMethod.GET,
            parameters = listOf(
                ApiParameter(name = "page", binding = ParameterBinding.Query, defaultValue = "1"),
                ApiParameter(name = "size", binding = ParameterBinding.Query, defaultValue = "10")
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
            path = "/api/users",
            method = HttpMethod.POST,
            contentType = "application/json",
            parameters = listOf(
                ApiParameter(name = "name", binding = ParameterBinding.Body, example = "John"),
                ApiParameter(name = "email", binding = ParameterBinding.Body, example = "john@example.com")
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
            path = "/api/protected",
            method = HttpMethod.GET,
            headers = listOf(
                ApiHeader(name = "Authorization", value = "Bearer token"),
                ApiHeader(name = "X-Custom-Header", value = "custom-value")
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
            path = "/api/auth/login",
            method = HttpMethod.POST,
            contentType = "application/x-www-form-urlencoded",
            parameters = listOf(
                ApiParameter(name = "username", binding = ParameterBinding.Form, example = "admin"),
                ApiParameter(name = "password", binding = ParameterBinding.Form, example = "secret")
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
            path = "/api/users/{id}",
            method = HttpMethod.GET,
            description = "Retrieve user information by ID"
        )

        val doc = formatter.format(endpoint)

        assertEquals("Retrieve user information by ID", doc.desc)
    }
}
