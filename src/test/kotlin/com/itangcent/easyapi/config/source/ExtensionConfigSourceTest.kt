package com.itangcent.easyapi.config.source

import com.itangcent.easyapi.config.parser.ConfigTextParser
import com.itangcent.easyapi.extension.ExtensionConfigRegistry
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking

class ExtensionConfigSourceTest : EasyApiLightCodeInsightFixtureTestCase() {

    private val configTextParser = ConfigTextParser(null)

    fun testPriority() {
        val source = ExtensionConfigSource(project, null, configTextParser)
        assertEquals("Priority should be 3", 3, source.priority)
    }

    fun testSourceId() {
        val source = ExtensionConfigSource(project, null, configTextParser)
        assertEquals("Source ID should be 'extension'", "extension", source.sourceId)
    }

    fun testCollectWithEmptyConfig() = runBlocking {
        val source = ExtensionConfigSource(project, emptyArray(), configTextParser)

        val result = source.collect()

        assertNotNull("Should return a sequence", result)
    }

    fun testCollectWithNullSelectedCodes() = runBlocking {
        val source = ExtensionConfigSource(project, null, configTextParser)

        val result = source.collect()

        assertNotNull("Should return a sequence", result)
    }

    fun testOnClassFilteringExcludesUnavailableExtensions() = runBlocking {
        // spring-webflux has on-class: reactor.core.publisher.Mono
        // This test project doesn't have reactor, so it should be filtered out
        val webfluxExtension = ExtensionConfigRegistry.getExtension("spring-webflux")
        assertNotNull("spring-webflux extension should exist", webfluxExtension)
        assertEquals("reactor.core.publisher.Mono", webfluxExtension?.onClass)

        val source = ExtensionConfigSource(project, arrayOf("spring-webflux"), configTextParser)
        val entries = source.collect().toList()

        // Since reactor.core.publisher.Mono is not in the test project,
        // spring-webflux config should be filtered out
        val hasWebfluxRules = entries.any { it.key.contains("webflux") || it.value.contains("Flux") }
        assertFalse("spring-webflux rules should be filtered out when reactor is not available", hasWebfluxRules)
    }

    fun testOnClassFilteringIncludesExtensionsWithoutOnClass() = runBlocking {
        // converts extension has no on-class requirement, so it should always be included
        val convertsExtension = ExtensionConfigRegistry.getExtension("converts")
        assertNotNull("converts extension should exist", convertsExtension)
        assertNull("converts extension should have no on-class requirement", convertsExtension?.onClass)

        val source = ExtensionConfigSource(project, arrayOf("converts"), configTextParser)
        val entries = source.collect().toList()

        assertTrue("converts config should be included", entries.isNotEmpty())
    }
}
