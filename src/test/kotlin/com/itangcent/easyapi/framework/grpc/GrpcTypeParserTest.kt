package com.itangcent.easyapi.framework.grpc

import org.junit.Assert.*
import org.junit.Test

class GrpcTypeParserTest {

    private val parser = GrpcTypeParser()

    @Test
    fun testMapProtobufType_string() {
        assertEquals("string", parser.mapProtobufType("java.lang.String"))
    }

    @Test
    fun testMapProtobufType_int() {
        assertEquals("int32", parser.mapProtobufType("int"))
        assertEquals("int32", parser.mapProtobufType("java.lang.Integer"))
    }

    @Test
    fun testMapProtobufType_long() {
        assertEquals("int64", parser.mapProtobufType("long"))
        assertEquals("int64", parser.mapProtobufType("java.lang.Long"))
    }

    @Test
    fun testMapProtobufType_float() {
        assertEquals("float", parser.mapProtobufType("float"))
        assertEquals("float", parser.mapProtobufType("java.lang.Float"))
    }

    @Test
    fun testMapProtobufType_double() {
        assertEquals("double", parser.mapProtobufType("double"))
        assertEquals("double", parser.mapProtobufType("java.lang.Double"))
    }

    @Test
    fun testMapProtobufType_boolean() {
        assertEquals("bool", parser.mapProtobufType("boolean"))
        assertEquals("bool", parser.mapProtobufType("java.lang.Boolean"))
    }

    @Test
    fun testMapProtobufType_bytes() {
        assertEquals("bytes", parser.mapProtobufType("com.google.protobuf.ByteString"))
        assertEquals("bytes", parser.mapProtobufType("byte[]"))
    }

    @Test
    fun testMapProtobufType_unknownType() {
        assertEquals("com.example.CustomMessage", parser.mapProtobufType("com.example.CustomMessage"))
    }

    @Test
    fun testMapProtobufType_simpleUnknown() {
        assertEquals("Object", parser.mapProtobufType("Object"))
    }
}
