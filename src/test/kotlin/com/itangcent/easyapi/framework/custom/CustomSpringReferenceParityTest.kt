package com.itangcent.easyapi.framework.custom

import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.ApiParameter
import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.core.export.ParameterBinding
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.core.export.isHttp
import com.itangcent.easyapi.core.export.path
import com.itangcent.easyapi.framework.springmvc.SpringMvcClassExporter
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

/**
 * Parity test: the Custom framework with the Spring-equivalent reference
 * ruleset (loaded from `custom-spring-reference.rules`) must produce
 * endpoint output structurally equivalent to the built-in Spring MVC
 * exporter on the same source fixture (`SpringParityCtrl`).
 *
 * ## Structural equalities asserted (per spec Decision 1)
 *
 * - Same endpoint count
 * - Same set of `{method, path}` pairs
 * - For each matched endpoint: same HTTP method, same path, same set of
 *   parameter bindings, same body presence, same response type name
 *
 * ## Documented tolerances (per spec Decision 1)
 *
 * - Endpoint ordering may differ (matched by `(method, path)`)
 * - Parameter ordering within the same binding may differ
 * - Content-Type header casing / presence for bodyless endpoints may differ
 *   (only asserted for body endpoints)
 * - `ApiEndpoint.name` and `folder` may differ (resolved via different rule
 *   paths — `api.name` vs Spring's `@ApiOperation`/javadoc path)
 * - Response body `ObjectModel` deep equality is not asserted (brittle);
 *   only presence is compared
 */
class CustomSpringReferenceParityTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var customExporter: CustomClassExporter
    private lateinit var springExporter: SpringMvcClassExporter

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        customExporter = CustomClassExporter(project)
        springExporter = SpringMvcClassExporter(project)
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

    fun testEndpointCountParity() = runTest {
        val psiClass = findClass("com.itangcent.custom.SpringParityCtrl")
        assertNotNull(psiClass)

        val springEndpoints = springExporter.export(psiClass!!)
        val customEndpoints = customExporter.export(psiClass)

        assertTrue("Spring exporter should produce endpoints", springEndpoints.isNotEmpty())
        assertTrue("Custom+ruleset should produce endpoints", customEndpoints.isNotEmpty())
        assertEquals(
            "Endpoint count must match between Spring and Custom+ruleset",
            springEndpoints.size,
            customEndpoints.size
        )
    }

    fun testMethodAndPathParity() = runTest {
        val psiClass = findClass("com.itangcent.custom.SpringParityCtrl")!!
        val springEndpoints = springExporter.export(psiClass)
        val customEndpoints = customExporter.export(psiClass)

        val springKeys = springEndpoints.map { it.methodPathKey() }.toSet()
        val customKeys = customEndpoints.map { it.methodPathKey() }.toSet()

        assertEquals(
            "The set of (HTTP method, path) pairs must match",
            springKeys,
            customKeys
        )
    }

    fun testParameterBindingsParity() = runTest {
        val psiClass = findClass("com.itangcent.custom.SpringParityCtrl")!!
        val springEndpoints = springExporter.export(psiClass)
        val customEndpoints = customExporter.export(psiClass)

        val springByMethodPath = springEndpoints.associateBy { it.methodPathKey() }
        val customByMethodPath = customEndpoints.associateBy { it.methodPathKey() }

        for (key in springByMethodPath.keys) {
            val springEp = springByMethodPath[key]!!
            val customEp = customByMethodPath[key]
            assertNotNull("Custom+ruleset missing endpoint for $key", customEp)

            // Compare binding types only — the two exporters resolve names through
            // different mechanisms (Spring reads the binding annotation in code;
            // Custom+ruleset reads it through the `custom.param.*` rules).
            val springBindings = springEp.httpMetadata!!.parameters
                .map { it.binding }.toSet()
            val customBindings = customEp!!.httpMetadata!!.parameters
                .map { it.binding }.toSet()

            assertEquals(
                "Parameter binding types must match for $key",
                springBindings,
                customBindings
            )
        }
    }

    fun testBodyPresenceParity() = runTest {
        val psiClass = findClass("com.itangcent.custom.SpringParityCtrl")!!
        val springEndpoints = springExporter.export(psiClass)
        val customEndpoints = customExporter.export(psiClass)

        val springByMethodPath = springEndpoints.associateBy { it.methodPathKey() }
        val customByMethodPath = customEndpoints.associateBy { it.methodPathKey() }

        for (key in springByMethodPath.keys) {
            val springEp = springByMethodPath[key]!!
            val customEp = customByMethodPath[key]!!
            assertEquals(
                "Request body presence must match for $key",
                springEp.httpMetadata!!.body != null,
                customEp.httpMetadata!!.body != null
            )
        }
    }

    fun testResponseTypeParity() = runTest {
        val psiClass = findClass("com.itangcent.custom.SpringParityCtrl")!!
        val springEndpoints = springExporter.export(psiClass)
        val customEndpoints = customExporter.export(psiClass)

        val springByMethodPath = springEndpoints.associateBy { it.methodPathKey() }
        val customByMethodPath = customEndpoints.associateBy { it.methodPathKey() }

        for (key in springByMethodPath.keys) {
            val springEp = springByMethodPath[key]!!
            val customEp = customByMethodPath[key]!!
            assertEquals(
                "Response type name must match for $key",
                springEp.httpMetadata!!.responseType,
                customEp.httpMetadata!!.responseType
            )
        }
    }

    fun testContentTypeParityForBodyEndpoints() = runTest {
        val psiClass = findClass("com.itangcent.custom.SpringParityCtrl")!!
        val springEndpoints = springExporter.export(psiClass)
        val customEndpoints = customExporter.export(psiClass)

        val springByMethodPath = springEndpoints.associateBy { it.methodPathKey() }
        val customByMethodPath = customEndpoints.associateBy { it.methodPathKey() }

        for (key in springByMethodPath.keys) {
            val springEp = springByMethodPath[key]!!
            val customEp = customByMethodPath[key]!!
            val springHttp = springEp.httpMetadata!!
            val customHttp = customEp.httpMetadata!!
            if (springHttp.body != null) {
                // For body endpoints, both must declare application/json.
                val springCt = springHttp.contentType?.lowercase()
                val customCt = customHttp.contentType?.lowercase()
                assertEquals(
                    "Content-Type must match for body endpoint $key",
                    springCt,
                    customCt
                )
            }
        }
    }

    fun testAllEndpointsAreHttp() = runTest {
        val psiClass = findClass("com.itangcent.custom.SpringParityCtrl")!!
        val customEndpoints = customExporter.export(psiClass)

        for (endpoint in customEndpoints) {
            assertTrue(
                "Custom+ruleset endpoint must carry HttpMetadata",
                endpoint.isHttp
            )
        }
    }

    private fun ApiEndpoint.methodPathKey(): Pair<HttpMethod, String> =
        (httpMetadata?.method ?: error("missing method")) to path
}
