package com.itangcent.easyapi.exporter.grpc

import com.itangcent.easyapi.exporter.model.GrpcStreamingType
import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.helper.StandardDocHelper
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import io.grpc.stub.annotations.RpcMethod
import java.io.File

/**
 * Tests for [GrpcMethodResolver].
 * 
 * Verifies that the resolver correctly:
 * - Discovers RPC methods from gRPC service implementation classes
 * - Detects streaming types from method signatures:
 *   - UNARY: `(Req, StreamObserver<Resp>) -> void`
 *   - CLIENT_STREAMING: `(StreamObserver<Resp>) -> StreamObserver<Req>`
 * - Extracts service names from ImplBase superclass naming convention
 * - Extracts package names from the class hierarchy
 * - Resolves request/response PsiClass types
 * - Filters out non-RPC methods (Object methods, lifecycle methods)
 * - Extracts method info from @RpcMethod annotation on static methods
 * 
 * Test fixtures:
 * - `EchoServiceImpl`: Contains both unary (`echo`) and client-streaming (`echoStream`) methods
 * - `UserInfo`: Non-gRPC class to verify empty result
 * - Generated `EchoServiceGrpc`: Uses @RpcMethod annotations on static methods
 */
class GrpcMethodResolverTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var resolver: GrpcMethodResolver

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        loadGeneratedGrpcFiles()
        resolver = GrpcMethodResolver.getInstance(project)
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

    private fun loadGeneratedGrpcFiles() {
        loadSource(RpcMethod::class.java)
        
        val generatedGrpcDir = File("build/generated/source/proto/test/grpc/com/itangcent/easyapi/grpc/test")
        if (generatedGrpcDir.exists()) {
            generatedGrpcDir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".java")) {
                    val content = file.readText()
                    val path = "com/itangcent/easyapi/grpc/test/${file.name}"
                    loadFile(path, content)
                }
            }
        }
        
        val generatedJavaDir = File("build/generated/source/proto/test/java/com/itangcent/easyapi/grpc/test")
        if (generatedJavaDir.exists()) {
            generatedJavaDir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".java")) {
                    val content = file.readText()
                    val path = "com/itangcent/easyapi/grpc/test/${file.name}"
                    loadFile(path, content)
                }
            }
        }
    }

    override fun createConfigReader() = TestConfigReader.EMPTY

    override fun customizeContext(builder: com.itangcent.easyapi.core.context.ActionContextBuilder) {
        builder.bind(DocHelper::class, StandardDocHelper())
    }

    /**
     * Tests that RPC methods are resolved from a gRPC service implementation.
     * Should find all public non-static methods matching RPC signatures.
     */
    fun testResolveRpcMethodsFromGrpcService() = runTest {
        val psiClass = findClass("com.itangcent.grpc.service.EchoServiceImpl")
        assertNotNull("Should find EchoServiceImpl class", psiClass)

        val methods = resolver.resolveRpcMethods(psiClass!!)
        assertTrue("Should resolve at least one RPC method", methods.isNotEmpty())
    }

    /**
     * Tests resolution of a unary RPC method.
     * 
     * Unary methods have signature: `void method(Request req, StreamObserver<Response> observer)`
     */
    fun testResolveUnaryMethod() = runTest {
        val psiClass = findClass("com.itangcent.grpc.service.EchoServiceImpl")
        assertNotNull("Should find EchoServiceImpl class", psiClass)

        val methods = resolver.resolveRpcMethods(psiClass!!)
        val echoMethod = methods.find { it.methodName == "echo" }

        assertNotNull("Should find echo method", echoMethod)
        assertEquals("Streaming type should be UNARY", GrpcStreamingType.UNARY, echoMethod!!.streamingType)
        assertEquals("Method name should be echo", "echo", echoMethod.methodName)
    }

    /**
     * Tests resolution of a client-streaming RPC method.
     * 
     * Client-streaming methods have signature:
     * `StreamObserver<Request> method(StreamObserver<Response> observer)`
     */
    fun testResolveClientStreamingMethod() = runTest {
        val psiClass = findClass("com.itangcent.grpc.service.EchoServiceImpl")
        assertNotNull("Should find EchoServiceImpl class", psiClass)

        val methods = resolver.resolveRpcMethods(psiClass!!)
        val streamMethod = methods.find { it.methodName == "echoStream" }

        assertNotNull("Should find echoStream method", streamMethod)
        assertEquals("Streaming type should be CLIENT_STREAMING", 
            GrpcStreamingType.CLIENT_STREAMING, streamMethod!!.streamingType)
    }

    /**
     * Tests extraction of service name from ImplBase superclass.
     * 
     * For `EchoServiceImpl extends EchoServiceGrpc.EchoServiceImplBase`,
     * the service name should be "EchoService" (ImplBase suffix removed).
     */
    fun testExtractServiceName() = runTest {
        val psiClass = findClass("com.itangcent.grpc.service.EchoServiceImpl")
        assertNotNull("Should find EchoServiceImpl class", psiClass)

        val serviceName = resolver.extractServiceName(psiClass!!)
        assertEquals("Service name should be EchoService", "EchoService", serviceName)
    }

    /**
     * Tests extraction of package name from the class hierarchy.
     * 
     * The package is extracted from the ImplBase's containing class (XxxGrpc),
     * which represents the protobuf package.
     */
    fun testExtractPackageName() = runTest {
        val psiClass = findClass("com.itangcent.grpc.service.EchoServiceImpl")
        assertNotNull("Should find EchoServiceImpl class", psiClass)

        val packageName = resolver.extractPackageName(psiClass!!)
        assertEquals("Package name should be com.itangcent.grpc", "com.itangcent.grpc", packageName)
    }

    /**
     * Tests that the full gRPC path is built correctly.
     * Format: `/{package}.{ServiceName}/{methodName}`
     */
    fun testBuildFullPath() = runTest {
        val psiClass = findClass("com.itangcent.grpc.service.EchoServiceImpl")
        assertNotNull("Should find EchoServiceImpl class", psiClass)

        val methods = resolver.resolveRpcMethods(psiClass!!)
        val echoMethod = methods.find { it.methodName == "echo" }

        assertNotNull("Should find echo method", echoMethod)
        assertEquals("Full path should be correct", 
            "/com.itangcent.grpc.EchoService/echo", echoMethod!!.fullPath)
    }

    /**
     * Tests that the request type PsiClass is resolved from method parameters.
     * For unary methods, this is the first parameter type.
     */
    fun testResolveRequestType() = runTest {
        val psiClass = findClass("com.itangcent.grpc.service.EchoServiceImpl")
        assertNotNull("Should find EchoServiceImpl class", psiClass)

        val methods = resolver.resolveRpcMethods(psiClass!!)
        val echoMethod = methods.find { it.methodName == "echo" }

        assertNotNull("Should find echo method", echoMethod)
        assertNotNull("Request type should be resolved", echoMethod!!.requestType)
        assertEquals("Request type should be EchoRequest", 
            "com.itangcent.grpc.EchoRequest", echoMethod.requestType!!.qualifiedName)
    }

    /**
     * Tests that the response type PsiClass is resolved from StreamObserver type argument.
     * Extracted from `StreamObserver<ResponseType>` parameter.
     */
    fun testResolveResponseType() = runTest {
        val psiClass = findClass("com.itangcent.grpc.service.EchoServiceImpl")
        assertNotNull("Should find EchoServiceImpl class", psiClass)

        val methods = resolver.resolveRpcMethods(psiClass!!)
        val echoMethod = methods.find { it.methodName == "echo" }

        assertNotNull("Should find echo method", echoMethod)
        assertNotNull("Response type should be resolved", echoMethod!!.responseType)
        assertEquals("Response type should be EchoResponse", 
            "com.itangcent.grpc.EchoResponse", echoMethod.responseType!!.qualifiedName)
    }

    /**
     * Tests that non-gRPC classes return an empty list.
     * Classes without RPC-style methods should produce no results.
     */
    fun testNonGrpcClassReturnsEmpty() = runTest {
        val psiClass = findClass("com.itangcent.grpc.model.UserInfo")
        assertNotNull("Should find UserInfo class", psiClass)

        val methods = resolver.resolveRpcMethods(psiClass!!)
        assertTrue("Non-gRPC class should return empty methods", methods.isEmpty())
    }

    /**
     * Tests streaming type detection for a unary method signature.
     * Pattern: `(Req, StreamObserver<Resp>) -> void`
     */
    fun testResolveStreamingTypeForUnary() = runTest {
        val psiClass = findClass("com.itangcent.grpc.service.EchoServiceImpl")
        assertNotNull("Should find EchoServiceImpl class", psiClass)

        val echoMethod = psiClass!!.findMethodsByName("echo", false).firstOrNull()
        assertNotNull("Should find echo method", echoMethod)

        val streamingType = resolver.resolveStreamingType(echoMethod as com.intellij.psi.PsiMethod)
        assertEquals("Streaming type should be UNARY", GrpcStreamingType.UNARY, streamingType)
    }

    /**
     * Tests streaming type detection for a client-streaming method signature.
     * Pattern: `(StreamObserver<Resp>) -> StreamObserver<Req>`
     */
    fun testResolveStreamingTypeForClientStreaming() = runTest {
        val psiClass = findClass("com.itangcent.grpc.service.EchoServiceImpl")
        assertNotNull("Should find EchoServiceImpl class", psiClass)

        val streamMethod = psiClass!!.findMethodsByName("echoStream", false).firstOrNull()
        assertNotNull("Should find echoStream method", streamMethod)

        val streamingType = resolver.resolveStreamingType(streamMethod as com.intellij.psi.PsiMethod)
        assertEquals("Streaming type should be CLIENT_STREAMING", 
            GrpcStreamingType.CLIENT_STREAMING, streamingType)
    }

    /**
     * Tests that each GrpcMethodInfo contains a reference to the original PsiMethod.
     * This is needed for navigation and further analysis.
     */
    fun testMethodInfoContainsPsiMethod() = runTest {
        val psiClass = findClass("com.itangcent.grpc.service.EchoServiceImpl")
        assertNotNull("Should find EchoServiceImpl class", psiClass)

        val methods = resolver.resolveRpcMethods(psiClass!!)
        assertTrue("Should have methods", methods.isNotEmpty())

        for (methodInfo in methods) {
            assertNotNull("Each GrpcMethodInfo should have psiMethod", methodInfo.psiMethod)
            assertEquals("psiMethod name should match methodName", 
                methodInfo.methodName, methodInfo.psiMethod.name)
        }
    }

    // ── @RpcMethod Annotation Tests ─────────────────────────────────────

    /**
     * Tests that static methods with @RpcMethod annotation are resolved
     * from generated gRPC classes.
     * 
     * Uses the auto-generated EchoServiceGrpc from test_service.proto.
     */
    fun testResolveFromRpcMethodAnnotation() = runTest {
        val psiClass = findClass("com.itangcent.easyapi.grpc.test.EchoServiceGrpc")
        assertNotNull("Should find EchoServiceGrpc class", psiClass)

        val methods = resolver.resolveRpcMethods(psiClass!!)
        assertEquals("Should resolve 3 methods from @RpcMethod annotation", 3, methods.size)
    }

    /**
     * Tests extraction of UNARY streaming type from @RpcMethod annotation.
     */
    fun testResolveUnaryFromAnnotation() = runTest {
        val psiClass = findClass("com.itangcent.easyapi.grpc.test.EchoServiceGrpc")
        assertNotNull("Should find EchoServiceGrpc class", psiClass)

        val methods = resolver.resolveRpcMethods(psiClass!!)
        val echoMethod = methods.find { it.methodName == "Echo" }

        assertNotNull("Should find Echo method", echoMethod)
        assertEquals("Streaming type should be UNARY", GrpcStreamingType.UNARY, echoMethod!!.streamingType)
    }

    /**
     * Tests that service name and method name are extracted from fullMethodName.
     * Format: "package.ServiceName/MethodName"
     */
    fun testParseFullMethodNameFromAnnotation() = runTest {
        val psiClass = findClass("com.itangcent.easyapi.grpc.test.EchoServiceGrpc")
        assertNotNull("Should find EchoServiceGrpc class", psiClass)

        val methods = resolver.resolveRpcMethods(psiClass!!)
        val echoMethod = methods.find { it.methodName == "Echo" }

        assertNotNull("Should find Echo method", echoMethod)
        assertEquals("Service name should be EchoService", "EchoService", echoMethod!!.serviceName)
        assertEquals("Method name should be Echo", "Echo", echoMethod.methodName)
    }

    /**
     * Tests that request and response types are extracted from annotation.
     */
    fun testExtractTypesFromAnnotation() = runTest {
        val psiClass = findClass("com.itangcent.easyapi.grpc.test.EchoServiceGrpc")
        assertNotNull("Should find EchoServiceGrpc class", psiClass)

        val methods = resolver.resolveRpcMethods(psiClass!!)
        val echoMethod = methods.find { it.methodName == "Echo" }

        assertNotNull("Should find Echo method", echoMethod)
        assertNotNull("Request type should be resolved", echoMethod!!.requestType)
        assertNotNull("Response type should be resolved", echoMethod.responseType)
        assertEquals("Request type should be EchoRequest", 
            "com.itangcent.easyapi.grpc.test.EchoRequest", echoMethod.requestType!!.qualifiedName)
        assertEquals("Response type should be EchoResponse", 
            "com.itangcent.easyapi.grpc.test.EchoResponse", echoMethod.responseType!!.qualifiedName)
    }

    /**
     * Tests that full path is built correctly from annotation data.
     */
    fun testBuildFullPathFromAnnotation() = runTest {
        val psiClass = findClass("com.itangcent.easyapi.grpc.test.EchoServiceGrpc")
        assertNotNull("Should find EchoServiceGrpc class", psiClass)

        val methods = resolver.resolveRpcMethods(psiClass!!)
        val echoMethod = methods.find { it.methodName == "Echo" }

        assertNotNull("Should find Echo method", echoMethod)
        assertEquals("Full path should be correct", 
            "/com.itangcent.easyapi.grpc.test.EchoService/Echo", echoMethod!!.fullPath)
    }

    /**
     * Tests that static methods without @RpcMethod annotation are excluded.
     */
    fun testStaticMethodsWithoutAnnotationExcluded() = runTest {
        val psiClass = findClass("com.itangcent.easyapi.grpc.test.EchoServiceGrpc")
        assertNotNull("Should find EchoServiceGrpc class", psiClass)

        val methods = resolver.resolveRpcMethods(psiClass!!)
        val getServiceDescriptor = methods.find { it.methodName == "getServiceDescriptor" }

        assertNull("getServiceDescriptor should not be included", getServiceDescriptor)
    }
}
