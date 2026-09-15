package com.itangcent.easyapi.framework.feign

import com.itangcent.easyapi.core.export.ApiParameter
import com.itangcent.easyapi.core.export.ParameterBinding
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

/**
 * Regression coverage for `required` resolution of Feign parameters (issue #1458).
 *
 * Spring-style Feign clients reuse the Spring binding annotations, so the same
 * `rule > annotation > framework default` chain applies. Native Feign declares no
 * annotations for path variables, so the URI template is the only source — and a
 * template variable is always present in the request line.
 */
class FeignParamRequiredTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var exporter: FeignClassExporter

    override fun setUp() {
        super.setUp()
        loadStubs()
        exporter = FeignClassExporter(project)
    }

    private fun loadStubs() {
        loadFile("spring/FeignClient.java")
        loadFile("spring/RequestMapping.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/RequestParam.java")
        loadFile("spring/PathVariable.java")
        loadFile("feign/RequestLine.java")
        loadFile("feign/Param.java")
        loadFile("model/Result.java")
        loadFile("model/IResult.java")
        loadFile(
            "api/feign/RequiredClient.java",
            """
            package com.itangcent.springboot.demo.client;
            import com.itangcent.model.Result;
            import org.springframework.cloud.openfeign.FeignClient;
            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.RequestParam;

            @FeignClient("required")
            @RequestMapping(value = "/required")
            public interface RequiredClient {

                @GetMapping("/search")
                Result<String> search(@RequestParam String keyword,
                                      @RequestParam(required = false) String tag);
            }
            """.trimIndent()
        )
        loadFile(
            "api/feign/NativeRequiredClient.java",
            """
            package com.itangcent.springboot.demo.client;
            import com.itangcent.model.Result;
            import feign.Param;
            import feign.RequestLine;
            import org.springframework.cloud.openfeign.FeignClient;

            @FeignClient("native")
            public interface NativeRequiredClient {

                @RequestLine("GET /native/{id}")
                Result<String> get(@Param("id") String id);
            }
            """.trimIndent()
        )
    }

    override fun createConfigReader() = TestConfigReader.empty(project)

    fun testSpringStyleRequestParamUsesSpringDefaults() = runTest {
        val params = exportParams("com.itangcent.springboot.demo.client.RequiredClient", "/required/search")
        assertRequired("@RequestParam defaults to required", true, params, "keyword")
        assertRequired("@RequestParam(required = false)", false, params, "tag")
    }

    fun testNativeFeignPathVariableIsRequired() = runTest {
        val params = exportParams("com.itangcent.springboot.demo.client.NativeRequiredClient", "/native/{id}")
        val pathParam = params.singleOrNull { it.binding == ParameterBinding.Path }
        assertNotNull(
            "native Feign should export the {id} template variable as a path parameter; " +
                "actual=[${params.joinToString { "${it.name}(${it.binding})=${it.required}" }}]",
            pathParam
        )
        assertTrue("a URI template variable is always present", pathParam!!.required)
    }

    private suspend fun exportParams(className: String, pathSuffix: String): List<ApiParameter> {
        val psiClass = findClass(className)
        assertNotNull("$className should be resolvable", psiClass)
        val endpoints = exporter.export(psiClass!!)
        // The @FeignClient name is prepended to the class-level path, so match on the tail.
        val endpoint = endpoints.singleOrNull { it.httpMetadata?.path?.endsWith(pathSuffix) == true }
        assertNotNull(
            "Should export an endpoint ending with $pathSuffix; actual=${endpoints.map { it.httpMetadata?.path }}",
            endpoint
        )
        return endpoint!!.httpMetadata!!.parameters
    }

    private fun assertRequired(message: String, expected: Boolean, params: List<ApiParameter>, name: String) {
        val param = params.singleOrNull { it.name == name }
        assertNotNull(
            "$message: parameter '$name' should be exported; " +
                "actual=[${params.joinToString { "${it.name}(${it.binding})=${it.required}" }}]",
            param
        )
        assertEquals(message, expected, param!!.required)
    }
}
