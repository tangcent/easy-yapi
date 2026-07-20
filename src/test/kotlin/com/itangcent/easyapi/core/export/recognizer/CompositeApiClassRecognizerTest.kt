package com.itangcent.easyapi.core.export.recognizer

import com.itangcent.easyapi.core.export.recognizer.ApiClassRecognizer
import com.itangcent.easyapi.core.export.recognizer.CompositeApiClassRecognizer
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

    override fun createConfigReader() = TestConfigReader.empty(project)

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

    /**
     * Decision CO5: `CompositeApiClassRecognizer.recognizers()` exposes the cached
     * recognizer list (populated by `buildRecognizers()` based on project settings)
     * so callers can iterate the EP-respecting seam instead of hard-coding concrete
     * framework recognizer imports.
     *
     * With default settings (no jaxrs/feign/actuator/grpc enabled), only
     * `SpringControllerRecognizer` is added — `recognizers()` must return a
     * non-empty list containing exactly that one recognizer. The Spring
     * recognizer is always enabled (unconditional `add` in `buildRecognizers`).
     */
    fun testRecognizersReturnsCachedList() {
        val list: List<ApiClassRecognizer> = recognizer.recognizers()

        assertNotNull("recognizers() must never return null", list)
        assertTrue(
            "recognizers() should contain at least SpringControllerRecognizer (always enabled): $list",
            list.isNotEmpty()
        )
        assertTrue(
            "recognizers() should include the SpringMVC framework (SpringControllerRecognizer is always enabled): ${list.map { it.frameworkName }}",
            list.any { it.frameworkName == "SpringMVC" }
        )
    }
}
