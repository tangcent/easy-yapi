package com.itangcent.easyapi.framework.springmvc

import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.core.export.ParameterBinding
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.core.export.path
import com.itangcent.easyapi.core.psi.helper.DocHelper
import com.itangcent.easyapi.core.psi.helper.UnifiedDocHelper
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

    // ========== Issue #1352: Non-overridden parent methods not exported ==========

    fun testIssue1352_ParentMethodsWithoutOverride_Exported() = runTest {
        loadFile("api/inherit/AbstractBaseController.java")
        loadFile("api/inherit/ChildController.java")
        val psiClass = findClass("com.itangcent.api.inherit.ChildController")!!
        val endpoints = exporter.export(psiClass)

        assertTrue("Should export endpoints from both parent and child", endpoints.isNotEmpty())

        val getItem = endpoints.find { it.path.contains("item") && it.httpMetadata?.method == HttpMethod.GET }
        assertNotNull("Should export GET /base/item inherited from AbstractBaseController", getItem)

        val postItem = endpoints.find { it.path.contains("item") && it.httpMetadata?.method == HttpMethod.POST }
        assertNotNull("Should export POST /base/item inherited from AbstractBaseController", postItem)

        val getOwn = endpoints.find { it.path.contains("own") && it.httpMetadata?.method == HttpMethod.GET }
        assertNotNull("Should export GET /own from ChildController", getOwn)
    }

    fun testIssue1352_ParentMethodPathPrefix() = runTest {
        loadFile("api/inherit/AbstractBaseController.java")
        loadFile("api/inherit/ChildController.java")
        val psiClass = findClass("com.itangcent.api.inherit.ChildController")!!
        val endpoints = exporter.export(psiClass)

        val getItem = endpoints.find { it.path.contains("item") && it.httpMetadata?.method == HttpMethod.GET }
        assertNotNull(getItem)
        assertTrue(
            "Path should include /base prefix from AbstractBaseController @RequestMapping, got: ${getItem!!.path}",
            getItem.path.contains("base")
        )
    }

    // ========== Issue #1343: bounded generic interface (LQ extends IQuery) ==========

    fun testIssue1343_BoundedGenericInterface_ThreeEndpointsExported() = runTest {
        loadFile("api/inherit/issue1343/IQuery.java")
        loadFile("api/inherit/issue1343/ConcreteQuery.java")
        loadFile("api/inherit/issue1343/IController.java")
        loadFile("api/inherit/issue1343/BusinessController.java")
        val psiClass = findClass("com.itangcent.api.inherit.issue1343.BusinessController")!!
        val endpoints = exporter.export(psiClass)

        assertEquals("Should export 3 endpoints, got: ${endpoints.size}", 3, endpoints.size)

        val getQuery = endpoints.find { it.httpMetadata?.method == HttpMethod.GET }
        assertNotNull("Should export GET /api/query (mapping inherited from IController)", getQuery)
        assertTrue(
            "GET path should be /api/query, got: ${getQuery!!.path}",
            getQuery.path.endsWith("/api/query")
        )

        val postSave = endpoints.find { it.httpMetadata?.method == HttpMethod.POST }
        assertNotNull("Should export POST /api/save (mapping inherited from IController)", postSave)
        assertTrue(
            "POST path should be /api/save, got: ${postSave!!.path}",
            postSave.path.endsWith("/api/save")
        )

        val deleteById = endpoints.find { it.httpMetadata?.method == HttpMethod.DELETE }
        assertNotNull("Should export DELETE /api/{id} (mapping inherited from IController)", deleteById)
        assertTrue(
            "DELETE path should be /api/{id}, got: ${deleteById!!.path}",
            deleteById.path.contains("/api/") && deleteById.path.contains("{id}")
        )
    }

    fun testIssue1343_BoundedGenericInterface_RequestBodyInherited() = runTest {
        loadFile("api/inherit/issue1343/IQuery.java")
        loadFile("api/inherit/issue1343/ConcreteQuery.java")
        loadFile("api/inherit/issue1343/IController.java")
        loadFile("api/inherit/issue1343/BusinessController.java")
        val psiClass = findClass("com.itangcent.api.inherit.issue1343.BusinessController")!!
        val endpoints = exporter.export(psiClass)

        // IController.save declares @RequestBody on its parameter; BusinessController does not re-declare it.
        // The @RequestBody must be inherited via searchParameterAnnotation -> superMethods().
        val postSave = endpoints.find { it.httpMetadata?.method == HttpMethod.POST }
        assertNotNull("Should export POST /api/save", postSave)
        assertNotNull(
            "save endpoint should have a request body inherited from interface @RequestBody",
            postSave!!.httpMetadata?.body
        )
    }

    fun testIssue1343_BoundedGenericInterface_PathVariableInherited() = runTest {
        loadFile("api/inherit/issue1343/IQuery.java")
        loadFile("api/inherit/issue1343/ConcreteQuery.java")
        loadFile("api/inherit/issue1343/IController.java")
        loadFile("api/inherit/issue1343/BusinessController.java")
        val psiClass = findClass("com.itangcent.api.inherit.issue1343.BusinessController")!!
        val endpoints = exporter.export(psiClass)

        // IController.delete declares @PathVariable("id"); BusinessController does not re-declare it.
        val deleteById = endpoints.find { it.httpMetadata?.method == HttpMethod.DELETE }
        assertNotNull("Should export DELETE /api/{id}", deleteById)
        val pathParams = deleteById!!.httpMetadata?.parameters
            ?.filter { it.binding == ParameterBinding.Path } ?: emptyList()
        assertTrue(
            "delete endpoint should have a Path parameter 'id' inherited from interface @PathVariable, " +
                "got: ${deleteById.httpMetadata?.parameters?.map { it.name + ":" + it.binding }}",
            pathParams.any { it.name == "id" }
        )
    }
}
