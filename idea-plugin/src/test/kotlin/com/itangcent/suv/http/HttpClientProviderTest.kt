package com.itangcent.suv.http

import com.google.inject.Inject
import com.itangcent.common.utils.DateUtils
import com.itangcent.common.utils.notNullOrBlank
import com.itangcent.http.*
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.with
import com.itangcent.mock.AdvancedContextTest
import com.itangcent.mock.SettingBinderAdaptor
import org.apache.http.entity.ContentType
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Test case of [HttpClientProvider]
 */
internal abstract class HttpClientProviderTest : AdvancedContextTest() {

    @Inject
    protected lateinit var httpClientProvider: HttpClientProvider

    abstract val httpClientProviderClass: KClass<out HttpClientProvider>

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.bind(HttpClientProvider::class) { it.with(httpClientProviderClass) }
        builder.bind(SettingBinder::class) {
            it.toInstance(SettingBinderAdaptor(Settings().also { settings ->
                settings.trustHosts = arrayOf(
                    "https://jsonplaceholder.typicode.com",
                    "!http://forbidden.com"
                )
            }))
        }
    }

    @Test
    fun `test callHttpGetRequest`() {
        // Build an instance of HttpClient using the provider.
        val httpClient = httpClientProvider.getHttpClient()

        // Create an instance of HttpRequest using the HttpClient instance.
        val httpRequest = httpClient.request()
            .method("GET")
            .url("https://jsonplaceholder.typicode.com/todos/1")

        // Send the request and receive the response.
        val httpResponse = httpRequest.call()

        // Assert that the response is not null.
        assertNotNull(httpResponse)

        // Assert that the response has a status code of 200.
        assertEquals(200, httpResponse.code())

        // Assert that the response has a non-empty entity.
        assertNotNull(httpResponse.bytes())
    }

    @Test
    fun `test callHttpPostRequest`() {
        // Build an instance of HttpClient using the provider.
        val httpClient = httpClientProvider.getHttpClient()

        // Create an instance of HttpRequest using the HttpClient instance.
        val httpRequest = httpClient.request()
            .method("POST")
            .url("https://jsonplaceholder.typicode.com/posts")
            .contentType(ContentType.TEXT_PLAIN.mimeType)
            .body("Hello, world!")

        // Send the request and receive the response.
        val httpResponse = httpRequest.call()

        // Assert that the response is not null.
        assertNotNull(httpResponse)

        // Assert that the response has a status code of 201.
        assertEquals(201, httpResponse.code())

        // Assert that the response has a non-empty entity.
        assertNotNull(httpResponse.bytes())

        // Assert that the response entity contains the expected text.
        assertTrue(httpResponse.string().notNullOrBlank())
    }

    @Test
    fun `test callHttpPutRequest`() {
        // Build an instance of HttpClient using the provider.
        val httpClient = httpClientProvider.getHttpClient()

        // Create an instance of HttpRequest using the HttpClient instance.
        val httpRequest = httpClient.request()
            .method("PUT")
            .url("https://jsonplaceholder.typicode.com/posts/1")
            .contentType(ContentType.TEXT_PLAIN.mimeType)
            .body("Hello, world!")

        // Send the request and receive the response.
        val httpResponse = httpRequest.call()

        // Assert that the response is not null.
        assertNotNull(httpResponse)

        // Assert that the response has a status code of 200.
        assertEquals(200, httpResponse.code())

        // Assert that the response has a non-empty entity.
        assertNotNull(httpResponse.bytes())

        // Assert that the response entity contains the expected text.
        assertTrue(httpResponse.string().notNullOrBlank())
    }

    @Test
    fun `test callHttpDeleteRequest`() {
        // Build an instance of HttpClient using the provider.
        val httpClient = httpClientProvider.getHttpClient()

        // Create an instance of HttpRequest using the HttpClient instance.
        val httpRequest = httpClient.request()
            .method("DELETE")
            .url("https://jsonplaceholder.typicode.com/posts/1")

        // Send the request and receive the response.
        val httpResponse = httpRequest.call()

        // Assert that the response is not null.
        assertNotNull(httpResponse)

        // Assert that the response has a status code of 200.
        assertEquals(200, httpResponse.code())

        // Assert that the response has a non-empty entity.
        assertNotNull(httpResponse.bytes())

        // Assert that the response entity contains the expected text.
        assertEquals("{}", httpResponse.string())
    }

    @Test
    fun testMethods() {
        val httpClient = httpClientProvider.getHttpClient()

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
        val httpClient = httpClientProvider.getHttpClient()
        val request = httpClient.request()

        assertFalse(request.containsHeader("x-token"))
        assertNull(request.headers("x-token"))
        assertNull(request.firstHeader("x-token"))
        assertNull(request.lastHeader("x-token"))

        assertDoesNotThrow { request.removeHeaders("x-token") }
        assertDoesNotThrow { request.removeHeader("x-token", "222222") }

        assertFalse(request.containsHeader("x-token"))
        assertNull(request.headers("x-token"))
        assertNull(request.firstHeader("x-token"))
        assertNull(request.lastHeader("x-token"))

        request.header("x-token", "111111")
        assertTrue(request.containsHeader("x-token"))
        Assertions.assertArrayEquals(arrayOf("111111"), request.headers("x-token"))
        assertEquals("111111", request.firstHeader("x-token"))
        assertEquals("111111", request.lastHeader("x-token"))

        request.header(BasicHttpHeader("x-token", null))
        request.header(BasicHttpHeader("x-token", "222222"))
        assertTrue(request.containsHeader("x-token"))
        Assertions.assertArrayEquals(arrayOf("111111", "222222"), request.headers("x-token"))
        assertEquals("111111", request.firstHeader("x-token"))
        assertEquals("222222", request.lastHeader("x-token"))

        request.removeHeader("x-token", "222222")
        assertTrue(request.containsHeader("x-token"))
        Assertions.assertArrayEquals(arrayOf("111111"), request.headers("x-token"))
        assertEquals("111111", request.firstHeader("x-token"))
        assertEquals("111111", request.lastHeader("x-token"))

        request.removeHeaders("x-token")
        assertFalse(request.containsHeader("x-token"))

        request.header("x-token", "111111")
        request.setHeader("x-token", "222222")
        Assertions.assertArrayEquals(arrayOf("222222"), request.headers("x-token"))
        assertEquals("222222", request.firstHeader("x-token"))
        assertEquals("222222", request.lastHeader("x-token"))
    }

    @Test
    fun testQuery() {
        val httpClient = httpClientProvider.getHttpClient()
        val request = httpClient.request()
        assertNull(request.querys())
        request.query("q", "test")
        kotlin.test.assertNotNull(request.querys())
    }

    @Test
    fun testBody() {
        val httpClient = httpClientProvider.getHttpClient()
        val request = httpClient.request()
        assertNull(request.body())
        request.body("body")
        assertEquals("body", request.body())
        request.body(1)
        assertEquals(1, request.body())
    }

    @Test
    fun testContentType() {
        val httpClient = httpClientProvider.getHttpClient()
        val request = httpClient.request()
        assertNull(request.contentType())
        request.contentType("application/json")
        assertEquals("application/json", request.contentType())
        assertEquals("application/json", request.firstHeader("content-type"))
        request.contentType(ContentType.IMAGE_PNG)
        assertEquals("image/png", request.contentType())
        assertEquals("image/png", request.firstHeader("content-type"))
    }

    @Test
    fun testParams() {
        val httpClient = httpClientProvider.getHttpClient()
        val request = httpClient.request()
        assertFalse(request.containsParam("auth"))
        assertNull(request.params("auth"))
        assertNull(request.firstParam("auth"))
        assertNull(request.lastParam("auth"))

        request.param("auth", "111111")
        assertTrue(request.containsParam("auth"))
        Assertions.assertArrayEquals(arrayOf("111111"), request.paramValues("auth"))
        assertEquals("111111", request.firstParamValue("auth"))
        assertEquals("111111", request.lastParamValue("auth"))
        request.firstParam("auth")?.let {
            assertEquals("auth", it.name())
            assertEquals("111111", it.value())
            assertEquals("text", it.type())
        }

        request.param("token", "xxxxx")
        request.param("auth", null)
        request.fileParam("auth", "222222")
        assertTrue(request.containsParam("auth"))
        Assertions.assertArrayEquals(arrayOf("111111", "222222"), request.paramValues("auth"))
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
        val httpClient = httpClientProvider.getHttpClient()
        val cookieStore = httpClient.cookieStore()
        assertTrue(cookieStore.cookies().isEmpty())

        val token = cookieStore.newCookie()
        token.setName("token")
        token.setValue("111111")
        token.setExpiryDate(DateUtils.parse("2021-01-01").time)
        token.setDomain("github.com")
        token.setPorts(intArrayOf(9999))
        token.setComment("for auth")
        token.setCommentURL("http://www.apache.org/licenses/LICENSE-2.0")
        token.setSecure(false)
        token.setPath("/")
        token.setVersion(100)
        assertTrue(token.isPersistent())

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
            assertEquals("for auth", it.getComment())
            assertEquals("http://www.apache.org/licenses/LICENSE-2.0", it.getCommentURL())
            assertEquals("/", it.getPath())
            assertEquals(100, it.getVersion())
            assertEquals(false, it.isSecure())
            assertEquals(DateUtils.parse("2099-01-01").time, it.getExpiryDate())

            val fromJson = BasicCookie.fromJson(it.json())
            assertEquals("token", fromJson.getName())
            assertEquals("111111", fromJson.getValue())
            assertEquals("github.com", fromJson.getDomain())
            assertEquals("for auth", fromJson.getComment())
            assertEquals("http://www.apache.org/licenses/LICENSE-2.0", fromJson.getCommentURL())
            assertEquals("/", fromJson.getPath())
            assertEquals(100, fromJson.getVersion())
            assertEquals(false, fromJson.isSecure())
            assertEquals(DateUtils.parse("2099-01-01").time, fromJson.getExpiryDate())

            val mutable = it.mutable()
            assertSame(mutable, mutable.mutable())
            assertEquals("token", mutable.getName())
            assertEquals("111111", mutable.getValue())
            assertEquals("github.com", mutable.getDomain())
            assertEquals("for auth", mutable.getComment())
            assertEquals("http://www.apache.org/licenses/LICENSE-2.0", mutable.getCommentURL())
            assertEquals("/", mutable.getPath())
            assertEquals(100, mutable.getVersion())
            assertEquals(false, mutable.isSecure())
            assertEquals(DateUtils.parse("2099-01-01").time, mutable.getExpiryDate())

            val str = it.toString()
            assertTrue(str.contains("token"))
            assertTrue(str.contains("111111"))
            assertTrue(str.contains("github.com"))
        }

        cookieStore.clear()
        assertTrue(cookieStore.cookies().isEmpty())
        cookieStore.addCookies(cookies.toTypedArray())
        assertEquals(1, cookies.size)

        token.setPorts(null)
        val apacheCookie = token.asApacheCookie()
        assertNull(apacheCookie.commentURL)
        assertTrue(apacheCookie.isPersistent)

        val packageApacheCookie = ApacheCookie(apacheCookie)
        assertEquals("token", packageApacheCookie.getName())
        assertEquals("111111", packageApacheCookie.getValue())
        assertEquals("github.com", packageApacheCookie.getDomain())
        assertEquals("for auth", packageApacheCookie.getComment())
        assertTrue(packageApacheCookie.isPersistent())
    }
}