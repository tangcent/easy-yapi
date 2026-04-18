package com.itangcent.easyapi.exporter.grpc

import org.junit.Assert.*
import org.junit.Test

class GrpcTypeParserPureTest {

    private val parser = GrpcTypeParser()

    @Test
    fun testMapProtobufTypeString() {
        assertEquals("string", parser.mapProtobufType("java.lang.String"))
    }

    @Test
    fun testMapProtobufTypeInt() {
        assertEquals("int32", parser.mapProtobufType("int"))
    }

    @Test
    fun testMapProtobufTypeInteger() {
        assertEquals("int32", parser.mapProtobufType("java.lang.Integer"))
    }

    @Test
    fun testMapProtobufTypeLong() {
        assertEquals("int64", parser.mapProtobufType("long"))
    }

    @Test
    fun testMapProtobufTypeFloat() {
        assertEquals("float", parser.mapProtobufType("float"))
    }

    @Test
    fun testMapProtobufTypeDouble() {
        assertEquals("double", parser.mapProtobufType("double"))
    }

    @Test
    fun testMapProtobufTypeBoolean() {
        assertEquals("bool", parser.mapProtobufType("boolean"))
    }

    @Test
    fun testMapProtobufTypeBoxedBoolean() {
        assertEquals("bool", parser.mapProtobufType("java.lang.Boolean"))
    }

    @Test
    fun testMapProtobufTypeByteString() {
        assertEquals("bytes", parser.mapProtobufType("com.google.protobuf.ByteString"))
    }

    @Test
    fun testMapProtobufTypeByteArray() {
        assertEquals("bytes", parser.mapProtobufType("byte[]"))
    }

    @Test
    fun testMapProtobufTypeBoxedLong() {
        assertEquals("int64", parser.mapProtobufType("java.lang.Long"))
    }

    @Test
    fun testMapProtobufTypeBoxedFloat() {
        assertEquals("float", parser.mapProtobufType("java.lang.Float"))
    }

    @Test
    fun testMapProtobufTypeBoxedDouble() {
        assertEquals("double", parser.mapProtobufType("java.lang.Double"))
    }

    @Test
    fun testMapProtobufTypeUnknown() {
        assertEquals("com.example.CustomType", parser.mapProtobufType("com.example.CustomType"))
    }

    @Test
    fun testMapProtobufTypeEmptyString() {
        assertEquals("", parser.mapProtobufType(""))
    }
}
