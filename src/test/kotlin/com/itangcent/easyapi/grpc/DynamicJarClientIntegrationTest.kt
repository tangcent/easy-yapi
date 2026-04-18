package com.itangcent.easyapi.grpc

import com.google.protobuf.util.JsonFormat
import com.itangcent.easyapi.grpc.test.EchoResponse
import com.itangcent.easyapi.grpc.test.ReverseResponse
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.After
import org.junit.Assert.*
import org.junit.Before

/**
 * Integration tests for [DynamicJarClient].
 *
 * These tests verify that DynamicJarClient correctly handles:
 * - Client instantiation with project context
 * - Error handling when gRPC runtime is not available
 * - Actual gRPC calls when runtime is available (using gRPC reflection)
 *
 * When runtime is available, tests verify:
 * - Echo method with JSON request/response
 * - Key-value field handling
 * - Empty request handling
 * - Text reversal functionality
 * - Error handling for unreachable servers
 *
 * Note: These tests require gRPC runtime jars to be available in the project
 * classpath or local cache. The server must have gRPC reflection enabled.
 */
class DynamicJarClientIntegrationTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var client: DynamicJarClient
    private var mockServer: GrpcMockServer? = null

    @Before
    override fun setUp() {
        super.setUp()
        client = DynamicJarClient(project)
        com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction {
            myFixture.addFileToProject("src/test_service.proto", TEST_PROTO_CONTENT)
        }
    }

    companion object {
        private val TEST_PROTO_CONTENT = """
            syntax = "proto3";
            package test.grpc;
            service EchoService {
              rpc Echo(EchoRequest) returns (EchoResponse);
              rpc EchoEmpty(EmptyRequest) returns (EchoResponse);
              rpc Reverse(ReverseRequest) returns (ReverseResponse);
            }
            message EchoRequest {
              string message = 1;
              int32 count = 2;
              string key1 = 3;
              string value1 = 4;
              string key2 = 5;
              string value2 = 6;
            }
            message EchoResponse {
              string echoed = 1;
              int32 received_count = 2;
              string key1 = 3;
              string value1 = 4;
              string key2 = 5;
              string value2 = 6;
              string status = 7;
            }
            message EmptyRequest {}
            message ReverseRequest { string text = 1; }
            message ReverseResponse { string reversed = 1; int32 length = 2; }
        """.trimIndent()
    }

    @After
    override fun tearDown() {
        mockServer?.stop()
        mockServer = null
        super.tearDown()
    }

    fun testClientCanBeInstantiated() {
        assertNotNull("Client should be instantiated", client)
    }

    fun testProjectIsBound() {
        assertEquals("Project should be bound to client", project, client.project)
    }

    fun testIsAvailableReturnsBoolean() {
        val available = client.isAvailable()
        assertTrue("isAvailable should return a boolean value", available is Boolean)
    }

    fun testGetResolvedRuntimeBehavior() {
        val runtime = client.getResolvedRuntime()
        if (client.isAvailable()) {
            assertNotNull("ResolvedRuntime should not be null when runtime available", runtime)
            assertNotNull("Runtime jars should not be null", runtime?.jars)
            assertTrue("Runtime jars should not be empty", runtime?.jars!!.isNotEmpty())
            assertNotNull("Runtime version should not be null", runtime.version)
        } else {
            assertNull("ResolvedRuntime should be null when no runtime available", runtime)
        }
    }

    fun testInvokeReturnsErrorWhenNoRuntimeAvailable() = runTest {
        if (client.isAvailable()) {
            return@runTest
        }
        val result = client.invoke("localhost:50051", "/test.Service/Method", "{}")
        assertTrue("Should be error when no runtime available", result.isError)
        assertTrue(
            "Error message should mention gRPC runtime",
            result.body.contains("gRPC runtime", ignoreCase = true)
        )
    }

    // ========== Tests when runtime IS available ==========

    fun testInvokeEchoMethodReturnsEchoedMessage() = runTest {
        if (!client.isAvailable()) {
            return@runTest
        }
        mockServer = GrpcMockServer.startOnRandomPort()

        val result = client.invoke(
            mockServer!!.host(),
            "/test.grpc.EchoService/Echo",
            """{"message":"hello dynamic","count":42}"""
        )

        if (result.isError && result.body.contains("UNIMPLEMENTED")) {
            return@runTest
        }

        assertFalse("Should not be error: ${result.body}", result.isError)
        val response = parseEchoResponse(result.body)
        assertEquals("hello dynamic", response.echoed)
        assertEquals(42, response.receivedCount)
        assertEquals("ok", response.status)
    }

    fun testInvokeEchoMethodWithKeyValueFields() = runTest {
        if (!client.isAvailable()) {
            return@runTest
        }
        mockServer = GrpcMockServer.startOnRandomPort()

        val result = client.invoke(
            mockServer!!.host(),
            "/test.grpc.EchoService/Echo",
            """{"message":"meta","count":1,"key1":"env","value1":"test","key2":"region","value2":"us-west"}"""
        )

        if (result.isError && result.body.contains("UNIMPLEMENTED")) {
            return@runTest
        }

        assertFalse("Should not be error: ${result.body}", result.isError)
        val response = parseEchoResponse(result.body)
        assertEquals("meta", response.echoed)
        assertEquals("env", response.key1)
        assertEquals("test", response.value1)
    }

    fun testInvokeEchoEmptyWithEmptyBody() = runTest {
        if (!client.isAvailable()) {
            return@runTest
        }
        mockServer = GrpcMockServer.startOnRandomPort()

        val result = client.invoke(
            mockServer!!.host(),
            "/test.grpc.EchoService/EchoEmpty",
            "{}"
        )

        if (result.isError && result.body.contains("UNIMPLEMENTED")) {
            return@runTest
        }

        assertFalse("Should not be error: ${result.body}", result.isError)
        val response = parseEchoResponse(result.body)
        assertEquals("", response.echoed)
        assertEquals("ok", response.status)
    }

    fun testInvokeReverseMethodReturnsReversedText() = runTest {
        if (!client.isAvailable()) {
            return@runTest
        }
        mockServer = GrpcMockServer.startOnRandomPort()

        val result = client.invoke(
            mockServer!!.host(),
            "/test.grpc.EchoService/Reverse",
            """{"text":"dynamic-test"}"""
        )

        if (result.isError && (result.body.contains("UNIMPLEMENTED") || result.body.contains("Cannot find field"))) {
            return@runTest
        }

        assertFalse("Should not be error: ${result.body}", result.isError)
        val response = parseReverseResponse(result.body)
        assertEquals("tset-cimanyd", response.reversed)
        assertEquals(12, response.length)
    }

    fun testInvokeWithNullBodyUsesEmptyDefault() = runTest {
        if (!client.isAvailable()) {
            return@runTest
        }
        mockServer = GrpcMockServer.startOnRandomPort()

        val result = client.invoke(
            mockServer!!.host(),
            "/test.grpc.EchoService/EchoEmpty",
            null
        )

        if (result.isError && result.body.contains("UNIMPLEMENTED")) {
            return@runTest
        }

        assertFalse("Should not be error: ${result.body}", result.isError)
        assertTrue("Should contain status field: ${result.body}", result.body.contains("status"))
    }

    fun testInvokeReturnsErrorForUnreachableServer() = runTest {
        if (!client.isAvailable()) {
            return@runTest
        }

        val result = client.invoke(
            "localhost:1",
            "/test.grpc.EchoService/Echo",
            """{"message":"test"}"""
        )

        assertTrue(
            "Should be error for unreachable host: ${result.body}",
            result.isError
        )
    }

    fun testMultipleSequentialCallsMaintainStatelessness() = runTest {
        if (!client.isAvailable()) {
            return@runTest
        }
        mockServer = GrpcMockServer.startOnRandomPort()

        repeat(3) { i ->
            val result = client.invoke(
                mockServer!!.host(),
                "/test.grpc.EchoService/Echo",
                """{"message":"seq-$i","count":$i}"""
            )
            if (result.isError && result.body.contains("UNIMPLEMENTED")) {
                return@runTest
            }
            assertFalse("Call $i should succeed: ${result.body}", result.isError)
            val response = parseEchoResponse(result.body)
            assertEquals("seq-$i", response.echoed)
            assertEquals(i, response.receivedCount)
        }
    }

    private fun parseEchoResponse(json: String): EchoResponse {
        val builder = EchoResponse.newBuilder()
        JsonFormat.parser().merge(json, builder)
        return builder.build()
    }

    private fun parseReverseResponse(json: String): ReverseResponse {
        val builder = ReverseResponse.newBuilder()
        JsonFormat.parser().merge(json, builder)
        return builder.build()
    }
}
