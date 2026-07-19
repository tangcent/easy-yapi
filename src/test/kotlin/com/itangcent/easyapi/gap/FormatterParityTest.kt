package com.itangcent.easyapi.gap

import com.itangcent.easyapi.channel.curl.CurlFormatter
import com.itangcent.easyapi.channel.postman.PostmanFormatter
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import org.junit.Assert.*

class FormatterParityTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun createConfigReader() = TestConfigReader.empty(project)

    fun testPostmanFormatterExists() {
        val formatter = PostmanFormatter(project = project)
        assertNotNull("PostmanFormatter should exist", formatter)
    }

    fun testCurlFormatterExists() {
        assertNotNull("CurlFormatter should exist", CurlFormatter)
    }
}
