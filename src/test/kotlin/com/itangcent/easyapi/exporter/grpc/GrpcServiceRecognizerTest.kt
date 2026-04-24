package com.itangcent.easyapi.exporter.grpc

import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.helper.UnifiedDocHelper
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

/**
 * Tests for [GrpcServiceRecognizer].
 * 
 * Verifies that the recognizer correctly identifies gRPC service classes by:
 * - Checking for `io.grpc.BindableService` in the class hierarchy
 * - Detecting ImplBase superclass pattern (e.g., `XxxGrpc.XxxImplBase`)
 * - Supporting `@GrpcService` annotation detection (grpc-spring-boot-starter)
 * - Supporting meta-annotation resolution
 * 
 * Recognition strategies (in order):
 * 1. Rule engine override (if configured)
 * 2. Extends BindableService directly or through ImplBase
 * 3. Annotated with @GrpcService or meta-annotations
 * 
 * Test fixtures:
 * - `EchoServiceImpl`: A valid gRPC service extending `EchoServiceGrpc.EchoServiceImplBase`
 * - `UserInfo`: A non-gRPC model class to verify rejection
 */
class GrpcServiceRecognizerTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var recognizer: GrpcServiceRecognizer

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        recognizer = GrpcServiceRecognizer()
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
     * Tests that a class extending XxxImplBase is recognized as a gRPC service.
     * The recognizer walks the superclass hierarchy to find ImplBase pattern.
     */
    fun testRecognizeGrpcServiceByImplBase() = runTest {
        val psiClass = findClass("com.itangcent.grpc.service.EchoServiceImpl")
        assertNotNull("Should find EchoServiceImpl class", psiClass)

        val isGrpc = recognizer.isApiClass(psiClass!!)
        assertTrue("EchoServiceImpl should be recognized as gRPC service", isGrpc)
    }

    /**
     * Tests that non-gRPC classes are not recognized.
     * Classes without BindableService or ImplBase in hierarchy should be rejected.
     */
    fun testNonGrpcClassNotRecognized() = runTest {
        val psiClass = findClass("com.itangcent.grpc.model.UserInfo")
        assertNotNull("Should find UserInfo class", psiClass)

        val isGrpc = recognizer.isApiClass(psiClass!!)
        assertFalse("UserInfo should NOT be recognized as gRPC service", isGrpc)
    }

    /**
     * Tests that the framework name is correctly reported as "gRPC".
     */
    fun testFrameworkName() {
        assertEquals("Framework name should be gRPC", "gRPC", recognizer.frameworkName)
    }

    /**
     * Tests that target annotations include the grpc-spring-boot-starter @GrpcService.
     * This is used for annotation-based indexing and search optimization.
     */
    fun testTargetAnnotations() {
        val annotations = recognizer.targetAnnotations
        assertTrue("Should contain GrpcService annotation", 
            annotations.contains("net.devh.boot.grpc.server.service.GrpcService"))
    }

    /**
     * Tests the static helper method for checking BindableService hierarchy.
     * Should return true for classes extending ImplBase (which implements BindableService).
     */
    fun testExtendsBindableService() = runTest {
        val psiClass = findClass("com.itangcent.grpc.service.EchoServiceImpl")
        assertNotNull("Should find EchoServiceImpl class", psiClass)

        val extendsBindable = GrpcServiceRecognizer.extendsBindableService(psiClass!!)
        assertTrue("EchoServiceImpl should extend BindableService (via ImplBase)", extendsBindable)
    }

    /**
     * Tests that non-gRPC classes don't have BindableService in their hierarchy.
     */
    fun testNonGrpcClassDoesNotExtendBindableService() = runTest {
        val psiClass = findClass("com.itangcent.grpc.model.UserInfo")
        assertNotNull("Should find UserInfo class", psiClass)

        val extendsBindable = GrpcServiceRecognizer.extendsBindableService(psiClass!!)
        assertFalse("UserInfo should NOT extend BindableService", extendsBindable)
    }

    /**
     * Tests the convenience method `isGrpcService` which is equivalent to `isApiClass`.
     * Provides a more semantic name for gRPC-specific checks.
     */
    fun testIsGrpcServiceConvenienceMethod() = runTest {
        val psiClass = findClass("com.itangcent.grpc.service.EchoServiceImpl")
        assertNotNull("Should find EchoServiceImpl class", psiClass)

        val isGrpc = recognizer.isGrpcService(psiClass!!)
        assertTrue("isGrpcService should return true for EchoServiceImpl", isGrpc)
    }
}
