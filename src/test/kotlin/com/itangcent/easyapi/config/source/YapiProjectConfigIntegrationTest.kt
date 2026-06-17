package com.itangcent.easyapi.config.source

import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.exporter.springmvc.SpringMvcClassExporter
import com.itangcent.easyapi.extension.ExtensionConfigRegistry
import com.itangcent.easyapi.psi.helper.DocMetadataResolver
import com.itangcent.easyapi.rule.RuleKeys
import com.itangcent.easyapi.rule.engine.RuleEngine
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

/**
 * Integration test for the `yapi.project` extension.
 *
 * The extension defines rules for [RuleKeys.YAPI_PROJECT]:
 *   - `yapi.project=#project`
 *   - `yapi.project=#module` (fallback for legacy `@module` doc tag)
 *
 * The rule resolves to the value of the `@project` doc tag (or `@module` as a
 * fallback) on a method, used to group APIs into YApi projects.
 */
class YapiProjectConfigIntegrationTest : EasyApiLightCodeInsightFixtureTestCase() {

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
        loadFile("api/yapiproject/ProjectController.java")
    }

    override fun createConfigReader(): TestConfigReader {
        val extension = ExtensionConfigRegistry.getExtension("yapi.project")
        assertNotNull("yapi.project extension should exist", extension)
        return TestConfigReader.fromConfigText(project, extension?.content ?: "")
    }

    fun testYapiProjectConfigLoadsCorrectly() = runTest {
        val extension = ExtensionConfigRegistry.getExtension("yapi.project")
        assertNotNull(extension)
        assertEquals("yapi.project", extension?.code)
        assertTrue(extension?.content?.isNotBlank() == true)
    }

    /**
     * The core rule: a method with `@project user-service` doc tag should
     * resolve [RuleKeys.YAPI_PROJECT] to `"user-service"`.
     */
    fun testYapiProjectRuleForMethodWithProjectTag() = runTest {
        val psiClass = findClass("com.itangcent.yapiproject.ProjectController")
        assertNotNull("Should find ProjectController", psiClass)

        val method = findMethod(psiClass!!, "getUser")
        assertNotNull("Should find getUser method", method)

        val ruleEngine = RuleEngine.getInstance(project)
        val project = ruleEngine.evaluate(RuleKeys.YAPI_PROJECT, method!!)
        assertEquals(
            "YAPI_PROJECT should be 'user-service' for method with @project user-service doc tag",
            "user-service",
            project
        )
    }

    /**
     * The core rule: a method with `@module order-service` doc tag (legacy
     * fallback) should resolve [RuleKeys.YAPI_PROJECT] to `"order-service"`.
     */
    fun testYapiProjectRuleForMethodWithModuleTag() = runTest {
        val psiClass = findClass("com.itangcent.yapiproject.ProjectController")
        assertNotNull("Should find ProjectController", psiClass)

        val method = findMethod(psiClass!!, "createOrder")
        assertNotNull("Should find createOrder method", method)

        val ruleEngine = RuleEngine.getInstance(project)
        val project = ruleEngine.evaluate(RuleKeys.YAPI_PROJECT, method!!)
        assertEquals(
            "YAPI_PROJECT should be 'order-service' for method with @module order-service doc tag",
            "order-service",
            project
        )
    }

    /**
     * The core rule: a method without `@project` or `@module` doc tag should
     * resolve [RuleKeys.YAPI_PROJECT] to `null`.
     */
    fun testYapiProjectRuleForMethodWithoutProjectTag() = runTest {
        val psiClass = findClass("com.itangcent.yapiproject.ProjectController")
        assertNotNull("Should find ProjectController", psiClass)

        val method = findMethod(psiClass!!, "noProject")
        assertNotNull("Should find noProject method", method)

        val ruleEngine = RuleEngine.getInstance(project)
        val project = ruleEngine.evaluate(RuleKeys.YAPI_PROJECT, method!!)
        assertNull(
            "YAPI_PROJECT should be null for method without @project or @module doc tag",
            project
        )
    }

    fun testProjectTagResolved() = runTest {
        val psiClass = findClass("com.itangcent.yapiproject.ProjectController")
        assertNotNull("Should find ProjectController", psiClass)

        val method = findMethod(psiClass!!, "getUser")
        assertNotNull("Should find getUser method", method)

        val resolver = DocMetadataResolver.getInstance(project)
        val project = resolver.resolveYapiProject(method!!)
        assertEquals("user-service", project)
    }

    fun testModuleTagResolvedAsFallback() = runTest {
        val psiClass = findClass("com.itangcent.yapiproject.ProjectController")
        assertNotNull("Should find ProjectController", psiClass)

        val method = findMethod(psiClass!!, "createOrder")
        assertNotNull("Should find createOrder method", method)

        val resolver = DocMetadataResolver.getInstance(project)
        val project = resolver.resolveYapiProject(method!!)
        assertEquals("order-service", project)
    }

    fun testNoProjectTagReturnsNull() = runTest {
        val psiClass = findClass("com.itangcent.yapiproject.ProjectController")
        assertNotNull("Should find ProjectController", psiClass)

        val method = findMethod(psiClass!!, "noProject")
        assertNotNull("Should find noProject method", method)

        val resolver = DocMetadataResolver.getInstance(project)
        val project = resolver.resolveYapiProject(method!!)
        assertNull("Method without @project or @module should return null", project)
    }

    fun testControllerExportsEndpoints() = runTest {
        val psiClass = findClass("com.itangcent.yapiproject.ProjectController")
        assertNotNull("Should find ProjectController", psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertEquals("Should export 3 endpoints", 3, endpoints.size)

        val getEndpoint = endpoints.find { it.httpMetadata?.method == HttpMethod.GET }
        assertNotNull("Should find GET endpoint", getEndpoint)
    }
}
