package com.itangcent.easyapi.framework.springmvc

import com.itangcent.easyapi.core.config.ConfigReader
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

/**
 * Regression coverage for `@RequestHeader` required-flag propagation.
 *
 * Header-bound parameters are exported through a dedicated branch
 * (`extractParamHeaders`) that builds [com.itangcent.easyapi.core.export.ApiHeader]
 * independently of the `ApiParameter` pipeline. The `param.required` rule must be
 * honoured for these parameters, and — when no rule is configured — the Spring
 * default applies: `@RequestHeader` is required unless `required = false` is
 * declared explicitly.
 */
class SpringMvcRequestHeaderRequiredTest {

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
            loadFile("spring/PostMapping.java")
            loadFile("spring/RequestHeader.java")
            loadFile("spring/RequestParam.java")
            loadFile("model/Result.java")
            loadFile("model/IResult.java")
        }

        protected fun loadHeaderController() {
            loadFile(
                "api/HeaderRequiredCtrl.java",
                """
                package com.itangcent.api;
                import com.itangcent.model.Result;
                import org.springframework.web.bind.annotation.PostMapping;
                import org.springframework.web.bind.annotation.RequestHeader;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                @RequestMapping(value = "/statuses")
                public class HeaderRequiredCtrl {

                    /**
                     * update message
                     *
                     * @param userId      caller id
                     * @param timezoneId  caller timezone
                     * @return update result
                     */
                    @PostMapping(value = "/classifications")
                    public Result<String> updateMessage(
                            @RequestHeader("x-user-id") String userId,
                            @RequestHeader(value = "x-timezone-id", required = false) String timezoneId) {
                        return Result.success("ok");
                    }
                }
                """.trimIndent()
            )
        }

        /**
         * With an empty rule set the exported header name falls back to the Java
         * parameter name, so the two `@RequestHeader` parameters surface as
         * `userId` and `timezoneId`.
         */
        protected suspend fun exportHeaders(): List<com.itangcent.easyapi.core.export.ApiHeader> {
            loadHeaderController()
            val psiClass = findClass("com.itangcent.api.HeaderRequiredCtrl")
            assertNotNull("HeaderRequiredCtrl should be resolvable", psiClass)
            val endpoints = exporter.export(psiClass!!)
            val endpoint = endpoints.singleOrNull { it.httpMetadata?.path == "/statuses/classifications" }
            assertNotNull(
                "Should export /statuses/classifications; actual=${endpoints.map { it.httpMetadata?.path }}",
                endpoint
            )
            return endpoint!!.httpMetadata!!.headers
        }

        protected fun assertRequiredMatchesSpringDeclaration(
            headers: List<com.itangcent.easyapi.core.export.ApiHeader>
        ) {
            val dump = headers.joinToString { "${it.name}(required=${it.required})" }
            val userId = headers.singleOrNull { it.name == "userId" }
            assertNotNull("userId should be exported as a header; actual headers=[$dump]", userId)
            assertTrue(
                "@RequestHeader without required=false is required, so the header must be marked required",
                userId!!.required
            )

            val timezoneId = headers.singleOrNull { it.name == "timezoneId" }
            assertNotNull("timezoneId should be exported as a header; actual headers=[$dump]", timezoneId)
            assertFalse(
                "@RequestHeader(required = false) must be exported as an optional header",
                timezoneId!!.required
            )
        }
    }

    /**
     * No `param.required` rule configured: the Spring default applies in code —
     * `@RequestHeader` is required, `@RequestHeader(required = false)` is optional.
     */
    class WithSpringDefaultBehavior : Base() {

        override fun createConfigReader(): ConfigReader = TestConfigReader.empty(project)

        fun testSpringRequestHeaderDefaultIsAppliedWithoutRules() = runTest {
            assertRequiredMatchesSpringDeclaration(exportHeaders())
        }
    }

    /**
     * A user-authored `param.required` rule matching the issue report must be
     * honoured for header parameters exactly like it is for query parameters.
     */
    class WithExplicitCustomRule : Base() {

        override fun createConfigReader(): ConfigReader {
            return TestConfigReader.fromRules(
                project,
                "param.required[@org.springframework.web.bind.annotation.RequestHeader]" to
                    """groovy:it.ann("org.springframework.web.bind.annotation.RequestHeader", "required") != "false""""
            )
        }

        fun testCustomParamRequiredRuleIsPropagatedToApiHeader() = runTest {
            assertRequiredMatchesSpringDeclaration(exportHeaders())
        }
    }

    /**
     * An explicit rule overrides the Spring default: `param.required = false`
     * must win over the annotation's implied `required = true`.
     */
    class CustomRuleTakesPrecedenceOverSpringDefault : Base() {

        override fun createConfigReader(): ConfigReader {
            return TestConfigReader.fromRules(
                project,
                "param.required[@org.springframework.web.bind.annotation.RequestHeader]" to "false"
            )
        }

        fun testCustomRuleOverridesSpringDefault() = runTest {
            val headers = exportHeaders()
            headers.forEach { header ->
                assertFalse(
                    "param.required=false must win over the @RequestHeader default " +
                        "(header=${header.name})",
                    header.required
                )
            }
        }
    }
}
