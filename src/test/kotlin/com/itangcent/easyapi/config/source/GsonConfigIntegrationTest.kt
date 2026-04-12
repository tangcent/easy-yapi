package com.itangcent.easyapi.config.source

import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.exporter.springmvc.SpringMvcClassExporter
import com.itangcent.easyapi.extension.ExtensionConfigRegistry
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class GsonConfigIntegrationTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var exporter: SpringMvcClassExporter

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        exporter = SpringMvcClassExporter(actionContext)
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
        loadFile("com/google/gson/annotations/SerializedName.java")
        loadFile("com/google/gson/annotations/Expose.java")
        loadFile("api/gson/ProductDTO.java")
        loadFile("api/gson/ProductController.java")
    }

    override fun createConfigReader(): TestConfigReader {
        val extension = ExtensionConfigRegistry.getExtension("gson")
        assertNotNull("gson extension should exist", extension)
        return TestConfigReader.fromConfigText(extension?.content ?: "")
    }

    override fun customizeContext(builder: com.itangcent.easyapi.core.context.ActionContextBuilder) {
        builder.bind(com.itangcent.easyapi.psi.helper.DocHelper::class, com.itangcent.easyapi.psi.helper.StandardDocHelper())
    }

    fun testGsonConfigLoadsCorrectly() = runTest {
        val extension = ExtensionConfigRegistry.getExtension("gson")
        assertNotNull("gson extension should exist", extension)
        assertEquals("Extension code should be gson", "gson", extension?.code)
        assertTrue("Extension should have content", extension?.content?.isNotBlank() == true)
    }

    fun testProductControllerExportsEndpoints() = runTest {
        val psiClass = findClass("com.itangcent.gson.ProductController")
        assertNotNull("Should find ProductController", psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertEquals("Should export 2 endpoints", 2, endpoints.size)

        val postEndpoint = endpoints.find { it.httpMetadata?.method == HttpMethod.POST }
        assertNotNull("Should find POST endpoint", postEndpoint)
        assertEquals("POST endpoint path should be /product/create", "/product/create", postEndpoint?.httpMetadata?.path)

        val getEndpoint = endpoints.find { it.httpMetadata?.method == HttpMethod.GET }
        assertNotNull("Should find GET endpoint", getEndpoint)
        assertTrue("GET endpoint path should contain /product/get", getEndpoint?.httpMetadata?.path?.contains("/product/get") == true)
    }

    fun testFieldsWithSerializedNameAreRenamed() = runTest {
        val psiClass = findClass("com.itangcent.gson.ProductDTO")
        assertNotNull("Should find ProductDTO", psiClass)

        val helper = com.itangcent.easyapi.psi.PsiClassHelper.getInstance(project)
        val model = helper.buildObjectModel(psiClass!!, actionContext)
        assertNotNull("Should build object model", model)

        val objectData = model?.asObject()
        assertNotNull("Should be an object", objectData)

        val fields = objectData!!.fields
        assertTrue("Field 'id' should be renamed to 'product_id' via @SerializedName", fields.containsKey("product_id"))
        assertTrue("Field 'name' should be renamed to 'product_name' via @SerializedName", fields.containsKey("product_name"))
        assertTrue("Field 'price' (without any annotation) should exist", fields.containsKey("price"))
    }
}
