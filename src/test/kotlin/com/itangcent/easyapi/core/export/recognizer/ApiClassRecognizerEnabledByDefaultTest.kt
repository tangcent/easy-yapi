package com.itangcent.easyapi.core.export.recognizer

import com.itangcent.easyapi.framework.feign.FeignClientRecognizer
import com.itangcent.easyapi.framework.grpc.GrpcServiceRecognizer
import com.itangcent.easyapi.framework.jaxrs.JaxRsResourceRecognizer
import com.itangcent.easyapi.framework.springmvc.ActuatorEndpointRecognizer
import com.itangcent.easyapi.framework.springmvc.SpringControllerRecognizer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Test-first test for [ApiClassRecognizer.enabledByDefault] SPI addition (PR6).
 *
 * This test is written BEFORE the SPI property is added to the interface.
 * It MUST fail to compile against current code (the `enabledByDefault` property
 * does not exist on [ApiClassRecognizer] yet) — mirroring the task 5 -> 6
 * pattern for `matchesClass`.
 *
 * After task 17 adds the SPI property (default `true`), assertions (a) and (b)
 * pass, and (c) passes for the default-on recognizers (SpringMVC, JAX-RS, gRPC).
 * Assertion (c) for the default-off recognizers (Feign, Actuator) only passes
 * after tasks 24/25 add the `false` overrides.
 *
 * Requirements: Framework Enablement 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7;
 * Decision: PR6
 */
class ApiClassRecognizerEnabledByDefaultTest {

    /**
     * Fixture recognizer that inherits the default `enabledByDefault`.
     * Used to verify the SPI property exists on the interface with a default impl.
     */
    private fun fixtureRecognizer(): ApiClassRecognizer = object : ApiClassRecognizer {
        override val frameworkName: String = "Fixture"
        override val targetAnnotations: Set<String> = emptySet()
        override suspend fun isApiClass(psiClass: com.intellij.psi.PsiClass): Boolean = false
    }

    /**
     * (a) Verifies that `enabledByDefault` exists on the [ApiClassRecognizer]
     * interface and is consumable via a fixture recognizer implementation.
     *
     * This test fails to compile before task 17 adds the SPI property.
     */
    @Test
    fun testEnabledByDefaultExistsOnInterface() {
        val fixture = fixtureRecognizer()
        // Accessing enabledByDefault on the interface type verifies the SPI member exists.
        assertEquals(true, fixture.enabledByDefault)
    }

    /**
     * (b) Verifies the default implementation returns `true`.
     */
    @Test
    fun testDefaultImplReturnsTrue() {
        val fixture = fixtureRecognizer()
        assertEquals(
            "Default enabledByDefault should be true",
            true,
            fixture.enabledByDefault
        )
    }

    /**
     * (c) Verifies the 5 in-tree recognizers' `enabledByDefault` values match
     * the pre-patch defaults:
     * - SpringControllerRecognizer → true (inherits default — Req 1.2)
     * - JaxRsResourceRecognizer → true (explicit — Req 1.3, preserves jaxrsEnable=true)
     * - GrpcServiceRecognizer → true (explicit — Req 1.4, preserves grpcEnable=true)
     * - FeignClientRecognizer → false (Req 1.5, preserves feignEnable=false)
     * - ActuatorEndpointRecognizer → false (Req 1.6, preserves actuatorEnable=false)
     *
     * This test fails to compile before task 17 adds the SPI property, and
     * fails on the Feign/Actuator assertions until tasks 24/25 add the
     * `false` overrides.
     */
    @Test
    fun testInTreeRecognizersMatchPrePatchDefaults() {
        // Default-on frameworks (pre-patch: jaxrsEnable=true, grpcEnable=true)
        assertEquals(
            "SpringControllerRecognizer should be default-on (inherits true)",
            true,
            SpringControllerRecognizer().enabledByDefault
        )
        assertEquals(
            "JaxRsResourceRecognizer should be default-on (preserves jaxrsEnable=true)",
            true,
            JaxRsResourceRecognizer().enabledByDefault
        )
        assertEquals(
            "GrpcServiceRecognizer should be default-on (preserves grpcEnable=true)",
            true,
            GrpcServiceRecognizer().enabledByDefault
        )

        // Default-off frameworks (pre-patch: feignEnable=false, actuatorEnable=false)
        assertEquals(
            "FeignClientRecognizer should be default-off (preserves feignEnable=false)",
            false,
            FeignClientRecognizer().enabledByDefault
        )
        assertEquals(
            "ActuatorEndpointRecognizer should be default-off (preserves actuatorEnable=false)",
            false,
            ActuatorEndpointRecognizer().enabledByDefault
        )
    }
}
