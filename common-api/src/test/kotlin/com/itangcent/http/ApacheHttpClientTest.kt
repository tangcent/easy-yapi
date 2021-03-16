package com.itangcent.http

import com.itangcent.common.utils.DateUtils
import org.apache.http.conn.ConnectTimeoutException
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApacheHttpClientTest {

    @Test
    fun testMethods() {
        val httpClient: HttpClient = ApacheHttpClient()

        //GET
        httpClient.get().url("https://github.com/tangcent/easy-yapi/pulls").let {
            assertEquals("https://github.com/tangcent/easy-yapi/pulls", it.url())
            assertEquals("GET", it.method())
        }
        httpClient.get("https://github.com/tangcent/easy-yapi/pulls").let {
            assertEquals("https://github.com/tangcent/easy-yapi/pulls", it.url())
            assertEquals("GET", it.method())
        }

        //POST
        httpClient.post().url("https://github.com/tangcent/easy-yapi/pulls").let {
            assertEquals("https://github.com/tangcent/easy-yapi/pulls", it.url())
            assertEquals("POST", it.method())
        }
        httpClient.post("https://github.com/tangcent/easy-yapi/pulls").let {
            assertEquals("https://github.com/tangcent/easy-yapi/pulls", it.url())
            assertEquals("POST", it.method())
        }

        //PUT
        httpClient.put().url("https://github.com/tangcent/easy-yapi/pulls").let {
            assertEquals("https://github.com/tangcent/easy-yapi/pulls", it.url())
            assertEquals("PUT", it.method())
        }
        httpClient.put("https://github.com/tangcent/easy-yapi/pulls").let {
            assertEquals("https://github.com/tangcent/easy-yapi/pulls", it.url())
            assertEquals("PUT", it.method())
        }

        //DELETE
        httpClient.delete().url("https://github.com/tangcent/easy-yapi/pulls").let {
            assertEquals("https://github.com/tangcent/easy-yapi/pulls", it.url())
            assertEquals("DELETE", it.method())
        }
        httpClient.delete("https://github.com/tangcent/easy-yapi/pulls").let {
            assertEquals("https://github.com/tangcent/easy-yapi/pulls", it.url())
            assertEquals("DELETE", it.method())
        }

        //OPTIONS
        httpClient.options().url("https://github.com/tangcent/easy-yapi/pulls").let {
            assertEquals("https://github.com/tangcent/easy-yapi/pulls", it.url())
            assertEquals("OPTIONS", it.method())
        }
        httpClient.options("https://github.com/tangcent/easy-yapi/pulls").let {
            assertEquals("https://github.com/tangcent/easy-yapi/pulls", it.url())
            assertEquals("OPTIONS", it.method())
        }

        //TRACE
        httpClient.trace().url("https://github.com/tangcent/easy-yapi/pulls").let {
            assertEquals("https://github.com/tangcent/easy-yapi/pulls", it.url())
            assertEquals("TRACE", it.method())
        }
        httpClient.trace("https://github.com/tangcent/easy-yapi/pulls").let {
            assertEquals("https://github.com/tangcent/easy-yapi/pulls", it.url())
            assertEquals("TRACE", it.method())
        }

        //PATCH
        httpClient.patch().url("https://github.com/tangcent/easy-yapi/pulls").let {
            assertEquals("https://github.com/tangcent/easy-yapi/pulls", it.url())
            assertEquals("PATCH", it.method())
        }
        httpClient.patch("https://github.com/tangcent/easy-yapi/pulls").let {
            assertEquals("https://github.com/tangcent/easy-yapi/pulls", it.url())
            assertEquals("PATCH", it.method())
        }

        //HEAD
        httpClient.head().url("https://github.com/tangcent/easy-yapi/pulls").let {
            assertEquals("https://github.com/tangcent/easy-yapi/pulls", it.url())
            assertEquals("HEAD", it.method())
        }
        httpClient.head("https://github.com/tangcent/easy-yapi/pulls").let {
            assertEquals("https://github.com/tangcent/easy-yapi/pulls", it.url())
            assertEquals("HEAD", it.method())
        }

    }

    @Test
    fun testHeaders() {
        val httpClient: HttpClient = ApacheHttpClient()
        val request = httpClient.request()
        assertFalse(request.containsHeader("x-token"))
        assertNull(request.headers("x-token"))
        assertNull(request.firstHeader("x-token"))
        assertNull(request.lastHeader("x-token"))

        request.header("x-token", "111111")
        assertTrue(request.containsHeader("x-token"))
        assertArrayEquals(arrayOf("111111"), request.headers("x-token"))
        assertEquals("111111", request.firstHeader("x-token"))
        assertEquals("111111", request.lastHeader("x-token"))

        request.header(BasicHttpHeader("x-token", "222222"))
        assertTrue(request.containsHeader("x-token"))
        assertArrayEquals(arrayOf("111111", "222222"), request.headers("x-token"))
        assertEquals("111111", request.firstHeader("x-token"))
        assertEquals("222222", request.lastHeader("x-token"))
    }

    @Test
    fun testQuery() {
        val httpClient: HttpClient = ApacheHttpClient()
        val request = httpClient.request()
        assertNull(request.querys())
        request.query("q", "test")
        assertNotNull(request.querys())
    }

    @Test
    fun testBody() {
        val httpClient: HttpClient = ApacheHttpClient()
        val request = httpClient.request()
        assertNull(request.body())
        request.body("body")
        assertEquals("body", request.body())
        request.body(1)
        assertEquals(1, request.body())
    }

    @Test
    fun testContentType() {
        val httpClient: HttpClient = ApacheHttpClient()
        val request = httpClient.request()
        assertNull(request.contentType())
        request.contentType("application/json")
        assertEquals("application/json", request.contentType())
        assertEquals("application/json", request.firstHeader("content-type"))
    }

    @Test
    fun testParams() {
        val httpClient: HttpClient = ApacheHttpClient()
        val request = httpClient.request()
        assertFalse(request.containsParam("auth"))
        assertNull(request.params("auth"))
        assertNull(request.firstParam("auth"))
        assertNull(request.lastParam("auth"))

        request.param("auth", "111111")
        assertTrue(request.containsParam("auth"))
        assertArrayEquals(arrayOf("111111"), request.paramValues("auth"))
        assertEquals("111111", request.firstParamValue("auth"))
        assertEquals("111111", request.lastParamValue("auth"))
        request.firstParam("auth")?.let {
            assertEquals("auth", it.name())
            assertEquals("111111", it.value())
            assertEquals("text", it.type())
        }

        request.fileParam("auth", "222222")
        assertTrue(request.containsParam("auth"))
        assertArrayEquals(arrayOf("111111", "222222"), request.paramValues("auth"))
        assertEquals("111111", request.firstParamValue("auth"))
        assertEquals("222222", request.lastParamValue("auth"))
        request.lastParam("auth")?.let {
            assertEquals("auth", it.name())
            assertEquals("222222", it.value())
            assertEquals("file", it.type())
        }
    }

    @Test
    fun testCookies() {
        val httpClient: HttpClient = ApacheHttpClient()
        val cookieStore = httpClient.cookieStore()
        assertTrue(cookieStore.cookies().isEmpty())

        val token = cookieStore.newCookie()
        token.setName("token")
        token.setValue("111111")
        token.setExpiryDate(DateUtils.parse("2021-01-01").time)
        token.setDomain("github.com")
        token.setSecure(false)
        token.setPath("/")
        token.setVersion(100)

        //add cookie which is expired
        cookieStore.addCookie(token)
        assertTrue(cookieStore.cookies().isEmpty())

        token.setExpiryDate(DateUtils.parse("2099-01-01").time)
        cookieStore.addCookie(token)

        val cookies = cookieStore.cookies()
        assertEquals(1, cookies.size)
        cookies.first().let {
            assertEquals("token", it.getName())
            assertEquals("111111", it.getValue())
            assertEquals("github.com", it.getDomain())
            assertEquals("/", it.getPath())
            assertEquals(100, it.getVersion())
            assertEquals(false, it.isSecure())
            assertEquals(DateUtils.parse("2099-01-01").time, it.getExpiryDate())
        }

        cookieStore.clear()
        assertTrue(cookieStore.cookies().isEmpty())
    }

    @Test
    fun testCall() {
        try {
            val httpResponse = ApacheHttpClient().request()
                    .url("https://github.com/tangcent/easy-yapi/pulls")
                    .call()
            assertEquals(200, httpResponse.code())
        } catch (e: ConnectTimeoutException) {
            //skip test if connect timed out
        }
    }
}