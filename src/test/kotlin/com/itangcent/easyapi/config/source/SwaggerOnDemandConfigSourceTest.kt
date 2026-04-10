package com.itangcent.easyapi.config.source

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking

class SwaggerOnDemandConfigSourceTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun setUp() {
        super.setUp()
        loadSwaggerAnnotationStubs()
    }

    private fun loadSwaggerAnnotationStubs() {
        loadFile("io/swagger/annotations/Api.java")
    }

    fun testCollectReturnsEntriesWhenEnabledAndAnnotationPresent() = runTest {
        val source = SwaggerOnDemandConfigSource(project)
        val entries = runBlocking { source.collect().toList() }
        assertTrue(
            "Should return config entries when enabled and annotation is present",
            entries.isNotEmpty()
        )
    }

    fun testCollectReturnsEmptyWhenSettingDisabled() = runTest {
        updateSettings { swaggerEnable = false }
        val source = SwaggerOnDemandConfigSource(project)
        val entries = runBlocking { source.collect().toList() }
        assertTrue(
            "Should return empty when swaggerEnable is false",
            entries.isEmpty()
        )
    }

    fun testPriorityIsSet() {
        val source = SwaggerOnDemandConfigSource(project)
        assertEquals(3, source.priority)
    }

    fun testSourceIdIsSwagger() {
        val source = SwaggerOnDemandConfigSource(project)
        assertEquals("swagger", source.sourceId)
    }
}
