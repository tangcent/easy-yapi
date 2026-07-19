package com.itangcent.easyapi.framework.springmvc.config

import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.framework.springmvc.SpringMvcClassExporter
import com.itangcent.easyapi.core.extension.ExtensionConfigRegistry
import com.itangcent.easyapi.core.rule.RuleKeys
import com.itangcent.easyapi.core.rule.engine.RuleEngine
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

/**
 * Integration test for the `ignore` extension.
 *
 * The extension defines a single rule for [RuleKeys.IGNORE]:
 *   `ignore=#ignore`
 * which resolves to `true` when the element has a `@ignore` doc tag.
 * Consumers (e.g. exporters) skip elements whose `ignore` rule evaluates to `true`.
 */
class IgnoreConfigIntegrationTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var exporter: SpringMvcClassExporter

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        exporter = SpringMvcClassExporter(project)
    }

    private fun loadTestFiles() {
        loadFile("spring/RequestMapping.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/RequestParam.java")
        loadFile("spring/PathVariable.java")
        loadFile("spring/RequestBody.java")
        loadFile("spring/RestController.java")
        loadFile("spring/Controller.java")
        loadFile("model/Result.java")
        loadFile("model/IResult.java")
        loadFile("model/UserInfo.java")
        loadFile("api/ignore/IgnoredController.java")
        loadFile("api/ignore/NormalController.java")
    }

    override fun createConfigReader(): TestConfigReader {
        val extension = ExtensionConfigRegistry.getExtension("ignore")
        assertNotNull("ignore extension should exist", extension)
        return TestConfigReader.fromConfigText(project, extension?.content ?: "")
    }

    fun testIgnoreConfigLoadsCorrectly() = runTest {
        val extension = ExtensionConfigRegistry.getExtension("ignore")
        assertNotNull(extension)
        assertEquals("ignore", extension?.code)
        assertTrue(extension?.content?.isNotBlank() == true)
    }

    /**
     * The core rule: a class with `@ignore` doc tag should resolve
     * [RuleKeys.IGNORE] to `true`.
     */
    fun testIgnoreRuleTrueForClassWithIgnoreTag() = runTest {
        val psiClass = findClass("com.itangcent.ignore.IgnoredController")
        assertNotNull("Should find IgnoredController", psiClass)

        val ruleEngine = RuleEngine.getInstance(project)
        val ignored = ruleEngine.evaluate(RuleKeys.IGNORE, psiClass!!)
        assertTrue(
            "Class with @ignore doc tag should be ignored (IGNORE rule should be true)",
            ignored
        )
    }

    /**
     * The core rule: a class without `@ignore` doc tag should resolve
     * [RuleKeys.IGNORE] to `false`.
     */
    fun testIgnoreRuleFalseForClassWithoutIgnoreTag() = runTest {
        val psiClass = findClass("com.itangcent.ignore.NormalController")
        assertNotNull("Should find NormalController", psiClass)

        val ruleEngine = RuleEngine.getInstance(project)
        val ignored = ruleEngine.evaluate(RuleKeys.IGNORE, psiClass!!)
        assertFalse(
            "Class without @ignore doc tag should NOT be ignored (IGNORE rule should be false)",
            ignored
        )
    }

    /**
     * The core rule: a method with `@ignore` doc tag should resolve
     * [RuleKeys.IGNORE] to `true`.
     */
    fun testIgnoreRuleTrueForMethodWithIgnoreTag() = runTest {
        val psiClass = findClass("com.itangcent.ignore.NormalController")
        assertNotNull("Should find NormalController", psiClass)

        val ignoredMethod = psiClass!!.methods.find { it.name == "ignoredMethod" }
        assertNotNull("Should find ignoredMethod", ignoredMethod)

        val ruleEngine = RuleEngine.getInstance(project)
        val ignored = ruleEngine.evaluate(RuleKeys.IGNORE, ignoredMethod!!)
        assertTrue(
            "Method with @ignore doc tag should be ignored (IGNORE rule should be true)",
            ignored
        )
    }

    /**
     * The core rule: a method without `@ignore` doc tag should resolve
     * [RuleKeys.IGNORE] to `false`.
     */
    fun testIgnoreRuleFalseForMethodWithoutIgnoreTag() = runTest {
        val psiClass = findClass("com.itangcent.ignore.NormalController")
        assertNotNull("Should find NormalController", psiClass)

        val normalMethod = psiClass!!.methods.find { it.name == "method" }
        assertNotNull("Should find method", normalMethod)

        val ruleEngine = RuleEngine.getInstance(project)
        val ignored = ruleEngine.evaluate(RuleKeys.IGNORE, normalMethod!!)
        assertFalse(
            "Method without @ignore doc tag should NOT be ignored (IGNORE rule should be false)",
            ignored
        )
    }

    fun testIgnoredClassIsNotExported() = runTest {
        val psiClass = findClass("com.itangcent.ignore.IgnoredController")
        assertNotNull("Should find IgnoredController", psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertEquals("Ignored class should export 0 endpoints", 0, endpoints.size)
    }

    fun testNormalClassIsExported() = runTest {
        val psiClass = findClass("com.itangcent.ignore.NormalController")
        assertNotNull("Should find NormalController", psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue("Normal class should export at least 1 endpoint", endpoints.isNotEmpty())
    }

    fun testIgnoredMethodIsNotExported() = runTest {
        val psiClass = findClass("com.itangcent.ignore.NormalController")
        assertNotNull("Should find NormalController", psiClass)

        val endpoints = exporter.export(psiClass!!)

        val ignoredMethodEndpoint = endpoints.find {
            it.httpMetadata?.path?.contains("ignored-method") == true
        }
        assertNull("Method with @ignore tag should NOT be exported", ignoredMethodEndpoint)
    }

    fun testNormalMethodIsExported() = runTest {
        val psiClass = findClass("com.itangcent.ignore.NormalController")
        assertNotNull("Should find NormalController", psiClass)

        val endpoints = exporter.export(psiClass!!)

        val normalEndpoint = endpoints.find {
            it.httpMetadata?.method == HttpMethod.GET && it.httpMetadata?.path?.contains("method") == true
        }
        assertNotNull("Normal method should be exported", normalEndpoint)
    }
}
