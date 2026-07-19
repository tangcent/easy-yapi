package com.itangcent.easyapi.framework.grpc

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for GrpcTypeParser pure logic methods.
 * PSI-dependent methods are tested via integration tests.
 */
class GrpcTypeParserLogicTest {

    private val parser = GrpcTypeParser()

    // ==================== mapProtobufType ====================

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
    fun `mapProtobufType returns custom type as-is`() {
        assertEquals("CustomType", parser.mapProtobufType("CustomType"))
    }
}
