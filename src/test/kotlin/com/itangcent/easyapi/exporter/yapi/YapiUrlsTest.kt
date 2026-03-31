package com.itangcent.easyapi.exporter.yapi

import org.junit.Assert.assertEquals
import org.junit.Test

class YapiUrlsTest {

    @Test
    fun `normalizeBaseUrl trims spaces and trailing slash`() {
        assertEquals("http://localhost:3000", YapiUrls.normalizeBaseUrl(" http://localhost:3000/ "))
    }

    @Test
    fun `cartUrl builds yapi cart path from normalized base url`() {
        assertEquals(
            "http://localhost:3000/project/12/interface/api/cat_34",
            YapiUrls.cartUrl(" http://localhost:3000/ ", "12", "34")
        )
    }
}
