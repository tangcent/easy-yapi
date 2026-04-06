package com.itangcent.easyapi.integration

import com.itangcent.easyapi.exporter.postman.PostmanFormatOptions
import com.itangcent.easyapi.exporter.postman.PostmanFormatter
import com.itangcent.easyapi.exporter.springmvc.SpringMvcClassExporter
import com.itangcent.easyapi.exporter.curl.CurlFormatter
import com.itangcent.easyapi.exporter.markdown.DefaultMarkdownFormatter
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.helper.StandardDocHelper
import com.itangcent.easyapi.psi.helper.UnifiedAnnotationHelper
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class EndToEndExportTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var springMvcExporter: SpringMvcClassExporter

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        springMvcExporter = SpringMvcClassExporter(actionContext)
    }

    private fun loadTestFiles() {
        loadFile("spring/GetMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/PutMapping.java")
        loadFile("spring/DeleteMapping.java")
        loadFile("spring/RequestMapping.java")
        loadFile("spring/PathVariable.java")
        loadFile("spring/RequestBody.java")
        loadFile("spring/RequestParam.java")
        loadFile("spring/RestController.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("constant/UserType.java")
        loadFile("api/UserCtrl.java")
    }

    override fun createConfigReader() = TestConfigReader.EMPTY

    override fun customizeContext(builder: com.itangcent.easyapi.core.context.ActionContextBuilder) {
        builder.bind(DocHelper::class, StandardDocHelper())
    }

    fun testExportSpringMvcToPostman() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull(psiClass)

        val endpoints = springMvcExporter.export(psiClass!!)
        assertTrue("Should export at least one endpoint", endpoints.isNotEmpty())

        val formatter = PostmanFormatter(
            actionContext = actionContext,
            options = PostmanFormatOptions(buildExample = true, autoMergeScript = false)
        )
        val collection = formatter.format(endpoints, "User API")

        assertNotNull("Collection should not be null", collection)
        assertTrue("Collection name should start with User API", collection.info?.name?.startsWith("User API") == true)
        assertTrue("Collection should have items", collection.item?.isNotEmpty() == true)
    }

    fun testExportSpringMvcToCurl() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull(psiClass)

        val endpoints = springMvcExporter.export(psiClass!!)
        assertTrue("Should export at least one endpoint", endpoints.isNotEmpty())

        val curlCommands = endpoints.map { CurlFormatter.format(it, "http://localhost:8080") }

        assertTrue("Should generate curl commands", curlCommands.isNotEmpty())
        curlCommands.forEach { curl ->
            assertTrue("Curl command should start with 'curl'", curl.startsWith("curl"))
        }
    }

    fun testExportSpringMvcToMarkdown() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull(psiClass)

        val endpoints = springMvcExporter.export(psiClass!!)
        assertTrue("Should export at least one endpoint", endpoints.isNotEmpty())

        val formatter = DefaultMarkdownFormatter(outputDemo = true)
        val markdown = formatter.format(endpoints, "User API")

        assertTrue("Markdown should contain title", markdown.contains("# User API"))
        assertTrue("Markdown should contain endpoints", markdown.contains("###"))
    }

    fun testExportPipelineAllSteps() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull(psiClass)

        val endpoints = springMvcExporter.export(psiClass!!)

        assertTrue("Step: Source discovery - Should find controller class", endpoints.isNotEmpty())

        endpoints.forEach { endpoint ->
            val path = endpoint.httpMetadata?.path
            assertNotNull("Step: Path resolution - Path should not be null", path)
            assertTrue("Step: Path resolution - Path should start with /", path!!.startsWith("/"))

            assertNotNull("Step: HTTP method - Method should not be null", endpoint.httpMetadata?.method)

            endpoint.httpMetadata?.parameters?.forEach { param ->
                assertNotNull("Step: Parameter extraction - Parameter name should not be null", param.name)
                assertNotNull("Step: Parameter binding - Binding should not be null", param.binding)
            }
        }

        val postmanCollection = PostmanFormatter(actionContext = actionContext).format(endpoints, "Test API")
        assertNotNull("Step: Format conversion - Postman collection should not be null", postmanCollection)

        val markdown = DefaultMarkdownFormatter().format(endpoints, "Test API")
        assertTrue("Step: Format conversion - Markdown should not be empty", markdown.isNotBlank())

        val curlCommands = endpoints.map { CurlFormatter.format(it, "http://localhost") }
        assertTrue("Step: Format conversion - Should generate curl commands", curlCommands.isNotEmpty())
    }
}
