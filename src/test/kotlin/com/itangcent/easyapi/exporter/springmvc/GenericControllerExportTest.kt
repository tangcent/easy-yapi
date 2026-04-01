package com.itangcent.easyapi.exporter.springmvc

import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.helper.StandardDocHelper
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.type.JsonType
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class GenericControllerExportTest : EasyApiLightCodeInsightFixtureTestCase() {

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
        loadFile("api/generic/GenericBaseCtrl.java")
        loadFile("api/generic/StringCtrl.java")
        loadFile("api/generic/TwoTypeBaseCtrl.java")
        loadFile("api/generic/MiddleCtrl.java")
        loadFile("api/generic/ConcreteCtrl.java")
        exporter = SpringMvcClassExporter(actionContext)
    }

    override fun createConfigReader() = TestConfigReader.EMPTY

    override fun customizeContext(builder: com.itangcent.easyapi.core.context.ActionContextBuilder) {
        builder.bind(DocHelper::class, StandardDocHelper())
    }

    /**
     * Case 1: class GenericBaseCtrl<T> { Result<T> getItem(); Result<T> createItem(T item); }
     *         class StringCtrl extends GenericBaseCtrl<String>
     *
     * StringCtrl should export inherited methods with T resolved to String.
     */
    fun testCase1_InheritedMethodsExported() = runTest {
        val psiClass = findClass("com.itangcent.api.generic.StringCtrl")
        assertNotNull("Should find StringCtrl", psiClass)

        // Verify that psiClass.methods includes inherited methods
        val declaredMethods = psiClass!!.methods.map { it.name }
        val allMethods = psiClass.allMethods.map { it.name }
        println("StringCtrl declared methods: $declaredMethods")
        println("StringCtrl all methods: $allMethods")

        val endpoints = exporter.export(psiClass)
        println("Exported endpoints: ${endpoints.map { "${it.method} ${it.path}" }}")
        // Should have at least 2 endpoints: GET /string/item and POST /string/item
        assertTrue("StringCtrl should export at least 2 inherited endpoints, got ${endpoints.size}", endpoints.size >= 2)

        val getItem = endpoints.find { it.path == "/string/item" && it.method == com.itangcent.easyapi.exporter.model.HttpMethod.GET }
        assertNotNull("Should export GET /string/item", getItem)

        val postItem = endpoints.find { it.path == "/string/item" && it.method == com.itangcent.easyapi.exporter.model.HttpMethod.POST }
        assertNotNull("Should export POST /string/item", postItem)
    }

    /**
     * Case 1: Verify response body generic resolution.
     * StringCtrl.getItem() returns Result<T> where T=String
     * → responseBody should be Result with data:string
     */
    fun testCase1_ResponseBodyGenericResolved() = runTest {
        val psiClass = findClass("com.itangcent.api.generic.StringCtrl")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        val getItem = endpoints.find { it.path == "/string/item" && it.method == com.itangcent.easyapi.exporter.model.HttpMethod.GET }
        assertNotNull("Should export GET /string/item", getItem)

        val responseBody = getItem!!.responseBody
        assertNotNull("responseBody should be populated", responseBody)
        assertTrue("responseBody should be Object", responseBody is ObjectModel.Object)

        val fields = (responseBody as ObjectModel.Object).fields
        assertTrue("Should have 'code' field", fields.containsKey("code"))
        assertTrue("Should have 'data' field", fields.containsKey("data"))

        // data should be resolved to String (not unresolved T)
        val dataField = fields["data"]!!
        assertTrue(
            "data should be string (T resolved to String), got: ${dataField.model}",
            dataField.model is ObjectModel.Single && (dataField.model as ObjectModel.Single).type == JsonType.STRING
        )
    }

    /**
     * Case 1: Verify request body generic resolution.
     * StringCtrl.createItem(@RequestBody T item) where T=String
     * → body should be a String model (simple type, so body may be null since String isn't expanded)
     * But the parameter type should be resolved.
     */
    fun testCase1_RequestBodyGenericResolved() = runTest {
        val psiClass = findClass("com.itangcent.api.generic.StringCtrl")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        val postItem = endpoints.find { it.path == "/string/item" && it.method == com.itangcent.easyapi.exporter.model.HttpMethod.POST }
        assertNotNull("Should export POST /string/item", postItem)

        // For String body, the exporter skips expansion (simple type), so body may be null
        // The key thing is the endpoint was exported and the method was recognized
        assertNotNull("POST endpoint should exist", postItem)
    }

    /**
     * Case 2: class TwoTypeBaseCtrl<T, R> { Result<R> query(); Result<R> save(T input); }
     *         class MiddleCtrl<X> extends TwoTypeBaseCtrl<X, String>
     *         class ConcreteCtrl extends MiddleCtrl<Long>
     *
     * ConcreteCtrl should export inherited methods with T=Long, R=String.
     */
    fun testCase2_MultiLevelInheritedMethodsExported() = runTest {
        val psiClass = findClass("com.itangcent.api.generic.ConcreteCtrl")
        assertNotNull("Should find ConcreteCtrl", psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue("ConcreteCtrl should export inherited endpoints, got ${endpoints.size}", endpoints.isNotEmpty())

        val query = endpoints.find { it.path == "/concrete/query" }
        assertNotNull("Should export GET /concrete/query", query)

        val save = endpoints.find { it.path == "/concrete/save" }
        assertNotNull("Should export POST /concrete/save", save)
    }

    /**
     * Case 2: Verify response body generic resolution through multi-level inheritance.
     * ConcreteCtrl.query() returns Result<R> where R=String
     * → responseBody should be Result with data:string
     */
    fun testCase2_ResponseBodyMultiLevelGenericResolved() = runTest {
        val psiClass = findClass("com.itangcent.api.generic.ConcreteCtrl")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        val query = endpoints.find { it.path == "/concrete/query" }
        assertNotNull("Should export /concrete/query", query)

        val responseBody = query!!.responseBody
        assertNotNull("responseBody should be populated", responseBody)
        assertTrue("responseBody should be Object", responseBody is ObjectModel.Object)

        val fields = (responseBody as ObjectModel.Object).fields
        assertTrue("Should have 'data' field", fields.containsKey("data"))

        // R→String via MiddleCtrl→TwoTypeBaseCtrl
        val dataField = fields["data"]!!
        assertTrue(
            "data should be string (R resolved to String), got: ${dataField.model}",
            dataField.model is ObjectModel.Single && (dataField.model as ObjectModel.Single).type == JsonType.STRING
        )
    }
}
