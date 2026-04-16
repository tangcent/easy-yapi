package com.itangcent.easyapi.config.source

import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.config.LayeredConfigReader
import com.itangcent.easyapi.config.parser.ConfigTextParser
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
        loadFile("com/fasterxml/jackson/annotation/JsonPropertyOrder.java")
        loadFile("com/fasterxml/jackson/annotation/JsonIgnoreProperties.java")
        loadFile("com/fasterxml/jackson/annotation/JsonUnwrapped.java")
        loadFile("com/fasterxml/jackson/annotation/JsonView.java")
        loadFile("api/jackson/UserDTO.java")
        loadFile("api/jackson/UserController.java")
        loadFile("api/jackson/OrderedDTO.java")
        loadFile("api/jackson/IgnorePropertiesDTO.java")
        loadFile("api/jackson/AddressDTO.java")
        loadFile("api/jackson/UnwrappedDTO.java")
        loadFile("api/jackson/JsonViewViews.java")
        loadFile("api/jackson/ViewDTO.java")
    }

    override fun createConfigReader(): TestConfigReader {
        val extension = ExtensionConfigRegistry.getExtension("jackson")
        assertNotNull("jackson extension should exist", extension)
        return TestConfigReader.fromConfigText(project, extension?.content ?: "")
    }

    fun testJacksonConfigLoadsCorrectly() = runTest {
        val extension = ExtensionConfigRegistry.getExtension("jackson")
        assertNotNull("jackson extension should exist", extension)
        assertEquals("Extension code should be jackson", "jackson", extension?.code)
        assertTrue("Extension should have content", extension?.content?.isNotBlank() == true)
    }

    // =====================================================================
    // @JsonProperty - field renaming
    // =====================================================================

    fun testJsonPropertyRenamesFields() = runTest {
        val fields = exportPostBodyFields("/user/create")

        assertTrue("Field 'user_id' should exist (renamed from 'id' via @JsonProperty)", fields.containsKey("user_id"))
        assertTrue(
            "Field 'user_name' should exist (renamed from 'name' via @JsonProperty)",
            fields.containsKey("user_name")
        )
        assertFalse("Original field 'id' should NOT exist", fields.containsKey("id"))
        assertFalse("Original field 'name' should NOT exist", fields.containsKey("name"))
    }

    // =====================================================================
    // @JsonIgnore - field exclusion
    // =====================================================================

    fun testJsonIgnoreExcludesField() = runTest {
        val fields = exportPostBodyFields("/user/create")

        assertFalse("Field 'password' should be excluded by @JsonIgnore", fields.containsKey("password"))
    }

    // =====================================================================
    // @JsonFormat - date format pattern produces mock value
    // =====================================================================

    fun testJsonFormatSetsMockValue() = runTest {
        val fields = exportPostBodyFields("/user/create")

        val createTimeField = fields["createTime"]
        assertNotNull("Field 'createTime' should exist", createTimeField)
        val mock = createTimeField!!.mock
        assertNotNull("@JsonFormat should produce a mock value for createTime", mock)
        assertTrue(
            "Mock value should contain @datetime with pattern, was: $mock",
            mock!!.contains("@datetime") && mock.contains("yyyy-MM-dd HH:mm:ss")
        )
    }

    // =====================================================================
    // @JsonPropertyOrder - field ordering
    // =====================================================================

    fun testJsonPropertyOrderReordersFields() = runTest {
        val fields = exportPostBodyFields("/user/ordered")

        val fieldNames = fields.keys.toList()
        assertTrue("Should have at least 3 fields", fieldNames.size >= 3)

        val nameIdx = fieldNames.indexOf("name")
        val emailIdx = fieldNames.indexOf("email")
        val ageIdx = fieldNames.indexOf("age")

        assertTrue("'name' should appear before 'email' per @JsonPropertyOrder", nameIdx < emailIdx)
        assertTrue("'email' should appear before 'age' per @JsonPropertyOrder", emailIdx < ageIdx)
    }

    // =====================================================================
    // @JsonIgnoreProperties - class-level field exclusion
    // =====================================================================

    fun testJsonIgnorePropertiesExcludesFields() = runTest {
        val fields = exportPostBodyFields("/user/ignore-properties")

        assertTrue("Field 'id' should exist", fields.containsKey("id"))
        assertTrue("Field 'name' should exist", fields.containsKey("name"))
        assertFalse("Field 'internalId' should be excluded by @JsonIgnoreProperties", fields.containsKey("internalId"))
        assertFalse("Field 'secretKey' should be excluded by @JsonIgnoreProperties", fields.containsKey("secretKey"))
    }

    // =====================================================================
    // @JsonUnwrapped - flatten nested object with optional prefix/suffix
    // =====================================================================

    fun testJsonUnwrappedFlattensNestedObject() = runTest {
        val fields = exportPostBodyFields("/user/unwrapped")

        assertTrue("Field 'name' should exist", fields.containsKey("name"))

        assertTrue("Unwrapped field 'street' should exist (from address)", fields.containsKey("street"))
        assertTrue("Unwrapped field 'city' should exist (from address)", fields.containsKey("city"))
        assertTrue("Unwrapped field 'zipCode' should exist (from address)", fields.containsKey("zipCode"))

        assertFalse("Nested object 'address' should NOT exist as a sub-object", fields.containsKey("address"))
    }

    fun testJsonUnwrappedWithPrefixAndSuffix() = runTest {
        val fields = exportPostBodyFields("/user/unwrapped")

        assertTrue(
            "Unwrapped field 'home_street_addr' should exist (prefix=home_, suffix=_addr)",
            fields.containsKey("home_street_addr")
        )
        assertTrue(
            "Unwrapped field 'home_city_addr' should exist (prefix=home_, suffix=_addr)",
            fields.containsKey("home_city_addr")
        )
        assertTrue(
            "Unwrapped field 'home_zipCode_addr' should exist (prefix=home_, suffix=_addr)",
            fields.containsKey("home_zipCode_addr")
        )

        assertFalse("Nested object 'homeAddress' should NOT exist as a sub-object", fields.containsKey("homeAddress"))
    }

    // =====================================================================
    // @JsonView - view-based field filtering in response body
    // =====================================================================

    fun testJsonViewPublicOnlyShowsPublicFields() = runTest {
        val fields = exportGetResponseBodyFields("/user/view/public")

        assertTrue("Public field 'id' should exist in Public view", fields.containsKey("id"))
        assertTrue("Public field 'name' should exist in Public view", fields.containsKey("name"))
        assertFalse("Internal field 'email' should NOT exist in Public view", fields.containsKey("email"))
        assertFalse("Admin field 'password' should NOT exist in Public view", fields.containsKey("password"))
    }

    fun testJsonViewInternalShowsPublicAndInternalFields() = runTest {
        val fields = exportGetResponseBodyFields("/user/view/internal")

        assertTrue("Public field 'id' should exist in Internal view", fields.containsKey("id"))
        assertTrue("Public field 'name' should exist in Internal view", fields.containsKey("name"))
        assertTrue("Internal field 'email' should exist in Internal view", fields.containsKey("email"))
        assertFalse("Admin field 'password' should NOT exist in Internal view", fields.containsKey("password"))
    }

    fun testJsonViewAdminShowsAllFields() = runTest {
        val fields = exportGetResponseBodyFields("/user/view/admin")

        assertTrue("Public field 'id' should exist in Admin view", fields.containsKey("id"))
        assertTrue("Public field 'name' should exist in Admin view", fields.containsKey("name"))
        assertTrue("Internal field 'email' should exist in Admin view", fields.containsKey("email"))
        assertTrue("Admin field 'password' should exist in Admin view", fields.containsKey("password"))
    }

    // =====================================================================
    // Response body also applies jackson rules
    // =====================================================================

    fun testResponseBodyAppliesJacksonRules() = runTest {
        val fields = exportGetResponseBodyFields("/user/get")

        assertTrue("Response field 'user_id' should exist", fields.containsKey("user_id"))
        assertTrue("Response field 'user_name' should exist", fields.containsKey("user_name"))
        assertFalse("Response field 'password' should be excluded by @JsonIgnore", fields.containsKey("password"))
    }

    // =====================================================================
    // Helper methods
    // =====================================================================

    private suspend fun exportEndpoints(): List<com.itangcent.easyapi.exporter.model.ApiEndpoint> {
        val psiClass = findClass("com.itangcent.jackson.UserController")
        assertNotNull("Should find UserController", psiClass)
        return exporter.export(psiClass!!)
    }

    private suspend fun exportPostBodyFields(path: String): Map<String, FieldModel> {
        val endpoints = exportEndpoints()
        val endpoint = endpoints.find { it.httpMetadata?.method == HttpMethod.POST && it.httpMetadata?.path == path }
        assertNotNull("Should find POST endpoint at $path", endpoint)

        val body = endpoint!!.httpMetadata!!.body
        assertNotNull("Endpoint at $path should have a request body", body)

        return findFields(body!!) ?: run {
            fail("Request body should be an object, was: ${body::class.simpleName}")
            emptyMap()
        }
    }

    private suspend fun exportGetResponseBodyFields(path: String): Map<String, FieldModel> {
        val endpoints = exportEndpoints()
        val endpoint = endpoints.find {
            it.httpMetadata?.method == HttpMethod.GET && it.httpMetadata?.path?.startsWith(path) == true
        }
        assertNotNull("Should find GET endpoint at $path", endpoint)

        val responseBody = endpoint!!.httpMetadata!!.responseBody
        assertNotNull("Endpoint at $path should have a response body", responseBody)

        return findFields(responseBody!!) ?: run {
            fail("Response body should be an object, was: ${responseBody::class.simpleName}")
            emptyMap()
        }
    }

    private fun findFields(model: ObjectModel): Map<String, FieldModel>? = model.asObject()?.fields
}
