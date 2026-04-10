package com.itangcent.easyapi.exporter.springmvc

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.psi.type.ResolvedType
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
        val classType = ResolvedType.ClassType(psiClass, emptyList())
        val resolvedMethod = classType.methods().first { it.name == "get" }

        val mappings = resolver.resolve(resolvedMethod)
        assertEquals(1, mappings.size)
        
        val mapping = mappings[0]
        assertEquals("/user/get/{id}", mapping.path)
        assertEquals(HttpMethod.GET, mapping.method)
    }

    fun testResolveGetMappingWithPathVariable() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")!!
        val classType = ResolvedType.ClassType(psiClass, emptyList())
        val resolvedMethod = classType.methods().first { it.name == "get" }

        val mappings = resolver.resolve(resolvedMethod)
        assertEquals(1, mappings.size)
        
        val mapping = mappings[0]
        assertEquals("/user/get/{id}", mapping.path)
        assertEquals(HttpMethod.GET, mapping.method)
    }

    fun testResolvePostMappingWithMultiplePaths() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")!!
        val classType = ResolvedType.ClassType(psiClass, emptyList())
        val resolvedMethod = classType.methods().first { it.name == "create" }

        val mappings = resolver.resolve(resolvedMethod)
        assertEquals(2, mappings.size)
        
        val paths = mappings.map { it.path }.sorted()
        assertEquals(listOf("/user/add", "/user/admin/add"), paths)
        
        mappings.forEach { assertEquals(HttpMethod.POST, it.method) }
    }

    fun testResolvePutMapping() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")!!
        val classType = ResolvedType.ClassType(psiClass, emptyList())
        val resolvedMethod = classType.methods().first { it.name == "update" }

        val mappings = resolver.resolve(resolvedMethod)
        assertEquals(1, mappings.size)
        
        val mapping = mappings[0]
        assertEquals("/user/update", mapping.path)
        assertEquals(HttpMethod.PUT, mapping.method)
    }

    fun testResolveNestedController() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl.ProfileApi")!!
        val classType = ResolvedType.ClassType(psiClass, emptyList())
        val resolvedMethod = classType.methods().first { it.name == "getProfileSettings" }

        val mappings = resolver.resolve(resolvedMethod)
        
        assertTrue(mappings.isNotEmpty())
        mappings.forEach { mapping ->
            assertTrue(mapping.path.contains("profile"))
            assertEquals(HttpMethod.GET, mapping.method)
        }
    }

    fun testResolveClassType_NoMethodMapping_ReturnsEmpty() = runTest {
        // Verifies the early-exit: a method with no mapping annotation (on itself or supers)
        // returns empty without walking supertypes for class-level mapping.
        loadFile("spring/RequestMapping.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("model/Result.java")
        loadFile("model/IResult.java")
        loadFile("model/UserInfo.java")
        loadFile("api/inherit/PlainBaseCtrl.java")
        loadFile("api/inherit/PlainSubCtrl.java")
        // PlainSubCtrl extends AnnotatedBaseCtrl — but here we use PlainBaseCtrl which has no annotations
        // Load a plain sub that has no method annotations and no super method annotations
        loadFile("api/inherit/AnnotatedBaseCtrl.java")

        val psiClass = findClass("com.itangcent.api.inherit.PlainBaseCtrl")!!
        val classType = ResolvedType.ClassType(psiClass, emptyList())
        val resolvedMethod = classType.methods().first { it.name == "getItem" }

        // PlainBaseCtrl.getItem has no mapping annotation, and no super with one either
        val mappings = resolver.resolve(resolvedMethod)
        assertTrue("Method with no mapping should return empty list", mappings.isEmpty())
    }

    fun testResolveClassType_InheritedMethodAnnotation() = runTest {
        // Verifies that resolve(resolvedMethod) finds annotations on super methods.
        loadFile("spring/RequestMapping.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("model/Result.java")
        loadFile("model/IResult.java")
        loadFile("model/UserInfo.java")
        loadFile("api/inherit/AnnotatedBaseCtrl.java")
        loadFile("api/inherit/PlainSubCtrl.java")

        val psiClass = findClass("com.itangcent.api.inherit.PlainSubCtrl")!!
        val classType = ResolvedType.ClassType(psiClass, emptyList())
        val resolvedMethod = classType.methods().first { it.name == "getItem" }

        // PlainSubCtrl.getItem has no @GetMapping, but AnnotatedBaseCtrl.getItem does
        // Class-level @RequestMapping("/annotated-base") is on AnnotatedBaseCtrl
        val mappings = resolver.resolve(resolvedMethod)
        assertTrue("Should resolve inherited method annotation", mappings.isNotEmpty())
        assertEquals(HttpMethod.GET, mappings.first().method)
        assertTrue(
            "Path should include class prefix from AnnotatedBaseCtrl",
            mappings.first().path.contains("annotated-base")
        )
    }

    fun testResolveClassType_CustomAnnotationNotMapping_NoNPE() = runTest {
        // Test for NPE fix: custom annotations that are NOT mapping annotations should not cause NPE
        loadFile("api/inherit/SendAuditLog.java")
        loadFile("api/inherit/OrderApi.java")
        loadFile("api/inherit/OrderController.java")

        val psiClass = findClass("com.itangcent.api.inherit.OrderController")!!
        val classType = ResolvedType.ClassType(psiClass, emptyList())
        val resolvedMethod = classType.methods().first { it.name == "createOrder" }

        // OrderController.createOrder has @SendAuditLog (not a mapping annotation)
        // OrderApi.createOrder has @PostMapping
        // Should resolve without NPE
        val mappings = resolver.resolve(resolvedMethod)
        assertTrue("Should resolve mapping from interface", mappings.isNotEmpty())
        assertEquals(HttpMethod.POST, mappings.first().method)
        assertTrue(
            "Path should include /order prefix from interface",
            mappings.first().path.contains("order")
        )
    }
}
