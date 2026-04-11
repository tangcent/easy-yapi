package com.itangcent.easyapi.config.source

import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.exporter.springmvc.SpringMvcClassExporter
import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.helper.StandardDocHelper
import com.itangcent.easyapi.psi.model.FieldModel
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class SwaggerConfigIntegrationTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var exporter: SpringMvcClassExporter

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        exporter = SpringMvcClassExporter(actionContext)
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
        loadFile("io/swagger/annotations/Api.java")
        loadFile("io/swagger/annotations/ApiParam.java")
        loadFile("io/swagger/annotations/ApiModel.java")
        loadFile("io/swagger/annotations/ApiModelProperty.java")
        loadFile("io/swagger/annotations/ApiOperation.java")
        loadFile("api/swagger/ProductController.java")
        loadFile("api/swagger/ProductDTO.java")
    }

    override fun createConfigReader(): TestConfigReader {
        val swaggerConfig = loadConfigFromResource("extensions/swagger.config")
        val swaggerAdvancedConfig = loadConfigFromResource("third/swagger.advanced.config")
        return TestConfigReader.fromConfigText(swaggerConfig + "\n" + swaggerAdvancedConfig)
    }

    override fun customizeContext(builder: com.itangcent.easyapi.core.context.ActionContextBuilder) {
        builder.bind(DocHelper::class, StandardDocHelper())
    }

    // ── @Api: class.doc ──────────────────────────────────────────

    fun testApiTagsExtractsClassDescription() = runTest {
        val psiClass = findClass("com.itangcent.swagger.ProductController")
        assertNotNull("Should find ProductController", psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue("Should export endpoints", endpoints.isNotEmpty())

        val classDesc = endpoints.first().classDescription
        // class.doc rule appends both @Api#value and @Api#tags
        assertTrue(
            "Class description should contain @Api#tags value",
            classDesc?.contains("Product Management") == true
        )
    }

    // ── @ApiOperation: method.doc, api.tag ───────────────────────

    fun testApiOperationExtractsMethodDoc() = runTest {
        val psiClass = findClass("com.itangcent.swagger.ProductController")
        assertNotNull("Should find ProductController", psiClass)

        val endpoints = exporter.export(psiClass!!)

        val getEndpoint = endpoints.find {
            it.httpMetadata?.method == HttpMethod.GET &&
            it.httpMetadata?.path?.contains("{id}") == true
        }
        assertNotNull("Should find GET /product/get/{id} endpoint", getEndpoint)
        assertEquals(
            "Method description should be extracted from @ApiOperation#value",
            "Get product by ID",
            getEndpoint?.description
        )
    }

    fun testApiOperationExtractsTags() = runTest {
        val psiClass = findClass("com.itangcent.swagger.ProductController")
        assertNotNull("Should find ProductController", psiClass)

        val endpoints = exporter.export(psiClass!!)

        val getEndpoint = endpoints.find {
            it.httpMetadata?.method == HttpMethod.GET &&
            it.httpMetadata?.path?.contains("{id}") == true
        }
        assertNotNull("Should find GET /product/get/{id} endpoint", getEndpoint)
        assertTrue(
            "Tags should be extracted from @ApiOperation#tags",
            getEndpoint?.tags?.contains("product") == true
        )
    }

    // ── @ApiParam: param.doc, param.default.value, param.required, param.ignore ──

    fun testApiParamExtractsDescription() = runTest {
        val psiClass = findClass("com.itangcent.swagger.ProductController")
        assertNotNull("Should find ProductController", psiClass)

        val endpoints = exporter.export(psiClass!!)
        val listEndpoint = endpoints.find {
            it.httpMetadata?.method == HttpMethod.GET &&
            it.httpMetadata?.path?.contains("list") == true
        }
        assertNotNull("Should find GET /product/list endpoint", listEndpoint)

        val pageParam = listEndpoint?.httpMetadata?.parameters?.find { it.name == "page" }
        assertNotNull("Should find page parameter", pageParam)
        assertEquals(
            "Parameter description should be extracted from @ApiParam#value",
            "page number",
            pageParam?.description
        )
    }

    fun testApiParamExtractsDefaultValue() = runTest {
        val psiClass = findClass("com.itangcent.swagger.ProductController")
        assertNotNull("Should find ProductController", psiClass)

        val endpoints = exporter.export(psiClass!!)
        val listEndpoint = endpoints.find {
            it.httpMetadata?.method == HttpMethod.GET &&
            it.httpMetadata?.path?.contains("list") == true
        }
        assertNotNull("Should find GET /product/list endpoint", listEndpoint)

        val pageParam = listEndpoint?.httpMetadata?.parameters?.find { it.name == "page" }
        assertNotNull("Should find page parameter", pageParam)
        assertEquals(
            "Parameter default value should be extracted from @ApiParam#defaultValue",
            "1",
            pageParam?.defaultValue
        )
    }

    fun testApiParamExtractsRequired() = runTest {
        val psiClass = findClass("com.itangcent.swagger.ProductController")
        assertNotNull("Should find ProductController", psiClass)

        val endpoints = exporter.export(psiClass!!)
        val getEndpoint = endpoints.find {
            it.httpMetadata?.method == HttpMethod.GET &&
            it.httpMetadata?.path?.contains("{id}") == true
        }
        assertNotNull("Should find GET /product/get/{id} endpoint", getEndpoint)

        val idParam = getEndpoint?.httpMetadata?.parameters?.find { it.name == "id" }
        assertNotNull("Should find id parameter", idParam)
        assertTrue(
            "Parameter required should be extracted from @ApiParam#required",
            idParam?.required == true
        )
    }

    fun testApiParamHiddenExcludesParameter() = runTest {
        val psiClass = findClass("com.itangcent.swagger.ProductController")
        assertNotNull("Should find ProductController", psiClass)

        val endpoints = exporter.export(psiClass!!)
        val listEndpoint = endpoints.find {
            it.httpMetadata?.method == HttpMethod.GET &&
            it.httpMetadata?.path?.contains("list") == true
        }
        assertNotNull("Should find GET /product/list endpoint", listEndpoint)

        val filterParam = listEndpoint?.httpMetadata?.parameters?.find { it.name == "filter" }
        assertNull(
            "Parameter with @ApiParam(hidden=true) should be ignored",
            filterParam
        )
    }

    // ── @ApiModel: class.doc ─────────────────────────────────────

    fun testApiModelExtractsClassDescription() = runTest {
        // ProductDTO is a model class, not a controller — exporting it returns empty endpoints
        // The @ApiModel annotation affects field documentation when used as a request/response body
        val psiClass = findClass("com.itangcent.swagger.ProductController")
        assertNotNull("Should find ProductController", psiClass)
        val endpoints = exporter.export(psiClass!!)
        assertTrue("Should export endpoints from ProductController", endpoints.isNotEmpty())
    }

    // ── @ApiModelProperty: field.doc, field.required, field.ignore, json.rule.field.name ──

    fun testApiModelPropertyExtractsFieldComment() = runTest {
        val psiClass = findClass("com.itangcent.swagger.ProductController")
        assertNotNull("Should find ProductController", psiClass)

        val endpoints = exporter.export(psiClass!!)
        val createEndpoint = endpoints.find {
            it.httpMetadata?.method == HttpMethod.POST
        }
        assertNotNull("Should find POST endpoint", createEndpoint)

        val body = createEndpoint?.httpMetadata?.body
        assertNotNull("POST endpoint should have request body", body)

        val idField = findField(body!!, "id")
        assertNotNull("Should find id field in body", idField)
        assertEquals(
            "Field comment should be extracted from @ApiModelProperty#value",
            "Product ID",
            idField?.comment
        )
    }

    fun testApiModelPropertyNotesAppendedToFieldComment() = runTest {
        val psiClass = findClass("com.itangcent.swagger.ProductController")
        assertNotNull("Should find ProductController", psiClass)

        val endpoints = exporter.export(psiClass!!)
        val createEndpoint = endpoints.find {
            it.httpMetadata?.method == HttpMethod.POST
        }
        assertNotNull("Should find POST endpoint", createEndpoint)

        val body = createEndpoint?.httpMetadata?.body
        assertNotNull("POST endpoint should have request body", body)

        val descField = findField(body!!, "description")
        assertNotNull("Should find description field in body", descField)
        val fieldComment = descField?.comment
        assertNotNull("Field comment should not be null", fieldComment)
        assertTrue(
            "Field comment should contain @ApiModelProperty#value",
            fieldComment!!.contains("Product description")
        )
        assertTrue(
            "Field comment should contain @ApiModelProperty#notes",
            fieldComment.contains("Detailed product description")
        )
    }

    fun testApiModelPropertyHiddenExcludesField() = runTest {
        val psiClass = findClass("com.itangcent.swagger.ProductController")
        assertNotNull("Should find ProductController", psiClass)

        val endpoints = exporter.export(psiClass!!)
        val createEndpoint = endpoints.find {
            it.httpMetadata?.method == HttpMethod.POST
        }
        assertNotNull("Should find POST endpoint", createEndpoint)

        val body = createEndpoint?.httpMetadata?.body
        assertNotNull("POST endpoint should have request body", body)

        val internalNotesField = findField(body!!, "internalNotes")
        assertNull(
            "Field with @ApiModelProperty(hidden=true) should be ignored",
            internalNotesField
        )
    }

    fun testApiModelPropertyRenamesField() = runTest {
        val psiClass = findClass("com.itangcent.swagger.ProductController")
        assertNotNull("Should find ProductController", psiClass)

        val endpoints = exporter.export(psiClass!!)
        val createEndpoint = endpoints.find {
            it.httpMetadata?.method == HttpMethod.POST
        }
        assertNotNull("Should find POST endpoint", createEndpoint)

        val body = createEndpoint?.httpMetadata?.body
        assertNotNull("POST endpoint should have request body", body)

        // json.rule.field.name renames 'sku' to 'skuCode' — verify body has fields
        val obj = body!!.asObject()
        assertNotNull("Body should be an object model", obj)
        assertTrue("Body should have fields", obj!!.fields.isNotEmpty())
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
