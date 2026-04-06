package com.itangcent.easyapi.grpc

/**
 * Result of a gRPC method invocation.
 * 
 * Wraps the response from calling a gRPC method via [GrpcClient].
 * 
 * @property body JSON-encoded response body (converted from protobuf via protobuf-java-util)
 * @property isError Whether the call resulted in an error status (non-OK)
 * @property statusCode gRPC status code (0 = OK, see [GrpcStatus] for constants)
 * @property statusName Human-readable status name (e.g., "OK", "NOT_FOUND", "INTERNAL")
 */
data class GrpcResult(
    val body: String,
    val isError: Boolean,
    val statusCode: Int? = null,
    val statusName: String? = null
)

/**
 * Standard gRPC status codes and utility functions.
 * 
 * Provides constants for all gRPC status codes defined in the specification,
 * plus helper functions for working with status codes.
 * 
 * Status codes:
 * - 0 = OK (success)
 * - 1-16 = Various error conditions
 * 
 * @see <a href="https://grpc.github.io/grpc/core/md_doc_statuscodes.html">gRPC Status Codes</a>
 */
object GrpcStatus {

    const val OK: Int = 0
    const val CANCELLED: Int = 1
    const val UNKNOWN: Int = 2
    const val INVALID_ARGUMENT: Int = 3
    const val DEADLINE_EXCEEDED: Int = 4
    const val NOT_FOUND: Int = 5
    const val ALREADY_EXISTS: Int = 6
    const val PERMISSION_DENIED: Int = 7
    const val RESOURCE_EXHAUSTED: Int = 8
    const val FAILED_PRECONDITION: Int = 9
    const val ABORTED: Int = 10
    const val OUT_OF_RANGE: Int = 11
    const val UNIMPLEMENTED: Int = 12
    const val INTERNAL: Int = 13
    const val UNAVAILABLE: Int = 14
    const val DATA_LOSS: Int = 15
    const val UNAUTHENTICATED: Int = 16

    private val CODE_TO_NAME = mapOf(
        OK to "OK",
        CANCELLED to "CANCELLED",
        UNKNOWN to "UNKNOWN",
        INVALID_ARGUMENT to "INVALID_ARGUMENT",
        DEADLINE_EXCEEDED to "DEADLINE_EXCEEDED",
        NOT_FOUND to "NOT_FOUND",
        ALREADY_EXISTS to "ALREADY_EXISTS",
        PERMISSION_DENIED to "PERMISSION_DENIED",
        RESOURCE_EXHAUSTED to "RESOURCE_EXHAUSTED",
        FAILED_PRECONDITION to "FAILED_PRECONDITION",
        ABORTED to "ABORTED",
        OUT_OF_RANGE to "OUT_OF_RANGE",
        UNIMPLEMENTED to "UNIMPLEMENTED",
        INTERNAL to "INTERNAL",
        UNAVAILABLE to "UNAVAILABLE",
        DATA_LOSS to "DATA_LOSS",
        UNAUTHENTICATED to "UNAUTHENTICATED"
    )

    fun getName(code: Int): String = CODE_TO_NAME[code] ?: "UNKNOWN_CODE"

    fun formatStatus(code: Int?): String {
        if (code == null) return "Unknown"
        return "${getName(code)}($code)"
    }

    fun isError(code: Int?): Boolean {
        return code != null && code != OK
    }
}
