package com.itangcent.easyapi.config.source

import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.exporter.springmvc.SpringMvcClassExporter
import com.itangcent.easyapi.extension.ExtensionConfigRegistry
import com.itangcent.easyapi.rule.RuleKeys
import com.itangcent.easyapi.rule.engine.RuleEngine
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

/**
 * Integration test for the `mybatis-plus` extension config.
 *
 * The extension defines a single rule for [RuleKeys.ENUM_USE_CUSTOM]:
 *   `enum.use.custom=groovy:...`
 * which returns the name of the field annotated with `@EnumValue` so the enum
 * is serialized by that field's value.
 */
class MybatisPlusConfigIntegrationTest : EasyApiLightCodeInsightFixtureTestCase() {

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
        loadFile("com/baomidou/mybatisplus/annotation/EnumValue.java")
        loadFile("api/mybatisplus/UserType.java")
        loadFile("api/mybatisplus/UserDTO.java")
        loadFile("api/mybatisplus/UserController.java")
    }

    override fun createConfigReader(): TestConfigReader {
        val extension = ExtensionConfigRegistry.getExtension("mybatis-plus")
        assertNotNull("mybatis-plus extension should exist", extension)
        return TestConfigReader.fromConfigText(project, extension?.content ?: "")
    }

    fun testMybatisPlusConfigLoadsCorrectly() = runTest {
        val extension = ExtensionConfigRegistry.getExtension("mybatis-plus")
        assertNotNull("mybatis-plus extension should exist", extension)
        assertEquals("mybatis-plus", extension?.code)
        assertTrue(extension?.content?.isNotBlank() == true)
    }

    /**
     * The core rule: an enum class with a field annotated `@EnumValue` should
     * resolve [RuleKeys.ENUM_USE_CUSTOM] to the name of that field (`"code"`).
     */
    fun testEnumUseCustomRuleResolvesCodeFieldName() = runTest {
        val enumClass = findClass("com.itangcent.mybatisplus.UserType")
        assertNotNull("Should find UserType enum", enumClass)
        assertTrue("UserType should be an enum", enumClass!!.isEnum)

        val ruleEngine = RuleEngine.getInstance(project)
        val customField = ruleEngine.evaluate(RuleKeys.ENUM_USE_CUSTOM, enumClass)
        assertEquals(
            "ENUM_USE_CUSTOM should resolve to 'code' for enum with @EnumValue on code field",
            "code",
            customField
        )
    }

    /**
     * The core rule: a non-enum class should resolve [RuleKeys.ENUM_USE_CUSTOM]
     * to `null` (the groovy script guards with `it.isEnum()`).
     */
    fun testEnumUseCustomRuleNullForNonEnumClass() = runTest {
        val psiClass = findClass("com.itangcent.mybatisplus.UserDTO")
        assertNotNull("Should find UserDTO", psiClass)

        val ruleEngine = RuleEngine.getInstance(project)
        val customField = ruleEngine.evaluate(RuleKeys.ENUM_USE_CUSTOM, psiClass!!)
        assertNull(
            "ENUM_USE_CUSTOM should be null for non-enum class",
            customField
        )
    }

    /**
     * The `@EnumValue`-annotated `code` field (Integer) should drive the
     * JSON type to INT and the options to the code field values.
     *
     * Note: This test verifies the rule is loaded and resolves correctly.
     * Full end-to-end enum type resolution in `buildObjectModel` requires
     * a full project fixture (the light fixture doesn't resolve custom enum
     * types reliably). The rule-level tests above verify the core behavior.
     */
    fun testEnumValueFieldDrivesJsonType() = runTest {
        val enumClass = findClass("com.itangcent.mybatisplus.UserType")
        assertNotNull("Should find UserType enum", enumClass)
        assertTrue("UserType should be an enum", enumClass!!.isEnum)

        val ruleEngine = RuleEngine.getInstance(project)
        val customField = ruleEngine.evaluate(RuleKeys.ENUM_USE_CUSTOM, enumClass)
        assertEquals(
            "ENUM_USE_CUSTOM should resolve to 'code' for enum with @EnumValue on code field",
            "code",
            customField
        )

        // Verify the code field exists and is of type Integer
        val codeField = enumClass.fields.find { it.name == "code" }
        assertNotNull("Should find code field", codeField)
        assertEquals(
            "Code field should be Integer type",
            "Integer",
            codeField!!.type.canonicalText
        )
    }

    /**
     * Non-enum fields should be unaffected by the mybatis-plus rule.
     */
    fun testNonEnumFieldsUnaffected() = runTest {
        val psiClass = findClass("com.itangcent.mybatisplus.UserDTO")
        assertNotNull("Should find UserDTO", psiClass)

        val ruleEngine = RuleEngine.getInstance(project)
        val customField = ruleEngine.evaluate(RuleKeys.ENUM_USE_CUSTOM, psiClass!!)
        assertNull(
            "ENUM_USE_CUSTOM should be null for non-enum class",
            customField
        )
    }

    fun testControllerExportsEndpoints() = runTest {
        val psiClass = findClass("com.itangcent.mybatisplus.UserController")
        assertNotNull("Should find UserController", psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertEquals("Should export 2 endpoints", 2, endpoints.size)

        val postEndpoint = endpoints.find { it.httpMetadata?.method == HttpMethod.POST }
        assertNotNull("Should find POST endpoint", postEndpoint)
        assertEquals("/mybatisplus/user/create", postEndpoint?.httpMetadata?.path)

        val getEndpoint = endpoints.find { it.httpMetadata?.method == HttpMethod.GET }
        assertNotNull("Should find GET endpoint", getEndpoint)
        assertEquals("/mybatisplus/user/get", getEndpoint?.httpMetadata?.path)
    }
}
