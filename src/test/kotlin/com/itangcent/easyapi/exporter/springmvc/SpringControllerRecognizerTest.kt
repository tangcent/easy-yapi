package com.itangcent.easyapi.exporter.springmvc

import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.rule.engine.RuleEngine
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class SpringControllerRecognizerTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var recognizer: SpringControllerRecognizer

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        val ruleEngine = RuleEngine(actionContext, actionContext.instance(ConfigReader::class))
        recognizer = SpringControllerRecognizer(ruleEngine)
    }

    private fun loadTestFiles() {
        loadFile("spring/RestController.java")
        loadFile("spring/Controller.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/RequestMapping.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("api/UserCtrl.java")
    }

    override fun createConfigReader() = TestConfigReader.EMPTY

    fun testRecognizeRestController() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull(psiClass)

        val isController = recognizer.isController(psiClass!!)
        assertTrue("Should recognize @RestController class as controller", isController)
    }

    fun testRecognizeNonController() = runTest {
        val psiClass = findClass("com.itangcent.model.UserInfo")
        assertNotNull(psiClass)

        val isController = recognizer.isController(psiClass!!)
        assertFalse("Should not recognize model class as controller", isController)
    }
}
