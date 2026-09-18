package com.itangcent.easyapi.core.config.source

import com.itangcent.easyapi.core.config.parser.ConfigTextParser
import com.itangcent.easyapi.core.extension.ExtensionConfigRegistry
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking

class ExtensionConfigSourceTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var configTextParser: ConfigTextParser

    override fun setUp() {
        super.setUp()
        configTextParser = ConfigTextParser.getInstance(project)
    }

    fun testPriority() {
        val source = ExtensionConfigSource(project, null, configTextParser)
        assertEquals("Priority should be 3", 3, source.priority)
    }

    fun testSourceId() {
        val source = ExtensionConfigSource(project, null, configTextParser)
        assertEquals("Source ID should be 'extension'", "extension", source.sourceId)
    }

    fun testSourceSelectionProbeExcludesUnavailableOnClassExtension() = runBlocking {
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

    fun testSourceSelectionProbeIncludesExtensionWithoutOnClassRequirement() = runBlocking {
        // converts extension has no on-class requirement, so it should always be included
        val convertsExtension = ExtensionConfigRegistry.getExtension("converts")
        assertNotNull("converts extension should exist", convertsExtension)
        assertNull("converts extension should have no on-class requirement", convertsExtension?.onClass)

        val source = ExtensionConfigSource(project, arrayOf("converts"), configTextParser)
        val entries = source.collect().toList()

        assertTrue("converts config should be included", entries.isNotEmpty())
    }

    /**
     * The export path is the reader that actually matters for #1461: the
     * Extensions tab persists a deselection as `-<code>`, so [ExtensionConfigSource]
     * must honour the exclusion instead of falling back to `defaultEnabled`.
     * It carries its own copy of the code grammar, hence the dedicated test
     * rather than relying on [ExtensionConfigRegistry.selectedCodes].
     */
    fun testSourceHonoursExclusionCode() = runBlocking {
        val code = "converts"
        val extension = ExtensionConfigRegistry.getExtension(code)
        assertNotNull("$code extension should exist", extension)
        assertTrue("$code should be enabled by default", extension!!.defaultEnabled)

        val baselineKeys = ExtensionConfigSource(project, emptyArray(), configTextParser)
            .collect().map { it.key }.toSet()
        val excludedKeys = ExtensionConfigSource(project, arrayOf("-$code"), configTextParser)
            .collect().map { it.key }.toSet()

        assertTrue("baseline should carry $code rules", baselineKeys.isNotEmpty())
        val dropped = baselineKeys - excludedKeys
        assertTrue(
            "'-$code' must drop that extension's rules at export time (issue #1461)",
            dropped.isNotEmpty()
        )
        assertEquals(
            "excluding $code must leave every other extension untouched",
            baselineKeys - dropped,
            excludedKeys
        )
    }
}
