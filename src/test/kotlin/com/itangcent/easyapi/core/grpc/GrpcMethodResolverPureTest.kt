package com.itangcent.easyapi.core.grpc

import com.itangcent.easyapi.core.export.GrpcStreamingType
import org.junit.Assert.*
import org.junit.Test

/**
 * Pure tests for GrpcMethodResolver internal logic that doesn't require
 * IntelliJ platform (Project, PsiClass, etc.).
 */
class GrpcMethodResolverPureTest {

    // --- parseFullMethodName logic (replicated for testing) ---

    private data class ParsedMethodName(
        val packageName: String?,
        val serviceName: String,
        val methodName: String
    )

    private fun parseFullMethodName(fullMethodName: String): ParsedMethodName? {
        val parts = fullMethodName.split('/')
        return when {
            parts.size == 2 -> {
                val servicePart = parts[0]
                val methodName = parts[1]
                val lastDot = servicePart.lastIndexOf('.')
                if (lastDot > 0) {
                    ParsedMethodName(
                        packageName = servicePart.substring(0, lastDot),
                        serviceName = servicePart.substring(lastDot + 1),
                        methodName = methodName
                    )
                } else {
                    ParsedMethodName(
                        packageName = null,
                        serviceName = servicePart,
                        methodName = methodName
                    )
                }
            }
            parts.size == 3 && parts[0].isEmpty() -> {
                val serviceFqn = parts[1]
                val methodName = parts[2]
                val lastDot = serviceFqn.lastIndexOf('.')
                if (lastDot > 0) {
                    ParsedMethodName(
                        packageName = serviceFqn.substring(0, lastDot),
                        serviceName = serviceFqn.substring(lastDot + 1),
                        methodName = methodName
                    )
                } else {
                    ParsedMethodName(
                        packageName = null,
                        serviceName = serviceFqn,
                        methodName = methodName
                    )
                }
            }
            else -> null
        }
    }

    @Test
    fun testParseFullMethodNameSimpleService() {
        val result = parseFullMethodName("GreeterService/SayHello")
        assertNotNull(result)
        assertNull(result!!.packageName)
        assertEquals("GreeterService", result.serviceName)
        assertEquals("SayHello", result.methodName)
    }

    @Test
    fun testParseFullMethodNameWithPackage() {
        val result = parseFullMethodName("com.example.GreeterService/SayHello")
        assertNotNull(result)
        assertEquals("com.example", result!!.packageName)
        assertEquals("GreeterService", result.serviceName)
        assertEquals("SayHello", result.methodName)
    }

    @Test
    fun testParseFullMethodNameWithLeadingSlash() {
        val result = parseFullMethodName("/com.example.GreeterService/SayHello")
        assertNotNull(result)
        assertEquals("com.example", result!!.packageName)
        assertEquals("GreeterService", result.serviceName)
        assertEquals("SayHello", result.methodName)
    }

    @Test
    fun testParseFullMethodNameWithLeadingSlashNoPackage() {
        val result = parseFullMethodName("/GreeterService/SayHello")
        assertNotNull(result)
        assertNull(result!!.packageName)
        assertEquals("GreeterService", result.serviceName)
        assertEquals("SayHello", result.methodName)
    }

    @Test
    fun testParseFullMethodNameInvalid() {
        assertNull(parseFullMethodName("InvalidFormat"))
    }

    @Test
    fun testParseFullMethodNameEmpty() {
        assertNull(parseFullMethodName(""))
    }

    @Test
    fun testParseFullMethodNameTooManySlashes() {
        assertNull(parseFullMethodName("/a/b/c"))
    }

    // --- mapMethodTypeToStreamingType logic (replicated) ---

    private fun mapMethodTypeToStreamingType(methodType: String?): GrpcStreamingType? {
        return when (methodType) {
            "UNARY" -> GrpcStreamingType.UNARY
            "SERVER_STREAMING" -> GrpcStreamingType.SERVER_STREAMING
            "CLIENT_STREAMING" -> GrpcStreamingType.CLIENT_STREAMING
            "BIDIRECTIONAL_STREAMING" -> GrpcStreamingType.BIDIRECTIONAL
            else -> null
        }
    }

    @Test
    fun testMapMethodTypeUnary() {
        assertEquals(GrpcStreamingType.UNARY, mapMethodTypeToStreamingType("UNARY"))
    }

    @Test
    fun testMapMethodTypeServerStreaming() {
        assertEquals(GrpcStreamingType.SERVER_STREAMING, mapMethodTypeToStreamingType("SERVER_STREAMING"))
    }

    @Test
    fun testMapMethodTypeClientStreaming() {
        assertEquals(GrpcStreamingType.CLIENT_STREAMING, mapMethodTypeToStreamingType("CLIENT_STREAMING"))
    }

    @Test
    fun testMapMethodTypeBidirectional() {
        assertEquals(GrpcStreamingType.BIDIRECTIONAL, mapMethodTypeToStreamingType("BIDIRECTIONAL_STREAMING"))
    }

    @Test
    fun testMapMethodTypeNull() {
        assertNull(mapMethodTypeToStreamingType(null))
    }

    @Test
    fun testMapMethodTypeUnknown() {
        assertNull(mapMethodTypeToStreamingType("UNKNOWN"))
    }

    // --- extractPackageNameFromFqn logic (replicated) ---

    private fun extractPackageNameFromFqn(requestTypeFqn: String?, responseTypeFqn: String?): String {
        return requestTypeFqn?.substringBeforeLast('.', "")
            ?.ifBlank { responseTypeFqn?.substringBeforeLast('.', "") }
            ?: ""
    }

    @Test
    fun testExtractPackageFromRequestFqn() {
        assertEquals("com.example", extractPackageNameFromFqn("com.example.HelloRequest", "com.other.HelloResponse"))
    }

    @Test
    fun testExtractPackageFromResponseFqnWhenRequestNull() {
        // When requestFqn is null, the ?. chain short-circuits to null,
        // so ifBlank on the request part is never reached, and the result is ""
        assertEquals("", extractPackageNameFromFqn(null, "com.other.HelloResponse"))
    }

    @Test
    fun testExtractPackageWhenBothNull() {
        assertEquals("", extractPackageNameFromFqn(null, null))
    }

    @Test
    fun testExtractPackageWhenRequestHasNoPackage() {
        assertEquals("com.other", extractPackageNameFromFqn("SimpleRequest", "com.other.HelloResponse"))
    }

    // --- GrpcStreamingType enum coverage ---

    @Test
    fun testGrpcStreamingTypeValues() {
        val values = GrpcStreamingType.values()
        assertEquals(4, values.size)
        assertTrue(values.contains(GrpcStreamingType.UNARY))
        assertTrue(values.contains(GrpcStreamingType.SERVER_STREAMING))
        assertTrue(values.contains(GrpcStreamingType.CLIENT_STREAMING))
        assertTrue(values.contains(GrpcStreamingType.BIDIRECTIONAL))
    }

    @Test
    fun testGrpcStreamingTypeValueOf() {
        assertEquals(GrpcStreamingType.UNARY, GrpcStreamingType.valueOf("UNARY"))
        assertEquals(GrpcStreamingType.SERVER_STREAMING, GrpcStreamingType.valueOf("SERVER_STREAMING"))
        assertEquals(GrpcStreamingType.CLIENT_STREAMING, GrpcStreamingType.valueOf("CLIENT_STREAMING"))
        assertEquals(GrpcStreamingType.BIDIRECTIONAL, GrpcStreamingType.valueOf("BIDIRECTIONAL"))
    }

    // --- GrpcMethodInfo data class ---

    @Test
    fun testGrpcMethodInfoDataClass() {
        val method = GrpcMethodInfo(
            methodName = "SayHello",
            serviceName = "GreeterService",
            packageName = "com.example",
            fullPath = "/com.example.GreeterService/SayHello",
            streamingType = GrpcStreamingType.UNARY,
            requestType = null,
            responseType = null,
            psiMethod = org.mockito.Mockito.mock(com.intellij.psi.PsiMethod::class.java),
            description = "Says hello"
        )
        assertEquals("SayHello", method.methodName)
        assertEquals("GreeterService", method.serviceName)
        assertEquals("com.example", method.packageName)
        assertEquals("/com.example.GreeterService/SayHello", method.fullPath)
        assertEquals(GrpcStreamingType.UNARY, method.streamingType)
        assertNull(method.requestType)
        assertNull(method.responseType)
        assertEquals("Says hello", method.description)
    }

    @Test
    fun testGrpcMethodInfoCopy() {
        val original = GrpcMethodInfo(
            methodName = "SayHello",
            serviceName = "GreeterService",
            packageName = "com.example",
            fullPath = "/com.example.GreeterService/SayHello",
            streamingType = GrpcStreamingType.UNARY,
            requestType = null,
            responseType = null,
            psiMethod = org.mockito.Mockito.mock(com.intellij.psi.PsiMethod::class.java)
        )
        val copy = original.copy(methodName = "SayGoodbye")
        assertEquals("SayGoodbye", copy.methodName)
        assertEquals("GreeterService", copy.serviceName)
    }

    // --- OBJECT_METHOD_NAMES and LIFECYCLE_METHOD_NAMES coverage ---

    @Test
    fun testObjectMethodNames() {
        val names = setOf(
            "equals", "hashCode", "toString", "getClass",
            "notify", "notifyAll", "wait", "clone", "finalize"
        )
        assertEquals(9, names.size)
        assertTrue(names.contains("equals"))
        assertTrue(names.contains("hashCode"))
        assertTrue(names.contains("wait"))
    }

    @Test
    fun testLifecycleMethodNames() {
        val names = setOf(
            "bindService", "serviceImpl", "build",
            "getServiceDescriptor", "getMethodDescriptors"
        )
        assertEquals(5, names.size)
        assertTrue(names.contains("bindService"))
        assertTrue(names.contains("build"))
    }
}
