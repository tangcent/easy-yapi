package com.itangcent.easyapi.grpc

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for [GrpcStatus] utility object.
 * 
 * [GrpcStatus] provides:
 * - Constants for all standard gRPC status codes (OK, CANCELLED, UNKNOWN, etc.)
 * - `getName(code)`: Converts status code to name string
 * - `formatStatus(code)`: Formats status as "NAME(code)" for display
 * - `isError(code)`: Checks if a code represents an error (non-OK)
 * 
 * Status codes follow the gRPC specification:
 * - 0 = OK (success)
 * - 1-16 = Various error conditions
 */
class GrpcStatusTest {

    /**
     * Tests that all standard gRPC status codes have correct values.
     * These values must match the gRPC specification exactly.
     */
    @Test
    fun testStatusCodes() {
        assertEquals("OK should be 0", 0, GrpcStatus.OK)
        assertEquals("CANCELLED should be 1", 1, GrpcStatus.CANCELLED)
        assertEquals("UNKNOWN should be 2", 2, GrpcStatus.UNKNOWN)
        assertEquals("INVALID_ARGUMENT should be 3", 3, GrpcStatus.INVALID_ARGUMENT)
        assertEquals("DEADLINE_EXCEEDED should be 4", 4, GrpcStatus.DEADLINE_EXCEEDED)
        assertEquals("NOT_FOUND should be 5", 5, GrpcStatus.NOT_FOUND)
        assertEquals("ALREADY_EXISTS should be 6", 6, GrpcStatus.ALREADY_EXISTS)
        assertEquals("PERMISSION_DENIED should be 7", 7, GrpcStatus.PERMISSION_DENIED)
        assertEquals("RESOURCE_EXHAUSTED should be 8", 8, GrpcStatus.RESOURCE_EXHAUSTED)
        assertEquals("FAILED_PRECONDITION should be 9", 9, GrpcStatus.FAILED_PRECONDITION)
        assertEquals("ABORTED should be 10", 10, GrpcStatus.ABORTED)
        assertEquals("OUT_OF_RANGE should be 11", 11, GrpcStatus.OUT_OF_RANGE)
        assertEquals("UNIMPLEMENTED should be 12", 12, GrpcStatus.UNIMPLEMENTED)
        assertEquals("INTERNAL should be 13", 13, GrpcStatus.INTERNAL)
        assertEquals("UNAVAILABLE should be 14", 14, GrpcStatus.UNAVAILABLE)
        assertEquals("DATA_LOSS should be 15", 15, GrpcStatus.DATA_LOSS)
        assertEquals("UNAUTHENTICATED should be 16", 16, GrpcStatus.UNAUTHENTICATED)
    }

    /**
     * Tests conversion of status codes to human-readable names.
     * Unknown codes should return "UNKNOWN_CODE".
     */
    @Test
    fun testGetName() {
        assertEquals("getName(0) should be OK", "OK", GrpcStatus.getName(0))
        assertEquals("getName(1) should be CANCELLED", "CANCELLED", GrpcStatus.getName(1))
        assertEquals("getName(5) should be NOT_FOUND", "NOT_FOUND", GrpcStatus.getName(5))
        assertEquals("getName(16) should be UNAUTHENTICATED", "UNAUTHENTICATED", GrpcStatus.getName(16))
        assertEquals("getName(999) should be UNKNOWN_CODE", "UNKNOWN_CODE", GrpcStatus.getName(999))
    }

    /**
     * Tests the formatStatus function which produces display-friendly strings.
     * Format: "NAME(code)" for known codes, "Unknown" for null.
     */
    @Test
    fun testFormatStatus() {
        assertEquals("formatStatus(0) should be OK(0)", "OK(0)", GrpcStatus.formatStatus(0))
        assertEquals("formatStatus(5) should be NOT_FOUND(5)", "NOT_FOUND(5)", GrpcStatus.formatStatus(5))
        assertEquals("formatStatus(null) should be Unknown", "Unknown", GrpcStatus.formatStatus(null))
    }

    /**
     * Tests the isError helper function.
     * Only OK (0) is not an error; all other codes are errors.
     * Null is considered not an error (defensive default).
     */
    @Test
    fun testIsError() {
        assertFalse("OK should not be error", GrpcStatus.isError(GrpcStatus.OK))
        assertTrue("CANCELLED should be error", GrpcStatus.isError(GrpcStatus.CANCELLED))
        assertTrue("UNKNOWN should be error", GrpcStatus.isError(GrpcStatus.UNKNOWN))
        assertTrue("NOT_FOUND should be error", GrpcStatus.isError(GrpcStatus.NOT_FOUND))
        assertTrue("INTERNAL should be error", GrpcStatus.isError(GrpcStatus.INTERNAL))
        assertFalse("null should not be error", GrpcStatus.isError(null))
    }
}
