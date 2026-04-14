package com.itangcent.easyapi.config.source

import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.exporter.springmvc.SpringMvcClassExporter
import com.itangcent.easyapi.extension.ExtensionConfigRegistry
import com.itangcent.easyapi.psi.model.FieldModel
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class JacksonConfigIntegrationTest : EasyApiLightCodeInsightFixtureTestCase() {

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
        loadFile("com/fasterxml/jackson/annotation/JsonProperty.java")
        loadFile("com/fasterxml/jackson/annotation/JsonIgnore.java")
        loadFile("com/fasterxml/jackson/annotation/JsonFormat.java")
        loadFile("api/jackson/UserDTO.java")
        loadFile("api/jackson/UserController.java")
    }

    override fun createConfigReader(): TestConfigReader {
        val extension = ExtensionConfigRegistry.getExtension("jackson")
        assertNotNull("jackson extension should exist", extension)
        return TestConfigReader.fromConfigText(extension?.content ?: "")
    }

    fun testJacksonConfigLoadsCorrectly() = runTest {
        val extension = ExtensionConfigRegistry.getExtension("jackson")
        assertNotNull("jackson extension should exist", extension)
        assertEquals("Extension code should be jackson", "jackson", extension?.code)
        assertTrue("Extension should have content", extension?.content?.isNotBlank() == true)
    }

    fun testUserControllerExportsEndpoints() = runTest {
        val psiClass = findClass("com.itangcent.jackson.UserController")
        assertNotNull("Should find UserController", psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertEquals("Should export 2 endpoints", 2, endpoints.size)

        val postEndpoint = endpoints.find { it.httpMetadata?.method == HttpMethod.POST }
        assertNotNull("Should find POST endpoint", postEndpoint)
        assertEquals("POST endpoint path should be /user/create", "/user/create", postEndpoint?.httpMetadata?.path)

        val getEndpoint = endpoints.find { it.httpMetadata?.method == HttpMethod.GET }
        assertNotNull("Should find GET endpoint", getEndpoint)
        assertTrue("GET endpoint path should contain /user/get", getEndpoint?.httpMetadata?.path?.contains("/user/get") == true)
    }

    /**
     * Verifies that @JsonProperty renames fields in the exported body.
     * UserDTO.id -> "user_id", UserDTO.name -> "user_name"
     */
    fun testJsonPropertyRenamesFields() = runTest {
        val psiClass = findClass("com.itangcent.jackson.UserController")
        assertNotNull(psiClass)
        val endpoints = exporter.export(psiClass!!)

        val postEndpoint = endpoints.find { it.httpMetadata?.method == HttpMethod.POST }
        assertNotNull("Should find POST endpoint", postEndpoint)

        val body = postEndpoint!!.httpMetadata!!.body
        assertNotNull("POST endpoint should have a request body", body)

        val fields = findFields(body!!) ?: run {
            fail("Request body should be an object, was: ${body::class.simpleName}")
            return@runTest
        }

        assertTrue("Field 'user_id' should exist (renamed from 'id' via @JsonProperty)", fields.containsKey("user_id"))
        assertTrue("Field 'user_name' should exist (renamed from 'name' via @JsonProperty)", fields.containsKey("user_name"))
        assertFalse("Original field 'id' should NOT exist", fields.containsKey("id"))
        assertFalse("Original field 'name' should NOT exist", fields.containsKey("name"))
    }

    /**
     * Verifies that @JsonIgnore excludes the field from the exported body.
     * UserDTO.password is annotated with @JsonIgnore.
     */
    fun testJsonIgnoreExcludesField() = runTest {
        val psiClass = findClass("com.itangcent.jackson.UserController")
        assertNotNull(psiClass)
        val endpoints = exporter.export(psiClass!!)

        val postEndpoint = endpoints.find { it.httpMetadata?.method == HttpMethod.POST }
        assertNotNull("Should find POST endpoint", postEndpoint)

        val body = postEndpoint!!.httpMetadata!!.body
        assertNotNull("POST endpoint should have a request body", body)

        val fields = findFields(body!!) ?: run {
            fail("Request body should be an object, was: ${body::class.simpleName}")
            return@runTest
        }

        assertFalse("Field 'password' should be excluded by @JsonIgnore", fields.containsKey("password"))
    }

    /**
     * Verifies that @JsonFormat annotated field is present in the exported body.
     * UserDTO.createTime has @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss").
     */
    fun testJsonFormatFieldIsPresent() = runTest {
        val psiClass = findClass("com.itangcent.jackson.UserController")
        assertNotNull(psiClass)
        val endpoints = exporter.export(psiClass!!)

        val postEndpoint = endpoints.find { it.httpMetadata?.method == HttpMethod.POST }
        assertNotNull("Should find POST endpoint", postEndpoint)

        val body = postEndpoint!!.httpMetadata!!.body
        assertNotNull("POST endpoint should have a request body", body)

        val fields = findFields(body!!) ?: run {
            fail("Request body should be an object, was: ${body::class.simpleName}")
            return@runTest
        }

        // createTime should be present (not ignored) and @JsonFormat rule should not break parsing
        assertTrue("Field 'createTime' should exist", fields.containsKey("createTime"))
    }

    /**
     * Verifies that the response body also applies jackson rules (field renaming, ignore).
     */
    fun testResponseBodyAppliesJacksonRules() = runTest {
        val psiClass = findClass("com.itangcent.jackson.UserController")
        assertNotNull(psiClass)
        val endpoints = exporter.export(psiClass!!)

        val getEndpoint = endpoints.find { it.httpMetadata?.method == HttpMethod.GET }
        assertNotNull("Should find GET endpoint", getEndpoint)

        val responseBody = getEndpoint!!.httpMetadata!!.responseBody
        assertNotNull("GET endpoint should have a response body", responseBody)

        val fields = findFields(responseBody!!) ?: run {
            fail("Response body should be an object, was: ${responseBody::class.simpleName}")
            return@runTest
        }

        assertTrue("Response field 'user_id' should exist", fields.containsKey("user_id"))
        assertTrue("Response field 'user_name' should exist", fields.containsKey("user_name"))
        assertFalse("Response field 'password' should be excluded by @JsonIgnore", fields.containsKey("password"))
    }

    private fun findFields(model: ObjectModel): Map<String, FieldModel>? = model.asObject()?.fields
}
