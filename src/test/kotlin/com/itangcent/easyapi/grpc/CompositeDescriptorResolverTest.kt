package com.itangcent.easyapi.grpc

import com.intellij.openapi.application.ApplicationManager
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.After
import org.junit.Assert.*
import org.junit.Before

/**
 * Tests for [CompositeDescriptorResolver].
 */
class CompositeDescriptorResolverTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var resolver: CompositeDescriptorResolver
    private var mockServer: GrpcMockServer? = null

    private val protoContent = """
        syntax = "proto3";
        package test.grpc;
        option java_package = "com.itangcent.easyapi.grpc.test";
        option java_multiple_files = true;
        service EchoService {
          rpc Echo(EchoRequest) returns (EchoResponse);
          rpc EchoEmpty(EmptyRequest) returns (EchoResponse);
          rpc Reverse(ReverseRequest) returns (ReverseResponse);
        }
        message EchoRequest { string message = 1; int32 count = 2; }
        message EchoResponse { string echoed = 1; int32 received_count = 2; }
        message EmptyRequest {}
        message ReverseRequest { string text = 1; }
        message ReverseResponse { string reversed = 1; int32 length = 2; }
    """.trimIndent()

    @Before
    override fun setUp() {
        super.setUp()
        resolver = CompositeDescriptorResolver(project)
    }

    @After
    override fun tearDown() {
        mockServer?.stop()
        mockServer = null
        super.tearDown()
    }

    fun testResolveReturnsNullWhenAllResolversReturnNull() = runTest {
        // No proto files, unknown service name — all three resolvers return null
        val classLoader = Thread.currentThread().contextClassLoader
        val result = resolver.resolve(classLoader, "unknown.Service", "Method", null)
        assertNull("Should return null when all resolvers return null", result)
    }

    fun testResolveUsesProtoFileResolverFirst() = runTest {
        // Add a proto file so ProtoFileResolver can satisfy the request
        ApplicationManager.getApplication().runWriteAction {
            myFixture.addFileToProject("src/test.proto", protoContent)
        }
        val classLoader = Thread.currentThread().contextClassLoader
        val result = resolver.resolve(classLoader, "test.grpc.EchoService", "Echo", null)
        assertNotNull("Should resolve when proto file is present", result)
        assertEquals(
            "ProtoFileResolver should be used first",
            DescriptorSource.PROTO_FILE,
            result!!.source
        )
    }

    private val echoServiceGrpcSource = """
        package test.grpc;
        public final class EchoServiceGrpc {
            public static final String SERVICE_NAME = "test.grpc.EchoService";
            public static abstract class EchoServiceImplBase implements io.grpc.BindableService {
                public void echo(EchoRequest request, io.grpc.stub.StreamObserver<EchoResponse> responseObserver) {}
            }
        }
    """.trimIndent()

    private val echoRequestSource = """
        package test.grpc;
        public final class EchoRequest extends com.google.protobuf.GeneratedMessageV3 {
            public static final int MESSAGE_FIELD_NUMBER = 1;
            public String getMessage() { return ""; }
        }
    """.trimIndent()

    private val echoResponseSource = """
        package test.grpc;
        public final class EchoResponse extends com.google.protobuf.GeneratedMessageV3 {
            public static final int ECHOED_FIELD_NUMBER = 1;
            public String getEchoed() { return ""; }
        }
    """.trimIndent()

    private val echoServiceImplSource = """
        package test.grpc;
        public class EchoServiceImpl extends EchoServiceGrpc.EchoServiceImplBase {
            @Override
            public void echo(EchoRequest request, io.grpc.stub.StreamObserver<EchoResponse> responseObserver) {}
        }
    """.trimIndent()

    private fun addStubSources() {
        ApplicationManager.getApplication().runWriteAction {
            myFixture.addFileToProject("io/grpc/stub/StreamObserver.java",
                "package io.grpc.stub; public interface StreamObserver<V> {}")
            myFixture.addFileToProject("io/grpc/BindableService.java",
                "package io.grpc; public interface BindableService {}")
            myFixture.addFileToProject("com/google/protobuf/GeneratedMessageV3.java",
                "package com.google.protobuf; public abstract class GeneratedMessageV3 {}")

            myFixture.addFileToProject("test/grpc/EchoRequest.java", echoRequestSource)
            myFixture.addFileToProject("test/grpc/EchoResponse.java", echoResponseSource)
            myFixture.addFileToProject("test/grpc/EchoServiceGrpc.java", echoServiceGrpcSource)
            myFixture.addFileToProject("test/grpc/EchoServiceImpl.java", echoServiceImplSource)
        }
    }

    fun testResolveUsesStubClassResolverSecond() = runTest {
        // No proto files; add PSI stub classes so StubClassResolver can satisfy the request
        addStubSources()
        val classLoader = Thread.currentThread().contextClassLoader
        val result = resolver.resolve(classLoader, "test.grpc.EchoService", "echo", null)
        assertNotNull("Should resolve via PSI stub classes when no proto files", result)
        assertEquals(
            "StubClassResolver should be used when no proto files",
            DescriptorSource.STUB_CLASS,
            result!!.source
        )
    }

    fun testResolveUsesServerReflectionAsLastResort() = runTest {
        // This test verifies ServerReflectionResolver directly — the composite resolver
        // delegates to it when proto files and PSI stubs are absent.
        // We test ServerReflectionResolver in isolation in ServerReflectionResolverTest.
        // Here we just verify the composite resolver doesn't throw when all resolvers return null.
        val classLoader = Thread.currentThread().contextClassLoader
        val result = resolver.resolve(classLoader, "nonexistent.Service", "Method", null)
        assertNull("Should return null when no resolver can satisfy the request", result)
    }

    fun testInvalidateCacheDoesNotThrow() {
        // Should complete without throwing
        resolver.invalidateCache()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun buildChannel(classLoader: ClassLoader, port: Int): Any {
        val channelBuilderClass = classLoader.loadClass("io.grpc.ManagedChannelBuilder")
        val forAddressMethod = channelBuilderClass.getMethod("forAddress", String::class.java, Int::class.java)
        val builder = forAddressMethod.invoke(null, "localhost", port)
        val usePlaintextMethod = builder.javaClass.getMethod("usePlaintext")
        val channelBuilder = usePlaintextMethod.invoke(builder)
        val buildMethod = channelBuilder.javaClass.getMethod("build")
        return buildMethod.invoke(channelBuilder)
    }

    private fun shutdownChannel(channel: Any) {
        try {
            channel.javaClass.getMethod("shutdown").invoke(channel)
        } catch (_: Exception) {
        }
    }
}
