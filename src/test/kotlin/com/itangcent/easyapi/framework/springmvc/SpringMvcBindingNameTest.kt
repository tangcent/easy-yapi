package com.itangcent.easyapi.framework.springmvc

import com.intellij.psi.PsiClass
import com.itangcent.easyapi.core.config.ConfigReader
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.ApiHeader
import com.itangcent.easyapi.core.export.ApiParameter
import com.itangcent.easyapi.core.export.ParameterBinding
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.core.internal.threading.readSync
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import org.junit.AssumptionViolatedException

/**
 * Regression coverage for the names of Spring MVC request values.
 *
 * Spring binds a handler parameter by the name its binding annotation declares
 * (`value` or `name` of `@RequestParam`, `@RequestPart`, `@PathVariable`,
 * `@RequestHeader`, `@CookieValue`) and falls back to the parameter name only
 * when the annotation names nothing. The exported name must follow the same
 * rule, unless a `param.name` rule is configured, which still wins.
 */
class SpringMvcBindingNameTest {

    abstract class Base : EasyApiLightCodeInsightFixtureTestCase() {

        protected lateinit var exporter: SpringMvcClassExporter

        override fun setUp() {
            super.setUp()
            loadSpringStubs()
            exporter = SpringMvcClassExporter(project)
        }

        private fun loadSpringStubs() {
            loadFile("spring/RestController.java")
            loadFile("spring/RequestMapping.java")
            loadFile("spring/GetMapping.java")
            loadFile("spring/PostMapping.java")
            loadFile("spring/RequestParam.java")
            loadFile("spring/RequestPart.java")
            loadFile("spring/PathVariable.java")
            loadFile("spring/RequestHeader.java")
            loadFile("spring/CookieValue.java")
            loadFile("org/springframework/web/multipart/MultipartFile.java")
            loadFile("model/Result.java")
            loadFile("model/IResult.java")
        }

        protected fun loadBindingNameController() {
            loadFile(
                "api/BindingNameCtrl.java",
                """
                package com.itangcent.api;
                import com.itangcent.model.Result;
                import org.springframework.web.bind.annotation.CookieValue;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.PathVariable;
                import org.springframework.web.bind.annotation.PostMapping;
                import org.springframework.web.bind.annotation.RequestHeader;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RequestParam;
                import org.springframework.web.bind.annotation.RequestPart;
                import org.springframework.web.bind.annotation.RestController;
                import org.springframework.web.multipart.MultipartFile;

                @RestController
                @RequestMapping(value = "/orders")
                public class BindingNameCtrl {

                    @GetMapping(value = "/search")
                    public Result<String> search(
                            @RequestParam("q") String keyword,
                            @RequestParam(name = "page_size") int pageSize,
                            @RequestParam(value = "sort_by", required = false) String sortBy,
                            @RequestParam String status) {
                        return Result.success("ok");
                    }

                    @PostMapping(value = "/headers")
                    public Result<String> headers(
                            @RequestHeader("X-Request-Id") String requestId,
                            @RequestHeader(name = "X-Tenant") String tenant,
                            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
                            @RequestHeader String locale) {
                        return Result.success("ok");
                    }

                    @GetMapping(value = "/{id}/items/{item}")
                    public Result<String> item(
                            @PathVariable("id") Long orderId,
                            @PathVariable(name = "item") String itemCode) {
                        return Result.success("ok");
                    }

                    @GetMapping(value = "/cookies")
                    public Result<String> cookies(
                            @CookieValue("SESSION") String sessionId,
                            @CookieValue(name = "lang", required = false) String language) {
                        return Result.success("ok");
                    }

                    @PostMapping(value = "/upload")
                    public Result<String> upload(
                            @RequestPart("meta") String metadata,
                            @RequestParam("avatar") MultipartFile file) {
                        return Result.success("ok");
                    }
                }
                """.trimIndent()
            )
        }

        protected suspend fun exportEndpoint(className: String, path: String): ApiEndpoint {
            val psiClass = findClass(className)
            assertNotNull("$className should be resolvable", psiClass)
            val endpoints = exporter.export(psiClass!!)
            val endpoint = endpoints.singleOrNull { it.httpMetadata?.path == path }
            assertNotNull(
                "Should export $path; actual=${endpoints.map { it.httpMetadata?.path }}",
                endpoint
            )
            return endpoint!!
        }

        protected suspend fun exportParams(className: String, path: String): List<ApiParameter> =
            exportEndpoint(className, path).httpMetadata!!.parameters

        protected suspend fun exportHeaders(className: String, path: String): List<ApiHeader> =
            exportEndpoint(className, path).httpMetadata!!.headers

        protected fun assertParam(
            params: List<ApiParameter>,
            name: String,
            binding: ParameterBinding,
            required: Boolean? = null
        ) {
            val dump = params.joinToString { "${it.name}(${it.binding})=${it.required}" }
            val param = params.singleOrNull { it.name == name }
            assertNotNull("parameter '$name' should be exported; actual=[$dump]", param)
            assertEquals("binding of '$name'; actual=[$dump]", binding, param!!.binding)
            if (required != null) {
                assertEquals("required of '$name'; actual=[$dump]", required, param.required)
            }
        }

        protected fun assertNoParam(params: List<ApiParameter>, name: String) {
            assertNull(
                "parameter '$name' must not be exported; actual=[${params.joinToString { "${it.name}(${it.binding})" }}]",
                params.singleOrNull { it.name == name }
            )
        }

        protected fun assertHeader(headers: List<ApiHeader>, name: String, required: Boolean? = null) {
            val dump = headers.joinToString { "${it.name}(required=${it.required})" }
            val header = headers.singleOrNull { it.name == name }
            assertNotNull("header '$name' should be exported; actual=[$dump]", header)
            if (required != null) {
                assertEquals("required of header '$name'; actual=[$dump]", required, header!!.required)
            }
        }

        protected fun assertNoHeader(headers: List<ApiHeader>, name: String) {
            assertNull(
                "header '$name' must not be exported; actual=[${headers.joinToString { it.name }}]",
                headers.singleOrNull { it.name == name }
            )
        }
    }

    /**
     * No `param.name` rule: the name declared on the annotation is exported,
     * and the parameter name only when the annotation names nothing.
     */
    class WithoutParamNameRule : Base() {

        override fun createConfigReader(): ConfigReader = TestConfigReader.empty(project)

        fun testRequestParamExportsDeclaredName() = runTest {
            loadBindingNameController()
            val params = exportParams("com.itangcent.api.BindingNameCtrl", "/orders/search")
            assertParam(params, "q", ParameterBinding.Query, required = true)
            assertParam(params, "page_size", ParameterBinding.Query, required = true)
            assertParam(params, "sort_by", ParameterBinding.Query, required = false)
            assertNoParam(params, "keyword")
            assertNoParam(params, "pageSize")
            assertNoParam(params, "sortBy")
        }

        fun testRequestParamWithoutNameKeepsParameterName() = runTest {
            loadBindingNameController()
            val params = exportParams("com.itangcent.api.BindingNameCtrl", "/orders/search")
            assertParam(params, "status", ParameterBinding.Query, required = true)
        }

        fun testRequestHeaderExportsDeclaredName() = runTest {
            loadBindingNameController()
            val headers = exportHeaders("com.itangcent.api.BindingNameCtrl", "/orders/headers")
            assertHeader(headers, "X-Request-Id", required = true)
            assertHeader(headers, "X-Tenant", required = true)
            assertHeader(headers, "X-Idempotency-Key", required = false)
            assertNoHeader(headers, "requestId")
            assertNoHeader(headers, "tenant")
            assertNoHeader(headers, "idempotencyKey")
        }

        fun testRequestHeaderWithoutNameKeepsParameterName() = runTest {
            loadBindingNameController()
            val headers = exportHeaders("com.itangcent.api.BindingNameCtrl", "/orders/headers")
            assertHeader(headers, "locale", required = true)
        }

        fun testPathVariableMatchesPathPlaceholder() = runTest {
            loadBindingNameController()
            val params = exportParams("com.itangcent.api.BindingNameCtrl", "/orders/{id}/items/{item}")
            val pathParams = params.filter { it.binding == ParameterBinding.Path }
            assertEquals(
                "each {placeholder} should be exported once, under its own name; " +
                    "actual=[${pathParams.joinToString { it.name }}]",
                listOf("id", "item"),
                pathParams.map { it.name }.sorted()
            )
            assertNoParam(params, "orderId")
            assertNoParam(params, "itemCode")
        }

        fun testCookieValueExportsDeclaredName() = runTest {
            loadBindingNameController()
            val params = exportParams("com.itangcent.api.BindingNameCtrl", "/orders/cookies")
            assertParam(params, "SESSION", ParameterBinding.Cookie, required = true)
            assertParam(params, "lang", ParameterBinding.Cookie, required = false)
            assertNoParam(params, "sessionId")
            assertNoParam(params, "language")
        }

        fun testRequestPartAndFileRequestParamExportDeclaredNames() = runTest {
            loadBindingNameController()
            val params = exportParams("com.itangcent.api.BindingNameCtrl", "/orders/upload")
            assertParam(params, "meta", ParameterBinding.Form, required = true)
            assertParam(params, "avatar", ParameterBinding.Form, required = true)
            assertNoParam(params, "metadata")
            assertNoParam(params, "file")
        }

        /**
         * The binding annotations sit on the interface method only; the
         * implementation does not repeat them. Spring still binds by the
         * interface-declared names.
         */
        fun testNamesDeclaredOnInterfaceMethodAreHonoured() = runTest {
            loadFile(
                "api/OrderApi.java",
                """
                package com.itangcent.api;
                import com.itangcent.model.Result;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.PathVariable;
                import org.springframework.web.bind.annotation.RequestHeader;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RequestParam;

                @RequestMapping(value = "/api/orders")
                public interface OrderApi {

                    @GetMapping(value = "/{id}")
                    Result<String> get(
                            @PathVariable("id") Long orderId,
                            @RequestParam(name = "with_items", required = false) Boolean withItems,
                            @RequestHeader("X-Api-Version") String version);
                }
                """.trimIndent()
            )
            loadFile(
                "api/OrderApiCtrl.java",
                """
                package com.itangcent.api;
                import com.itangcent.api.OrderApi;
                import com.itangcent.model.Result;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                public class OrderApiCtrl implements OrderApi {

                    @Override
                    public Result<String> get(Long orderId, Boolean withItems, String version) {
                        return Result.success("ok");
                    }
                }
                """.trimIndent()
            )
            val psiClass = findClass("com.itangcent.api.OrderApiCtrl")
            assertNotNull("OrderApiCtrl should be resolvable", psiClass)
            val endpoints = exporter.export(psiClass!!)
            // The mapping is inherited from the interface; match on the tail of the path.
            val endpoint = endpoints.singleOrNull { it.httpMetadata?.path?.endsWith("/orders/{id}") == true }
            assertNotNull(
                "Should export the inherited /api/orders/{id}; actual=${endpoints.map { it.httpMetadata?.path }}",
                endpoint
            )
            val params = endpoint!!.httpMetadata!!.parameters
            assertEquals(
                "the {id} placeholder should be exported once; actual=[${params.joinToString { "${it.name}(${it.binding})" }}]",
                listOf("id"),
                params.filter { it.binding == ParameterBinding.Path }.map { it.name }
            )
            assertParam(params, "with_items", ParameterBinding.Query, required = false)
            assertNoParam(params, "orderId")
            assertNoParam(params, "withItems")
            assertHeader(endpoint.httpMetadata!!.headers, "X-Api-Version", required = true)
        }
    }

    /**
     * A configured `param.name` rule still wins over the annotation name, so
     * existing rule-based setups keep their output.
     */
    class WithParamNameRule : Base() {

        override fun createConfigReader(): ConfigReader {
            return TestConfigReader.fromRules(
                project,
                "param.name[@org.springframework.web.bind.annotation.RequestHeader]" to "X-From-Rule"
            )
        }

        fun testParamNameRuleWinsOverAnnotationName() = runTest {
            loadBindingNameController()
            val headers = exportHeaders("com.itangcent.api.BindingNameCtrl", "/orders/headers")
            assertHeader(headers, "X-From-Rule")
            assertNoHeader(headers, "X-Request-Id")
            assertNoHeader(headers, "X-Tenant")
        }

        fun testParametersWithoutMatchingRuleUseAnnotationName() = runTest {
            loadBindingNameController()
            val params = exportParams("com.itangcent.api.BindingNameCtrl", "/orders/search")
            assertParam(params, "q", ParameterBinding.Query)
        }
    }

    /**
     * The same rules apply to a Kotlin controller, including a nullable header
     * declared with `required = false`.
     */
    class KotlinController : Base() {

        override fun createConfigReader(): ConfigReader = TestConfigReader.empty(project)

        override fun setUp() {
            super.setUp()
            try {
                Class.forName("org.jetbrains.kotlin.idea.KotlinLanguage")
            } catch (e: ClassNotFoundException) {
                throw AssumptionViolatedException("Kotlin plugin not available")
            }
        }

        private fun loadKotlinController() {
            loadFile(
                "api/kt/KtOrderCtrl.kt",
                """
                package com.itangcent.api.kt

                import org.springframework.web.bind.annotation.CookieValue
                import org.springframework.web.bind.annotation.PathVariable
                import org.springframework.web.bind.annotation.PostMapping
                import org.springframework.web.bind.annotation.RequestHeader
                import org.springframework.web.bind.annotation.RequestMapping
                import org.springframework.web.bind.annotation.RequestParam
                import org.springframework.web.bind.annotation.RestController

                @RestController
                @RequestMapping("/kt/orders")
                class KtOrderCtrl {

                    @PostMapping("/{id}/pay")
                    fun pay(
                        @PathVariable("id") orderId: Long,
                        @RequestParam(name = "pay_channel") payChannel: String,
                        @RequestHeader(value = "X-Idempotency-Key", required = false) idempotencyKey: String?,
                        @RequestHeader(name = "X-Tenant") tenant: String,
                        @CookieValue("SESSION") sessionId: String?
                    ): String {
                        return "ok"
                    }
                }
                """.trimIndent()
            )
        }

        private suspend fun exportKotlinEndpoint(): ApiEndpoint {
            val psiClass = findClass(KT_CTRL)
            assertNotNull("$KT_CTRL should be resolvable", psiClass)
            val endpoints = exporter.export(psiClass!!)
            val endpoint = endpoints.singleOrNull { it.httpMetadata?.path == KT_PATH }
            assertNotNull(
                "Should export $KT_PATH; actual=${endpoints.map { it.httpMetadata?.path }}; psi=${describe(psiClass)}",
                endpoint
            )
            return endpoint!!
        }

        /** What the fixture sees on the Kotlin light class, for a readable failure. */
        private fun describe(psiClass: PsiClass): String = readSync {
            val classAnns = psiClass.annotations.map { it.qualifiedName }
            val methods = psiClass.methods.filter { it.name == "pay" }.map { method ->
                val params = method.parameterList.parameters.map { p ->
                    p.name + p.annotations.map { ann ->
                        ann.qualifiedName + ann.parameterList.attributes.map { "${it.name}=${it.value?.text}" }
                    }
                }
                "${method.name}${method.annotations.map { it.qualifiedName }}$params"
            }
            "${psiClass.javaClass.simpleName}$classAnns$methods"
        }

        fun testKotlinParametersExportDeclaredNames() = runTest {
            loadKotlinController()
            val endpoint = exportKotlinEndpoint()
            val params = endpoint.httpMetadata!!.parameters
            assertEquals(
                "the {id} placeholder should be exported once; actual=[${params.joinToString { "${it.name}(${it.binding})" }}]",
                listOf("id"),
                params.filter { it.binding == ParameterBinding.Path }.map { it.name }
            )
            assertParam(params, "pay_channel", ParameterBinding.Query, required = true)
            assertParam(params, "SESSION", ParameterBinding.Cookie)
            assertNoParam(params, "orderId")
            assertNoParam(params, "payChannel")
            assertNoParam(params, "sessionId")
        }

        fun testKotlinOptionalHeaderExportsDeclaredName() = runTest {
            loadKotlinController()
            val headers = exportKotlinEndpoint().httpMetadata!!.headers
            assertHeader(headers, "X-Idempotency-Key", required = false)
            assertHeader(headers, "X-Tenant", required = true)
            assertNoHeader(headers, "idempotencyKey")
            assertNoHeader(headers, "tenant")
        }

        private companion object {
            const val KT_CTRL = "com.itangcent.api.kt.KtOrderCtrl"
            const val KT_PATH = "/kt/orders/{id}/pay"
        }
    }
}
