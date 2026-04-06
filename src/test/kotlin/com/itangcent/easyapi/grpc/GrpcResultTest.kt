package com.itangcent.easyapi.grpc

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for [GrpcResult] data class.
 * 
 * [GrpcResult] wraps the result of a gRPC method invocation:
 * - `body`: JSON-encoded response body (from protobuf JSON conversion)
 * - `isError`: Whether the call resulted in an error status
 * - `statusCode`: gRPC status code (0 = OK, see [GrpcStatus] for constants)
 * - `statusName`: Human-readable status name (e.g., "OK", "NOT_FOUND")
 * 
 * Used by [GrpcClient] implementations to return call results.
 */
class GrpcResultTest {

    /**
     * Tests a successful gRPC result with OK status.
     * Successful results have `isError = false` and `statusCode = 0`.
     */
    @Test
    fun testGrpcResultSuccess() {
        val result = GrpcResult(
            body = """{"message": "hello"}""",
            isError = false,
            statusCode = GrpcStatus.OK,
            statusName = "OK"
        )

        assertFalse("Should not be error", result.isError)
        assertEquals("Status code should be OK", GrpcStatus.OK, result.statusCode)
        assertEquals("Status name should be OK", "OK", result.statusName)
        assertTrue("Body should contain message", result.body.contains("hello"))
    }

    /**
     * Tests an error gRPC result with NOT_FOUND status.
     * Error results have `isError = true` and non-zero status codes.
     */
    @Test
    fun testGrpcResultError() {
        val result = GrpcResult(
            body = """{"error": "not found"}""",
            isError = true,
            statusCode = GrpcStatus.NOT_FOUND,
            statusName = "NOT_FOUND"
        )

        assertTrue("Should be error", result.isError)
        assertEquals("Status code should be NOT_FOUND", GrpcStatus.NOT_FOUND, result.statusCode)
    }

    /**
     * Tests a result with null status (e.g., when status couldn't be determined).
     * This can happen when the gRPC call fails before receiving a status.
     */
    @Test
    fun testGrpcResultWithNullStatus() {
        val result = GrpcResult(
            body = "",
            isError = true,
            statusCode = null,
            statusName = null
        )

        assertTrue("Should be error", result.isError)
        assertNull("Status code should be null", result.statusCode)
        assertNull("Status name should be null", result.statusName)
    }
}
