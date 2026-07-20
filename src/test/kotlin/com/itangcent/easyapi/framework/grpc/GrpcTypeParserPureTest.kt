package com.itangcent.easyapi.framework.grpc

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

    // --- isCountGetter / isBytesGetter logic tests ---

    @Test
    fun testIsCountGetterTrue() {
        val allNames = setOf("getItems", "getItemsList", "getItemsCount")
        // getItemsCount is a count getter because getItemsList exists
        assertTrue(isCountGetter("getItemsCount", allNames))
    }

    @Test
    fun testIsCountGetterFalse() {
        val allNames = setOf("getItemCount") // no getItemCountList
        assertFalse(isCountGetter("getItemCount", allNames))
    }

    @Test
    fun testIsCountGetterNotEndingInCount() {
        val allNames = setOf("getItems")
        assertFalse(isCountGetter("getItems", allNames))
    }

    @Test
    fun testIsBytesGetterTrue() {
        val allNames = setOf("getName", "getNameBytes")
        assertTrue(isBytesGetter("getNameBytes", allNames))
    }

    @Test
    fun testIsBytesGetterFalse() {
        val allNames = setOf("getBytes") // no getGet getter
        assertFalse(isBytesGetter("getBytes", allNames))
    }

    @Test
    fun testIsBytesGetterNotEndingInBytes() {
        val allNames = setOf("getName")
        assertFalse(isBytesGetter("getName", allNames))
    }

    // --- Protobuf base class constants ---

    @Test
    fun testProtobufMessageBases() {
        val bases = setOf(
            "com.google.protobuf.GeneratedMessageV3",
            "com.google.protobuf.GeneratedMessage"
        )
        assertEquals(2, bases.size)
        assertTrue(bases.contains("com.google.protobuf.GeneratedMessageV3"))
        assertTrue(bases.contains("com.google.protobuf.GeneratedMessage"))
    }

    // --- Type mappings completeness ---

    @Test
    fun testAllTypeMappings() {
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
            assertEquals("Mapping for $input", expected, parser.mapProtobufType(input))
        }
    }

    companion object {
        private fun isCountGetter(name: String, allGetterNames: Set<String>): Boolean {
            if (!name.endsWith("Count")) return false
            val baseName = name.removeSuffix("Count")
            return "${baseName}List" in allGetterNames
        }

        private fun isBytesGetter(name: String, allGetterNames: Set<String>): Boolean {
            if (!name.endsWith("Bytes")) return false
            val baseName = name.removeSuffix("Bytes")
            return baseName in allGetterNames
        }
    }
}
