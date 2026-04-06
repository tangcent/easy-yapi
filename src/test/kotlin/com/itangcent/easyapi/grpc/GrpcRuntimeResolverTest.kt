package com.itangcent.easyapi.grpc

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for [ResolvedRuntime] data class.
 * 
 * [ResolvedRuntime] represents the result of resolving gRPC runtime JARs:
 * - `jars`: List of Path objects pointing to resolved JAR files
 * - `version`: The resolved gRPC version string (e.g., "1.58.0")
 * 
 * Created by [GrpcRuntimeResolver] when it successfully finds gRPC runtime
 * JARs in Maven local or Gradle cache. Used by [DynamicJarClient] to create
 * a classloader for dynamic gRPC invocation.
 * 
 * Note: This test only covers the data class itself. Integration tests for
 * [GrpcRuntimeResolver] require actual Maven/Gradle cache on the system.
 */
class GrpcRuntimeResolverTest {

    /**
     * Tests basic construction and property access of ResolvedRuntime.
     */
    @Test
    fun testResolvedRuntimeDataClass() {
        val jars = listOf(java.nio.file.Paths.get("/path/to/grpc-netty-1.58.0.jar"))
        val runtime = ResolvedRuntime(jars, "1.58.0")

        assertEquals("Version should be 1.58.0", "1.58.0", runtime.version)
        assertEquals("Should have 1 jar", 1, runtime.jars.size)
        assertTrue("Jar path should contain grpc-netty", 
            runtime.jars.first().toString().contains("grpc-netty"))
    }

    /**
     * Tests that data class equality works correctly for caching and comparison.
     */
    @Test
    fun testResolvedRuntimeEquality() {
        val jars1 = listOf(java.nio.file.Paths.get("/path/to/grpc.jar"))
        val jars2 = listOf(java.nio.file.Paths.get("/path/to/grpc.jar"))

        val runtime1 = ResolvedRuntime(jars1, "1.0.0")
        val runtime2 = ResolvedRuntime(jars2, "1.0.0")

        assertEquals("Equal ResolvedRuntimes should be equal", runtime1, runtime2)
        assertEquals("Hash codes should be equal", runtime1.hashCode(), runtime2.hashCode())
    }

    /**
     * Tests the copy function for creating modified versions.
     */
    @Test
    fun testResolvedRuntimeCopy() {
        val jars = listOf(java.nio.file.Paths.get("/path/to/grpc.jar"))
        val runtime = ResolvedRuntime(jars, "1.0.0")

        val copy = runtime.copy(version = "2.0.0")

        assertEquals("Original version should be unchanged", "1.0.0", runtime.version)
        assertEquals("Copy version should be 2.0.0", "2.0.0", copy.version)
    }
}
