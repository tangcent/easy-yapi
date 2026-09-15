package com.itangcent.easyapi.framework.springmvc

import com.itangcent.easyapi.core.config.ConfigReader
import com.itangcent.easyapi.core.export.EndpointBuilder
import com.itangcent.easyapi.core.export.ParameterBinding
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.core.psi.model.ObjectModel
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

/**
 * Regression coverage for `required` resolution of Actuator endpoints (issue #1458).
 *
 * The scanner used to hard-code `required = true` for `@Selector` parameters and
 * never consulted `field.required` for the synthetic write-operation body. Both
 * must honour the rule layer first, then fall back to the Actuator default
 * (a selector is part of the path, so it is required).
 */
class ActuatorParamRequiredTest {

    abstract class Base : EasyApiLightCodeInsightFixtureTestCase() {

        protected lateinit var scanner: ActuatorEndpointScanner

        override fun setUp() {
            super.setUp()
            loadActuatorStubs()
            scanner = ActuatorEndpointScanner(project, EndpointBuilder.getInstance(project))
        }

        protected fun loadActuatorStubs() {
            loadFile("spring/Endpoint.java")
            loadFile("spring/ReadOperation.java")
            loadFile("spring/WriteOperation.java")
            loadFile("spring/DeleteOperation.java")
            loadFile("spring/Selector.java")
            loadFile("api/actuator/StandardEndpoint.java")
        }

        protected suspend fun scanStandardEndpoint() =
            scanner.scan(findClass("com.itangcent.springboot.demo.controller.StandardEndpoint")!!)

        protected fun pathParam(endpoint: com.itangcent.easyapi.core.export.ApiEndpoint, name: String) =
            endpoint.httpMetadata?.parameters.orEmpty()
                .singleOrNull { it.binding == ParameterBinding.Path && it.name == name }

        protected fun bodyField(endpoint: com.itangcent.easyapi.core.export.ApiEndpoint, name: String) =
            (endpoint.httpMetadata?.body as? ObjectModel.Object)?.fields?.get(name)
    }

    /**
     * Without rules the Actuator defaults apply: a `@Selector` is part of the path
     * and therefore required; body fields follow `field.required` (unset → false).
     */
    class WithActuatorDefaults : Base() {

        override fun createConfigReader(): ConfigReader = TestConfigReader.empty(project)

        fun testSelectorParameterIsRequiredByDefault() = runTest {
            val endpoints = scanStandardEndpoint()
            val readEndpoint = endpoints.single { it.name == "endpointByGet" }
            val username = pathParam(readEndpoint, "username")
            assertNotNull("@Selector username should be exported as a path parameter", username)
            assertTrue("a @Selector is part of the path and therefore required", username!!.required)
        }

        fun testWriteBodyFieldIsOptionalWithoutRule() = runTest {
            val endpoints = scanStandardEndpoint()
            val writeEndpoint = endpoints.single { it.name == "endpointByPost" }
            val username = bodyField(writeEndpoint, "username")
            assertNotNull("username should be part of the synthesized request body", username)
            assertFalse("without a field.required rule the field stays optional", username!!.required)
        }
    }

    /**
     * An explicit rule wins over the hard-coded Actuator default.
     */
    class CustomRuleOverridesActuatorDefault : Base() {

        override fun createConfigReader(): ConfigReader {
            return TestConfigReader.fromRules(
                project,
                "param.required[@org.springframework.boot.actuate.endpoint.annotation.Selector]" to "false",
                "field.required" to """groovy:it.name() == "username""""
            )
        }

        fun testRuleOverridesSelectorDefault() = runTest {
            val endpoints = scanStandardEndpoint()
            val readEndpoint = endpoints.single { it.name == "endpointByGet" }
            val username = pathParam(readEndpoint, "username")
            assertNotNull("@Selector username should be exported as a path parameter", username)
            assertFalse("param.required=false must win over the @Selector default", username!!.required)
        }

        fun testFieldRequiredRuleAppliesToWriteBody() = runTest {
            val endpoints = scanStandardEndpoint()
            val writeEndpoint = endpoints.single { it.name == "endpointByPost" }
            val username = bodyField(writeEndpoint, "username")
            assertNotNull("username should be part of the synthesized request body", username)
            assertTrue("field.required must be honoured for the synthesized body", username!!.required)
        }
    }
}
