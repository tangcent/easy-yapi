package com.itangcent.easyapi.core.http

import org.junit.Assert.*
import org.junit.Test

class HttpRequestWrapperTest {

    @Test
    fun testUrl() {
        val request = HttpRequest(
            url = "http://localhost:8080/api/users",
            method = "GET"
        )
        val wrapper = HttpRequestWrapper(request)
        
        assertEquals("http://localhost:8080/api/users", wrapper.url())
    }

    @Test
    fun testMethod() {
        val request = HttpRequest(
            url = "http://test.com",
            method = "POST"
        )
        val wrapper = HttpRequestWrapper(request)
        
        assertEquals("POST", wrapper.method())
    }

    @Test
    fun testHeaders() {
        val request = HttpRequest(
            url = "http://test.com",
            method = "GET",
            headers = listOf(KeyValue("Authorization", "Bearer token"))
        )
        val wrapper = HttpRequestWrapper(request)
        
        assertEquals(1, wrapper.headers().size)
        assertEquals("Authorization", wrapper.headers()[0].name)
        assertEquals("Bearer token", wrapper.headers()[0].value)
    }

    @Test
    fun testQuery() {
        val request = HttpRequest(
            url = "http://test.com",
            method = "GET",
            query = listOf(KeyValue("id", "123"))
        )
        val wrapper = HttpRequestWrapper(request)
        
        assertEquals(1, wrapper.query().size)
        assertEquals("id", wrapper.query()[0].name)
    }

    @Test
    fun testBody() {
        val request = HttpRequest(
            url = "http://test.com",
            method = "POST",
            body = "{\"name\":\"test\"}"
        )
        val wrapper = HttpRequestWrapper(request)
        
        assertEquals("{\"name\":\"test\"}", wrapper.body())
    }

    @Test
    fun testFormParams() {
        val request = HttpRequest(
            url = "http://test.com",
            method = "POST",
            formParams = listOf(FormParam.Text("username", "admin"))
        )
        val wrapper = HttpRequestWrapper(request)
        
        assertEquals(1, wrapper.formParams().size)
        assertEquals("username", wrapper.formParams()[0].name)
    }

    @Test
    fun testCookies() {
        val request = HttpRequest(
            url = "http://test.com",
            method = "GET",
            cookies = listOf(HttpCookie("session", "abc123"))
        )
        val wrapper = HttpRequestWrapper(request)
        
        assertEquals(1, wrapper.cookies().size)
        assertEquals("session", wrapper.cookies()[0].name)
    }

    @Test
    fun testContentType() {
        val request = HttpRequest(
            url = "http://test.com",
            method = "POST",
            contentType = "application/json"
        )
        val wrapper = HttpRequestWrapper(request)

        assertEquals("application/json", wrapper.contentType())
    }

    // ---- mutable header support ----

    @Test
    fun testSetHeaderAddsNewHeader() {
        val request = HttpRequest(url = "http://test.com", method = "GET")
        val wrapper = HttpRequestWrapper(request)

        assertEquals(0, wrapper.headers().size)
        wrapper.setHeader("Authorization", "Bearer token")
        assertEquals(1, wrapper.headers().size)
        assertEquals("Authorization", wrapper.headers()[0].name)
        assertEquals("Bearer token", wrapper.headers()[0].value)
    }

    @Test
    fun testSetHeaderUpsertsCaseInsensitively() {
        val request = HttpRequest(
            url = "http://test.com",
            method = "GET",
            headers = listOf(KeyValue("Content-Type", "application/json"))
        )
        val wrapper = HttpRequestWrapper(request)

        assertEquals(1, wrapper.headers().size)
        // Replace existing header (case-insensitive match)
        wrapper.setHeader("content-type", "text/plain")
        assertEquals("upsert should NOT add a duplicate header", 1, wrapper.headers().size)
        assertEquals("text/plain", wrapper.headers()[0].value)
    }

    @Test
    fun testSetHeaderReplacesAllMatchingCaseVariants() {
        val request = HttpRequest(
            url = "http://test.com",
            method = "GET",
            headers = listOf(KeyValue("X-Custom", "old"), KeyValue("x-custom", "dup"))
        )
        val wrapper = HttpRequestWrapper(request)

        wrapper.setHeader("X-CUSTOM", "new")
        assertEquals(1, wrapper.headers().size)
        assertEquals("new", wrapper.headers()[0].value)
    }

    @Test
    fun testRemoveHeaderIsCaseInsensitive() {
        val request = HttpRequest(
            url = "http://test.com",
            method = "GET",
            headers = listOf(KeyValue("Authorization", "Bearer x"), KeyValue("Content-Type", "application/json"))
        )
        val wrapper = HttpRequestWrapper(request)

        wrapper.removeHeader("authorization")
        assertEquals(1, wrapper.headers().size)
        assertEquals("Content-Type", wrapper.headers()[0].name)
    }

    @Test
    fun testRemoveHeaderNoOpWhenAbsent() {
        val request = HttpRequest(
            url = "http://test.com",
            method = "GET",
            headers = listOf(KeyValue("Content-Type", "application/json"))
        )
        val wrapper = HttpRequestWrapper(request)

        wrapper.removeHeader("Authorization")
        assertEquals(1, wrapper.headers().size)
    }

    @Test
    fun testToHttpRequestCopiesAllFieldsAndUsesHeaderOverrides() {
        val request = HttpRequest(
            url = "http://test.com/api",
            method = "POST",
            headers = listOf(KeyValue("Content-Type", "application/json")),
            query = listOf(KeyValue("page", "1")),
            body = """{"k":"v"}""",
            formParams = listOf(FormParam.Text("field", "val")),
            cookies = listOf(HttpCookie("session", "abc")),
            contentType = "application/json"
        )
        val wrapper = HttpRequestWrapper(request)

        wrapper.setHeader("Authorization", "Bearer newTok")
        wrapper.removeHeader("Content-Type")

        val built = wrapper.toHttpRequest()
        assertEquals("http://test.com/api", built.url)
        assertEquals("POST", built.method)
        assertEquals(1, built.headers.size)
        assertEquals("Authorization", built.headers[0].name)
        assertEquals("Bearer newTok", built.headers[0].value)
        assertEquals(1, built.query.size)
        assertEquals("""{"k":"v"}""", built.body)
        assertEquals(1, built.formParams.size)
        assertEquals(1, built.cookies.size)
        assertEquals("application/json", built.contentType)
    }

    @Test
    fun testToHttpRequestPreservesOriginalWhenNoMutation() {
        val request = HttpRequest(
            url = "http://test.com",
            method = "GET",
            headers = listOf(KeyValue("Authorization", "Bearer original"))
        )
        val wrapper = HttpRequestWrapper(request)

        val built = wrapper.toHttpRequest()
        assertEquals(request.headers, built.headers)
        assertEquals(request.url, built.url)
        assertEquals(request.method, built.method)
    }
}

class HttpResponseWrapperTest {

    @Test
    fun testCode() {
        val request = HttpRequest(url = "http://test.com", method = "GET")
        val response = HttpResponse(code = 200, body = "OK")
        val requestWrapper = HttpRequestWrapper(request)
        val wrapper = HttpResponseWrapper(response, requestWrapper)
        
        assertEquals(200, wrapper.code())
    }

    @Test
    fun testHeaders() {
        val request = HttpRequest(url = "http://test.com", method = "GET")
        val response = HttpResponse(
            code = 200,
            body = "OK",
            headers = mapOf("Content-Type" to listOf("application/json"))
        )
        val requestWrapper = HttpRequestWrapper(request)
        val wrapper = HttpResponseWrapper(response, requestWrapper)
        
        assertEquals(1, wrapper.headers().size)
        assertTrue(wrapper.headers().containsKey("Content-Type"))
    }

    @Test
    fun testBody() {
        val request = HttpRequest(url = "http://test.com", method = "GET")
        val response = HttpResponse(code = 200, body = "{\"result\":\"success\"}")
        val requestWrapper = HttpRequestWrapper(request)
        val wrapper = HttpResponseWrapper(response, requestWrapper)
        
        assertEquals("{\"result\":\"success\"}", wrapper.body())
    }

    @Test
    fun testRequest() {
        val request = HttpRequest(url = "http://test.com", method = "GET")
        val response = HttpResponse(code = 200, body = "OK")
        val requestWrapper = HttpRequestWrapper(request)
        val wrapper = HttpResponseWrapper(response, requestWrapper)
        
        assertEquals(requestWrapper, wrapper.request())
    }

    @Test
    fun testDiscard() {
        val request = HttpRequest(url = "http://test.com", method = "GET")
        val response = HttpResponse(code = 200, body = "OK")
        val requestWrapper = HttpRequestWrapper(request)
        val wrapper = HttpResponseWrapper(response, requestWrapper)
        
        assertFalse(wrapper.isDiscarded())
        wrapper.discard()
        assertTrue(wrapper.isDiscarded())
    }

    private fun wrapper(response: HttpResponse): HttpResponseWrapper {
        val request = HttpRequestWrapper(HttpRequest(url = "http://test.com", method = "GET"))
        return HttpResponseWrapper(response, request)
    }

    @Test
    fun `bytes returns null for text body`() {
        val w = wrapper(HttpResponse(code = 200, body = "hello", responseBody = ResponseBody.Text("hello")))
        assertNull(w.bytes())
    }

    @Test
    fun `bytes returns raw bytes for Bytes body`() {
        val bytes = byteArrayOf(0, 1, 2, 0xFF.toByte())
        val w = wrapper(HttpResponse(code = 200, responseBody = ResponseBody.Bytes(bytes)))
        assertArrayEquals(bytes, w.bytes())
    }

    @Test
    fun `bytes returns null for spilled file body`() {
        val src = java.nio.file.Files.createTempFile("easyapi-wrapper", null)
        try {
            val w = wrapper(HttpResponse(code = 200, responseBody = ResponseBody.File(src)))
            assertNull(w.bytes())
        } finally {
            java.nio.file.Files.deleteIfExists(src)
        }
    }

    @Test
    fun `saveBody writes text body`() {
        val w = wrapper(HttpResponse(code = 200, body = "hello", responseBody = ResponseBody.Text("hello")))
        val target = java.nio.file.Files.createTempFile("easyapi-wrapper", null).toFile()
        try {
            assertTrue(w.saveBody(target.path))
            assertEquals("hello", target.readText(Charsets.UTF_8))
        } finally {
            target.delete()
        }
    }

    @Test
    fun `saveBody writes binary body`() {
        val bytes = byteArrayOf(0, 1, 2, 0xFF.toByte())
        val w = wrapper(HttpResponse(code = 200, responseBody = ResponseBody.Bytes(bytes)))
        val target = java.nio.file.Files.createTempFile("easyapi-wrapper", null).toFile()
        try {
            assertTrue(w.saveBody(target.path))
            assertArrayEquals(bytes, target.readBytes())
        } finally {
            target.delete()
        }
    }

    @Test
    fun `saveBody copies spilled file`() {
        val src = java.nio.file.Files.createTempFile("easyapi-wrapper-src", null)
        java.nio.file.Files.write(src, byteArrayOf(9, 8, 7))
        val w = wrapper(HttpResponse(code = 200, responseBody = ResponseBody.File(src)))
        val target = java.nio.file.Files.createTempFile("easyapi-wrapper", null).toFile()
        try {
            assertTrue(w.saveBody(target.path))
            assertArrayEquals(byteArrayOf(9, 8, 7), target.readBytes())
        } finally {
            java.nio.file.Files.deleteIfExists(src)
            target.delete()
        }
    }

    @Test
    fun `saveBody falls back to body text when no carrier`() {
        val w = wrapper(HttpResponse(code = 200, body = "legacy"))
        val target = java.nio.file.Files.createTempFile("easyapi-wrapper", null).toFile()
        try {
            assertTrue(w.saveBody(target.path))
            assertEquals("legacy", target.readText(Charsets.UTF_8))
        } finally {
            target.delete()
        }
    }

    @Test
    fun `saveBody returns false when no body at all`() {
        val w = wrapper(HttpResponse(code = 204))
        val target = java.nio.file.Files.createTempFile("easyapi-wrapper", null).toFile()
        try {
            assertFalse(w.saveBody(target.path))
        } finally {
            target.delete()
        }
    }
}
