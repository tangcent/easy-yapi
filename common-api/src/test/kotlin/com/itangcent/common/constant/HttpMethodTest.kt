package com.itangcent.common.constant

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Test case for [HttpMethod]
 */
internal class HttpMethodTest {

    @Test
    fun `test preferMethod function`() {

        assertEquals("ALL", HttpMethod.preferMethod(""))
        assertEquals("ALL", HttpMethod.preferMethod("foo"))

        assertEquals("GET", HttpMethod.preferMethod("GET"))
        assertEquals("POST", HttpMethod.preferMethod("POST"))
        assertEquals("DELETE", HttpMethod.preferMethod("DELETE"))
        assertEquals("PUT", HttpMethod.preferMethod("PUT"))
        assertEquals("PATCH", HttpMethod.preferMethod("PATCH"))
        assertEquals("OPTIONS", HttpMethod.preferMethod("OPTIONS"))
        assertEquals("TRACE", HttpMethod.preferMethod("TRACE"))
        assertEquals("HEAD", HttpMethod.preferMethod("HEAD"))

        assertEquals("GET", HttpMethod.preferMethod("get"))
        assertEquals("POST", HttpMethod.preferMethod("post"))
        assertEquals("DELETE", HttpMethod.preferMethod("delete"))
        assertEquals("PUT", HttpMethod.preferMethod("put"))
        assertEquals("PATCH", HttpMethod.preferMethod("patch"))
        assertEquals("OPTIONS", HttpMethod.preferMethod("options"))
        assertEquals("TRACE", HttpMethod.preferMethod("trace"))
        assertEquals("HEAD", HttpMethod.preferMethod("head"))

        assertEquals("GET", HttpMethod.preferMethod("XXX.GET"))
        assertEquals("POST", HttpMethod.preferMethod("XXX.POST"))
        assertEquals("GET", HttpMethod.preferMethod("POST.GET"))
        assertEquals("POST", HttpMethod.preferMethod("GET.POST"))

        assertEquals("GET", HttpMethod.preferMethod("POST_GET"))
        assertEquals("GET", HttpMethod.preferMethod("GET_POST"))

        assertEquals("GET", HttpMethod.preferMethod("[GET]"))
        assertEquals("POST", HttpMethod.preferMethod("[POST]"))
        assertEquals("GET", HttpMethod.preferMethod("[GET][POST]"))
        assertEquals("GET", HttpMethod.preferMethod("[POST][GET]"))
    }
}