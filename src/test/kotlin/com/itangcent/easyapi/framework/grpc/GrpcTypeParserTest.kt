package com.itangcent.easyapi.framework.grpc

import com.itangcent.easyapi.core.grpc.ProtoUtils
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for [GrpcTypeParser]'s pure logic.
 *
 * Consolidated from four near-identical classes (`GrpcTypeParserTest`,
 * `GrpcTypeParserPureTest`, `GrpcTypeParserPureLogicTest`, `GrpcTypeParserLogicTest`) that
 * repeated the same `mapProtobufType` assertions. Two of their tests asserted against
 * *local copies* of the production logic (`isCountGetter`/`isBytesGetter` and the protobuf
 * base-class set) and therefore could never fail — those are replaced here with real
 * assertions against the production members.
 *
 * `parseMessageType`/`isProtobufMessage` need PSI; they are exercised by
 * [GrpcRuleIntegrationTest].
 */
class GrpcTypeParserTest {

    private val parser = GrpcTypeParser()

    // ==================== mapProtobufType ====================

    @Test
    fun `mapProtobufType maps every scalar spelling in the shared table`() {
        for ((input, expected) in ProtoUtils.PROTO_SCALAR_TYPES) {
            assertEquals("Mapping for $input", expected, parser.mapProtobufType(input))
        }
    }

    @Test
    fun `mapProtobufType resolves the bare simple names as well`() {
        // These used to fall through verbatim here while ProtoUtils mapped them, because the
        // two carried separate tables. Both now share one — locking the alignment.
        assertEquals("string", parser.mapProtobufType("String"))
        assertEquals("int32", parser.mapProtobufType("Integer"))
        assertEquals("int64", parser.mapProtobufType("Long"))
        assertEquals("float", parser.mapProtobufType("Float"))
        assertEquals("double", parser.mapProtobufType("Double"))
        assertEquals("bool", parser.mapProtobufType("Boolean"))
    }

    @Test
    fun `mapProtobufType returns a non-scalar verbatim`() {
        assertEquals("com.example.CustomMessage", parser.mapProtobufType("com.example.CustomMessage"))
        assertEquals("com.example.CustomType", parser.mapProtobufType("com.example.CustomType"))
        assertEquals("CustomType", parser.mapProtobufType("CustomType"))
        assertEquals("Object", parser.mapProtobufType("Object"))
        assertEquals("java.util.List", parser.mapProtobufType("java.util.List"))
        assertEquals("", parser.mapProtobufType(""))
    }

    @Test
    fun `only the non-scalar fallback differs from ProtoUtils`() {
        // Scalars: identical, because both read the shared table.
        for ((input, expected) in ProtoUtils.PROTO_SCALAR_TYPES) {
            assertEquals("Scalar '$input' must agree", expected, ProtoUtils.mapJavaTypeToProto(input))
        }
        // Documented divergence, not an oversight:
        //  - mapJavaTypeToProto builds a .proto descriptor, where a message is referenced by
        //    its bare name, so it folds case and strips the package.
        //  - mapProtobufType builds an ObjectModel shown in documentation, where the value is
        //    a type reference, so a non-scalar is kept verbatim.
        assertEquals("string", ProtoUtils.mapJavaTypeToProto("STRING"))
        assertEquals("STRING", parser.mapProtobufType("STRING"))
        assertEquals("MyMessage", ProtoUtils.mapJavaTypeToProto("com.example.MyMessage"))
        assertEquals("com.example.MyMessage", parser.mapProtobufType("com.example.MyMessage"))
    }

    // ==================== isCountGetter / isBytesGetter ====================

    @Test
    fun `isCountGetter detects a count getter only when the list getter exists`() {
        assertTrue(parser.isCountGetter("getItemsCount", setOf("getItems", "getItemsList", "getItemsCount")))
        assertFalse("no getItemCountList getter", parser.isCountGetter("getItemCount", setOf("getItemCount")))
        assertFalse("does not end in Count", parser.isCountGetter("getItems", setOf("getItems")))
    }

    @Test
    fun `isBytesGetter detects a bytes getter only when the string getter exists`() {
        assertTrue(parser.isBytesGetter("getNameBytes", setOf("getName", "getNameBytes")))
        assertFalse("no getGet getter", parser.isBytesGetter("getBytes", setOf("getBytes")))
        assertFalse("does not end in Bytes", parser.isBytesGetter("getName", setOf("getName")))
    }

    // ==================== GrpcServiceRecognizer constants ====================

    @Test
    fun `GrpcServiceRecognizer exposes its annotation and service FQN`() {
        assertNotNull(GrpcServiceRecognizer.GRPC_SERVICE_ANNOTATIONS)
        assertTrue(
            GrpcServiceRecognizer.GRPC_SERVICE_ANNOTATIONS.contains("net.devh.boot.grpc.server.service.GrpcService")
        )
        assertEquals("io.grpc.BindableService", GrpcServiceRecognizer.BINDABLE_SERVICE_FQN)
    }
}
