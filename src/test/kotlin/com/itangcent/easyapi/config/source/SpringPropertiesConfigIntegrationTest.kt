package com.itangcent.easyapi.config.source

import com.itangcent.easyapi.extension.ExtensionConfigRegistry
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class SpringPropertiesConfigIntegrationTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun setUp() {
        super.setUp()
    }

    override fun createConfigReader(): TestConfigReader {
        val extension = ExtensionConfigRegistry.getExtension("spring-properties")
        assertNotNull("spring-properties extension should exist", extension)
        val content = extension?.content ?: ""
        assertTrue("Extension content should not be blank", content.isNotBlank())
        return TestConfigReader.fromConfigText(content)
    }

    fun testSpringPropertiesConfigLoadsCorrectly() = runTest {
        val extension = ExtensionConfigRegistry.getExtension("spring-properties")
        assertNotNull("spring-properties extension should exist", extension)
        assertEquals("Extension code should be spring-properties", "spring-properties", extension?.code)
        assertTrue("Extension should have content", extension?.content?.isNotBlank() == true)
        
        val configReader = createConfigReader()
        assertTrue("properties.additional rules should exist", configReader.getAll("properties.additional").isNotEmpty())
        assertTrue("class.prefix.path rules should exist", configReader.getAll("class.prefix.path").isNotEmpty())
    }

    fun testSpringPropertiesConfigIsDisabledByDefault() = runTest {
        val extension = ExtensionConfigRegistry.getExtension("spring-properties")
        assertNotNull("spring-properties extension should exist", extension)
        
        assertFalse("Extension should be disabled by default", extension!!.defaultEnabled)
    }
}
