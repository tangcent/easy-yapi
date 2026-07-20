package com.itangcent.easyapi.framework.feign

import com.itangcent.easyapi.core.rule.engine.RuleEngine
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class FeignClientRecognizerTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var recognizer: FeignClientRecognizer

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        val ruleEngine = RuleEngine.getInstance(project)
        recognizer = FeignClientRecognizer(ruleEngine, enabled = true)
    }

    private fun loadTestFiles() {
        loadFile("spring/FeignClient.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("api/feign/UserClient.java")
    }

    override fun createConfigReader() = TestConfigReader.empty(project)

    fun testRecognizeFeignClient() = runTest {
        val psiClass = findClass("com.itangcent.springboot.demo.client.UserClient")
        assertNotNull(psiClass)

        val isFeignClient = recognizer.isFeignClient(psiClass!!)
        assertTrue("Should recognize @FeignClient class as Feign client", isFeignClient)
    }

    fun testRecognizeNonFeignClient() = runTest {
        val psiClass = findClass("com.itangcent.model.UserInfo")
        assertNotNull(psiClass)

        val isFeignClient = recognizer.isFeignClient(psiClass!!)
        assertFalse("Should not recognize model class as Feign client", isFeignClient)
    }

    fun testDisabledRecognizer() = runTest {
        val disabledRecognizer = FeignClientRecognizer(enabled = false)
        val psiClass = findClass("com.itangcent.springboot.demo.client.UserClient")
        assertNotNull(psiClass)

        val isFeignClient = disabledRecognizer.isFeignClient(psiClass!!)
        assertFalse("Disabled recognizer should return false", isFeignClient)
    }

    fun testFeignAnnotationsConstant() {
        assertEquals(
            setOf("org.springframework.cloud.openfeign.FeignClient"),
            FeignClientRecognizer.FEIGN_ANNOTATIONS
        )
    }

    fun testFrameworkName() {
        assertEquals("Feign", recognizer.frameworkName)
    }

    fun testTargetAnnotations() {
        assertEquals(FeignClientRecognizer.FEIGN_ANNOTATIONS, recognizer.targetAnnotations)
    }
}
