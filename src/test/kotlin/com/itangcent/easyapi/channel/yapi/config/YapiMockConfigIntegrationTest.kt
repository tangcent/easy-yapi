package com.itangcent.easyapi.channel.yapi.config

import com.itangcent.easyapi.channel.yapi.MockRuleLoader
import com.itangcent.easyapi.core.extension.ExtensionConfigRegistry
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class YapiMockConfigIntegrationTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun setUp() {
        super.setUp()
        loadTestFiles()
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
    }

    override fun createConfigReader(): TestConfigReader {
        val extension = ExtensionConfigRegistry.getExtension("yapi-mock")
        assertNotNull("yapi-mock extension should exist", extension)
        return TestConfigReader.fromConfigText(project, extension?.content ?: "")
    }

    fun testYapiMockConfigLoadsCorrectly() = runTest {
        val extension = ExtensionConfigRegistry.getExtension("yapi-mock")
        assertNotNull(extension)
        assertEquals("yapi-mock", extension?.code)
        assertTrue(extension?.content?.isNotBlank() == true)
    }

    fun testMockRulesLoaded() {
        val rules = MockRuleLoader.getInstance(project).getMockRules()
        assertTrue("Should have mock rules loaded", rules.isNotEmpty())
    }

    fun testEmailMockRule() {
        val rules = MockRuleLoader.getInstance(project).getMockRules()
        assertEquals("@email", rules["*.email|string"])
    }

    fun testPhoneMockRule() {
        val rules = MockRuleLoader.getInstance(project).getMockRules()
        assertEquals("@phone", rules["*.phone|string"])
    }

    fun testPasswordMockRule() {
        val rules = MockRuleLoader.getInstance(project).getMockRules()
        assertEquals("******", rules["*.password|string"])
    }

    fun testIdMockRule() {
        val rules = MockRuleLoader.getInstance(project).getMockRules()
        assertEquals("@integer", rules["*.id|integer"])
    }

    fun testNameMockRule() {
        val rules = MockRuleLoader.getInstance(project).getMockRules()
        assertEquals("@string", rules["*.name|string"])
    }

    fun testTokenMockRule() {
        val rules = MockRuleLoader.getInstance(project).getMockRules()
        assertEquals("@string(32)", rules["*.token|string"])
    }

    fun testAddressMockRule() {
        val rules = MockRuleLoader.getInstance(project).getMockRules()
        assertEquals("@county(true)", rules["*.address|string"])
    }

    fun testDatetimeMockRule() {
        val rules = MockRuleLoader.getInstance(project).getMockRules()
        assertEquals("@datetime", rules["*.datetime|string"])
    }
}
