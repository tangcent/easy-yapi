package com.itangcent.easyapi.exporter

import com.itangcent.easyapi.exporter.model.ExportFormat
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.Assert.*

class ApiExporterRegistryTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var registry: ApiExporterRegistry

    override fun setUp() {
        super.setUp()
        registry = ApiExporterRegistry.getInstance(project)
    }

    fun testGetInstance() {
        assertNotNull(registry)
        assertSame(registry, ApiExporterRegistry.getInstance(project))
    }

    fun testGetMarkdownExporter() {
        val exporter = registry.getExporter(ExportFormat.MARKDOWN)
        assertNotNull(exporter)
        assertTrue(exporter is com.itangcent.easyapi.exporter.markdown.MarkdownExporter)
    }

    fun testGetPostmanExporter() {
        val exporter = registry.getExporter(ExportFormat.POSTMAN)
        assertNotNull(exporter)
        assertTrue(exporter is com.itangcent.easyapi.exporter.postman.PostmanExporter)
    }

    fun testGetCurlExporter() {
        val exporter = registry.getExporter(ExportFormat.CURL)
        assertNotNull(exporter)
        assertTrue(exporter is com.itangcent.easyapi.exporter.curl.CurlExporter)
    }

    fun testGetHttpClientExporter() {
        val exporter = registry.getExporter(ExportFormat.HTTP_CLIENT)
        assertNotNull(exporter)
        assertTrue(exporter is com.itangcent.easyapi.exporter.httpclient.HttpClientExporter)
    }

    fun testGetAllExporters() {
        val exporters = registry.getAllExporters()
        assertEquals(5, exporters.size)
        
        val exporterTypes = exporters.map { it::class.simpleName }.toSet()
        assertTrue(exporterTypes.contains("MarkdownExporter"))
        assertTrue(exporterTypes.contains("PostmanExporter"))
        assertTrue(exporterTypes.contains("CurlExporter"))
        assertTrue(exporterTypes.contains("HttpClientExporter"))
        assertTrue(exporterTypes.contains("YapiExporter"))
    }

    fun testAllExportersAreUnique() {
        val exporters = registry.getAllExporters()
        val uniqueExporters = exporters.toSet()
        assertEquals(exporters.size, uniqueExporters.size)
    }
}
