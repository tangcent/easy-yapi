package com.itangcent.easyapi.config.source

import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.channel.yapi.YapiMetaRuleKeys
import com.itangcent.easyapi.exporter.channel.yapi.YapiMetadataPopulator
import com.itangcent.easyapi.exporter.channel.yapi.mock
import com.itangcent.easyapi.exporter.channel.yapi.open
import com.itangcent.easyapi.exporter.channel.yapi.status
import com.itangcent.easyapi.exporter.channel.yapi.tags
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.exporter.springmvc.SpringMvcClassExporter
import com.itangcent.easyapi.extension.ExtensionConfigRegistry
import com.itangcent.easyapi.psi.model.FieldModel
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.rule.RuleKeys
import com.itangcent.easyapi.rule.engine.RuleEngine
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

/**
 * Integration test for the `yapi` extension.
 *
 * The extension defines rules for:
 *   - [YapiMetaRuleKeys.API_TAG]: `api.tag[@java.lang.Deprecated]=deprecated`, `api.tag[#deprecated]=deprecated`,
 *     `api.tag[#tag]=...` (and kotlin variants)
 *   - [YapiMetaRuleKeys.API_STATUS]: `api.status[#undone]=undone`, `api.status[#todo]=undone`
 *   - [YapiMetaRuleKeys.API_OPEN]: `api.open[#open]=true`
 *   - [YapiMetaRuleKeys.FIELD_MOCK]: `field.mock=#mock`
 */
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
        val extension = ExtensionConfigRegistry.getExtension("yapi")
        assertNotNull("yapi extension should exist", extension)
        return TestConfigReader.fromConfigText(project, extension?.content ?: "")
    }

    // ── Rule-level tests: ruleEngine.evaluate ────────────────────

    /**
     * The core rule: a method with `@open` doc tag should resolve
     * [YapiMetaRuleKeys.API_OPEN] to `true`.
     */
    fun testApiOpenRuleForMethodWithOpenTag() = runTest {
        val psiClass = findClass("com.itangcent.yapi.YapiController")
        assertNotNull("Should find YapiController", psiClass)

        val publicMethod = psiClass!!.methods.find { it.name == "getPublicInfo" }
        assertNotNull("Should find getPublicInfo method", publicMethod)

        val ruleEngine = RuleEngine.getInstance(project)
        val open = ruleEngine.evaluate(YapiMetaRuleKeys.API_OPEN, publicMethod!!)
        assertTrue(
            "API_OPEN should be true for method with @open doc tag",
            open
        )
    }

    /**
     * The core rule: a method without `@open` doc tag should resolve
     * [YapiMetaRuleKeys.API_OPEN] to `false`.
     */
    fun testApiOpenRuleForMethodWithoutOpenTag() = runTest {
        val psiClass = findClass("com.itangcent.yapi.YapiController")
        assertNotNull("Should find YapiController", psiClass)

        val privateMethod = psiClass!!.methods.find { it.name == "getPrivateInfo" }
        assertNotNull("Should find getPrivateInfo method", privateMethod)

        val ruleEngine = RuleEngine.getInstance(project)
        val open = ruleEngine.evaluate(YapiMetaRuleKeys.API_OPEN, privateMethod!!)
        assertFalse(
            "API_OPEN should be false for method without @open doc tag",
            open
        )
    }

    /**
     * The core rule: a method with `@undone` doc tag should resolve
     * [YapiMetaRuleKeys.API_STATUS] to `"undone"`.
     */
    fun testApiStatusRuleForMethodWithUndoneTag() = runTest {
        val psiClass = findClass("com.itangcent.yapi.YapiController")
        assertNotNull("Should find YapiController", psiClass)

        val createMethod = psiClass!!.methods.find { it.name == "createItem" }
        assertNotNull("Should find createItem method", createMethod)

        val ruleEngine = RuleEngine.getInstance(project)
        val status = ruleEngine.evaluate(YapiMetaRuleKeys.API_STATUS, createMethod!!)
        assertEquals(
            "API_STATUS should be 'undone' for method with @undone doc tag",
            "undone",
            status
        )
    }

    /**
     * The core rule: a method with `@todo` doc tag should resolve
     * [YapiMetaRuleKeys.API_STATUS] to `"undone"`.
     */
    fun testApiStatusRuleForMethodWithTodoTag() = runTest {
        val psiClass = findClass("com.itangcent.yapi.YapiController")
        assertNotNull("Should find YapiController", psiClass)

        val updateMethod = psiClass!!.methods.find { it.name == "updateItem" }
        assertNotNull("Should find updateItem method", updateMethod)

        val ruleEngine = RuleEngine.getInstance(project)
        val status = ruleEngine.evaluate(YapiMetaRuleKeys.API_STATUS, updateMethod!!)
        assertEquals(
            "API_STATUS should be 'undone' for method with @todo doc tag",
            "undone",
            status
        )
    }

    /**
     * The core rule: a method without `@undone`/`@todo` doc tag should resolve
     * [YapiMetaRuleKeys.API_STATUS] to `null`.
     */
    fun testApiStatusRuleForMethodWithoutStatusTag() = runTest {
        val psiClass = findClass("com.itangcent.yapi.YapiController")
        assertNotNull("Should find YapiController", psiClass)

        val publicMethod = psiClass!!.methods.find { it.name == "getPublicInfo" }
        assertNotNull("Should find getPublicInfo method", publicMethod)

        val ruleEngine = RuleEngine.getInstance(project)
        val status = ruleEngine.evaluate(YapiMetaRuleKeys.API_STATUS, publicMethod!!)
        assertNull(
            "API_STATUS should be null for method without @undone/@todo doc tag",
            status
        )
    }

    /**
     * The core rule: a field with `@mock 123` doc tag should resolve
     * [YapiMetaRuleKeys.FIELD_MOCK] to `"123"`.
     */
    fun testFieldMockRuleForFieldWithMockTag() = runTest {
        val psiClass = findClass("com.itangcent.yapi.ItemDTO")
        assertNotNull("Should find ItemDTO", psiClass)

        val idField = psiClass!!.fields.find { it.name == "id" }
        assertNotNull("Should find id field", idField)

        val ruleEngine = RuleEngine.getInstance(project)
        val mock = ruleEngine.evaluate(YapiMetaRuleKeys.FIELD_MOCK, idField!!)
        assertEquals(
            "FIELD_MOCK should be '123' for field with @mock 123 doc tag",
            "123",
            mock
        )
    }

    /**
     * The core rule: a field without `@mock` doc tag should resolve
     * [YapiMetaRuleKeys.FIELD_MOCK] to `null`.
     */
    fun testFieldMockRuleForFieldWithoutMockTag() = runTest {
        val psiClass = findClass("com.itangcent.yapi.ItemDTO")
        assertNotNull("Should find ItemDTO", psiClass)

        val descField = psiClass!!.fields.find { it.name == "description" }
        assertNotNull("Should find description field", descField)

        val ruleEngine = RuleEngine.getInstance(project)
        val mock = ruleEngine.evaluate(YapiMetaRuleKeys.FIELD_MOCK, descField!!)
        assertNull(
            "FIELD_MOCK should be null for field without @mock doc tag",
            mock
        )
    }

    /**
     * The core rule: a method in a class annotated `@java.lang.Deprecated`
     * should resolve [YapiMetaRuleKeys.API_TAG] to include `"deprecated"`.
     */
    fun testApiTagRuleForDeprecatedClass() = runTest {
        val psiClass = findClass("com.itangcent.yapi.DeprecatedController")
        assertNotNull("Should find DeprecatedController", psiClass)

        val oldMethod = psiClass!!.methods.find { it.name == "oldMethod" }
        assertNotNull("Should find oldMethod", oldMethod)

        val ruleEngine = RuleEngine.getInstance(project)
        val tag = ruleEngine.evaluate(YapiMetaRuleKeys.API_TAG, oldMethod!!)
        assertNotNull("API_TAG should not be null for method in @Deprecated class", tag)
        assertTrue(
            "API_TAG should contain 'deprecated' for method in @Deprecated class. Was: $tag",
            tag!!.contains("deprecated")
        )
    }

    /**
     * The core rule: a method with `@deprecated` javadoc tag should resolve
     * [YapiMetaRuleKeys.API_TAG] to include `"deprecated"`.
     */
    fun testApiTagRuleForMethodWithDeprecatedDocTag() = runTest {
        val psiClass = findClass("com.itangcent.yapi.DeprecatedDocController")
        assertNotNull("Should find DeprecatedDocController", psiClass)

        val oldMethod = psiClass!!.methods.find { it.name == "oldMethod" }
        assertNotNull("Should find oldMethod", oldMethod)

        val ruleEngine = RuleEngine.getInstance(project)
        val tag = ruleEngine.evaluate(YapiMetaRuleKeys.API_TAG, oldMethod!!)
        assertNotNull("API_TAG should not be null for method with @deprecated doc tag", tag)
        assertTrue(
            "API_TAG should contain 'deprecated' for method with @deprecated doc tag. Was: $tag",
            tag!!.contains("deprecated")
        )
    }

    // ── Exporter-level tests (end-to-end) ────────────────────────

    fun testApiOpenWithOpenTag() = runTest {
        val psiClass = findClass("com.itangcent.yapi.YapiController")
        assertNotNull("Should find YapiController", psiClass)

        val endpoints = exportWithYapiMetadata(psiClass!!)

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

        val endpoints = exportWithYapiMetadata(psiClass!!)

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

    fun testApiStatusWithUndoneTag() = runTest {
        val psiClass = findClass("com.itangcent.yapi.YapiController")
        assertNotNull("Should find YapiController", psiClass)

        val endpoints = exportWithYapiMetadata(psiClass!!)

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

        val endpoints = exportWithYapiMetadata(psiClass!!)

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

    fun testFieldMockWithMockTag() = runTest {
        val psiClass = findClass("com.itangcent.yapi.YapiController")
        assertNotNull("Should find YapiController", psiClass)

        val endpoints = exportWithYapiMetadata(psiClass!!)

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

        val endpoints = exportWithYapiMetadata(psiClass!!)

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

    fun testApiTagWithJavaDeprecatedAnnotation() = runTest {
        val psiClass = findClass("com.itangcent.yapi.DeprecatedController")
        assertNotNull("Should find DeprecatedController", psiClass)

        val endpoints = exportWithYapiMetadata(psiClass!!)

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

    fun testApiTagWithDeprecatedDocTag() = runTest {
        val psiClass = findClass("com.itangcent.yapi.DeprecatedDocController")
        assertNotNull("Should find DeprecatedDocController", psiClass)

        val endpoints = exportWithYapiMetadata(psiClass!!)

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

        val endpoints = exportWithYapiMetadata(psiClass!!)

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

    /**
     * Exports endpoints and populates YApi-specific metadata (tags/status/open)
     * via [YapiMetadataPopulator], mirroring what the easy-yapi export pipeline does.
     */
    private suspend fun exportWithYapiMetadata(psiClass: com.intellij.psi.PsiClass): List<com.itangcent.easyapi.exporter.model.ApiEndpoint> {
        val ruleEngine = RuleEngine.getInstance(project)
        return exporter.export(psiClass).map { YapiMetadataPopulator.populate(it, ruleEngine) }
    }

    private fun findField(model: ObjectModel, name: String): FieldModel? {
        val obj = model.asObject() ?: return null
        return obj.fields[name]
    }
}
