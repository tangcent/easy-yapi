package com.itangcent.easyapi.gap

import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.core.context.ActionContext
import com.itangcent.easyapi.exporter.curl.CurlFormatter
import com.itangcent.easyapi.exporter.postman.PostmanFormatter
import com.itangcent.easyapi.testFramework.TestConfigReader
import org.junit.Assert.*
import org.junit.Test

class FormatterParityTest {

    @Test
    fun testPostmanFormatterExists() {
        val context = ActionContext.builder()
            .bind(ConfigReader::class, TestConfigReader.EMPTY)
            .build()
        val formatter = PostmanFormatter(actionContext = context)
        assertNotNull("PostmanFormatter should exist", formatter)
    }

    @Test
    fun testCurlFormatterExists() {
        assertNotNull("CurlFormatter should exist", CurlFormatter)
    }
}
