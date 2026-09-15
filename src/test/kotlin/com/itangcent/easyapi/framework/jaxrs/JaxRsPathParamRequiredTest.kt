package com.itangcent.easyapi.framework.jaxrs

import com.itangcent.easyapi.core.config.ConfigReader
import com.itangcent.easyapi.core.export.ApiParameter
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

/**
 * Regression coverage for the `required` flag of JAX-RS parameters (issue #1458).
 *
 * JAX-RS bindings carry no `required` attribute, so the framework layer can only
 * supply a *default per binding*: a `@PathParam` is part of the URI template and
 * therefore always present, while `@QueryParam`/`@HeaderParam`/`@CookieParam`/
 * `@FormParam` are optional. `param.required` keeps top priority.
 */
class JaxRsPathParamRequiredTest {

    abstract class Base : EasyApiLightCodeInsightFixtureTestCase() {

        protected lateinit var exporter: JaxRsClassExporter

        override fun setUp() {
            super.setUp()
            loadJaxRsStubs()
            exporter = JaxRsClassExporter(project)
        }

        protected fun loadJaxRsStubs() {
            loadFile("jaxrs/Path.java")
            loadFile("jaxrs/GET.java")
            loadFile("jaxrs/PathParam.java")
            loadFile("jaxrs/QueryParam.java")
            loadFile("model/Result.java")
            loadFile("model/IResult.java")
            loadFile(
                "api/jaxrs/ItemResource.java",
                """
                package com.itangcent.jaxrs;
                import com.itangcent.model.Result;
                import javax.ws.rs.GET;
                import javax.ws.rs.Path;
                import javax.ws.rs.PathParam;
                import javax.ws.rs.QueryParam;

                @Path("/items")
                public class ItemResource {

                    @GET
                    @Path("/{id}")
                    public Result<String> get(@PathParam("id") String id,
                                              @QueryParam("tag") String tag) {
                        return Result.success("ok");
                    }
                }
                """.trimIndent()
            )
        }

        protected suspend fun exportParams(): List<ApiParameter> {
            val psiClass = findClass("com.itangcent.jaxrs.ItemResource")
            assertNotNull("ItemResource should be resolvable", psiClass)
            val endpoints = exporter.export(psiClass!!)
            val endpoint = endpoints.singleOrNull { it.httpMetadata?.path == "/items/{id}" }
            assertNotNull(
                "Should export /items/{id}; actual=${endpoints.map { it.httpMetadata?.path }}",
                endpoint
            )
            return endpoint!!.httpMetadata!!.parameters
        }

        protected fun assertRequired(message: String, expected: Boolean, params: List<ApiParameter>, name: String) {
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
     * No `param.required` rule: the per-binding JAX-RS default applies.
     */
    class WithJaxRsDefaults : Base() {

        override fun createConfigReader(): ConfigReader = TestConfigReader.empty(project)

        fun testPathParamIsRequired() = runTest {
            val params = exportParams()
            assertRequired("a @PathParam always appears in the URI template", true, params, "id")
        }

        fun testQueryParamIsOptional() = runTest {
            val params = exportParams()
            assertRequired("@QueryParam has no required attribute, so it stays optional", false, params, "tag")
        }
    }

    /**
     * An explicit rule wins over the binding default.
     */
    class CustomRuleOverridesJaxRsDefault : Base() {

        override fun createConfigReader(): ConfigReader {
            return TestConfigReader.fromRules(
                project,
                "param.required[@javax.ws.rs.PathParam]" to "false"
            )
        }

        fun testRuleOverridesPathParamDefault() = runTest {
            val params = exportParams()
            assertRequired("param.required=false must win over the @PathParam default", false, params, "id")
        }
    }
}
