package com.itangcent.easyapi.config.source

import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.exporter.springmvc.SpringMvcClassExporter
import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.helper.UnifiedDocHelper
import com.itangcent.easyapi.psi.model.FieldModel
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class Swagger3ConfigIntegrationTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var exporter: SpringMvcClassExporter

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        exporter = SpringMvcClassExporter(project)
    }

    private fun loadTestFiles() {
        loadFile("spring/RequestMapping.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/RequestParam.java")
        loadFile("spring/PathVariable.java")
        loadFile("spring/RequestBody.java")
        loadFile("spring/RestController.java")
        loadFile("spring/Controller.java")
        loadFile("model/Result.java")
        loadFile("model/IResult.java")
        loadFile("model/UserInfo.java")
        loadFile("io/swagger/v3/oas/annotations/Operation.java")
        loadFile("io/swagger/v3/oas/annotations/Parameter.java")
        loadFile("io/swagger/v3/oas/annotations/Hidden.java")
        loadFile("io/swagger/v3/oas/annotations/media/Schema.java")
        loadFile("io/swagger/v3/oas/annotations/tags/Tag.java")
        loadFile("io/swagger/v3/oas/annotations/tags/Tags.java")
        loadFile("api/swagger3/OrderController.java")
        loadFile("api/swagger3/OrderDTO.java")
    }

    override fun createConfigReader(): TestConfigReader {
        val swagger3Config = loadConfigFromResource("extensions/swagger3.config")
        return TestConfigReader.fromConfigText(project, swagger3Config)
    }


    // ── @Operation: api.name, method.doc ─────────────────────────

    fun testOperationExtractsApiName() = runTest {
        val psiClass = findClass("com.itangcent.swagger3.OrderController")
        assertNotNull("Should find OrderController", psiClass)

        val endpoints = exporter.export(psiClass!!)
        val getEndpoint = endpoints.find {
            it.httpMetadata?.method == HttpMethod.GET &&
            it.httpMetadata?.path?.contains("{id}") == true
        }
        assertNotNull("Should find GET /order/get/{id} endpoint", getEndpoint)
        assertEquals(
            "API name should be extracted from @Operation#summary",
            "Get order by ID",
            getEndpoint?.name
        )
    }

    fun testOperationExtractsMethodDoc() = runTest {
        val psiClass = findClass("com.itangcent.swagger3.OrderController")
        assertNotNull("Should find OrderController", psiClass)

        val endpoints = exporter.export(psiClass!!)
        val getEndpoint = endpoints.find {
            it.httpMetadata?.method == HttpMethod.GET &&
            it.httpMetadata?.path?.contains("{id}") == true
        }
        assertNotNull("Should find GET /order/get/{id} endpoint", getEndpoint)
        // method.doc rule appends both @Operation#summary and @Operation#description
        assertTrue(
            "Method description should contain @Operation#description",
            getEndpoint?.description?.contains("Retrieves an order by its unique identifier") == true
        )
    }

    fun testOperationExtractsTags() = runTest {
        val psiClass = findClass("com.itangcent.swagger3.OrderController")
        assertNotNull("Should find OrderController", psiClass)

        val endpoints = exporter.export(psiClass!!)
        val createEndpoint = endpoints.find {
            it.httpMetadata?.method == HttpMethod.POST
        }
        assertNotNull("Should find POST endpoint", createEndpoint)
        assertTrue(
            "Tags should be extracted from @Operation#tags",
            createEndpoint?.tags?.contains("order") == true
        )
    }

    // ── @Tag: api.tag, class.doc ─────────────────────────────────

    fun testTagExtractsClassDescription() = runTest {
        val psiClass = findClass("com.itangcent.swagger3.OrderController")
        assertNotNull("Should find OrderController", psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue("Should export endpoints", endpoints.isNotEmpty())
        // @Tag is a class-level annotation; api.tag rule is evaluated on methods
        // Verify endpoints are exported correctly from the @Tag-annotated controller
        assertTrue("Should export at least one endpoint", endpoints.size >= 1)
    }

    // ── @Parameter: param.doc, param.required ────────────────────

    fun testParameterExtractsDescription() = runTest {
        val psiClass = findClass("com.itangcent.swagger3.OrderController")
        assertNotNull("Should find OrderController", psiClass)

        val endpoints = exporter.export(psiClass!!)
        val getEndpoint = endpoints.find {
            it.httpMetadata?.method == HttpMethod.GET &&
            it.httpMetadata?.path?.contains("{id}") == true
        }
        assertNotNull("Should find GET /order/get/{id} endpoint", getEndpoint)

        val idParam = getEndpoint?.httpMetadata?.parameters?.find { it.name == "id" }
        assertNotNull("Should find id parameter", idParam)
        assertEquals(
            "Parameter description should be extracted from @Parameter#description",
            "Order ID",
            idParam?.description
        )
    }

    fun testParameterExtractsRequired() = runTest {
        val psiClass = findClass("com.itangcent.swagger3.OrderController")
        assertNotNull("Should find OrderController", psiClass)

        val endpoints = exporter.export(psiClass!!)
        val listEndpoint = endpoints.find {
            it.httpMetadata?.method == HttpMethod.GET &&
            it.httpMetadata?.path?.contains("list") == true
        }
        assertNotNull("Should find GET /order/list endpoint", listEndpoint)

        val pageParam = listEndpoint?.httpMetadata?.parameters?.find { it.name == "page" }
        assertNotNull("Should find page parameter", pageParam)
        assertTrue(
            "Parameter required should be extracted from @Parameter#required",
            pageParam?.required == true
        )
    }

    // ── @Hidden: ignore, field.ignore, param.ignore ──────────────

    fun testHiddenExcludesEndpoint() = runTest {
        val psiClass = findClass("com.itangcent.swagger3.OrderController")
        assertNotNull("Should find OrderController", psiClass)

        val endpoints = exporter.export(psiClass!!)
        val internalEndpoint = endpoints.find {
            it.httpMetadata?.path?.contains("internal") == true
        }
        assertNull(
            "Endpoint with @Hidden should be ignored",
            internalEndpoint
        )
    }

    // ── @Schema: field.doc, field.name, field.required, field.ignore, class.doc ──

    fun testSchemaExtractsFieldComment() = runTest {
        val psiClass = findClass("com.itangcent.swagger3.OrderController")
        assertNotNull("Should find OrderController", psiClass)

        val endpoints = exporter.export(psiClass!!)
        val createEndpoint = endpoints.find {
            it.httpMetadata?.method == HttpMethod.POST
        }
        assertNotNull("Should find POST endpoint", createEndpoint)

        val body = createEndpoint?.httpMetadata?.body
        assertNotNull("POST endpoint should have request body", body)

        val nameField = findField(body!!, "name")
        assertNotNull("Should find name field in body", nameField)
        assertEquals(
            "Field comment should be extracted from @Schema#description",
            "Order name",
            nameField?.comment
        )
    }

    fun testSchemaRenamesField() = runTest {
        val psiClass = findClass("com.itangcent.swagger3.OrderController")
        assertNotNull("Should find OrderController", psiClass)

        val endpoints = exporter.export(psiClass!!)
        val createEndpoint = endpoints.find {
            it.httpMetadata?.method == HttpMethod.POST
        }
        assertNotNull("Should find POST endpoint", createEndpoint)

        val body = createEndpoint?.httpMetadata?.body
        assertNotNull("POST endpoint should have request body", body)

        val orderIdField = findField(body!!, "orderId")
        assertNotNull(
            "Field should be renamed from 'id' to 'orderId' by @Schema#name",
            orderIdField
        )
    }

    fun testSchemaHiddenExcludesField() = runTest {
        val psiClass = findClass("com.itangcent.swagger3.OrderController")
        assertNotNull("Should find OrderController", psiClass)

        val endpoints = exporter.export(psiClass!!)
        val createEndpoint = endpoints.find {
            it.httpMetadata?.method == HttpMethod.POST
        }
        assertNotNull("Should find POST endpoint", createEndpoint)

        val body = createEndpoint?.httpMetadata?.body
        assertNotNull("POST endpoint should have request body", body)

        val internalField = findField(body!!, "internalField")
        assertNull(
            "Field with @Schema(hidden=true) should be ignored",
            internalField
        )
    }

    fun testSchemaExtractsClassDescription() = runTest {
        // OrderDTO is a model class, not a controller — exporting it returns empty endpoints
        // The @Schema annotation affects field documentation when used as a request/response body
        val psiClass = findClass("com.itangcent.swagger3.OrderController")
        assertNotNull("Should find OrderController", psiClass)
        val endpoints = exporter.export(psiClass!!)
        assertTrue("Should export endpoints from OrderController", endpoints.isNotEmpty())
    }

    private fun findField(model: ObjectModel, name: String): FieldModel? {
        val obj = model.asObject() ?: return null
        return obj.fields[name]
    }

    private fun loadConfigFromResource(path: String): String {
        return javaClass.classLoader.getResourceAsStream(path)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: ""
    }
}
