package com.itangcent.easyapi.core.config.source

import com.itangcent.easyapi.core.extension.ExtensionConfigRegistry
import com.itangcent.easyapi.core.rule.RuleKeys
import com.itangcent.easyapi.core.rule.engine.RuleEngine
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

/**
 * Integration test for the `spring-configuration` extension.
 *
 * The extension defines rules for [RuleKeys.PROPERTIES_PREFIX], which is consumed by
 * `com.itangcent.easyapi.core.ide.action.FieldsToPropertiesAction` to prepend a prefix
 * (typically from `@ConfigurationProperties(prefix=...)`) to generated property keys.
 *
 * Therefore, the meaningful test is to evaluate `RuleKeys.PROPERTIES_PREFIX` against
 * a class annotated with `@ConfigurationProperties(prefix = "app.config")` and
 * verify that the prefix is correctly resolved.
 */
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

    /**
     * The core behavior: `@ConfigurationProperties(prefix = "app.config")` should
     * resolve `properties.prefix` to `"app.config"`.
     */
    fun testPropertiesPrefixResolvedFromAnnotation() = runTest {
        val psiClass = findClass("com.itangcent.config.AppConfig")
        assertNotNull("Should find AppConfig", psiClass)

        val ruleEngine = RuleEngine.getInstance(project)
        val prefix = ruleEngine.evaluate(RuleKeys.PROPERTIES_PREFIX, psiClass!!)
        assertEquals(
            "properties.prefix should be resolved from @ConfigurationProperties(prefix = \"app.config\")",
            "app.config",
            prefix
        )
    }

    /**
     * A class without `@ConfigurationProperties` should resolve `properties.prefix`
     * to null (no rule applies).
     */
    fun testPropertiesPrefixIsNullForNonAnnotatedClass() = runTest {
        // Use a class that is loaded by the fixture but not annotated.
        // ConfigurationProperties itself is a class without @ConfigurationProperties.
        val psiClass = findClass("org.springframework.boot.context.properties.ConfigurationProperties")
        assertNotNull("Should find ConfigurationProperties annotation class", psiClass)

        val ruleEngine = RuleEngine.getInstance(project)
        val prefix = ruleEngine.evaluate(RuleKeys.PROPERTIES_PREFIX, psiClass!!)
        assertNull(
            "properties.prefix should be null for a class without @ConfigurationProperties",
            prefix
        )
    }
}
