package com.itangcent.easyapi.exporter.springmvc

import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.exporter.model.path
import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.helper.UnifiedDocHelper
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

/**
 * Integration tests for inherited controller export covering all cases:
 * - Case 2: Annotations on super, no annotations on override
 * - Case 3: Annotations on override, no annotations on super
 * - Case 4: Generic interface with annotations on super
 * - Composite: Generic + annotations on super + override without annotations
 */
class InheritedControllerExportTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var exporter: SpringMvcClassExporter

    override fun setUp() {
        super.setUp()
        loadFile("spring/RequestMapping.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/PutMapping.java")
        loadFile("spring/DeleteMapping.java")
        loadFile("spring/PatchMapping.java")
        loadFile("spring/RequestParam.java")
        loadFile("spring/PathVariable.java")
        loadFile("spring/RequestBody.java")
        loadFile("spring/RequestHeader.java")
        loadFile("spring/ModelAttribute.java")
        loadFile("spring/RestController.java")
        loadFile("spring/Controller.java")
        loadFile("model/Result.java")
        loadFile("model/IResult.java")
        loadFile("model/UserInfo.java")
        exporter = SpringMvcClassExporter(project)
    }

    override fun createConfigReader() = TestConfigReader.empty(project)


    // ========== Case 2: Annotations on super abstract class ==========

    fun testCase2_AnnotationsOnSuper_EndpointsExported() = runTest {
        loadFile("api/inherit/AnnotatedBaseCtrl.java")
        loadFile("api/inherit/PlainSubCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.PlainSubCtrl")!!
        val endpoints = exporter.export(psiClass)

        assertTrue("Should export endpoints", endpoints.isNotEmpty())

        val getItem = endpoints.find { it.path.contains("item") && it.httpMetadata?.method == HttpMethod.GET }
        assertNotNull("Should export GET /annotated-base/item", getItem)

        val postItem = endpoints.find { it.path.contains("item") && it.httpMetadata?.method == HttpMethod.POST }
        assertNotNull("Should export POST /annotated-base/item", postItem)
    }

    // ========== Case 3: Annotations on override ==========

    fun testCase3_AnnotationsOnOverride_EndpointsExported() = runTest {
        loadFile("api/inherit/PlainBaseCtrl.java")
        loadFile("api/inherit/AnnotatedSubCtrl.java")
        val psiClass = findClass("com.itangcent.api.inherit.AnnotatedSubCtrl")!!
        val endpoints = exporter.export(psiClass)

        assertTrue("Should export endpoints", endpoints.isNotEmpty())

        val getItem = endpoints.find { it.httpMetadata?.method == HttpMethod.GET }
        assertNotNull("Should export GET endpoint from override annotations", getItem)

        val postItem = endpoints.find { it.httpMetadata?.method == HttpMethod.POST }
        assertNotNull("Should export POST endpoint from override annotations", postItem)
    }

    // ========== Case 4: Generic interface with annotations ==========

    fun testCase4_GenericInterface_EndpointsExported() = runTest {
        loadFile("api/inherit/GenericIface.java")
        loadFile("api/inherit/GenericIfaceImpl.java")
        val psiClass = findClass("com.itangcent.api.inherit.GenericIfaceImpl")!!
        val endpoints = exporter.export(psiClass)

        assertTrue("Should export endpoints from generic interface", endpoints.isNotEmpty())

        val getQuery = endpoints.find { it.httpMetadata?.method == HttpMethod.GET }
        assertNotNull("Should export GET endpoint", getQuery)

        val postSave = endpoints.find { it.httpMetadata?.method == HttpMethod.POST }
        assertNotNull("Should export POST endpoint", postSave)
    }

    fun testCase4_GenericInterface_SupertypeRequestMappingPath() = runTest {
        loadFile("api/inherit/GenericIface.java")
        loadFile("api/inherit/GenericIfaceImpl.java")
        val psiClass = findClass("com.itangcent.api.inherit.GenericIfaceImpl")!!
        val endpoints = exporter.export(psiClass)

        // GenericIface has @RequestMapping("/generic-iface"), GenericIfaceImpl has none
        // Supertype walk should find the class-level path
        val getQuery = endpoints.find { it.httpMetadata?.method == HttpMethod.GET }
        assertNotNull(getQuery)
        assertTrue(
            "Path should include /generic-iface prefix from interface, got: ${getQuery!!.path}",
            getQuery.path.contains("generic-iface")
        )
    }

    // ========== Composite: Generic + annotations on super + override without annotations ==========

    fun testComposite_GenericWithAnnotationsOnSuper_EndpointsExported() = runTest {
        loadFile("api/inherit/AnnotatedGenericBase.java")
        loadFile("api/inherit/PlainGenericSub.java")
        val psiClass = findClass("com.itangcent.api.inherit.PlainGenericSub")!!
        val endpoints = exporter.export(psiClass)

        assertTrue("Should export endpoints from composite case", endpoints.isNotEmpty())

        val getQuery = endpoints.find { it.httpMetadata?.method == HttpMethod.GET }
        assertNotNull("Should export GET /generic-base/query", getQuery)

        val postSave = endpoints.find { it.httpMetadata?.method == HttpMethod.POST }
        assertNotNull("Should export POST /generic-base/save", postSave)
    }

    fun testComposite_GenericWithAnnotationsOnSuper_PathPrefix() = runTest {
        loadFile("api/inherit/AnnotatedGenericBase.java")
        loadFile("api/inherit/PlainGenericSub.java")
        val psiClass = findClass("com.itangcent.api.inherit.PlainGenericSub")!!
        val endpoints = exporter.export(psiClass)

        // AnnotatedGenericBase has @RequestMapping("/generic-base")
        val getQuery = endpoints.find { it.httpMetadata?.method == HttpMethod.GET }
        assertNotNull(getQuery)
        assertTrue(
            "Path should include /generic-base prefix, got: ${getQuery!!.path}",
            getQuery.path.contains("generic-base")
        )
    }

    // ========== Existing IUserApi + UserApiImpl (from issue #1285) ==========

    fun testIUserApi_EndpointsExported() = runTest {
        loadFile("model/Model.java")
        loadFile("api/IUserApi.java")
        loadFile("api/UserApiImpl.java")
        val psiClass = findClass("com.itangcent.api.UserApiImpl")!!
        val endpoints = exporter.export(psiClass)

        assertTrue("Should export endpoints from UserApiImpl", endpoints.isNotEmpty())

        val loginAuth = endpoints.find { it.path.contains("loginAuth") }
        assertNotNull("Should export loginAuth endpoint", loginAuth)
    }

    fun testIUserApi_SupertypeRequestMappingPath() = runTest {
        loadFile("model/Model.java")
        loadFile("api/IUserApi.java")
        loadFile("api/UserApiImpl.java")
        val psiClass = findClass("com.itangcent.api.UserApiImpl")!!
        val endpoints = exporter.export(psiClass)

        // IUserApi has @RequestMapping("user"), UserApiImpl has none
        val loginAuth = endpoints.find { it.path.contains("loginAuth") }
        assertNotNull(loginAuth)
        assertTrue(
            "Path should include /user prefix from IUserApi, got: ${loginAuth!!.path}",
            loginAuth.path.contains("user")
        )
    }

    fun testIUserApi_RequestBodyInheritedFromInterface() = runTest {
        loadFile("model/Model.java")
        loadFile("api/IUserApi.java")
        loadFile("api/UserApiImpl.java")
        val psiClass = findClass("com.itangcent.api.UserApiImpl")!!
        val endpoints = exporter.export(psiClass)

        // IUserApi.loginAuth has @RequestBody on parameter, UserApiImpl does not re-declare it
        // The @RequestBody should be inherited from the interface
        val loginAuth = endpoints.find { it.path.contains("loginAuth") }
        assertNotNull("Should export loginAuth endpoint", loginAuth)

        val httpMetadata = loginAuth!!.httpMetadata
        assertNotNull("Should have httpMetadata", httpMetadata)

        val body = httpMetadata!!.body
        assertNotNull(
            "Should have request body inherited from interface @RequestBody annotation",
            body
        )
    }

    fun testComposite_RequestBodyInheritedFromGenericBase() = runTest {
        loadFile("api/inherit/AnnotatedGenericBase.java")
        loadFile("api/inherit/PlainGenericSub.java")
        val psiClass = findClass("com.itangcent.api.inherit.PlainGenericSub")!!
        val endpoints = exporter.export(psiClass)

        // AnnotatedGenericBase.save has @RequestBody on parameter, PlainGenericSub does not re-declare it
        val postSave = endpoints.find { it.httpMetadata?.method == HttpMethod.POST }
        assertNotNull("Should export POST /generic-base/save", postSave)

        val httpMetadata = postSave!!.httpMetadata
        assertNotNull("Should have httpMetadata", httpMetadata)

        val body = httpMetadata!!.body
        assertNotNull(
            "Should have request body inherited from generic base @RequestBody annotation",
            body
        )
    }
}
