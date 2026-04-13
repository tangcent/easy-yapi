package com.itangcent.easyapi.exporter.springmvc

import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.psi.helper.UnifiedAnnotationHelper
import com.itangcent.easyapi.rule.engine.RuleEngine
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class ContentTypeResolverTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var resolver: ContentTypeResolver

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        val annotationHelper = UnifiedAnnotationHelper()
        val ruleEngine = RuleEngine(project, createConfigReader())
        resolver = ContentTypeResolver(annotationHelper, ruleEngine)
    }

    private fun loadTestFiles() {
        loadFile("spring/RestController.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/PutMapping.java")
        loadFile("spring/DeleteMapping.java")
        loadFile("spring/RequestMapping.java")
        loadFile("spring/RequestBody.java")
        loadFile("spring/RequestParam.java")
        loadFile("spring/PathVariable.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("api/UserCtrl.java")
    }

    override fun createConfigReader() = TestConfigReader.EMPTY

    fun testResolveContentTypeForGetMethod() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull(psiClass)

        val method = findMethod(psiClass!!, "greeting")
        assertNotNull(method)

        val mapping = ResolvedMapping("/user/greeting", HttpMethod.GET)
        val contentType = resolver.resolve(method!!, mapping)
        assertNull("GET method should have no content type by default", contentType)
    }

    fun testResolveContentTypeForPostWithBody() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull(psiClass)

        val method = findMethod(psiClass!!, "create")
        assertNotNull(method)

        val mapping = ResolvedMapping("/user/add", HttpMethod.POST)
        val contentType = resolver.resolve(method!!, mapping)
        assertEquals("application/json", contentType)
    }

    fun testResolveContentTypeWithConsumes() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull(psiClass)

        val method = findMethod(psiClass!!, "create")
        assertNotNull(method)

        val mapping = ResolvedMapping("/user/add", HttpMethod.POST, consumes = listOf("application/xml"))
        val contentType = resolver.resolve(method!!, mapping)
        assertEquals("application/xml", contentType)
    }
}
