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
        val exporter = SpringMvcClassExporter(project)
        assertNotNull("SpringMvcClassExporter should exist", exporter)
    }

    fun testFeignExporterExists() = runTest {
        val exporter = FeignClassExporter(project)
        assertNotNull("FeignClassExporter should exist", exporter)
    }

    fun testJaxRsExporterExists() = runTest {
        val exporter = JaxRsClassExporter(project)
        assertNotNull("JaxRsClassExporter should exist", exporter)
    }
}
