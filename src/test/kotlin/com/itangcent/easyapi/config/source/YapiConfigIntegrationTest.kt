package com.itangcent.easyapi.config.source

import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.exporter.springmvc.SpringMvcClassExporter
import com.itangcent.easyapi.psi.model.FieldModel
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class YapiConfigIntegrationTest : EasyApiLightCodeInsightFixtureTestCase() {

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
        loadFile(
            "java/lang/Deprecated.java",
            "package java.lang;\npublic @interface Deprecated {}"
        )
        loadFile("api/yapi/YapiController.java")
        loadFile("api/yapi/ItemDTO.java")
        loadFile("api/yapi/DeprecatedController.java")
        loadFile("api/yapi/DeprecatedDocController.java")
    }

    override fun createConfigReader(): TestConfigReader {
        val yapiConfig = javaClass.getResourceAsStream("/extensions/yapi.config")
            ?.bufferedReader()?.readText() ?: ""
        return TestConfigReader.fromConfigText(project, yapiConfig)
    }

    // ── api.open[#open]=true ─────────────────────────────────────

    fun testApiOpenWithOpenTag() = runTest {
        val psiClass = findClass("com.itangcent.yapi.YapiController")
        assertNotNull("Should find YapiController", psiClass)

        val endpoints = exporter.export(psiClass!!)

        val publicEndpoint = endpoints.find {
            it.httpMetadata?.method == HttpMethod.GET &&
                    it.httpMetadata?.path?.contains("public") == true
        }
        assertNotNull("Should find GET /yapi/public endpoint", publicEndpoint)
        assertTrue(
            "Endpoint with @open tag should have open=true",
            publicEndpoint?.open == true
        )
    }

    fun testApiOpenWithoutOpenTag() = runTest {
        val psiClass = findClass("com.itangcent.yapi.YapiController")
        assertNotNull("Should find YapiController", psiClass)

        val endpoints = exporter.export(psiClass!!)

        val privateEndpoint = endpoints.find {
            it.httpMetadata?.method == HttpMethod.GET &&
                    it.httpMetadata?.path?.contains("private") == true
        }
        assertNotNull("Should find GET /yapi/private endpoint", privateEndpoint)
        assertFalse(
            "Endpoint without @open tag should have open=false",
            privateEndpoint?.open == true
        )
    }

    // ── api.status[#undone]=undone / api.status[#todo]=undone ────

    fun testApiStatusWithUndoneTag() = runTest {
        val psiClass = findClass("com.itangcent.yapi.YapiController")
        assertNotNull("Should find YapiController", psiClass)

        val endpoints = exporter.export(psiClass!!)

        val createEndpoint = endpoints.find {
            it.httpMetadata?.method == HttpMethod.POST &&
                    it.httpMetadata?.path?.contains("create") == true
        }
        assertNotNull("Should find POST /yapi/create endpoint", createEndpoint)
        assertEquals(
            "Endpoint with @undone tag should have status=undone",
            "undone",
            createEndpoint?.status
        )
    }

    fun testApiStatusWithTodoTag() = runTest {
        val psiClass = findClass("com.itangcent.yapi.YapiController")
        assertNotNull("Should find YapiController", psiClass)

        val endpoints = exporter.export(psiClass!!)

        val updateEndpoint = endpoints.find {
            it.httpMetadata?.method == HttpMethod.POST &&
                    it.httpMetadata?.path?.contains("update") == true
        }
        assertNotNull("Should find POST /yapi/update endpoint", updateEndpoint)
        assertEquals(
            "Endpoint with @todo tag should have status=undone",
            "undone",
            updateEndpoint?.status
        )
    }

    // ── field.mock=#mock ─────────────────────────────────────────

    fun testFieldMockWithMockTag() = runTest {
        val psiClass = findClass("com.itangcent.yapi.YapiController")
        assertNotNull("Should find YapiController", psiClass)

        val endpoints = exporter.export(psiClass!!)

        val createEndpoint = endpoints.find {
            it.httpMetadata?.method == HttpMethod.POST &&
                    it.httpMetadata?.path?.contains("create") == true
        }
        assertNotNull("Should find POST /yapi/create endpoint", createEndpoint)

        val body = createEndpoint?.httpMetadata?.body
        assertNotNull("POST endpoint should have request body", body)
        assertNotNull("Body should be an ObjectModel", body?.asObject())

        val idField = findField(body!!, "id")
        assertNotNull("Should find id field in body. Available fields: ${body.asObject()?.fields?.keys}", idField)
        assertEquals(
            "Field with @mock tag should have mock value",
            "123",
            idField?.mock
        )

        val nameField = findField(body, "name")
        assertNotNull("Should find name field in body", nameField)
        assertEquals(
            "Field with @mock tag should have mock value",
            "test-item",
            nameField?.mock
        )
    }

    fun testFieldMockWithoutMockTag() = runTest {
        val psiClass = findClass("com.itangcent.yapi.YapiController")
        assertNotNull("Should find YapiController", psiClass)

        val endpoints = exporter.export(psiClass!!)

        val createEndpoint = endpoints.find {
            it.httpMetadata?.method == HttpMethod.POST &&
                    it.httpMetadata?.path?.contains("create") == true
        }
        assertNotNull("Should find POST /yapi/create endpoint", createEndpoint)

        val body = createEndpoint?.httpMetadata?.body
        assertNotNull("POST endpoint should have request body", body)

        val descField = findField(body!!, "description")
        assertNotNull("Should find description field in body", descField)
        assertNull(
            "Field without @mock tag should not have mock value",
            descField?.mock
        )
    }

    // ── api.tag[@java.lang.Deprecated]=deprecated ────────────────

    fun testApiTagWithJavaDeprecatedAnnotation() = runTest {
        val psiClass = findClass("com.itangcent.yapi.DeprecatedController")
        assertNotNull("Should find DeprecatedController", psiClass)

        val endpoints = exporter.export(psiClass!!)

        val oldEndpoint = endpoints.find {
            it.httpMetadata?.method == HttpMethod.GET &&
                    it.httpMetadata?.path?.contains("deprecated/old") == true
        }
        assertNotNull("Should find GET /deprecated/old endpoint", oldEndpoint)
        assertTrue(
            "Endpoint in @Deprecated class should have 'deprecated' tag",
            oldEndpoint?.tags?.contains("deprecated") == true
        )
    }

    // ── api.tag[#deprecated]=deprecated ──────────────────────────

    fun testApiTagWithDeprecatedDocTag() = runTest {
        val psiClass = findClass("com.itangcent.yapi.DeprecatedDocController")
        assertNotNull("Should find DeprecatedDocController", psiClass)

        val endpoints = exporter.export(psiClass!!)

        val oldEndpoint = endpoints.find {
            it.httpMetadata?.method == HttpMethod.GET &&
                    it.httpMetadata?.path?.contains("doc/old") == true
        }
        assertNotNull("Should find GET /doc/old endpoint", oldEndpoint)
        assertTrue(
            "Endpoint with @deprecated javadoc tag should have 'deprecated' tag",
            oldEndpoint?.tags?.contains("deprecated") == true
        )
    }

    fun testApiTagWithoutDeprecatedDocTag() = runTest {
        val psiClass = findClass("com.itangcent.yapi.DeprecatedDocController")
        assertNotNull("Should find DeprecatedDocController", psiClass)

        val endpoints = exporter.export(psiClass!!)

        val newEndpoint = endpoints.find {
            it.httpMetadata?.method == HttpMethod.GET &&
                    it.httpMetadata?.path?.contains("doc/new") == true
        }
        assertNotNull("Should find GET /doc/new endpoint", newEndpoint)
        assertFalse(
            "Endpoint without @deprecated javadoc tag should not have 'deprecated' tag",
            newEndpoint?.tags?.contains("deprecated") == true
        )
    }

    private fun findField(model: ObjectModel, name: String): FieldModel? {
        val obj = model.asObject() ?: return null
        return obj.fields[name]
    }
}
