package com.itangcent.easyapi.framework.springmvc

import com.itangcent.easyapi.core.config.ConfigReader
import com.itangcent.easyapi.core.export.ApiParameter
import com.itangcent.easyapi.core.export.ParameterBinding
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.core.extension.ExtensionConfigRegistry
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
                    public void setName(String name) { this.name = name; }
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

    /**
     * A bundled validation extension is active: its `param.required` rules only
     * mark parameters carrying a constraint as required. Every other parameter
     * gets no value from them and keeps the Spring default.
     *
     * `jakarta-validation` and `javax-validation` are enabled by default whenever
     * their `NotNull` annotation is on the classpath, so this is the setup of most
     * Spring Boot projects.
     */
    abstract class BundledValidationRules(
        private val extensionCode: String,
        private val constraintPackage: String
    ) : Base() {

        override fun createConfigReader(): ConfigReader {
            val extension = ExtensionConfigRegistry.getExtension(extensionCode)
            assertNotNull("the built-in $extensionCode extension must be on the test classpath", extension)
            return TestConfigReader.fromConfigText(project, extension!!.content)
        }

        private fun loadValidatedController() {
            loadFile("${constraintPackage.replace('.', '/')}/NotNull.java")
            loadFile("spring/RequestHeader.java")
            loadFile(
                "api/ValidatedParamCtrl.java",
                """
                package com.itangcent.api;
                import com.itangcent.model.Result;
                import $constraintPackage.NotNull;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.PathVariable;
                import org.springframework.web.bind.annotation.RequestHeader;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RequestParam;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                @RequestMapping(value = "/validated")
                public class ValidatedParamCtrl {

                    @GetMapping(value = "/search")
                    public Result<String> search(
                            @RequestHeader String traceId,
                            @RequestParam String keyword,
                            @RequestParam(required = false) String tag,
                            @RequestParam(required = false) @NotNull String owner,
                            String plain) {
                        return Result.success("ok");
                    }

                    @GetMapping(value = "/detail/{id}")
                    public Result<String> detail(@PathVariable String id) {
                        return Result.success("ok");
                    }
                }
                """.trimIndent()
            )
        }

        private suspend fun exportSearchParams(): List<ApiParameter> {
            loadValidatedController()
            return exportParams("com.itangcent.api.ValidatedParamCtrl", "/validated/search")
        }

        fun testPlainRequestParamStaysRequired() = runTest {
            assertRequired(
                "a @RequestParam without a constraint keeps the Spring default (required)",
                true,
                exportSearchParams(),
                "keyword"
            )
        }

        fun testRequestParamRequiredFalseStaysOptional() = runTest {
            assertRequired("@RequestParam(required = false)", false, exportSearchParams(), "tag")
        }

        fun testNotNullMakesOptionalRequestParamRequired() = runTest {
            assertRequired(
                "@NotNull matches the param.required rule, which wins over required = false",
                true,
                exportSearchParams(),
                "owner"
            )
        }

        fun testUnannotatedParameterStaysOptional() = runTest {
            assertRequired(
                "an unannotated parameter keeps Spring's default resolution (required = false)",
                false,
                exportSearchParams(),
                "plain"
            )
        }

        fun testPlainPathVariableStaysRequired() = runTest {
            loadValidatedController()
            val params = exportParams("com.itangcent.api.ValidatedParamCtrl", "/validated/detail/{id}")
            assertRequired(
                "a @PathVariable without a constraint keeps the Spring default (required)",
                true,
                params,
                "id"
            )
        }

        fun testPlainRequestHeaderStaysRequired() = runTest {
            loadValidatedController()
            val psiClass = findClass("com.itangcent.api.ValidatedParamCtrl")
            assertNotNull("ValidatedParamCtrl should be resolvable", psiClass)
            val endpoint = exporter.export(psiClass!!).singleOrNull { it.httpMetadata?.path == "/validated/search" }
            assertNotNull("Should export /validated/search", endpoint)
            val headers = endpoint!!.httpMetadata!!.headers
            val traceId = headers.singleOrNull { it.name == "traceId" }
            assertNotNull(
                "traceId should be exported as a header; actual=[${headers.joinToString { "${it.name}=${it.required}" }}]",
                traceId
            )
            assertTrue(
                "a @RequestHeader without a constraint keeps the Spring default (required)",
                traceId!!.required
            )
        }
    }

    class WithBundledJakartaValidation : BundledValidationRules(
        "jakarta-validation", "jakarta.validation.constraints"
    )

    class WithBundledJakartaValidationStrict : BundledValidationRules(
        "jakarta-validation-strict", "jakarta.validation.constraints"
    )

    class WithBundledJavaxValidation : BundledValidationRules(
        "javax-validation", "javax.validation.constraints"
    )

    class WithBundledJavaxValidationStrict : BundledValidationRules(
        "javax-validation-strict", "javax.validation.constraints"
    )

    /**
     * The bundled `swagger` extension reads `param.required` from
     * `@ApiParam#required`. A parameter without `@ApiParam` has no such attribute,
     * so the rule gives no value and the Spring default applies.
     */
    class WithBundledSwaggerRules : Base() {

        override fun createConfigReader(): ConfigReader {
            val extension = ExtensionConfigRegistry.getExtension("swagger")
            assertNotNull("the built-in swagger extension must be on the test classpath", extension)
            return TestConfigReader.fromConfigText(project, extension!!.content)
        }

        private suspend fun exportSearchParams(): List<ApiParameter> {
            loadFile("io/swagger/annotations/ApiParam.java")
            loadFile(
                "api/SwaggerParamCtrl.java",
                """
                package com.itangcent.api;
                import com.itangcent.model.Result;
                import io.swagger.annotations.ApiParam;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RequestParam;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                @RequestMapping(value = "/swagger")
                public class SwaggerParamCtrl {

                    @GetMapping(value = "/search")
                    public Result<String> search(
                            @RequestParam String keyword,
                            @RequestParam(required = false) @ApiParam(required = true) String owner) {
                        return Result.success("ok");
                    }
                }
                """.trimIndent()
            )
            return exportParams("com.itangcent.api.SwaggerParamCtrl", "/swagger/search")
        }

        fun testRequestParamWithoutApiParamStaysRequired() = runTest {
            assertRequired(
                "a @RequestParam without @ApiParam keeps the Spring default (required)",
                true,
                exportSearchParams(),
                "keyword"
            )
        }

        fun testApiParamRequiredWinsOverRequiredFalse() = runTest {
            assertRequired(
                "@ApiParam(required = true) wins over @RequestParam(required = false)",
                true,
                exportSearchParams(),
                "owner"
            )
        }
    }
}
