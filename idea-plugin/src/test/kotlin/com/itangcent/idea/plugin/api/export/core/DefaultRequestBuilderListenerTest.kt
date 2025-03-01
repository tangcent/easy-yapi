package com.itangcent.idea.plugin.api.export.core

import com.google.inject.Inject
import com.itangcent.common.model.Request
import com.itangcent.common.model.Response
import com.itangcent.common.model.URL
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.with
import com.itangcent.mock.AdvancedContextTest
import com.itangcent.mock.FakeExportContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Test case of [DefaultRequestBuilderListener]
 */
internal class DefaultRequestBuilderListenerTest : AdvancedContextTest() {

    @Inject
    private lateinit var requestBuilderListener: RequestBuilderListener

    private lateinit var request: Request

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        builder.bind(RequestBuilderListener::class) { it.with(DefaultRequestBuilderListener::class) }
    }

    @BeforeEach
    fun init() {
        request = Request()
    }

    @Test
    fun testSetName() {
        requestBuilderListener.setName(
            FakeExportContext.INSTANCE,
            request, "name"
        )
        assertEquals("name", request.name)
    }

    @Test
    fun testSetMethod() {
        requestBuilderListener.setMethod(
            FakeExportContext.INSTANCE,
            request, "POST"
        )
        assertEquals("POST", request.method)
    }

    @Test
    fun testSetPath() {
        requestBuilderListener.setPath(
            FakeExportContext.INSTANCE,
            request, URL.of("/login")
        )
        assertEquals(URL.of("/login"), request.path)
    }

    @Test
    fun testSetModelAsBody() {
        requestBuilderListener.setModelAsBody(
            FakeExportContext.INSTANCE,
            request, "body"
        )
        assertEquals("body", request.body)
    }

    @Test
    fun testAddModelAsParam() {
        requestBuilderListener.addModelAsParam(
            FakeExportContext.INSTANCE,
            request, mapOf("a" to 1)
        )
        assertEquals("a", request.querys!![0].name)
    }

    @Test
    fun testAddModelAsFormParam() {
        requestBuilderListener.addModelAsFormParam(
            FakeExportContext.INSTANCE,
            request, mapOf("a" to 1)
        )
        assertEquals("a", request.formParams!![0].name)
    }

    @Test
    fun testAddFormParam() {
        requestBuilderListener.addFormParam(
            FakeExportContext.INSTANCE,
            request, "token", "123", "token for auth"
        )
        request.formParams!![0].let {
            assertEquals("token", it.name)
            assertEquals("123", it.value)
            assertEquals("token for auth", it.desc)
        }
    }

    @Test
    fun testAddParam() {
        requestBuilderListener.addParam(
            FakeExportContext.INSTANCE,
            request, "token", "123", "token for auth"
        )
        request.querys!![0].let {
            assertEquals("token", it.name)
            assertEquals("123", it.value)
            assertEquals("token for auth", it.desc)
        }
    }

    @Test
    fun testRemoveParam() {
        requestBuilderListener.addParam(
            FakeExportContext.INSTANCE,
            request, "token", "123", "token for auth"
        )
        val param = request.querys!!.find { it.name == "token" }!!
        requestBuilderListener.removeParam(
            FakeExportContext.INSTANCE,
            request, param
        )
        assertTrue(request.querys!!.isEmpty())
    }

    @Test
    fun testAddPathParam() {
        requestBuilderListener.addPathParam(
            FakeExportContext.INSTANCE,
            request, "token", "123", "token for auth"
        )
        request.paths!![0].let {
            assertEquals("token", it.name)
            assertEquals("123", it.value)
            assertEquals("token for auth", it.desc)
        }
    }

    @Test
    fun testSetJsonBody() {
        requestBuilderListener.setJsonBody(
            FakeExportContext.INSTANCE,
            request, "token", "token for auth"
        )
        assertEquals("token", request.body)
        assertEquals("token for auth", request.bodyAttr)
    }

    @Test
    fun testAppendDesc() {
        requestBuilderListener.appendDesc(
            FakeExportContext.INSTANCE,
            request, "abc"
        )
        assertEquals("abc", request.desc)
        requestBuilderListener.appendDesc(
            FakeExportContext.INSTANCE,
            request, "def"
        )
        assertEquals("abc\ndef", request.desc)
    }

    @Test
    fun testAddHeader() {
        requestBuilderListener.addHeader(
            FakeExportContext.INSTANCE,
            request, "token", "123"
        )
        request.headers!![0].let {
            assertEquals("token", it.name)
            assertEquals("123", it.value)
        }
    }

    @Test
    fun testAddResponse() {
        val response = Response()
        requestBuilderListener.addResponse(
            FakeExportContext.INSTANCE,
            request, response
        )
        assertSame(response, request.response!!.first())
    }

    @Test
    fun testAddResponseHeader() {
        val response = Response()
        requestBuilderListener.addResponseHeader(
            FakeExportContext.INSTANCE,
            response, "token", "123"
        )
        response.headers!![0].let {
            assertEquals("token", it.name)
            assertEquals("123", it.value)
        }
    }

    @Test
    fun testSetResponseBody() {
        val response = Response()
        requestBuilderListener.setResponseBody(
            FakeExportContext.INSTANCE,
            response, "raw", "123"
        )
        assertEquals("raw", response.bodyType)
        assertEquals("123", response.body)
    }

    @Test
    fun testSetResponseCode() {
        val response = Response()
        requestBuilderListener.setResponseCode(
            FakeExportContext.INSTANCE,
            response, 200
        )
        assertEquals(200, response.code)
    }

    @Test
    fun testAppendResponseBodyDesc() {
        val response = Response()
        requestBuilderListener.appendResponseBodyDesc(
            FakeExportContext.INSTANCE,
            response, "abc"
        )
        assertEquals("abc", response.bodyDesc)
        requestBuilderListener.appendResponseBodyDesc(
            FakeExportContext.INSTANCE,
            response, "def"
        )
        assertEquals("abc\ndef", response.bodyDesc)
    }
}