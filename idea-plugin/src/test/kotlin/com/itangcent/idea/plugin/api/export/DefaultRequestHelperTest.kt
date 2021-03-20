package com.itangcent.idea.plugin.api.export

import com.google.inject.Inject
import com.itangcent.common.model.Request
import com.itangcent.common.model.Response
import com.itangcent.common.model.URL
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.with
import com.itangcent.mock.AdvancedContextTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Test case of [DefaultRequestHelper]
 */
internal class DefaultRequestHelperTest : AdvancedContextTest() {

    @Inject
    private lateinit var requestHelper: RequestHelper

    private lateinit var request: Request

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.bind(RequestHelper::class) { it.with(DefaultRequestHelper::class) }
    }

    @BeforeEach
    fun init() {
        request = Request()
    }

    @Test
    fun testSetName() {
        requestHelper.setName(request, "name")
        assertEquals("name", request.name)
    }

    @Test
    fun testSetMethod() {
        requestHelper.setMethod(request, "POST")
        assertEquals("POST", request.method)
    }

    @Test
    fun testSetPath() {
        requestHelper.setPath(request, URL.of("/login"))
        assertEquals(URL.of("/login"), request.path)
    }

    @Test
    fun testSetModelAsBody() {
        requestHelper.setModelAsBody(request, "body")
        assertEquals("body", request.body)
    }

    @Test
    fun testAddModelAsParam() {
        requestHelper.addModelAsParam(request, mapOf("a" to 1))
        assertEquals("a", request.formParams!![0].name)
    }

    @Test
    fun testAddFormParam() {
        requestHelper.addFormParam(request, "token", "123", "token for auth")
        request.formParams!![0].let {
            assertEquals("token", it.name)
            assertEquals("123", it.value)
            assertEquals("token for auth", it.desc)
        }
    }

    @Test
    fun testAddParam() {
        requestHelper.addParam(request, "token", "123", "token for auth")
        request.querys!![0].let {
            assertEquals("token", it.name)
            assertEquals("123", it.value)
            assertEquals("token for auth", it.desc)
        }
    }

    @Test
    fun testRemoveParam() {
        requestHelper.addParam(request, "token", "123", "token for auth")
        val param = request.querys!!.find { it.name == "token" }!!
        requestHelper.removeParam(request, param)
        assertTrue(request.querys!!.isEmpty())
    }

    @Test
    fun testAddPathParam() {
        requestHelper.addPathParam(request, "token", "123", "token for auth")
        request.paths!![0].let {
            assertEquals("token", it.name)
            assertEquals("123", it.value)
            assertEquals("token for auth", it.desc)
        }
    }

    @Test
    fun testSetJsonBody() {
        requestHelper.setJsonBody(request, "token", "token for auth")
        assertEquals("token", request.body)
        assertEquals("token for auth", request.bodyAttr)
    }

    @Test
    fun testAppendDesc() {
        requestHelper.appendDesc(request, "abc")
        assertEquals("abc", request.desc)
        requestHelper.appendDesc(request, "def")
        assertEquals("abcdef", request.desc)
    }

    @Test
    fun testAddHeader() {
        requestHelper.addHeader(request, "token", "123")
        request.headers!![0].let {
            assertEquals("token", it.name)
            assertEquals("123", it.value)
        }
    }

    @Test
    fun testAddResponse() {
        val response = Response()
        requestHelper.addResponse(request, response)
        assertSame(response, request.response!!.first())
    }

    @Test
    fun testAddResponseHeader() {
        val response = Response()
        requestHelper.addResponseHeader(response, "token", "123")
        response.headers!![0].let {
            assertEquals("token", it.name)
            assertEquals("123", it.value)
        }
    }

    @Test
    fun testSetResponseBody() {
        val response = Response()
        requestHelper.setResponseBody(response, "raw", "123")
        assertEquals("raw", response.bodyType)
        assertEquals("123", response.body)
    }

    @Test
    fun testSetResponseCode() {
        val response = Response()
        requestHelper.setResponseCode(response, 200)
        assertEquals(200, response.code)
    }

    @Test
    fun testAppendResponseBodyDesc() {
        val response = Response()
        requestHelper.appendResponseBodyDesc(response, "abc")
        assertEquals("abc", response.bodyDesc)
        requestHelper.appendResponseBodyDesc(response, "def")
        assertEquals("abc\ndef", response.bodyDesc)
    }
}