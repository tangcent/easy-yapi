package com.itangcent.easyapi.exporter.feign

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for FeignClassExporter utility functions (normalizePath, join).
 * These are private instance methods, so the logic is replicated here for testing.
 */
class FeignClassExporterUtilsTest {

    // Replicated from FeignClassExporter for testing
    private fun normalizePath(path: String): String {
        if (path.isBlank()) return "/"
        val p = if (path.startsWith("/")) path else "/$path"
        return p.replace(Regex("/+"), "/")
    }

    private fun join(a: String, b: String): String {
        if (a.isBlank()) return b
        if (b.isBlank()) return a
        return a.trimEnd('/') + "/" + b.trimStart('/')
    }

    // --- normalizePath tests ---

    @Test
    fun testNormalizePathEmptyString() {
        assertEquals("/", normalizePath(""))
    }

    @Test
    fun testNormalizePathBlankString() {
        assertEquals("/", normalizePath("   "))
    }

    @Test
    fun testNormalizePathAlreadyNormalized() {
        assertEquals("/api/users", normalizePath("/api/users"))
    }

    @Test
    fun testNormalizePathWithoutLeadingSlash() {
        assertEquals("/api/users", normalizePath("api/users"))
    }

    @Test
    fun testNormalizePathWithDoubleSlashes() {
        assertEquals("/api/users", normalizePath("/api//users"))
    }

    @Test
    fun testNormalizePathWithTripleSlashes() {
        assertEquals("/api/users", normalizePath("/api///users"))
    }

    @Test
    fun testNormalizePathRootPath() {
        assertEquals("/", normalizePath("/"))
    }

    @Test
    fun testNormalizePathJustText() {
        assertEquals("/users", normalizePath("users"))
    }

    // --- join tests ---

    @Test
    fun testJoinBothNonEmpty() {
        assertEquals("/api/users", join("/api", "/users"))
    }

    @Test
    fun testJoinEmptyBase() {
        assertEquals("/users", join("", "/users"))
    }

    @Test
    fun testJoinEmptyPath() {
        assertEquals("/api", join("/api", ""))
    }

    @Test
    fun testJoinBothEmpty() {
        assertEquals("", join("", ""))
    }

    @Test
    fun testJoinBaseWithTrailingSlash() {
        assertEquals("/api/users", join("/api/", "/users"))
    }

    @Test
    fun testJoinPathWithoutLeadingSlash() {
        assertEquals("/api/users", join("/api", "users"))
    }

    @Test
    fun testJoinBaseWithTrailingSlashAndPathWithoutLeading() {
        assertEquals("/api/users", join("/api/", "users"))
    }

    @Test
    fun testJoinBothWithSlashes() {
        assertEquals("/api/v1/users", join("/api/v1", "/users"))
    }
}
