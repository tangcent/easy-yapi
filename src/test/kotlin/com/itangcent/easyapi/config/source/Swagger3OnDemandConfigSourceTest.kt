package com.itangcent.easyapi.config.source

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking

class Swagger3OnDemandConfigSourceTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun setUp() {
        super.setUp()
        loadSwagger3AnnotationStubs()
    }

    private fun loadSwagger3AnnotationStubs() {
        loadFile("io/swagger/v3/oas/annotations/Operation.java")
    }

    fun testCollectReturnsEntriesWhenEnabledAndAnnotationPresent() = runTest {
        val source = Swagger3OnDemandConfigSource(project)
        val entries = runBlocking { source.collect().toList() }
        assertTrue(
            "Should return config entries when enabled and annotation is present",
            entries.isNotEmpty()
        )
    }

    fun testCollectReturnsEmptyWhenSettingDisabled() = runTest {
        updateSettings { swagger3Enable = false }
        val source = Swagger3OnDemandConfigSource(project)
        val entries = runBlocking { source.collect().toList() }
        assertTrue(
            "Should return empty when swagger3Enable is false",
            entries.isEmpty()
        )
    }

    fun testPriorityIsSet() {
        val source = Swagger3OnDemandConfigSource(project)
        assertEquals(3, source.priority)
    }

    fun testSourceIdIsSwagger3() {
        val source = Swagger3OnDemandConfigSource(project)
        assertEquals("swagger3", source.sourceId)
    }
}
