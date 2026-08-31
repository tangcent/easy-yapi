package com.itangcent.easyapi.framework.jaxrs

import com.itangcent.easyapi.core.config.ConfigReader
import com.itangcent.easyapi.core.export.ApiHeader
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

/**
 * Regression coverage for the JAX-RS side of header required-flag propagation.
 *
 * `JaxRsClassExporter.extractParamHeaders` builds [ApiHeader] from the resolved
 * parameter list without consulting `param.required`, so header parameters never
 * carried a required flag. JAX-RS has no `required` attribute on `@HeaderParam`,
 * so `param.required` is the only source of truth here.
 */
class JaxRsHeaderRequiredTest {

    abstract class Base : EasyApiLightCodeInsightFixtureTestCase() {

        protected lateinit var exporter: JaxRsClassExporter

        override fun setUp() {
            super.setUp()
            loadJaxRsStubs()
            exporter = JaxRsClassExporter(project)
        }

        protected fun loadJaxRsStubs() {
            loadFile("jaxrs/Path.java")
            loadFile("jaxrs/POST.java")
            loadFile("jaxrs/HeaderParam.java")
            loadFile("model/Result.java")
            loadFile("model/IResult.java")
            loadFile(
                "api/jaxrs/HeaderResource.java",
                """
                package com.itangcent.jaxrs;
                import com.itangcent.model.Result;
                import javax.ws.rs.HeaderParam;
                import javax.ws.rs.POST;
                import javax.ws.rs.Path;

                @Path("/headers")
                public class HeaderResource {

                    @POST
                    @Path("/apply")
                    public Result<String> apply(@HeaderParam("x-user-id") String userId) {
                        return Result.success("ok");
                    }
                }
                """.trimIndent()
            )
        }

        protected suspend fun exportUserIdHeader(): ApiHeader {
            val psiClass = findClass("com.itangcent.jaxrs.HeaderResource")
            assertNotNull("HeaderResource should be resolvable", psiClass)
            val endpoints = exporter.export(psiClass!!)
            val endpoint = endpoints.singleOrNull { it.httpMetadata?.path == "/headers/apply" }
            assertNotNull(
                "Should export /headers/apply; actual=${endpoints.map { it.httpMetadata?.path }}",
                endpoint
            )
            val headers = endpoint!!.httpMetadata!!.headers
            val header = headers.singleOrNull { it.name == "x-user-id" }
            assertNotNull(
                "@HeaderParam(\"x-user-id\") should be exported as a header; " +
                    "actual=[${headers.joinToString { "${it.name}(required=${it.required})" }}]",
                header
            )
            return header!!
        }
    }

    class WithParamRequiredRule : Base() {

        override fun createConfigReader(): ConfigReader {
            return TestConfigReader.fromRules(
                project,
                "param.required[@javax.ws.rs.HeaderParam]" to "true"
            )
        }

        fun testParamRequiredRuleReachesTheHeader() = runTest {
            assertTrue(
                "A param.required rule must be honoured for JAX-RS header parameters",
                exportUserIdHeader().required
            )
        }
    }

    class WithoutAnyRule : Base() {

        override fun createConfigReader(): ConfigReader = TestConfigReader.empty(project)

        fun testHeaderRequiredDefaultsToFalseWithoutRules() = runTest {
            assertFalse(
                "Without a param.required rule the header stays optional",
                exportUserIdHeader().required
            )
        }
    }
}
