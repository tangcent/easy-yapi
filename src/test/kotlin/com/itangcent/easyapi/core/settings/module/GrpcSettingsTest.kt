package com.itangcent.easyapi.core.settings.module

import org.junit.Assert.*
import org.junit.Test

class GrpcSettingsTest {

    @Test
    fun testDefaults() {
        val settings = GrpcSettings()
        assertArrayEquals(emptyArray(), settings.grpcArtifactConfigs)
        assertArrayEquals(emptyArray(), settings.grpcAdditionalJars)
        assertFalse(settings.grpcCallEnabled)
        assertArrayEquals(emptyArray(), settings.grpcRepositories)
    }

    @Test
    fun testCustomValues() {
        val settings = GrpcSettings(
            grpcArtifactConfigs = arrayOf("org.example:grpc-api:1.0"),
            grpcAdditionalJars = arrayOf("/path/to/extra.jar"),
            grpcCallEnabled = true,
            grpcRepositories = arrayOf("https://repo.example.com")
        )
        assertArrayEquals(arrayOf("org.example:grpc-api:1.0"), settings.grpcArtifactConfigs)
        assertArrayEquals(arrayOf("/path/to/extra.jar"), settings.grpcAdditionalJars)
        assertTrue(settings.grpcCallEnabled)
        assertArrayEquals(arrayOf("https://repo.example.com"), settings.grpcRepositories)
    }

    // ── equals ──

    @Test
    fun testEqualsSameInstance() {
        val settings = GrpcSettings()
        assertEquals(settings, settings)
    }

    @Test
    fun testEqualsNull() {
        val settings = GrpcSettings()
        assertNotEquals(settings, null)
    }

    @Test
    fun testEqualsDifferentType() {
        val settings = GrpcSettings()
        assertNotEquals(settings, "not a GrpcSettings")
    }

    @Test
    fun testEqualsSameValues() {
        val s1 = GrpcSettings(
            grpcArtifactConfigs = arrayOf("a", "b"),
            grpcAdditionalJars = arrayOf("/x.jar"),
            grpcCallEnabled = true,
            grpcRepositories = arrayOf("https://r")
        )
        val s2 = GrpcSettings(
            grpcArtifactConfigs = arrayOf("a", "b"),
            grpcAdditionalJars = arrayOf("/x.jar"),
            grpcCallEnabled = true,
            grpcRepositories = arrayOf("https://r")
        )
        assertEquals(s1, s2)
    }

    @Test
    fun testEqualsDifferentGrpcArtifactConfigs() {
        val s1 = GrpcSettings(grpcArtifactConfigs = arrayOf("a"))
        val s2 = GrpcSettings(grpcArtifactConfigs = arrayOf("b"))
        assertNotEquals(s1, s2)
    }

    @Test
    fun testEqualsDifferentGrpcAdditionalJars() {
        val s1 = GrpcSettings(grpcAdditionalJars = arrayOf("/a.jar"))
        val s2 = GrpcSettings(grpcAdditionalJars = arrayOf("/b.jar"))
        assertNotEquals(s1, s2)
    }

    @Test
    fun testEqualsDifferentGrpcCallEnabled() {
        val s1 = GrpcSettings(grpcCallEnabled = true)
        val s2 = GrpcSettings(grpcCallEnabled = false)
        assertNotEquals(s1, s2)
    }

    @Test
    fun testEqualsDifferentGrpcRepositories() {
        val s1 = GrpcSettings(grpcRepositories = arrayOf("https://a"))
        val s2 = GrpcSettings(grpcRepositories = arrayOf("https://b"))
        assertNotEquals(s1, s2)
    }

    @Test
    fun testEqualsArraysWithSameContentAreEqual() {
        val s1 = GrpcSettings(grpcArtifactConfigs = arrayOf("x", "y"))
        val s2 = GrpcSettings(grpcArtifactConfigs = arrayOf("x", "y"))
        assertEquals(s1, s2)
    }

    // ── hashCode ──

    @Test
    fun testHashCodeConsistentWithEquals() {
        val s1 = GrpcSettings(
            grpcArtifactConfigs = arrayOf("a"),
            grpcAdditionalJars = arrayOf("/x.jar"),
            grpcCallEnabled = true,
            grpcRepositories = arrayOf("https://r")
        )
        val s2 = GrpcSettings(
            grpcArtifactConfigs = arrayOf("a"),
            grpcAdditionalJars = arrayOf("/x.jar"),
            grpcCallEnabled = true,
            grpcRepositories = arrayOf("https://r")
        )
        assertEquals(s1, s2)
        assertEquals(s1.hashCode(), s2.hashCode())
    }

    @Test
    fun testHashCodeConsistentAcrossCalls() {
        val settings = GrpcSettings()
        assertEquals(settings.hashCode(), settings.hashCode())
    }

    @Test
    fun testHashCodeDifferentForDifferentValues() {
        val s1 = GrpcSettings(grpcCallEnabled = true)
        val s2 = GrpcSettings(grpcCallEnabled = false)
        assertNotEquals(s1.hashCode(), s2.hashCode())
    }

    // ── copy (data class) ──

    @Test
    fun testCopyProducesEqualInstance() {
        val original = GrpcSettings(
            grpcArtifactConfigs = arrayOf("a"),
            grpcCallEnabled = true
        )
        val copy = original.copy()
        assertEquals(original, copy)
        assertEquals(original.hashCode(), copy.hashCode())
    }

    @Test
    fun testCopyWithModification() {
        val original = GrpcSettings(grpcCallEnabled = false)
        val modified = original.copy(grpcCallEnabled = true)
        assertNotEquals(original, modified)
        assertTrue(modified.grpcCallEnabled)
    }
}
