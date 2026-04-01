package com.itangcent.easyapi.http

import org.junit.Assert.*
import org.junit.Test

class HttpModelsTest {

    @Test
    fun testKeyValueCreation() {
        val kv = kv("Content-Type", "application/json")
        assertEquals("Content-Type", kv.name)
        assertEquals("application/json", kv.value)
        assertEquals("Content-Type", kv.first)
        assertEquals("application/json", kv.second)
    }

    @Test
    fun testKeyValuePair() {
        val pair: KeyValue = "Authorization" to "Bearer token"
        assertEquals("Authorization", pair.name)
        assertEquals("Bearer token", pair.value)
    }
}

class FormParamTest {

    @Test
    fun testFormParamText() {
        val textParam = FormParam.Text("username", "john")
        assertEquals("username", textParam.name)
        assertEquals("john", textParam.value)
    }

    @Test
    fun testFormParamTextEquality() {
        val param1 = FormParam.Text("field", "value")
        val param2 = FormParam.Text("field", "value")
        assertEquals(param1, param2)
    }

    @Test
    fun testFormParamFile() {
        val fileParam = FormParam.File(
            name = "avatar",
            fileName = "profile.jpg",
            contentType = "image/jpeg",
            bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        )
        assertEquals("avatar", fileParam.name)
        assertEquals("profile.jpg", fileParam.fileName)
        assertEquals("image/jpeg", fileParam.contentType)
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()), fileParam.bytes)
    }

    @Test
    fun testFormParamFileEquality() {
        val bytes = byteArrayOf(0x01, 0x02, 0x03)
        val file1 = FormParam.File("file", "test.txt", "text/plain", bytes)
        val file2 = FormParam.File("file", "test.txt", "text/plain", byteArrayOf(0x01, 0x02, 0x03))
        assertEquals(file1, file2)
    }

    @Test
    fun testFormParamFileWithoutContentType() {
        val fileParam = FormParam.File(
            name = "document",
            fileName = "data.bin",
            bytes = byteArrayOf(0x00, 0x01)
        )
        assertEquals("document", fileParam.name)
        assertEquals("data.bin", fileParam.fileName)
        assertNull(fileParam.contentType)
    }
}

class HttpRequestTest {

    @Test
    fun testHttpRequestCreation() {
        val request = HttpRequest(
            url = "http://api.example.com/users",
            method = "GET",
            headers = listOf(kv("Accept", "application/json")),
            query = listOf(kv("page", "1"))
        )
        assertEquals("http://api.example.com/users", request.url)
        assertEquals("GET", request.method)
        assertEquals(1, request.headers.size)
        assertEquals(1, request.query.size)
    }

    @Test
    fun testHttpRequestWithDefaults() {
        val request = HttpRequest(url = "http://example.com")
        assertEquals("http://example.com", request.url)
        assertEquals("GET", request.method)
        assertTrue(request.headers.isEmpty())
        assertTrue(request.query.isEmpty())
        assertNull(request.body)
        assertTrue(request.formParams.isEmpty())
        assertTrue(request.cookies.isEmpty())
        assertNull(request.contentType)
    }

    @Test
    fun testHttpRequestWithBody() {
        val request = HttpRequest(
            url = "http://api.example.com/users",
            method = "POST",
            body = "{\"name\": \"John\"}",
            contentType = "application/json"
        )
        assertEquals("POST", request.method)
        assertEquals("{\"name\": \"John\"}", request.body)
        assertEquals("application/json", request.contentType)
    }

    @Test
    fun testHttpRequestWithFormParams() {
        val request = HttpRequest(
            url = "http://api.example.com/upload",
            method = "POST",
            formParams = listOf(
                FormParam.Text("username", "john"),
                FormParam.File("avatar", "photo.jpg", "image/jpeg", byteArrayOf(0xFF.toByte()))
            )
        )
        assertEquals(2, request.formParams.size)
    }
}

class HttpRequestExtensionsTest {

    @Test
    fun testIsMultipartWithFile() {
        val request = HttpRequest(
            url = "http://example.com/upload",
            formParams = listOf(FormParam.File("file", "test.txt", bytes = byteArrayOf()))
        )
        assertTrue(request.isMultipart())
    }

    @Test
    fun testIsMultipartWithContentType() {
        val request = HttpRequest(
            url = "http://example.com/upload",
            contentType = "multipart/form-data"
        )
        assertTrue(request.isMultipart())
    }

    @Test
    fun testIsMultipartFalse() {
        val request = HttpRequest(
            url = "http://example.com/submit",
            formParams = listOf(FormParam.Text("name", "value"))
        )
        assertFalse(request.isMultipart())
    }

    @Test
    fun testTextFormParams() {
        val request = HttpRequest(
            url = "http://example.com/submit",
            formParams = listOf(
                FormParam.Text("username", "john"),
                FormParam.Text("email", "john@example.com"),
                FormParam.File("avatar", "photo.jpg", bytes = byteArrayOf())
            )
        )
        val textParams = request.textFormParams()
        assertEquals(2, textParams.size)
        assertEquals("john", textParams["username"])
        assertEquals("john@example.com", textParams["email"])
    }

    @Test
    fun testBuildUrlWithoutQuery() {
        val request = HttpRequest(url = "http://example.com/api")
        assertEquals("http://example.com/api", request.buildUrl())
    }

    @Test
    fun testBuildUrlWithQuery() {
        val request = HttpRequest(
            url = "http://example.com/api",
            query = listOf(kv("page", "1"), kv("size", "10"))
        )
        val builtUrl = request.buildUrl()
        assertTrue(builtUrl.startsWith("http://example.com/api?"))
        assertTrue(builtUrl.contains("page=1"))
        assertTrue(builtUrl.contains("size=10"))
    }

    @Test
    fun testBuildUrlWithExistingQuery() {
        val request = HttpRequest(
            url = "http://example.com/api?version=1",
            query = listOf(kv("page", "1"))
        )
        val builtUrl = request.buildUrl()
        assertTrue(builtUrl.startsWith("http://example.com/api?version=1&"))
        assertTrue(builtUrl.contains("page=1"))
    }
}

class HttpResponseTest {

    @Test
    fun testHttpResponseCreation() {
        val response = HttpResponse(
            code = 200,
            headers = mapOf("Content-Type" to listOf("application/json")),
            body = "{\"id\": 1}"
        )
        assertEquals(200, response.code)
        assertEquals(1, response.headers.size)
        assertEquals("{\"id\": 1}", response.body)
    }

    @Test
    fun testHttpResponseWithDefaults() {
        val response = HttpResponse(code = 404)
        assertEquals(404, response.code)
        assertTrue(response.headers.isEmpty())
        assertNull(response.body)
    }

    @Test
    fun testHttpResponseEquality() {
        val resp1 = HttpResponse(200, mapOf("X-Custom" to listOf("value")), "OK")
        val resp2 = HttpResponse(200, mapOf("X-Custom" to listOf("value")), "OK")
        assertEquals(resp1, resp2)
    }
}

class HttpCookieTest {

    @Test
    fun testHttpCookieCreation() {
        val cookie = HttpCookie(
            name = "session",
            value = "abc123",
            domain = "example.com",
            path = "/"
        )
        assertEquals("session", cookie.name)
        assertEquals("abc123", cookie.value)
        assertEquals("example.com", cookie.domain)
        assertEquals("/", cookie.path)
    }

    @Test
    fun testHttpCookieWithDefaults() {
        val cookie = HttpCookie(name = "token", value = "xyz")
        assertEquals("token", cookie.name)
        assertEquals("xyz", cookie.value)
        assertNull(cookie.domain)
        assertNull(cookie.path)
    }

    @Test
    fun testHttpCookieEquality() {
        val cookie1 = HttpCookie("id", "123", "example.com", "/")
        val cookie2 = HttpCookie("id", "123", "example.com", "/")
        assertEquals(cookie1, cookie2)
    }
}
