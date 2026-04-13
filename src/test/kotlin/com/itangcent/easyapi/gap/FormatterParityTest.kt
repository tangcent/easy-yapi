package com.itangcent.easyapi.gap

import com.itangcent.easyapi.exporter.curl.CurlFormatter
import com.itangcent.easyapi.exporter.postman.PostmanFormatter
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import org.junit.Assert.*

class FormatterParityTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun createConfigReader() = TestConfigReader.EMPTY

    fun testPostmanFormatterExists() {
        val formatter = PostmanFormatter(project = project)
        assertNotNull("PostmanFormatter should exist", formatter)
    }

    fun testCurlFormatterExists() {
        assertNotNull("CurlFormatter should exist", CurlFormatter)
    }
}
