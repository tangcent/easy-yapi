package com.itangcent.easyapi.config.source

import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.exporter.springmvc.SpringMvcClassExporter
import com.itangcent.easyapi.extension.ExtensionConfigRegistry
import com.itangcent.easyapi.psi.PsiClassHelper
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class ConvertsConfigIntegrationTest : EasyApiLightCodeInsightFixtureTestCase() {

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
        loadJDKClass("java.util.Date")
        loadJDKClass("java.math.BigInteger")
        loadFile("api/converts/ConvertDTO.java")
        loadFile("api/converts/ConvertController.java")
    }

    override fun createConfigReader(): TestConfigReader {
        val extension = ExtensionConfigRegistry.getExtension("converts")
        assertNotNull("converts extension should exist", extension)
        return TestConfigReader.fromConfigText(project, extension?.content ?: "")
    }

    fun testConvertsConfigLoadsCorrectly() = runTest {
        val extension = ExtensionConfigRegistry.getExtension("converts")
        assertNotNull("converts extension should exist", extension)
        assertEquals("converts", extension?.code)
        assertTrue(extension?.content?.isNotBlank() == true)
    }

    fun testDateIsConvertedToString() = runTest {
        val psiClass = findClass("com.itangcent.converts.ConvertDTO")
        assertNotNull("Should find ConvertDTO", psiClass)

        val helper = PsiClassHelper.getInstance(project)
        val model = helper.buildObjectModel(psiClass!!)
        assertNotNull("Should build object model", model)

        val fields = model?.asObject()?.fields
        assertNotNull("Should have fields", fields)

        val createTimeField = fields!!["createTime"]
        assertNotNull("Field 'createTime' should exist", createTimeField)

        val fieldType = (createTimeField!!.model as? ObjectModel.Single)?.type
        assertEquals("Date field should be converted to string", "string", fieldType)
    }

    fun testBigIntegerIsConvertedToLong() = runTest {
        val psiClass = findClass("com.itangcent.converts.ConvertDTO")
        assertNotNull("Should find ConvertDTO", psiClass)

        val helper = PsiClassHelper.getInstance(project)
        val model = helper.buildObjectModel(psiClass!!)
        assertNotNull("Should build object model", model)

        val fields = model?.asObject()?.fields
        assertNotNull("Should have fields", fields)

        val idField = fields!!["id"]
        assertNotNull("Field 'id' should exist", idField)

        val fieldType = (idField!!.model as? ObjectModel.Single)?.type
        assertEquals("BigInteger field should be converted to long", "long", fieldType)
    }

    fun testStringFieldIsNotConverted() = runTest {
        val psiClass = findClass("com.itangcent.converts.ConvertDTO")
        assertNotNull("Should find ConvertDTO", psiClass)

        val helper = PsiClassHelper.getInstance(project)
        val model = helper.buildObjectModel(psiClass!!)
        assertNotNull("Should build object model", model)

        val fields = model?.asObject()?.fields
        assertNotNull("Should have fields", fields)

        val nameField = fields!!["name"]
        assertNotNull("Field 'name' should exist", nameField)

        val fieldType = (nameField!!.model as? ObjectModel.Single)?.type
        assertEquals("String field should remain string", "string", fieldType)
    }

    fun testControllerExportsEndpoints() = runTest {
        val psiClass = findClass("com.itangcent.converts.ConvertController")
        assertNotNull("Should find ConvertController", psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertEquals("Should export 1 endpoint", 1, endpoints.size)

        val postEndpoint = endpoints.find { it.httpMetadata?.method == HttpMethod.POST }
        assertNotNull("Should find POST endpoint", postEndpoint)
        assertEquals("/converts/create", postEndpoint?.httpMetadata?.path)
    }
}
