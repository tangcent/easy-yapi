package com.itangcent.easyapi.config.source

import com.itangcent.easyapi.extension.ExtensionConfigRegistry
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class SpringConfigurationConfigIntegrationTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun setUp() {
        super.setUp()
        loadTestFiles()
    }

    private fun loadTestFiles() {
        loadFile("org/springframework/boot/context/properties/ConfigurationProperties.java")
        loadFile("api/config/AppConfig.java")
    }

    override fun createConfigReader(): TestConfigReader {
        val extension = ExtensionConfigRegistry.getExtension("spring-configuration")
        assertNotNull("spring-configuration extension should exist", extension)
        val content = extension?.content ?: ""
        assertTrue("Extension content should not be blank", content.isNotBlank())
        return TestConfigReader.fromConfigText(project, content)
    }

    fun testSpringConfigurationConfigLoadsCorrectly() = runTest {
        val extension = ExtensionConfigRegistry.getExtension("spring-configuration")
        assertNotNull("spring-configuration extension should exist", extension)
        assertEquals("Extension code should be spring-configuration", "spring-configuration", extension?.code)
        assertTrue("Extension should have content", extension?.content?.isNotBlank() == true)
        
        val configReader = createConfigReader()
        assertTrue("properties.prefix rules should exist", configReader.getAll("properties.prefix").isNotEmpty())
    }

    fun testConfigurationPropertiesAnnotationIsDetected() = runTest {
        val psiClass = findClass("com.itangcent.config.AppConfig")
        assertNotNull("Should find AppConfig", psiClass)
        
        val annotations = psiClass!!.annotations
        assertTrue("AppConfig should have @ConfigurationProperties annotation", 
            annotations.any { it.qualifiedName == "org.springframework.boot.context.properties.ConfigurationProperties" })
    }
}
