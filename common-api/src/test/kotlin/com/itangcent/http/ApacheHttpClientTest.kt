package com.itangcent.http

import com.itangcent.common.utils.DateUtils
import com.itangcent.common.utils.FileUtils
import com.itangcent.common.utils.mapToTypedArray
import com.itangcent.common.utils.readString
import org.apache.http.HttpEntity
import org.apache.http.HttpEntityEnclosingRequest
import org.apache.http.HttpResponse
import org.apache.http.HttpVersion
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.config.SocketConfig
import org.apache.http.conn.ConnectTimeoutException
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClients
import org.apache.http.message.BasicStatusLine
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.nio.charset.Charset
import java.nio.file.Path
import kotlin.test.*

/**
 * Test case of [ApacheHttpClient]
 */
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

        assertDoesNotThrow { request.removeHeaders("x-token") }
        assertDoesNotThrow { request.removeHeader("x-token", "222222") }

        assertFalse(request.containsHeader("x-token"))
        assertNull(request.headers("x-token"))
        assertNull(request.firstHeader("x-token"))
        assertNull(request.lastHeader("x-token"))

        request.header("x-token", "111111")
        assertTrue(request.containsHeader("x-token"))
        assertArrayEquals(arrayOf("111111"), request.headers("x-token"))
        assertEquals("111111", request.firstHeader("x-token"))
        assertEquals("111111", request.lastHeader("x-token"))

        request.header(BasicHttpHeader("x-token", null))
        request.header(BasicHttpHeader("x-token", "222222"))
        assertTrue(request.containsHeader("x-token"))
        assertArrayEquals(arrayOf("111111", "222222"), request.headers("x-token"))
        assertEquals("111111", request.firstHeader("x-token"))
        assertEquals("222222", request.lastHeader("x-token"))

        request.removeHeader("x-token", "222222")
        assertTrue(request.containsHeader("x-token"))
        assertArrayEquals(arrayOf("111111"), request.headers("x-token"))
        assertEquals("111111", request.firstHeader("x-token"))
        assertEquals("111111", request.lastHeader("x-token"))

        request.removeHeaders("x-token")
        assertFalse(request.containsHeader("x-token"))
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
        request.contentType(ContentType.IMAGE_PNG)
        assertEquals("image/png", request.contentType())
        assertEquals("image/png", request.firstHeader("content-type"))
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

        request.param("token", "xxxxx")
        request.param("auth", null)
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

    @Test
    fun testCall() {
        try {
            val httpClient = ApacheHttpClient(
                HttpClients.custom()
                    .setDefaultSocketConfig(
                        SocketConfig.custom()
                            .setSoTimeout(30 * 1000)
                            .build()
                    )
                    .setDefaultRequestConfig(
                        RequestConfig.custom()
                            .setConnectTimeout(30 * 1000)
                            .setConnectionRequestTimeout(30 * 1000)
                            .setSocketTimeout(30 * 1000)
                            .build()
                    ).build()
            )
            val httpResponse = httpClient
                .post("https://www.apache.org/licenses/LICENSE-2.0")
                .param("hello", "hello")
                .body("hello")
                .call()
            if (500 == httpResponse.code()) {
                assertTrue(httpResponse.string()!!.contains("Internal Server Error"))
            }
        } catch (e: ConnectTimeoutException) {
            //skip test if connect timed out
        }
    }

    open class AbstractCallTest {

        protected lateinit var httpClient: org.apache.http.client.HttpClient
        protected lateinit var httpResponse: HttpResponse
        protected lateinit var httpEntity: HttpEntity

        protected var responseCode: Int = 200
        protected lateinit var responseBody: String
        protected lateinit var responseHeaders: Array<Pair<String, String>>
        protected lateinit var responseCharset: Charset

        protected lateinit var httpUriRequest: HttpUriRequest
        protected var closed: Boolean = false

        @BeforeEach
        fun setUp() {
            //by default
            responseCode = 200
            responseBody = "{}"
            responseHeaders = arrayOf()
            responseCharset = Charsets.UTF_8
            closed = false

            httpClient = mock()
            httpResponse = mock(extraInterfaces = arrayOf(Closeable::class))
            httpEntity = mock()

            httpClient.stub {
                this.on(httpClient.execute(any<HttpUriRequest>(), any()))
                    .doAnswer {
                        httpUriRequest = it.getArgument(0)
                        httpResponse
                    }
            }
            (httpResponse as Closeable).stub {
                this.on((httpResponse as Closeable).close())
                    .doAnswer {
                        closed = true
                    }
            }
            httpResponse.stub {
                this.on(httpResponse.statusLine)
                    .doAnswer { BasicStatusLine(HttpVersion.HTTP_1_0, responseCode, "") }
                this.on(httpResponse.entity)
                    .thenReturn(httpEntity)
                this.on(httpResponse.allHeaders)
                    .doAnswer {
                        responseHeaders.mapToTypedArray {
                            org.apache.http.message.BasicHeader(
                                it.first,
                                it.second
                            )
                        }
                    }
            }
            httpEntity.stub {
                this.on(httpEntity.content)
                    .doAnswer { responseBody.byteInputStream(responseCharset) }
            }
        }
    }

    open class CallTest : AbstractCallTest() {

        @Test
        fun testCallPostJson() {
            responseCode = 200
            responseBody = "ok"
            responseHeaders = arrayOf(
                "Content-type" to "application/json;charset=UTF-8",
                "x-token" to "123", "x-token" to "987",
                "Content-Disposition" to "attachment; filename=\"test.json\""
            )

            val httpClient = ApacheHttpClient(this.httpClient)
            val httpRequest = httpClient
                .post("https://www.apache.org/licenses/LICENSE-2.0")
                .param("hello", "hello")
                .contentType("application/json")
                .body("hello")
            val httpResponse = httpRequest
                .call()
            assertSame(httpRequest, httpResponse.request())

            assertTrue(httpUriRequest is HttpEntityEnclosingRequest)
            val httpUriRequest = (this.httpUriRequest as HttpEntityEnclosingRequest)
            assertTrue(httpUriRequest.entity is StringEntity)

            assertEquals(200, httpResponse.code())
            assertEquals("ok", httpResponse.string())
            assertEquals("ok", httpResponse.stream().readString(Charsets.UTF_8))

            assertEquals(true, httpResponse.containsHeader("Content-type"))
            assertEquals(false, httpResponse.containsHeader("y-token"))
            assertEquals("application/json;charset=UTF-8", httpResponse.contentType())
            assertEquals("123", httpResponse.firstHeader("x-token"))
            assertEquals("987", httpResponse.lastHeader("x-token"))
            assertArrayEquals(arrayOf("123", "987"), httpResponse.headers("x-token"))
            assertEquals("test.json", httpResponse.getHeaderFileName())
            httpResponse.close()
            assertTrue(closed)
        }

        @Test
        fun testCallPostFormData() {
            responseCode = 200
            responseBody = "ok"
            responseHeaders = arrayOf(
                "Content-type" to "application/json;charset=UTF-8",
                "x-token" to "123", "x-token" to "987",
                "Content-Disposition" to "attachment; filename=\"\""
            )

            val httpClient = ApacheHttpClient(this.httpClient)
            val httpRequest = httpClient
                .post("https://www.apache.org/licenses/LICENSE-2.0")
                .contentType(ContentType.MULTIPART_FORM_DATA)
                .param("hello", "hello")
            val httpResponse = httpRequest
                .call()
            assertSame(httpRequest, httpResponse.request())
            assertEquals(200, httpResponse.code())
            assertEquals("ok", httpResponse.string())
            assertEquals("ok", httpResponse.stream().readString(Charsets.UTF_8))

            assertTrue(httpUriRequest is HttpEntityEnclosingRequest)
            val httpUriRequest = (this.httpUriRequest as HttpEntityEnclosingRequest)
            assertEquals("org.apache.http.entity.mime.MultipartFormEntity", httpUriRequest.entity::class.qualifiedName)

            assertEquals(true, httpResponse.containsHeader("Content-type"))
            assertEquals(false, httpResponse.containsHeader("y-token"))
            assertEquals("application/json;charset=UTF-8", httpResponse.contentType())
            assertEquals("123", httpResponse.firstHeader("x-token"))
            assertEquals("987", httpResponse.lastHeader("x-token"))
            assertArrayEquals(arrayOf("123", "987"), httpResponse.headers("x-token"))
            assertNull(httpResponse.getHeaderFileName())
            httpResponse.close()
            assertTrue(closed)
        }

        @Test
        fun testCallPostUrlencoded() {
            responseCode = 200
            responseBody = "ok"
            responseHeaders = arrayOf(
                "Content-type" to "application/json;charset=UTF-8",
                "x-token" to "123", "x-token" to "987"
            )

            val httpClient = ApacheHttpClient(this.httpClient)
            val httpRequest = httpClient
                .post("https://www.apache.org/licenses/LICENSE-2.0")
                .contentType(ContentType.APPLICATION_FORM_URLENCODED)
                .param("hello", "hello")
            val httpResponse = httpRequest
                .call()
            assertSame(httpRequest, httpResponse.request())
            assertEquals(200, httpResponse.code())
            assertEquals("ok", httpResponse.string())
            assertEquals("ok", httpResponse.stream().readString(Charsets.UTF_8))

            assertTrue(httpUriRequest is HttpEntityEnclosingRequest)
            val httpUriRequest = (this.httpUriRequest as HttpEntityEnclosingRequest)
            assertTrue(httpUriRequest.entity is UrlEncodedFormEntity)

            assertEquals(true, httpResponse.containsHeader("Content-type"))
            assertEquals(false, httpResponse.containsHeader("y-token"))
            assertEquals("application/json;charset=UTF-8", httpResponse.contentType())
            assertEquals("123", httpResponse.firstHeader("x-token"))
            assertEquals("987", httpResponse.lastHeader("x-token"))
            assertArrayEquals(arrayOf("123", "987"), httpResponse.headers("x-token"))
            assertNull(httpResponse.getHeaderFileName())
            httpResponse.close()
            assertTrue(closed)
        }

        @Test
        fun testCallPostBodyOverForm() {
            responseCode = 200
            responseBody = "ok"
            responseHeaders = arrayOf(
                "Content-type" to "application/json",
                "x-token" to "123", "x-token" to "987",
                "Content-Disposition" to "attachment; filename=\"test.json\""
            )

            val httpClient = ApacheHttpClient(this.httpClient)
            val httpRequest = httpClient
                .post("https://www.apache.org/licenses/LICENSE-2.0")
                .contentType(ContentType.MULTIPART_FORM_DATA)
                .param("hello", "hello")
                .body("hello")
            val httpResponse = httpRequest
                .call()
            assertSame(httpRequest, httpResponse.request())

            assertTrue(httpUriRequest is HttpEntityEnclosingRequest)
            val httpUriRequest = (this.httpUriRequest as HttpEntityEnclosingRequest)
            assertTrue(httpUriRequest.entity is StringEntity)

            assertEquals(200, httpResponse.code())
            assertEquals("ok", httpResponse.string())
            assertEquals("ok", httpResponse.stream().readString(Charsets.UTF_8))

            assertEquals(true, httpResponse.containsHeader("Content-type"))
            assertEquals(false, httpResponse.containsHeader("y-token"))
            assertEquals("application/json", httpResponse.contentType())
            assertEquals("123", httpResponse.firstHeader("x-token"))
            assertEquals("987", httpResponse.lastHeader("x-token"))
            assertArrayEquals(arrayOf("123", "987"), httpResponse.headers("x-token"))
            assertEquals("test.json", httpResponse.getHeaderFileName())
            httpResponse.close()
            assertTrue(closed)
        }

        @Test
        fun testUrlWithOutQuestionMark() {
            responseCode = 200
            responseBody = "ok"
            responseHeaders = arrayOf("Content-type" to "application/json;charset=UTF-8")

            val httpClient = ApacheHttpClient(this.httpClient)
            val httpRequest = httpClient
                .get("https://www.apache.org/licenses/LICENSE-2.0")
                .query("x", "1")
                .query("y", "2")
                .contentType("application/json")
            val httpResponse = httpRequest
                .call()
            assertSame(httpRequest, httpResponse.request())

            assertFalse(httpUriRequest is HttpEntityEnclosingRequest)
            assertEquals("https://www.apache.org/licenses/LICENSE-2.0?x=1&y=2", httpUriRequest.uri.toString())
            httpResponse.close()
            assertTrue(closed)
        }

        @Test
        fun testUrlWithQuestionMark() {
            responseCode = 200
            responseBody = "ok"
            responseHeaders = arrayOf("Content-type" to "application/json;charset=UTF-8")

            val httpClient = ApacheHttpClient(this.httpClient)
            val httpRequest = httpClient
                .get("https://www.apache.org/licenses/LICENSE-2.0?x=1")
                .query("y", "2")
                .contentType("application/json")
            val httpResponse = httpRequest
                .call()
            assertSame(httpRequest, httpResponse.request())

            assertFalse(httpUriRequest is HttpEntityEnclosingRequest)
            assertEquals("https://www.apache.org/licenses/LICENSE-2.0?x=1&y=2", httpUriRequest.uri.toString())
            httpResponse.close()
            assertTrue(closed)
        }
    }

    class PostFileTest : AbstractCallTest() {

        @JvmField
        @TempDir
        var tempDir: Path? = null

        @Test
        fun testCallPostFileFormData() {
            responseCode = 200
            responseBody = "ok"
            responseHeaders = arrayOf("Content-type" to "application/json;charset=UTF-8")

            assertThrows<FileNotFoundException> {
                ApacheHttpClient(this.httpClient)
                    .post("https://www.apache.org/licenses/LICENSE-2.0")
                    .contentType(ContentType.MULTIPART_FORM_DATA)
                    .param("hello", "hello")
                    .fileParam("file", "${tempDir}/a.txt")
                    .call()
            }

            FileUtils.forceMkdir(File("${tempDir}/a"))
            assertThrows<FileNotFoundException> {
                ApacheHttpClient(this.httpClient)
                    .post("https://www.apache.org/licenses/LICENSE-2.0")
                    .contentType(ContentType.MULTIPART_FORM_DATA)
                    .param("hello", "hello")
                    .fileParam("file", "${tempDir}/a")
                    .call()
            }

            val txtFile = File("${tempDir}/a/a.txt")
            FileUtils.forceMkdirParent(txtFile)
            FileUtils.write(txtFile, "abc")
            assertDoesNotThrow {
                ApacheHttpClient(this.httpClient)
                    .post("https://www.apache.org/licenses/LICENSE-2.0")
                    .contentType(ContentType.MULTIPART_FORM_DATA)
                    .param("hello", "hello")
                    .fileParam("file", "${tempDir}/a/a.txt")
                    .fileParam("file", null)
                    .call()
            }
        }
    }
}