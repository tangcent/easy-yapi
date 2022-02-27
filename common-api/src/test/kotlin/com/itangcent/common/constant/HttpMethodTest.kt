package com.itangcent.common.constant

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * Test case for [HttpMethod]
 */
internal class HttpMethodTest {

    @Test
    fun preferMethod() {

        Assertions.assertEquals("ALL", HttpMethod.preferMethod(""))

        Assertions.assertEquals("GET", HttpMethod.preferMethod("GET"))
        Assertions.assertEquals("POST", HttpMethod.preferMethod("POST"))

        Assertions.assertEquals("GET", HttpMethod.preferMethod("XXX.GET"))
        Assertions.assertEquals("POST", HttpMethod.preferMethod("XXX.POST"))
        Assertions.assertEquals("GET", HttpMethod.preferMethod("POST.GET"))
        Assertions.assertEquals("POST", HttpMethod.preferMethod("GET.POST"))

        Assertions.assertEquals("GET", HttpMethod.preferMethod("POST_GET"))
        Assertions.assertEquals("GET", HttpMethod.preferMethod("GET_POST"))

        Assertions.assertEquals("GET", HttpMethod.preferMethod("[GET]"))
        Assertions.assertEquals("POST", HttpMethod.preferMethod("[POST]"))
        Assertions.assertEquals("GET", HttpMethod.preferMethod("[GET][POST]"))
        Assertions.assertEquals("GET", HttpMethod.preferMethod("[POST][GET]"))
    }
}