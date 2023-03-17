package com.itangcent.idea.plugin.api.export.core

import org.apache.http.HttpResponse
import org.apache.http.StatusLine
import org.apache.http.entity.ByteArrayEntity
import org.apache.http.entity.ContentType
import org.apache.http.message.BasicHeader
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.nio.charset.StandardCharsets
import kotlin.test.assertEquals
import kotlin.test.assertSame


/**
 * Test case of [ReservedResponseHandle]
 */
internal class ReservedResponseHandleTest {

    @Test
    fun testHandleResponse() {
        val httpResponse = Mockito.mock(HttpResponse::class.java)
        val statusLine = Mockito.mock(StatusLine::class.java)
        Mockito.`when`(statusLine.statusCode).thenReturn(200)
        Mockito.`when`(httpResponse.statusLine).thenReturn(statusLine)
        Mockito.`when`(httpResponse.getLastHeader("token")).thenReturn(BasicHeader("token", "123"))
        Mockito.`when`(httpResponse.getHeaders("token")).thenReturn(arrayOf(BasicHeader("token", "123"),
                BasicHeader("token", "456")))
        val entity = ByteArrayEntity("hello world".toByteArray(StandardCharsets.UTF_8),
                ContentType.DEFAULT_TEXT.withCharset(StandardCharsets.UTF_8))
        Mockito.`when`(httpResponse.entity).thenReturn(entity)

        val stringResponseHandler = StringResponseHandler()
        val reservedResponseHandle = stringResponseHandler.reserved()
        assertSame(reservedResponseHandle, stringResponseHandler.reserved())
        assertSame<Any>(reservedResponseHandle, reservedResponseHandle.reserved())
        val reservedResult = reservedResponseHandle.handleResponse(httpResponse)
        assertEquals("hello world", reservedResult.result())
        assertEquals("hello world", reservedResult.result())//again
        assertEquals(200, reservedResult.status())
        assertEquals("123", reservedResult.header("token"))
        assertEquals(listOf("123", "456"), reservedResult.headers("token"))
    }
}