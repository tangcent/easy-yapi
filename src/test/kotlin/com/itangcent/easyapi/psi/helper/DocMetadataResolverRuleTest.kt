package com.itangcent.easyapi.psi.helper

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

/**
 * Tests for [DocMetadataResolver] with various rule configurations.
 *
 * Covers rule-based behavior for all public methods:
 * - `api.name`, `class.doc`, `method.doc`, `field.doc`
 * - `folder.name` (class-level and method-level override)
 * - `param.name`, `param.type`, `param.doc`, `param.required`, `param.ignore`,
 *   `param.default.value`, `param.demo`, `param.mock`
 * - `api.tag`, `api.status`, `api.open`
 * - `yapi.project`
 * - `ignore`
 * - `method.return`, `method.return.main`
 * - `method.default.http.method`
 * - `method.additional.header`, `method.additional.param`,
 *   `method.additional.response.header`
 * - `path.multi`
 */
class DocMetadataResolverRuleTest {

    /**
     * Base class providing shared test file loading for rule-based tests.
     */
    abstract class RuleTestBase : EasyApiLightCodeInsightFixtureTestCase() {

        protected lateinit var metadataResolver: DocMetadataResolver

        override fun setUp() {
            super.setUp()
            loadTestFiles()
            metadataResolver = DocMetadataResolver.getInstance(project)
        }

        private fun loadTestFiles() {
            loadFile("spring/RequestMapping.java")
            loadFile("spring/GetMapping.java")
            loadFile("spring/PostMapping.java")
            loadFile("spring/RestController.java")
            loadFile("model/Result.java")
            loadFile("model/UserInfo.java")
            loadFile("api/TitleTestCtrl.java")
        }
    }

    // ================================================================
    //  api.name rule
    // ================================================================

    class WithApiNameRule : RuleTestBase() {

        override fun createConfigReader() = TestConfigReader.fromRules(
            project, "api.name" to "groovy:\"Custom API Name\""
        )

        fun testResolveApiNameUsesRuleValue() = runTest {
            val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
            val method = findMethod(psiClass, "getUser")!!
            assertEquals("Custom API Name", metadataResolver.resolveApiName(method))
        }
    }

    // ================================================================
    //  class.doc rule
    // ================================================================

    class WithClassDocRule : RuleTestBase() {

        override fun createConfigReader() = TestConfigReader.fromRules(
            project, "class.doc" to "groovy:\"Swagger API Category\""
        )

        fun testResolveClassDocAppendsRuleValue() = runTest {
            val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
            val desc = metadataResolver.resolveClassDoc(psiClass)
            assertTrue(
                "Class doc should contain rule value",
                desc.contains("Swagger API Category")
            )
        }

        /**
         * When the class has no doc comment, the rule value becomes the
         * entire class doc. This is the core fix for issue #1382.
         */
        fun testResolveFolderFallsBackToClassDocRule() = runTest {
            val psiClass = findClass("com.itangcent.api.NoClassDocCtrl")!!
            val folder = metadataResolver.resolveFolder(psiClass)
            assertEquals("Swagger API Category", folder.name)
            assertEquals("Swagger API Category", folder.description)
        }
    }

    // ================================================================
    //  method.doc rule
    // ================================================================

    class WithMethodDocRule : RuleTestBase() {

        override fun createConfigReader() = TestConfigReader.fromRules(
            project, "method.doc" to "groovy:\"Rule-based method doc\""
        )

        fun testResolveMethodDocAppendsRuleValue() = runTest {
            val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
            val method = findMethod(psiClass, "getUser")!!
            val doc = metadataResolver.resolveMethodDoc(method)
            assertTrue("Should contain doc comment", doc.contains("Get user by ID"))
            assertTrue("Should contain rule value", doc.contains("Rule-based method doc"))
        }

        fun testResolveApiNameFallsBackToMethodDocRule() = runTest {
            val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
            val method = findMethod(psiClass, "noDocMethod")!!
            // noDocMethod has no doc comment, so api.name falls back to method.doc rule
            assertEquals("Rule-based method doc", metadataResolver.resolveApiName(method))
        }
    }

    // ================================================================
    //  field.doc rule
    // ================================================================

    class WithFieldDocRule : RuleTestBase() {

        override fun createConfigReader() = TestConfigReader.fromRules(
            project, "field.doc" to "groovy:\"Rule field description\""
        )

        fun testResolveFieldDocAppendsRuleValue() = runTest {
            val psiClass = findClass("com.itangcent.model.UserInfo")!!
            val field = psiClass.findFieldByName("name", false)!!
            val doc = metadataResolver.resolveFieldDoc(field)
            assertTrue("Should contain doc comment", doc?.contains("user name") == true)
            assertTrue("Should contain rule value", doc?.contains("Rule field description") == true)
        }
    }

    // ================================================================
    //  folder.name rule (class-level and method-level override)
    // ================================================================

    class WithFolderNameRule : RuleTestBase() {

        override fun createConfigReader() = TestConfigReader.fromRules(
            project, "folder.name" to "groovy:\"custom-folder\""
        )

        fun testResolveFolderUsesRuleValue() = runTest {
            val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
            val folder = metadataResolver.resolveFolder(psiClass)
            assertEquals("custom-folder", folder.name)
        }

        fun testResolveFolderNameMethodReturnsRuleValue() = runTest {
            val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
            val method = findMethod(psiClass, "getUser")!!
            assertEquals("custom-folder", metadataResolver.resolveFolderName(method))
        }
    }

    class WithMethodFolderOverride : RuleTestBase() {

        override fun createConfigReader() = TestConfigReader.fromRules(
            project,
            "folder.name" to "groovy:it.name() == \"getUser\" ? \"method-override-folder\" : null"
        )

        fun testMethodFolderOverridesClassFolder() = runTest {
            val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
            val classFolder = metadataResolver.resolveFolder(psiClass)
            val method = findMethod(psiClass, "getUser")!!
            val methodFolderName = metadataResolver.resolveFolderName(method)
            val finalFolder = methodFolderName.takeIf { it.isNotBlank() } ?: classFolder.name
            assertEquals("method-override-folder", finalFolder)
        }

        fun testNonMatchingMethodFallsBackToClassFolder() = runTest {
            val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
            val classFolder = metadataResolver.resolveFolder(psiClass)
            val method = findMethod(psiClass, "createUser")!!
            val methodFolderName = metadataResolver.resolveFolderName(method)
            val finalFolder = methodFolderName.takeIf { it.isNotBlank() } ?: classFolder.name
            assertEquals(classFolder.name, finalFolder)
        }
    }

    // ================================================================
    //  param.name rule
    // ================================================================

    class WithParamNameRule : RuleTestBase() {

        override fun createConfigReader() = TestConfigReader.fromRules(
            project, "param.name" to "groovy:\"customParamName\""
        )

        fun testResolveParamNameUsesRuleValue() = runTest {
            val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
            val method = findMethod(psiClass, "getUser")!!
            val param = method.parameterList.parameters[0]
            assertEquals("customParamName", metadataResolver.resolveParamName(param, "id"))
        }
    }

    // ================================================================
    //  param.type rule
    // ================================================================

    class WithParamTypeRule : RuleTestBase() {

        override fun createConfigReader() = TestConfigReader.fromRules(
            project, "param.type" to "groovy:\"string\""
        )

        fun testResolveParamTypeUsesRuleValue() = runTest {
            val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
            val method = findMethod(psiClass, "getUser")!!
            val param = method.parameterList.parameters[0]
            assertEquals("string", metadataResolver.resolveParamType(param, "java.lang.Long"))
        }
    }

    // ================================================================
    //  param.doc rule
    // ================================================================

    class WithParamDocRule : RuleTestBase() {

        override fun createConfigReader() = TestConfigReader.fromRules(
            project, "param.doc" to "groovy:\"Rule param description\""
        )

        fun testResolveParamDocAppendsRuleValue() = runTest {
            val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
            val method = findMethod(psiClass, "getUser")!!
            val param = method.parameterList.parameters[0]
            val doc = metadataResolver.resolveParamDoc(param)
            assertEquals("Rule param description", doc)
        }
    }

    // ================================================================
    //  param.required rule
    // ================================================================

    class WithParamRequiredRule : RuleTestBase() {

        override fun createConfigReader() = TestConfigReader.fromRules(
            project, "param.required" to "groovy:true"
        )

        fun testIsParamRequiredReturnsTrueFromRule() = runTest {
            val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
            val method = findMethod(psiClass, "getUser")!!
            val param = method.parameterList.parameters[0]
            assertTrue(metadataResolver.isParamRequired(param))
        }
    }

    // ================================================================
    //  param.ignore rule
    // ================================================================

    class WithParamIgnoreRule : RuleTestBase() {

        override fun createConfigReader() = TestConfigReader.fromRules(
            project, "param.ignore" to "groovy:true"
        )

        fun testIsParamIgnoredReturnsTrueFromRule() = runTest {
            val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
            val method = findMethod(psiClass, "getUser")!!
            val param = method.parameterList.parameters[0]
            assertTrue(metadataResolver.isParamIgnored(param))
        }
    }

    // ================================================================
    //  param.default.value rule
    // ================================================================

    class WithParamDefaultValueRule : RuleTestBase() {

        override fun createConfigReader() = TestConfigReader.fromRules(
            project, "param.default.value" to "groovy:\"100\""
        )

        fun testResolveParamDefaultValueUsesRuleValue() = runTest {
            val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
            val method = findMethod(psiClass, "getUser")!!
            val param = method.parameterList.parameters[0]
            assertEquals("100", metadataResolver.resolveParamDefaultValue(param))
        }
    }

    // ================================================================
    //  param.demo rule
    // ================================================================

    class WithParamDemoRule : RuleTestBase() {

        override fun createConfigReader() = TestConfigReader.fromRules(
            project, "param.demo" to "groovy:\"demo-value\""
        )

        fun testResolveParamDemoUsesRuleValue() = runTest {
            val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
            val method = findMethod(psiClass, "getUser")!!
            val param = method.parameterList.parameters[0]
            assertEquals("demo-value", metadataResolver.resolveParamDemo(param))
        }
    }

    // ================================================================
    //  param.mock rule
    // ================================================================

    class WithParamMockRule : RuleTestBase() {

        override fun createConfigReader() = TestConfigReader.fromRules(
            project, "param.mock" to "groovy:\"mock-value\""
        )

        fun testResolveParamMockUsesRuleValue() = runTest {
            val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
            val method = findMethod(psiClass, "getUser")!!
            val param = method.parameterList.parameters[0]
            assertEquals("mock-value", metadataResolver.resolveParamMock(param))
        }
    }

    // ================================================================
    //  ignore rule
    // ================================================================

    class WithIgnoreRule : RuleTestBase() {

        override fun createConfigReader() = TestConfigReader.fromRules(
            project, "ignore" to "groovy:true"
        )

        fun testIsIgnoredReturnsTrueFromRule() = runTest {
            val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
            assertTrue(metadataResolver.isIgnored(psiClass))
        }
    }

    // ================================================================
    //  method.return rule
    // ================================================================

    class WithMethodReturnRule : RuleTestBase() {

        override fun createConfigReader() = TestConfigReader.fromRules(
            project, "method.return" to "groovy:\"com.itangcent.model.Result<com.itangcent.model.UserInfo>\""
        )

        fun testResolveMethodReturnUsesRuleValue() = runTest {
            val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
            val method = findMethod(psiClass, "getUser")!!
            assertEquals(
                "com.itangcent.model.Result<com.itangcent.model.UserInfo>",
                metadataResolver.resolveMethodReturn(method)
            )
        }
    }

    // ================================================================
    //  method.return.main rule
    // ================================================================

    class WithMethodReturnMainRule : RuleTestBase() {

        override fun createConfigReader() = TestConfigReader.fromRules(
            project, "method.return.main" to "groovy:\"data\""
        )

        fun testResolveMethodReturnMainUsesRuleValue() = runTest {
            val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
            val method = findMethod(psiClass, "getUser")!!
            assertEquals("data", metadataResolver.resolveMethodReturnMain(method))
        }
    }

    // ================================================================
    //  method.default.http.method rule
    // ================================================================

    class WithDefaultHttpMethodRule : RuleTestBase() {

        override fun createConfigReader() = TestConfigReader.fromRules(
            project, "method.default.http.method" to "groovy:\"POST\""
        )

        fun testResolveDefaultHttpMethodUsesRuleValue() = runTest {
            val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
            val method = findMethod(psiClass, "getUser")!!
            assertEquals("POST", metadataResolver.resolveDefaultHttpMethod(method))
        }
    }

    // ================================================================
    //  method.additional.header rule
    // ================================================================

    class WithAdditionalHeaderRule : RuleTestBase() {

        override fun createConfigReader() = TestConfigReader.fromRules(
            project,
            "method.additional.header" to "groovy:'{\"name\":\"X-Custom-Header\",\"value\":\"val\",\"desc\":\"custom header\",\"required\":true}'"
        )

        fun testResolveAdditionalHeadersUsesRuleValue() = runTest {
            val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
            val method = findMethod(psiClass, "getUser")!!
            val headers = metadataResolver.resolveAdditionalHeaders(method)
            assertEquals(1, headers.size)
            assertEquals("X-Custom-Header", headers[0].name)
            assertEquals("val", headers[0].value)
            assertEquals("custom header", headers[0].description)
            assertTrue(headers[0].required)
        }
    }

    // ================================================================
    //  method.additional.param rule
    // ================================================================

    class WithAdditionalParamRule : RuleTestBase() {

        override fun createConfigReader() = TestConfigReader.fromRules(
            project,
            "method.additional.param" to "groovy:'{\"name\":\"extraParam\",\"type\":\"string\",\"required\":false,\"desc\":\"extra param\",\"value\":\"default\"}'"
        )

        fun testResolveAdditionalParamsUsesRuleValue() = runTest {
            val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
            val method = findMethod(psiClass, "getUser")!!
            val params = metadataResolver.resolveAdditionalParams(method)
            assertEquals(1, params.size)
            assertEquals("extraParam", params[0].name)
            assertEquals("extra param", params[0].description)
            assertEquals("default", params[0].defaultValue)
        }
    }

    // ================================================================
    //  method.additional.response.header rule
    // ================================================================

    class WithAdditionalResponseHeaderRule : RuleTestBase() {

        override fun createConfigReader() = TestConfigReader.fromRules(
            project,
            "method.additional.response.header" to "groovy:'{\"name\":\"X-Response-Id\",\"value\":\"123\",\"desc\":\"response id\",\"required\":false}'"
        )

        fun testResolveAdditionalResponseHeadersUsesRuleValue() = runTest {
            val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
            val method = findMethod(psiClass, "getUser")!!
            val headers = metadataResolver.resolveAdditionalResponseHeaders(method)
            assertEquals(1, headers.size)
            assertEquals("X-Response-Id", headers[0].name)
            assertEquals("123", headers[0].value)
            assertEquals("response id", headers[0].description)
        }
    }

    // ================================================================
    //  path.multi rule
    // ================================================================

    class WithPathMultiRule : RuleTestBase() {

        override fun createConfigReader() = TestConfigReader.fromRules(
            project, "path.multi" to "groovy:\"first\""
        )

        fun testResolvePathMultiUsesRuleValue() = runTest {
            val psiClass = findClass("com.itangcent.api.TitleTestCtrl")!!
            val method = findMethod(psiClass, "getUser")!!
            val selector = metadataResolver.resolvePathMulti(method)
            assertNotNull(selector)
        }
    }
}
