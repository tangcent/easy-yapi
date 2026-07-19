package com.itangcent.easyapi.framework.grpc

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for GrpcTypeParser pure logic methods.
 * Focuses on mapProtobufType and companion constants.
 */
class GrpcTypeParserPureLogicTest {

    private val parser = GrpcTypeParser()

    // ==================== mapProtobufType tests ====================

    @Test
    fun `mapProtobufType maps String to string`() {
        assertEquals("string", parser.mapProtobufType("java.lang.String"))
    }

    @Test
    fun `mapProtobufType maps int to int32`() {
        assertEquals("int32", parser.mapProtobufType("int"))
    }

    @Test
    fun `mapProtobufType maps Integer to int32`() {
        assertEquals("int32", parser.mapProtobufType("java.lang.Integer"))
    }

    @Test
    fun `mapProtobufType maps long to int64`() {
        assertEquals("int64", parser.mapProtobufType("long"))
    }

    @Test
    fun `mapProtobufType maps Long to int64`() {
        assertEquals("int64", parser.mapProtobufType("java.lang.Long"))
    }

    @Test
    fun `mapProtobufType maps float to float`() {
        assertEquals("float", parser.mapProtobufType("float"))
    }

    @Test
    fun `mapProtobufType maps Float to float`() {
        assertEquals("float", parser.mapProtobufType("java.lang.Float"))
    }

    @Test
    fun `mapProtobufType maps double to double`() {
        assertEquals("double", parser.mapProtobufType("double"))
    }

    @Test
    fun `mapProtobufType maps Double to double`() {
        assertEquals("double", parser.mapProtobufType("java.lang.Double"))
    }

    @Test
    fun `mapProtobufType maps boolean to bool`() {
        assertEquals("bool", parser.mapProtobufType("boolean"))
    }

    @Test
    fun `mapProtobufType maps Boolean to bool`() {
        assertEquals("bool", parser.mapProtobufType("java.lang.Boolean"))
    }

    @Test
    fun `mapProtobufType maps ByteString to bytes`() {
        assertEquals("bytes", parser.mapProtobufType("com.google.protobuf.ByteString"))
    }

    @Test
    fun `mapProtobufType maps byte array to bytes`() {
        assertEquals("bytes", parser.mapProtobufType("byte[]"))
    }

    @Test
    fun `mapProtobufType returns unknown type as-is`() {
        assertEquals("com.example.MyMessage", parser.mapProtobufType("com.example.MyMessage"))
    }

    @Test
    fun `mapProtobufType returns empty string as-is`() {
        assertEquals("", parser.mapProtobufType(""))
    }

    @Test
    fun `mapProtobufType returns List type as-is`() {
        assertEquals("java.util.List", parser.mapProtobufType("java.util.List"))
    }

    // ==================== GrpcServiceRecognizer constants tests ====================

    @Test
    fun `GrpcServiceRecognizer has GRPC_SERVICE_ANNOTATIONS`() {
        assertNotNull(GrpcServiceRecognizer.GRPC_SERVICE_ANNOTATIONS)
        assertTrue(GrpcServiceRecognizer.GRPC_SERVICE_ANNOTATIONS.contains("net.devh.boot.grpc.server.service.GrpcService"))
    }

    @Test
    fun `GrpcServiceRecognizer BINDABLE_SERVICE_FQN is correct`() {
        assertEquals("io.grpc.BindableService", GrpcServiceRecognizer.BINDABLE_SERVICE_FQN)
    }

    // ==================== ProtobufField classification logic via isCountGetter/isBytesGetter ====================
    // These are private but tested indirectly through parseMessageType with mocks.
    // The mapProtobufType method is the main pure logic entry point.

    @Test
    fun `mapProtobufType handles all scalar types`() {
        val expectedMappings = mapOf(
            "java.lang.String" to "string",
            "int" to "int32",
            "java.lang.Integer" to "int32",
            "long" to "int64",
            "java.lang.Long" to "int64",
            "float" to "float",
            "java.lang.Float" to "float",
            "double" to "double",
            "java.lang.Double" to "double",
            "boolean" to "bool",
            "java.lang.Boolean" to "bool",
            "com.google.protobuf.ByteString" to "bytes",
            "byte[]" to "bytes"
        )
        for ((input, expected) in expectedMappings) {
            assertEquals("Failed for $input", expected, parser.mapProtobufType(input))
        }
    }
}
