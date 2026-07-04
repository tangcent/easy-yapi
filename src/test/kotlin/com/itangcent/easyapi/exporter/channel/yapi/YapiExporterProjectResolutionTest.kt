package com.itangcent.easyapi.exporter.channel.yapi

import com.intellij.openapi.project.Project
import com.intellij.testFramework.registerServiceInstance
import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.config.ConfigReloadListener
import com.itangcent.easyapi.exporter.channel.ChannelConfig
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.ExportContext
import com.itangcent.easyapi.exporter.model.ExportResult
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import com.itangcent.easyapi.testFramework.wrap
import org.junit.Assert.*
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class YapiExporterProjectResolutionTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var exporterProject: Project
    private lateinit var exporter: YapiExporter
    private lateinit var testPsiClass: com.intellij.psi.PsiClass
    private lateinit var testPsiMethod: com.intellij.psi.PsiMethod

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        testPsiClass = findClass("com.itangcent.test.ResolutionCtrl")!!
        testPsiMethod = findMethod(testPsiClass, "testAction")!!
        exporterProject = createExporterProject()
        exporter = YapiExporter(exporterProject)
    }

    override fun createConfigReader() = TestConfigReader.empty(project)

    private fun loadTestFiles() {
        loadFile("test/ResolutionCtrl.java", """
            package com.itangcent.test;
            public class ResolutionCtrl {
                public void testAction() {}
                public void anotherAction() {}
            }
        """.trimIndent())
    }

    private fun createExporterProject(): Project {
        val settingsHelper = mock<YapiSettingsHelper> {
            onBlocking { resolveServerUrl(any()) } doReturn "http://localhost:3000"
            onBlocking { resolveToken(any(), any()) } doReturn null
        }
        return wrap(project) {
            replaceService(YapiSettingsHelper::class, settingsHelper)
        }
    }

    private fun updateConfig(vararg rules: Pair<String, String>) {
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(project, *rules)
        )
        project.messageBus.syncPublisher(ConfigReloadListener.TOPIC).onConfigReloaded()
    }

    private fun createTestEndpoint(
        sourceMethod: com.intellij.psi.PsiMethod? = null,
        sourceClass: com.intellij.psi.PsiClass? = null
    ): ApiEndpoint {
        return ApiEndpoint(
            name = "Test API",
            sourceMethod = sourceMethod,
            sourceClass = sourceClass,
            metadata = httpMetadata(path = "/api/test", method = HttpMethod.GET)
        )
    }

    private fun createTestContext(endpoints: List<ApiEndpoint>): ExportContext {
        return ExportContext(
            project = exporterProject,
            endpoints = endpoints,
            channelId = "yapi",
            channelConfig = ChannelConfig.Empty
        )
    }

    private fun resolveYapiProjectFromError(result: ExportResult): String? {
        if (result is ExportResult.Error) {
            val regex = Regex("No valid token for module '(.+?)'")
            val match = regex.find(result.message)
            return match?.groupValues?.get(1)
        }
        return null
    }

    @org.junit.Test
    fun `test method rule resolves yapi project`() {
        updateConfig("yapi.project" to """groovy:"method-project"""")

        val endpoint = createTestEndpoint(
            sourceMethod = testPsiMethod,
            sourceClass = testPsiClass
        )
        val context = createTestContext(listOf(endpoint))
        val result = kotlinx.coroutines.runBlocking { exporter.export(context) }

        val resolvedProject = resolveYapiProjectFromError(result)
        assertEquals("method-project", resolvedProject)
    }

    @org.junit.Test
    fun `test falls back to class when method rule returns null`() {
        updateConfig("yapi.project" to """groovy:it.contextType() == "class" ? "class-project" : null""")

        val endpoint = createTestEndpoint(
            sourceMethod = testPsiMethod,
            sourceClass = testPsiClass
        )
        val context = createTestContext(listOf(endpoint))
        val result = kotlinx.coroutines.runBlocking { exporter.export(context) }

        val resolvedProject = resolveYapiProjectFromError(result)
        assertEquals(
            "When resolveYapiProject(psiMethod) returns null, should fall back to resolveYapiProject(psiClass)",
            "class-project",
            resolvedProject
        )
    }

    @org.junit.Test
    fun `test class rule resolves when method is null`() {
        updateConfig("yapi.project" to """groovy:"class-project"""")

        val endpoint = createTestEndpoint(
            sourceMethod = null,
            sourceClass = testPsiClass
        )
        val context = createTestContext(listOf(endpoint))
        val result = kotlinx.coroutines.runBlocking { exporter.export(context) }

        val resolvedProject = resolveYapiProjectFromError(result)
        assertEquals("class-project", resolvedProject)
    }

    @org.junit.Test
    fun `test blank method result does not fall back to class`() {
        updateConfig("yapi.project" to """groovy:it.contextType() == "class" ? "class-project" : " """)

        val endpoint = createTestEndpoint(
            sourceMethod = testPsiMethod,
            sourceClass = testPsiClass
        )
        val context = createTestContext(listOf(endpoint))
        val result = kotlinx.coroutines.runBlocking { exporter.export(context) }

        val resolvedProject = resolveYapiProjectFromError(result)
        assertNotEquals(
            "Blank string from method rule should NOT trigger fallback to class rule",
            "class-project",
            resolvedProject
        )
    }

    @org.junit.Test
    fun `test falls back to project name when both method and class return null`() {
        val endpoint = createTestEndpoint(
            sourceMethod = testPsiMethod,
            sourceClass = testPsiClass
        )
        val context = createTestContext(listOf(endpoint))
        val result = kotlinx.coroutines.runBlocking { exporter.export(context) }

        val resolvedProject = resolveYapiProjectFromError(result)
        assertNotNull(
            "Should fall back to module name or project name when both rules return null",
            resolvedProject
        )
    }

    @org.junit.Test
    fun `test method rule takes precedence over class rule`() {
        updateConfig("yapi.project" to """groovy:it.contextType() == "method" ? "method-project" : "class-project"""")

        val endpoint = createTestEndpoint(
            sourceMethod = testPsiMethod,
            sourceClass = testPsiClass
        )
        val context = createTestContext(listOf(endpoint))
        val result = kotlinx.coroutines.runBlocking { exporter.export(context) }

        val resolvedProject = resolveYapiProjectFromError(result)
        assertEquals(
            "When both method and class rules resolve, method should take precedence",
            "method-project",
            resolvedProject
        )
    }

    @org.junit.Test
    fun `test no source method or class falls back to project name`() {
        val endpoint = createTestEndpoint(
            sourceMethod = null,
            sourceClass = null
        )
        val context = createTestContext(listOf(endpoint))
        val result = kotlinx.coroutines.runBlocking { exporter.export(context) }

        val resolvedProject = resolveYapiProjectFromError(result)
        assertNotNull(
            "Should fall back to project name when no source method or class",
            resolvedProject
        )
    }
}
