package com.itangcent.easyapi.exporter.springmvc

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class RequestMappingResolverTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var resolver: RequestMappingResolver

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        val annotationHelper = com.itangcent.easyapi.psi.helper.UnifiedAnnotationHelper()
        val engine = com.itangcent.easyapi.rule.engine.RuleEngine(actionContext, actionContext.instance())
        resolver = RequestMappingResolver(annotationHelper, engine)
    }

    private fun loadTestFiles() {
        loadFile("spring/RequestMapping.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/PutMapping.java")
        loadFile("spring/DeleteMapping.java")
        loadFile("spring/PatchMapping.java")
        loadFile("spring/RequestMethod.java")
        loadFile("spring/RestController.java")
        loadFile("spring/RequestBody.java")
        loadFile("api/UserCtrl.java")
    }

    override fun createConfigReader() = TestConfigReader.EMPTY

    fun testResolveSimpleGetMapping() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")!!
        val method = findMethod(psiClass, "greeting")
        assertNotNull(method)

        val mappings = resolver.resolve(psiClass, method!!)
        assertEquals(1, mappings.size)
        
        val mapping = mappings[0]
        assertEquals("/user/greeting", mapping.path)
        assertEquals(HttpMethod.GET, mapping.method)
    }

    fun testResolveGetMappingWithPathVariable() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")!!
        val method = findMethod(psiClass, "get")
        assertNotNull(method)

        val mappings = resolver.resolve(psiClass, method!!)
        assertEquals(1, mappings.size)
        
        val mapping = mappings[0]
        assertEquals("/user/get/{id}", mapping.path)
        assertEquals(HttpMethod.GET, mapping.method)
    }

    fun testResolvePostMappingWithMultiplePaths() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")!!
        val method = findMethod(psiClass, "create")
        assertNotNull(method)

        val mappings = resolver.resolve(psiClass, method!!)
        assertEquals(2, mappings.size)
        
        val paths = mappings.map { it.path }.sorted()
        assertEquals(listOf("/user/add", "/user/admin/add"), paths)
        
        mappings.forEach { assertEquals(HttpMethod.POST, it.method) }
    }

    fun testResolvePutMapping() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")!!
        val method = findMethod(psiClass, "update")
        assertNotNull(method)

        val mappings = resolver.resolve(psiClass, method!!)
        assertEquals(1, mappings.size)
        
        val mapping = mappings[0]
        assertEquals("/user/update", mapping.path)
        assertEquals(HttpMethod.PUT, mapping.method)
    }

    fun testResolveNestedController() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl.ProfileApi")!!
        val method = findMethod(psiClass, "getProfileSettings")
        assertNotNull(method)

        val parentClass = findClass("com.itangcent.api.UserCtrl")!!
        val mappings = resolver.resolve(parentClass, method!!)
        
        assertTrue(mappings.isNotEmpty())
        mappings.forEach { mapping ->
            assertTrue(mapping.path.contains("profile"))
            assertEquals(HttpMethod.GET, mapping.method)
        }
    }
}
