package com.itangcent.easyapi.core.export.recognizer

import com.itangcent.easyapi.core.settings.SettingBinder
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.core.settings.update
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

/**
 * Test-first test for [ApiClassRecognizer.matchesClass] SPI addition (PR1).
 *
 * This test is written BEFORE the SPI member is added to the interface.
 * It MUST fail to compile against current code (the `matchesClass` method
 * does not exist on [ApiClassRecognizer] yet) — mirroring the original
 * tasks 36->37 pattern for CO5.
 *
 * After task 6 implements the SPI member (default `false`), the existence
 * and default-behavior assertions (a) and (b) pass. Assertion (c) — that
 * `GrpcServiceRecognizer.matchesClass` returns `true` on a class extending
 * `BindableService` — only passes after task 8 adds the gRPC override.
 *
 * Requirements: Recognizer Relocation 2.6, 4.6; Decision: PR1
 */
class ApiClassRecognizerMatchesClassTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun setUp() {
        super.setUp()
        // Enable all 5 frameworks so the EP seam returns every recognizer.
        // Feign (default-off) and Actuator (default-off) need explicit enablement;
        // JAX-RS (default-on), gRPC (default-on), SpringMVC (always-on) are
        // already on, so no entry is needed for them.
        SettingBinder.getInstance(project).update(GeneralSettings::class) {
            enabledFrameworks = arrayOf("Feign", "SpringActuator")
        }
        loadTestFiles()
    }

    private fun loadTestFiles() {
        loadFile("grpc/BindableService.java")
        loadFile("grpc/StreamObserver.java")
        loadFile("grpc/GrpcService.java")
        loadFile("grpc/EchoRequest.java")
        loadFile("grpc/EchoResponse.java")
        loadFile("grpc/EchoServiceGrpc.java")
        loadFile("grpc/EchoServiceImpl.java")
        loadFile("grpc/UserInfo.java")
    }

    override fun createConfigReader() = TestConfigReader.empty(project)

    /**
     * (a) Verifies that `matchesClass` exists on the [ApiClassRecognizer]
     * interface and is consumable via the EP seam
     * ([CompositeApiClassRecognizer.recognizers]).
     *
     * This test fails to compile before task 6 adds the SPI member.
     */
    fun testMatchesClassExistsOnInterface() = runTest {
        val recognizers = CompositeApiClassRecognizer.getInstance(project).recognizers()
        assertTrue("Should have at least one recognizer", recognizers.isNotEmpty())
        val fixtureClass = findClass("com.itangcent.grpc.model.UserInfo")!!
        // Calling matchesClass on the interface type verifies the SPI member exists.
        recognizers.forEach { recognizer ->
            recognizer.matchesClass(fixtureClass)
        }
    }

    /**
     * (b) Verifies the default implementation returns `false` for the 4
     * non-gRPC recognizers (SpringControllerRecognizer, JaxRsResourceRecognizer,
     * FeignClientRecognizer, ActuatorEndpointRecognizer).
     *
     * These recognizers inherit the default `false` implementation and do NOT
     * override `matchesClass` (PR1: only gRPC overrides).
     */
    fun testDefaultImplReturnsFalseForNonGrpcRecognizers() = runTest {
        val fixtureClass = findClass("com.itangcent.grpc.model.UserInfo")!!
        val recognizers = CompositeApiClassRecognizer.getInstance(project).recognizers()

        val nonGrpcFrameworkNames = setOf("SpringMVC", "JAX-RS", "Feign", "SpringActuator")
        val nonGrpcRecognizers = recognizers.filter { it.frameworkName in nonGrpcFrameworkNames }
        assertEquals(
            "Should have 4 non-gRPC recognizers (got: ${recognizers.map { it.frameworkName }})",
            4,
            nonGrpcRecognizers.size
        )

        nonGrpcRecognizers.forEach { recognizer ->
            assertEquals(
                "matchesClass should return false (default) for ${recognizer.frameworkName}",
                false,
                recognizer.matchesClass(fixtureClass)
            )
        }
    }

    /**
     * (c) Verifies that the gRPC recognizer's `matchesClass` returns `true`
     * for a class extending `BindableService` (via `EchoServiceImplBase`).
     *
     * EchoServiceImpl extends EchoServiceImplBase which implements BindableService.
     * `matchesClass` must return true — but only AFTER task 8 adds the gRPC
     * override. Until then, the gRPC recognizer inherits the default `false`.
     */
    fun testGrpcRecognizerMatchesClassOnBindableServiceExtender() = runTest {
        val grpcServiceImpl = findClass("com.itangcent.grpc.service.EchoServiceImpl")!!
        val recognizers = CompositeApiClassRecognizer.getInstance(project).recognizers()
        val grpcRecognizer = recognizers.firstOrNull { it.frameworkName == "gRPC" }
        assertNotNull(
            "GrpcServiceRecognizer should be in EP (got: ${recognizers.map { it.frameworkName }})",
            grpcRecognizer
        )

        assertEquals(
            "GrpcServiceRecognizer.matchesClass should return true for EchoServiceImpl",
            true,
            grpcRecognizer!!.matchesClass(grpcServiceImpl)
        )
    }

    /**
     * (c, negative case) Verifies that the gRPC recognizer's `matchesClass`
     * returns `false` for a non-gRPC class (no `BindableService` supertype,
     * no `@GrpcService` meta-annotation).
     *
     * This assertion passes at task 6 (gRPC inherits default `false`).
     */
    fun testGrpcRecognizerDoesNotMatchNonGrpcClass() = runTest {
        val nonGrpcClass = findClass("com.itangcent.grpc.model.UserInfo")!!
        val recognizers = CompositeApiClassRecognizer.getInstance(project).recognizers()
        val grpcRecognizer = recognizers.firstOrNull { it.frameworkName == "gRPC" }
        assertNotNull(
            "GrpcServiceRecognizer should be in EP (got: ${recognizers.map { it.frameworkName }})",
            grpcRecognizer
        )

        assertEquals(
            "GrpcServiceRecognizer.matchesClass should return false for UserInfo",
            false,
            grpcRecognizer!!.matchesClass(nonGrpcClass)
        )
    }
}
