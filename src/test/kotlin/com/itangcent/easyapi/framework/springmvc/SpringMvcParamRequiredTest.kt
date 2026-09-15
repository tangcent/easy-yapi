package com.itangcent.easyapi.framework.springmvc

import com.itangcent.easyapi.core.config.ConfigReader
import com.itangcent.easyapi.core.export.ApiParameter
import com.itangcent.easyapi.core.export.ParameterBinding
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

/**
 * Regression coverage for `required` resolution of Spring MVC **parameters**
 * (issue #1458).
 *
 * The expected priority is `param.required` rule > value parsed from the
 * framework annotation > framework default. The framework defaults follow
 * Spring's own argument resolvers:
 *
 * - `@RequestParam` / `@RequestHeader` / `@CookieValue` / `@PathVariable` /
 *   `@RequestPart`: required unless `required = false`, a `defaultValue` is
 *   supplied, or the parameter is `Optional`.
 * - a plain parameter without any annotation: resolved by
 *   `RequestParamMethodArgumentResolver(useDefaultResolution = true)`, whose
 *   fallback `NamedValueInfo` is `required = false`.
 * - `@ModelAttribute`: the bean itself is not a required value.
 */
class SpringMvcParamRequiredTest {

    abstract class Base : EasyApiLightCodeInsightFixtureTestCase() {

        protected lateinit var exporter: SpringMvcClassExporter

        override fun setUp() {
            super.setUp()
            loadSpringStubs()
            exporter = SpringMvcClassExporter(project)
        }

        protected fun loadSpringStubs() {
            loadFile("spring/RestController.java")
            loadFile("spring/RequestMapping.java")
            loadFile("spring/GetMapping.java")
            loadFile("spring/PostMapping.java")
            loadFile("spring/RequestParam.java")
            loadFile("spring/PathVariable.java")
            loadFile("spring/CookieValue.java")
            loadFile("spring/RequestPart.java")
            loadFile("spring/ModelAttribute.java")
            loadFile("model/Result.java")
            loadFile("model/IResult.java")
        }

        protected fun loadItemController() {
            loadFile(
                "api/ParamRequiredCtrl.java",
                """
                package com.itangcent.api;
                import com.itangcent.model.Result;
                import java.util.Optional;
                import org.springframework.web.bind.annotation.CookieValue;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.ModelAttribute;
                import org.springframework.web.bind.annotation.PathVariable;
                import org.springframework.web.bind.annotation.PostMapping;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RequestParam;
                import org.springframework.web.bind.annotation.RequestPart;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                @RequestMapping(value = "/items")
                public class ParamRequiredCtrl {

                    @GetMapping(value = "/list")
                    public Result<String> list(
                            @RequestParam String keyword,
                            @RequestParam(required = false) String tag,
                            @RequestParam(defaultValue = "10") String size,
                            @RequestParam Optional<String> cursor,
                            String plain,
                            @ModelAttribute ItemQuery query) {
                        return Result.success("ok");
                    }

                    @GetMapping(value = "/detail/{id}")
                    public Result<String> detail(
                            @PathVariable String id,
                            @CookieValue("sid") String sessionId) {
                        return Result.success("ok");
                    }

                    @PostMapping(value = "/upload")
                    public Result<String> upload(@RequestPart("meta") String meta) {
                        return Result.success("ok");
                    }
                }
                """.trimIndent()
            )
            loadFile(
                "api/ItemQuery.java",
                """
                package com.itangcent.api;
                public class ItemQuery {
                    private String name;
                    public String getName() { return name; }
                }
                """.trimIndent()
            )
        }

        protected suspend fun exportParams(className: String, path: String): List<ApiParameter> {
            val psiClass = findClass(className)
            assertNotNull("$className should be resolvable", psiClass)
            val endpoints = exporter.export(psiClass!!)
            val endpoint = endpoints.singleOrNull { it.httpMetadata?.path == path }
            assertNotNull(
                "Should export $path; actual=${endpoints.map { it.httpMetadata?.path }}",
                endpoint
            )
            return endpoint!!.httpMetadata!!.parameters
        }

        protected fun assertRequired(
            message: String,
            expected: Boolean,
            params: List<ApiParameter>,
            name: String
        ) {
            val param = params.singleOrNull { it.name == name }
            assertNotNull(
                "$message: parameter '$name' should be exported; " +
                    "actual=[${params.joinToString { "${it.name}(${it.binding})=${it.required}" }}]",
                param
            )
            assertEquals(message, expected, param!!.required)
        }
    }

    /**
     * No `param.required` rule: Spring's own semantics apply.
     */
    class WithSpringDefaults : Base() {

        override fun createConfigReader(): ConfigReader = TestConfigReader.empty(project)

        fun testRequestParamIsRequiredByDefault() = runTest {
            loadItemController()
            val params = exportParams("com.itangcent.api.ParamRequiredCtrl", "/items/list")
            assertRequired("@RequestParam defaults to required", true, params, "keyword")
        }

        fun testRequestParamExplicitRequiredFalse() = runTest {
            loadItemController()
            val params = exportParams("com.itangcent.api.ParamRequiredCtrl", "/items/list")
            assertRequired("@RequestParam(required = false)", false, params, "tag")
        }

        fun testRequestParamWithDefaultValueIsOptional() = runTest {
            loadItemController()
            val params = exportParams("com.itangcent.api.ParamRequiredCtrl", "/items/list")
            assertRequired("a defaultValue implicitly sets required = false", false, params, "size")
        }

        fun testRequestParamOfOptionalTypeIsOptional() = runTest {
            loadItemController()
            val params = exportParams("com.itangcent.api.ParamRequiredCtrl", "/items/list")
            assertRequired("Optional parameters are not required", false, params, "cursor")
        }

        fun testUnannotatedParameterIsOptional() = runTest {
            loadItemController()
            val params = exportParams("com.itangcent.api.ParamRequiredCtrl", "/items/list")
            assertRequired(
                "an unannotated parameter uses Spring's default resolution (required = false)",
                false,
                params,
                "plain"
            )
        }

        fun testModelAttributeIsOptional() = runTest {
            loadItemController()
            val params = exportParams("com.itangcent.api.ParamRequiredCtrl", "/items/list")
            val formParams = params.filter { it.binding == ParameterBinding.Form }
            assertTrue("@ModelAttribute should produce form parameters", formParams.isNotEmpty())
            formParams.forEach {
                assertFalse("@ModelAttribute values are not required (param=${it.name})", it.required)
            }
        }

        fun testPathVariableIsRequired() = runTest {
            loadItemController()
            val params = exportParams("com.itangcent.api.ParamRequiredCtrl", "/items/detail/{id}")
            assertRequired("@PathVariable is required", true, params, "id")
        }

        fun testCookieValueIsRequired() = runTest {
            loadItemController()
            val params = exportParams("com.itangcent.api.ParamRequiredCtrl", "/items/detail/{id}")
            assertRequired("@CookieValue is required", true, params, "sessionId")
        }

        fun testRequestPartIsRequired() = runTest {
            loadItemController()
            val params = exportParams("com.itangcent.api.ParamRequiredCtrl", "/items/upload")
            assertRequired("@RequestPart is required", true, params, "meta")
        }
    }

    /**
     * An explicit rule wins over the framework default.
     */
    class CustomRuleOverridesSpringDefault : Base() {

        override fun createConfigReader(): ConfigReader {
            return TestConfigReader.fromRules(
                project,
                "param.required[@org.springframework.web.bind.annotation.RequestParam]" to "false"
            )
        }

        fun testRuleOverridesRequestParamDefault() = runTest {
            loadItemController()
            val params = exportParams("com.itangcent.api.ParamRequiredCtrl", "/items/list")
            assertRequired("param.required=false must win over the @RequestParam default", false, params, "keyword")
        }
    }
}
