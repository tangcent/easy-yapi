package com.itangcent.suv.http

import com.itangcent.common.utils.DateUtils
import com.itangcent.common.utils.FileUtils
import com.itangcent.common.utils.readString
import com.itangcent.http.*
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody
import org.apache.http.HttpEntityEnclosingRequest
import org.apache.http.conn.ConnectTimeoutException
import org.apache.http.entity.ContentType
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import java.io.File
import java.io.FileNotFoundException
import java.nio.charset.Charset
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass
import kotlin.test.*

/**
 * Test case of [OkHttpClient]
 *
 * @author tangcent
 * @date 2024/05/09
 */
class OkHttpClientTest {

    @Test
    fun testMethods() {
        val httpClient: HttpClient = OkHttpClient()

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
        val httpClient: HttpClient = OkHttpClient()
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
        val httpClient: HttpClient = OkHttpClient()
        val request = httpClient.request()
        assertNull(request.querys())
        request.query("q", "test")
        assertNotNull(request.querys())
    }

    @Test
    fun testBody() {
        val httpClient: HttpClient = OkHttpClient()
        val request = httpClient.request()
        assertNull(request.body())
        request.body("body")
        assertEquals("body", request.body())
        request.body(1)
        assertEquals(1, request.body())
    }

    @Test
    fun testContentType() {
        val httpClient: HttpClient = OkHttpClient()
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
        val httpClient: HttpClient = OkHttpClient()
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
        val httpClient: HttpClient = OkHttpClient()
        val cookieStore = httpClient.cookieStore()
        assertTrue(cookieStore.cookies().isEmpty())

        val token = cookieStore.newCookie()
        token.setName("token")
        token.setValue("111111")
        token.setExpiryDate(DateUtils.parse("2021-01-01").time)
        token.setDomain("github.com")
        token.setSecure(false)
        token.setPath("/")
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
            assertEquals("/", it.getPath())
            assertEquals(false, it.isSecure())
            assertEquals(DateUtils.parse("2099-01-01").time, it.getExpiryDate())

            val fromJson = BasicCookie.fromJson(it.json())
            assertEquals("token", fromJson.getName())
            assertEquals("111111", fromJson.getValue())
            assertEquals("github.com", fromJson.getDomain())
            assertEquals("/", fromJson.getPath())
            assertEquals(false, fromJson.isSecure())
            assertEquals(DateUtils.parse("2099-01-01").time, fromJson.getExpiryDate())

            val mutable = it.mutable()
            assertSame(mutable, mutable.mutable())
            assertEquals("token", mutable.getName())
            assertEquals("111111", mutable.getValue())
            assertEquals("github.com", mutable.getDomain())
            assertEquals("/", mutable.getPath())
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
    }

    @Test
    fun testCall() {
        try {
            val httpClient = OkHttpClient(
                okhttp3.OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
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
}

open class AbstractCallTest {

    protected lateinit var call: okhttp3.Call
    protected lateinit var httpClient: okhttp3.OkHttpClient
    protected lateinit var httpClientBuilder: okhttp3.OkHttpClient.Builder
    protected lateinit var httpResponseBody: ResponseBody

    protected var responseCode: Int = 200
    protected lateinit var responseBody: String
    protected lateinit var responseHeaders: Array<String>
    protected lateinit var responseCharset: Charset

    protected lateinit var httpRequest: okhttp3.Request

    @BeforeEach
    fun setUp() {
        //by default
        responseCode = 200
        responseBody = "{}"
        responseHeaders = arrayOf()
        responseCharset = Charsets.UTF_8

        httpClientBuilder = mock()
        httpClient = mock()
        call = mock()
        httpResponseBody = mock()

        httpClientBuilder.stub {
            this.on(httpClientBuilder.cookieJar(any()))
                .doAnswer {
                    httpClientBuilder
                }
            this.on(httpClientBuilder.build())
                .doAnswer {
                    httpClient
                }
        }
        httpClient.stub {
            this.on(httpClient.newCall(any()))
                .doAnswer {
                    httpRequest = it.getArgument(0)
                    call
                }
        }
        call.stub {
            this.on(call.execute())
                .doAnswer {
                    Response.Builder()
                        .request(okhttp3.Request.Builder().url("https://www.apache.org/licenses/LICENSE-2.0").build())
                        .protocol(okhttp3.Protocol.HTTP_1_1)
                        .code(responseCode)
                        .message("ok")
                        .headers(Headers.headersOf(*responseHeaders))
                        .body(httpResponseBody)
                        .build()
                }
        }
        httpResponseBody.stub {
            this.on(httpResponseBody.bytes())
                .doAnswer { responseBody.encodeToByteArray() }
            this.on(httpResponseBody.string())
                .doAnswer { responseBody }
            this.on(httpResponseBody.byteStream())
                .doAnswer { responseBody.byteInputStream() }
            this.on(httpResponseBody.charStream())
                .doAnswer { responseBody.reader() }
            this.on(httpResponseBody.contentLength())
                .doAnswer { responseBody.encodeToByteArray().size.toLong() }
            this.on(httpResponseBody.contentType())
                .doAnswer {
                    (Headers.headersOf(*responseHeaders)["Content-type"]
                        ?: ContentType.APPLICATION_JSON.toString()).toMediaType()
                }
        }
    }
}

open class CallTest : AbstractCallTest() {

    @Test
    fun testCallPostJson() {
        responseCode = 200
        responseBody = "ok"
        responseHeaders = arrayOf(
            "Content-type", "application/json;charset=UTF-8",
            "x-token", "123", "x-token", "987",
            "Content-Disposition", "attachment; filename=\"test.json\""
        )

        val httpClient = OkHttpClient(this.httpClientBuilder)
        val httpRequest = httpClient
            .post("https://www.apache.org/licenses/LICENSE-2.0")
            .param("hello", "hello")
            .contentType("application/json")
            .body("hello")
        val httpResponse = httpRequest
            .call()
        assertSame(httpRequest, httpResponse.request())

        assertTrue("okhttp3.RequestBody" in this.httpRequest.body!!::class.toString())

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
    }

    @Test
    fun testCallPostFormData() {
        responseCode = 200
        responseBody = "ok"
        responseHeaders = arrayOf(
            "Content-type", "application/json;charset=UTF-8",
            "x-token", "123",
            "x-token", "987",
            "Content-Disposition", "attachment; filename=\"\""
        )

        val httpClient = OkHttpClient(this.httpClientBuilder)
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

        assertEquals<KClass<*>>(okhttp3.MultipartBody::class, this.httpRequest.body!!::class)

        assertEquals(true, httpResponse.containsHeader("Content-type"))
        assertEquals(false, httpResponse.containsHeader("y-token"))
        assertEquals("application/json;charset=UTF-8", httpResponse.contentType())
        assertEquals("123", httpResponse.firstHeader("x-token"))
        assertEquals("987", httpResponse.lastHeader("x-token"))
        assertArrayEquals(arrayOf("123", "987"), httpResponse.headers("x-token"))
        assertNull(httpResponse.getHeaderFileName())
        httpResponse.close()

    }

    @Test
    fun testCallPostUrlencoded() {
        responseCode = 200
        responseBody = "ok"
        responseHeaders = arrayOf(
            "Content-type", "application/json;charset=UTF-8",
            "x-token", "123", "x-token", "987"
        )

        val httpClient = OkHttpClient(this.httpClientBuilder)
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

        assertEquals<KClass<*>>(okhttp3.FormBody::class, this.httpRequest.body!!::class)

        assertEquals(true, httpResponse.containsHeader("Content-type"))
        assertEquals(false, httpResponse.containsHeader("y-token"))
        assertEquals("application/json;charset=UTF-8", httpResponse.contentType())
        assertEquals("123", httpResponse.firstHeader("x-token"))
        assertEquals("987", httpResponse.lastHeader("x-token"))
        assertArrayEquals(arrayOf("123", "987"), httpResponse.headers("x-token"))
        assertNull(httpResponse.getHeaderFileName())
        httpResponse.close()

    }

    @Test
    fun testCallPostBodyOverForm() {
        responseCode = 200
        responseBody = "ok"
        responseHeaders = arrayOf(
            "Content-type", "application/json",
            "x-token", "123", "x-token", "987",
            "Content-Disposition", "attachment; filename=\"test.json\""
        )

        val httpClient = OkHttpClient(this.httpClientBuilder)
        val httpRequest = httpClient
            .post("https://www.apache.org/licenses/LICENSE-2.0")
            .contentType(ContentType.MULTIPART_FORM_DATA)
            .param("hello", "hello")
            .body("hello")
        val httpResponse = httpRequest
            .call()
        assertSame(httpRequest, httpResponse.request())

        assertEquals<KClass<*>>(okhttp3.MultipartBody::class, this.httpRequest.body!!::class)

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

    }

    @Test
    fun testUrlWithOutQuestionMark() {
        responseCode = 200
        responseBody = "ok"
        responseHeaders = arrayOf("Content-type", "application/json;charset=UTF-8")

        val httpClient = OkHttpClient(this.httpClientBuilder)
        val httpRequest = httpClient
            .get("https://www.apache.org/licenses/LICENSE-2.0")
            .query("x", "1")
            .query("y", "2")
            .contentType("application/json")
        val httpResponse = httpRequest
            .call()
        assertSame(httpRequest, httpResponse.request())

        assertFalse(this.httpRequest.body is HttpEntityEnclosingRequest)
        assertEquals("https://www.apache.org/licenses/LICENSE-2.0?x=1&y=2", this.httpRequest.url.toString())
        httpResponse.close()

    }

    @Test
    fun testUrlWithQuestionMark() {
        responseCode = 200
        responseBody = "ok"
        responseHeaders = arrayOf("Content-type", "application/json;charset=UTF-8")

        val httpClient = OkHttpClient(this.httpClientBuilder)
        val httpRequest = httpClient
            .get("https://www.apache.org/licenses/LICENSE-2.0?x=1")
            .query("y", "2")
            .contentType("application/json")
        val httpResponse = httpRequest
            .call()
        assertSame(httpRequest, httpResponse.request())

        assertFalse(this.httpRequest.body is HttpEntityEnclosingRequest)
        assertEquals("https://www.apache.org/licenses/LICENSE-2.0?x=1&y=2", this.httpRequest.url.toString())
        httpResponse.close()

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
        responseHeaders = arrayOf("Content-type", "application/json;charset=UTF-8")

        assertThrows<FileNotFoundException> {
            OkHttpClient(this.httpClientBuilder)
                .post("https://www.apache.org/licenses/LICENSE-2.0")
                .contentType(ContentType.MULTIPART_FORM_DATA)
                .param("hello", "hello")
                .fileParam("file", "${tempDir}/a.txt")
                .call()
        }

        FileUtils.forceMkdir(File("${tempDir}/a"))
        assertThrows<FileNotFoundException> {
            OkHttpClient(this.httpClientBuilder)
                .post("https://www.apache.org/licenses/LICENSE-2.0")
                .contentType(ContentType.MULTIPART_FORM_DATA)
                .param("hello", "hello")
                .fileParam("file", "${tempDir}/a")
                .call()
        }

        val txtFile = File("${tempDir}/a/a.txt")
        FileUtils.forceMkdirParent(txtFile)
        FileUtils.write(txtFile, "abc")
        assertThrows<FileNotFoundException> {
            OkHttpClient(this.httpClientBuilder)
                .post("https://www.apache.org/licenses/LICENSE-2.0")
                .contentType(ContentType.MULTIPART_FORM_DATA)
                .param("hello", "hello")
                .fileParam("file", "${tempDir}/a/a.txt")
                .fileParam("file", null)
                .call()
        }
    }
}