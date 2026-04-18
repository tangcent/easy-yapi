package com.itangcent.easyapi.config.source

import com.itangcent.easyapi.config.parser.ConfigTextParser
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking

class ExtensionConfigSourceTest : EasyApiLightCodeInsightFixtureTestCase() {

    private val configTextParser = ConfigTextParser(null)

    fun testPriority() {
        val source = ExtensionConfigSource(null, configTextParser)
        assertEquals("Priority should be 3", 3, source.priority)
    }

    fun testSourceId() {
        val source = ExtensionConfigSource(null, configTextParser)
        assertEquals("Source ID should be 'extension'", "extension", source.sourceId)
    }

    fun testCollectWithEmptyConfig() = runBlocking {
        val source = ExtensionConfigSource(emptyArray(), configTextParser)

        val result = source.collect()

        assertNotNull("Should return a sequence", result)
    }

    fun testCollectWithNullSelectedCodes() = runBlocking {
        val source = ExtensionConfigSource(null, configTextParser)

        val result = source.collect()

        assertNotNull("Should return a sequence", result)
    }
}
