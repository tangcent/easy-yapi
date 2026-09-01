package com.itangcent.easyapi.framework.custom

import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.core.export.ParameterBinding
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.core.export.isHttp
import com.itangcent.easyapi.core.export.path
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

/**
 * Integration tests for [CustomClassExporter]. Uses the custom-annotated
 * `Ctrl` fixture (proprietary `@MyApi`/`@MyEndpoint` annotations) for the
 * core path/method/endpoint tests, and the Spring-equivalent reference
 * ruleset (loaded from `custom-spring-reference.rules`) for parameter-binding
 * verification — proving the ruleset reproduces Spring MVC parameter binding.
 */
class CustomClassExporterTest {

    /** Default config: class + method recognition, POST HTTP method, /ctrl base path. */
    class WithDefaultConfig : EasyApiLightCodeInsightFixtureTestCase() {

        private lateinit var exporter: CustomClassExporter

        override fun setUp() {
            super.setUp()
            loadTestFiles()
            exporter = CustomClassExporter(project)
        }

        private fun loadTestFiles() {
            loadFile("custom/annotation/MyApi.java")
            loadFile("custom/annotation/MyEndpoint.java")
            loadFile("custom/api/Ctrl.java")
            loadFile("model/Result.java")
            loadFile("model/UserInfo.java")
        }

        override fun createConfigReader() = TestConfigReader.fromConfigText(
            project,
            """
            custom.class.is.api=groovy:it.hasAnn("com.itangcent.custom.annotation.MyApi")
            custom.method.is.api=groovy:it.hasAnn("com.itangcent.custom.annotation.MyEndpoint")
            custom.http.method=groovy:"POST"
            custom.path=groovy:it.contextType() == "class" ? "/ctrl" : ""
            """.trimIndent()
        )

        fun testExportCustomApiClass() = runTest {
            val psiClass = findClass("com.itangcent.custom.Ctrl")
            assertNotNull(psiClass)

            val endpoints = exporter.export(psiClass!!)
            assertTrue("Should export endpoints", endpoints.isNotEmpty())
        }

        fun testNonApiClassReturnsEmpty() = runTest {
            val psiClass = findClass("com.itangcent.model.UserInfo")
            assertNotNull(psiClass)

            val endpoints = exporter.export(psiClass!!)
            assertTrue("Non-API class should return empty endpoints", endpoints.isEmpty())
        }

        fun testMethodIsApiGate() = runTest {
            val psiClass = findClass("com.itangcent.custom.Ctrl")
            assertNotNull(psiClass)

            val endpoints = exporter.export(psiClass!!)
            // Both methods in Ctrl carry @MyEndpoint, so both should be exported.
            assertEquals(2, endpoints.size)
        }

        fun testHttpMethodFromRule() = runTest {
            val psiClass = findClass("com.itangcent.custom.Ctrl")
            assertNotNull(psiClass)

            val endpoints = exporter.export(psiClass!!)
            assertTrue("Should export endpoints", endpoints.isNotEmpty())
            for (endpoint in endpoints) {
                assertEquals(HttpMethod.POST, endpoint.httpMetadata?.method)
            }
        }

        fun testClassBasePath() = runTest {
            val psiClass = findClass("com.itangcent.custom.Ctrl")
            assertNotNull(psiClass)

            val endpoints = exporter.export(psiClass!!)
            assertTrue(endpoints.isNotEmpty())
            for (endpoint in endpoints) {
                assertEquals("/ctrl", endpoint.path)
            }
        }

        fun testEndpointsAreHttp() = runTest {
            val psiClass = findClass("com.itangcent.custom.Ctrl")
            assertNotNull(psiClass)

            val endpoints = exporter.export(psiClass!!)
            assertTrue(endpoints.isNotEmpty())
            for (endpoint in endpoints) {
                assertTrue("Endpoint must carry HttpMetadata", endpoint.isHttp)
                assertNotNull(endpoint.httpMetadata)
            }
        }

        fun testEndpointMetadataShape() = runTest {
            val psiClass = findClass("com.itangcent.custom.Ctrl")
            assertNotNull(psiClass)

            val endpoints = exporter.export(psiClass!!)
            assertTrue(endpoints.isNotEmpty())
            for (endpoint in endpoints) {
                val http = endpoint.httpMetadata!!
                assertNotNull(http.path)
                assertNotNull(http.method)
                assertNotNull(endpoint.name)
                assertNotNull(endpoint.className)
            }
        }
    }

    /** Config without custom.http.method — verifies the POST fallback. */
    class WithHttpMethodFallback : EasyApiLightCodeInsightFixtureTestCase() {

        private lateinit var exporter: CustomClassExporter

        override fun setUp() {
            super.setUp()
            loadTestFiles()
            exporter = CustomClassExporter(project)
        }

        private fun loadTestFiles() {
            loadFile("custom/annotation/MyApi.java")
            loadFile("custom/annotation/MyEndpoint.java")
            loadFile("custom/api/Ctrl.java")
            loadFile("model/Result.java")
            loadFile("model/UserInfo.java")
        }

        override fun createConfigReader() = TestConfigReader.fromConfigText(
            project,
            """
            custom.class.is.api=groovy:it.hasAnn("com.itangcent.custom.annotation.MyApi")
            custom.method.is.api=groovy:it.hasAnn("com.itangcent.custom.annotation.MyEndpoint")
            custom.path=groovy:it.contextType() == "class" ? "/ctrl" : ""
            """.trimIndent()
        )

        fun testHttpMethodFallsBackToPost() = runTest {
            val psiClass = findClass("com.itangcent.custom.Ctrl")
            assertNotNull(psiClass)

            val endpoints = exporter.export(psiClass!!)
            assertTrue("Should export endpoints", endpoints.isNotEmpty())
            for (endpoint in endpoints) {
                assertEquals(
                    "Should fall back to POST when no HTTP method rule resolves",
                    HttpMethod.POST,
                    endpoint.httpMetadata?.method
                )
            }
        }
    }

    /** Config with class.prefix.path — verifies prefix composition. */
    class WithClassPrefixPath : EasyApiLightCodeInsightFixtureTestCase() {

        private lateinit var exporter: CustomClassExporter

        override fun setUp() {
            super.setUp()
            loadTestFiles()
            exporter = CustomClassExporter(project)
        }

        private fun loadTestFiles() {
            loadFile("custom/annotation/MyApi.java")
            loadFile("custom/annotation/MyEndpoint.java")
            loadFile("custom/api/Ctrl.java")
            loadFile("model/Result.java")
        }

        override fun createConfigReader() = TestConfigReader.fromConfigText(
            project,
            """
            custom.class.is.api=groovy:it.hasAnn("com.itangcent.custom.annotation.MyApi")
            custom.method.is.api=groovy:it.hasAnn("com.itangcent.custom.annotation.MyEndpoint")
            custom.http.method=groovy:"POST"
            custom.path=groovy:it.contextType() == "class" ? "ctrl" : it.contextType() == "method" ? "greet" : ""
            class.prefix.path=groovy:"/api/v1"
            """.trimIndent()
        )

        fun testPathCompositionWithClassPrefix() = runTest {
            val psiClass = findClass("com.itangcent.custom.Ctrl")
            assertNotNull(psiClass)

            val endpoints = exporter.export(psiClass!!)
            assertTrue(endpoints.isNotEmpty())
            val greetEndpoint = endpoints.find { it.sourceMethod?.name == "greet" }
            assertNotNull(greetEndpoint)
            assertEquals("/api/v1/ctrl/greet", greetEndpoint!!.path)
        }
    }

    /** Loads the Spring reference ruleset — verifies parameter bindings. */
    class WithSpringReferenceRuleset : EasyApiLightCodeInsightFixtureTestCase() {

        private lateinit var exporter: CustomClassExporter

        override fun setUp() {
            super.setUp()
            loadTestFiles()
            exporter = CustomClassExporter(project)
        }

        private fun loadTestFiles() {
            loadFile("spring/Controller.java")
            loadFile("spring/RestController.java")
            loadFile("spring/ResponseBody.java")
            loadFile("spring/RequestMapping.java")
            loadFile("spring/GetMapping.java")
            loadFile("spring/PostMapping.java")
            loadFile("spring/PathVariable.java")
            loadFile("spring/RequestParam.java")
            loadFile("spring/RequestBody.java")
            loadFile("spring/RequestHeader.java")
            loadFile("spring/CookieValue.java")
            loadFile("spring/ModelAttribute.java")
            loadFile("spring/AliasFor.java")
            loadFile("spring/Component.java")
            loadFile("model/Result.java")
            loadFile("model/UserInfo.java")
            loadFile("custom/api/SpringParityCtrl.java")
        }

        override fun createConfigReader(): com.itangcent.easyapi.core.config.ConfigReader {
            val ruleset = javaClass.getResourceAsStream("/custom/custom-spring-reference.rules")!!
                .reader().readText()
            return TestConfigReader.fromConfigText(project, ruleset)
        }

        fun testSpringRulesetExportsAllEndpoints() = runTest {
            val psiClass = findClass("com.itangcent.custom.SpringParityCtrl")
            assertNotNull(psiClass)

            val endpoints = exporter.export(psiClass!!)
            assertTrue("Should export endpoints", endpoints.size >= 4)
        }

        fun testPathVariableBinding() = runTest {
            val psiClass = findClass("com.itangcent.custom.SpringParityCtrl")
            assertNotNull(psiClass)

            val endpoints = exporter.export(psiClass!!)
            val getUser = endpoints.find { it.sourceMethod?.name == "getUser" }
            assertNotNull(getUser)
            val pathParam = getUser!!.httpMetadata!!.parameters.find { it.binding == ParameterBinding.Path }
            assertNotNull("getUser should have a path parameter", pathParam)
        }

        fun testJsonBodyBinding() = runTest {
            val psiClass = findClass("com.itangcent.custom.SpringParityCtrl")
            assertNotNull(psiClass)

            val endpoints = exporter.export(psiClass!!)
            val createUser = endpoints.find { it.sourceMethod?.name == "createUser" }
            assertNotNull(createUser)
            assertNotNull("createUser should have a request body", createUser!!.httpMetadata!!.body)
        }

        fun testQueryBinding() = runTest {
            val psiClass = findClass("com.itangcent.custom.SpringParityCtrl")
            assertNotNull(psiClass)

            val endpoints = exporter.export(psiClass!!)
            val searchUsers = endpoints.find { it.sourceMethod?.name == "searchUsers" }
            assertNotNull(searchUsers)
            val queryParams = searchUsers!!.httpMetadata!!.parameters.filter { it.binding == ParameterBinding.Query }
            assertTrue("searchUsers should have query parameters", queryParams.size >= 2)
        }

        fun testHeaderBinding() = runTest {
            val psiClass = findClass("com.itangcent.custom.SpringParityCtrl")
            assertNotNull(psiClass)

            val endpoints = exporter.export(psiClass!!)
            val getByHeader = endpoints.find { it.sourceMethod?.name == "getByHeader" }
            assertNotNull(getByHeader)
            val headerParam = getByHeader!!.httpMetadata!!.parameters.find { it.binding == ParameterBinding.Header }
            assertNotNull("getByHeader should have a header parameter", headerParam)
        }

        fun testCookieBinding() = runTest {
            val psiClass = findClass("com.itangcent.custom.SpringParityCtrl")
            assertNotNull(psiClass)

            val endpoints = exporter.export(psiClass!!)
            val getByCookie = endpoints.find { it.sourceMethod?.name == "getByCookie" }
            assertNotNull(getByCookie)
            val cookieParam = getByCookie!!.httpMetadata!!.parameters.find { it.binding == ParameterBinding.Cookie }
            assertNotNull("getByCookie should have a cookie parameter", cookieParam)
        }
    }

    /** Reference ruleset + a `param.required` rule matching @RequestHeader. */
    class WithParamRequiredRule : EasyApiLightCodeInsightFixtureTestCase() {

        private lateinit var exporter: CustomClassExporter

        override fun setUp() {
            super.setUp()
            loadTestFiles()
            exporter = CustomClassExporter(project)
        }

        private fun loadTestFiles() {
            loadFile("spring/Controller.java")
            loadFile("spring/RestController.java")
            loadFile("spring/ResponseBody.java")
            loadFile("spring/RequestMapping.java")
            loadFile("spring/GetMapping.java")
            loadFile("spring/PostMapping.java")
            loadFile("spring/PathVariable.java")
            loadFile("spring/RequestParam.java")
            loadFile("spring/RequestBody.java")
            loadFile("spring/RequestHeader.java")
            loadFile("spring/CookieValue.java")
            loadFile("spring/ModelAttribute.java")
            loadFile("spring/AliasFor.java")
            loadFile("spring/Component.java")
            loadFile("model/Result.java")
            loadFile("model/UserInfo.java")
            loadFile("custom/api/SpringParityCtrl.java")
        }

        override fun createConfigReader(): com.itangcent.easyapi.core.config.ConfigReader {
            val ruleset = javaClass.getResourceAsStream("/custom/custom-spring-reference.rules")!!
                .reader().readText()
            return TestConfigReader.fromConfigText(
                project,
                ruleset + "\nparam.required=groovy:it.hasAnn(\"org.springframework.web.bind.annotation.RequestHeader\")"
            )
        }

        fun testParamRequiredRuleReachesHeader() = runTest {
            val psiClass = findClass("com.itangcent.custom.SpringParityCtrl")
            assertNotNull(psiClass)

            val endpoints = exporter.export(psiClass!!)
            val getByHeader = endpoints.find { it.sourceMethod?.name == "getByHeader" }
            assertNotNull(getByHeader)
            val headerParam = getByHeader!!.httpMetadata!!.parameters.find { it.binding == ParameterBinding.Header }
            assertNotNull("getByHeader should have a header parameter", headerParam)
            assertTrue("header param should be required when param.required matches", headerParam!!.required)
        }
    }
}
