package com.itangcent.easyapi.gap

import com.itangcent.easyapi.exporter.feign.FeignClassExporter
import com.itangcent.easyapi.exporter.jaxrs.JaxRsClassExporter
import com.itangcent.easyapi.exporter.springmvc.SpringMvcClassExporter
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import org.junit.Assert.*

class FeatureParityTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun createConfigReader() = TestConfigReader.EMPTY

    fun testSpringMvcExporterExists() = runTest {
        val exporter = SpringMvcClassExporter(actionContext)
        assertNotNull("SpringMvcClassExporter should exist", exporter)
    }

    fun testFeignExporterExists() = runTest {
        val exporter = FeignClassExporter(actionContext)
        assertNotNull("FeignClassExporter should exist", exporter)
    }

    fun testJaxRsExporterExists() = runTest {
        val exporter = JaxRsClassExporter(actionContext)
        assertNotNull("JaxRsClassExporter should exist", exporter)
    }
}
