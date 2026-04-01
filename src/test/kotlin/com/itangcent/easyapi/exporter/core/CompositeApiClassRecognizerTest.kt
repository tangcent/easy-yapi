package com.itangcent.easyapi.exporter.core

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class CompositeApiClassRecognizerTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var recognizer: CompositeApiClassRecognizer

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        recognizer = CompositeApiClassRecognizer(project)
    }

    private fun loadTestFiles() {
        loadFile("spring/RestController.java")
        loadFile("spring/Controller.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/RequestMapping.java")
        loadFile("spring/FeignClient.java")
        loadFile("jaxrs/Path.java")
        loadFile("jaxrs/GET.java")
        loadFile("jaxrs/POST.java")
        loadFile("feign/RequestLine.java")
        loadFile("model/IResult.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("api/UserCtrl.java")
    }

    override fun createConfigReader() = TestConfigReader.EMPTY

    fun testGetInstance() {
        val instance = CompositeApiClassRecognizer.getInstance(project)
        assertNotNull(instance)
        assertSame(instance, CompositeApiClassRecognizer.getInstance(project))
    }

    fun testIsApiClassWithRestController() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull(psiClass)

        val isApi = recognizer.isApiClass(psiClass!!)
        assertTrue("Should recognize @RestController class as API class", isApi)
    }

    fun testIsApiClassWithNonApiClass() = runTest {
        val psiClass = findClass("com.itangcent.model.UserInfo")
        assertNotNull(psiClass)

        val isApi = recognizer.isApiClass(psiClass!!)
        assertFalse("Should not recognize model class as API class", isApi)
    }

    fun testMatchingFrameworksWithRestController() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull(psiClass)

        val frameworks = recognizer.matchingFrameworks(psiClass!!)
        assertTrue("Should have at least one matching framework", frameworks.isNotEmpty())
        assertTrue("Should include Spring MVC", frameworks.any { it.contains("Spring", ignoreCase = true) })
    }

    fun testMatchingFrameworksWitNonApiClass() = runTest {
        val psiClass = findClass("com.itangcent.model.UserInfo")
        assertNotNull(psiClass)

        val frameworks = recognizer.matchingFrameworks(psiClass!!)
        assertTrue("Should have no matching frameworks", frameworks.isEmpty())
    }

    fun testAllTargetAnnotations() {
        val annotations = recognizer.allTargetAnnotations
        assertTrue("Should have target annotations", annotations.isNotEmpty())
        assertTrue("Should include Spring annotations", 
            annotations.any { it.contains("RestController") || it.contains("Controller") })
    }
}
