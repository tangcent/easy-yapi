package com.itangcent.easyapi.grpc

import org.junit.Assert.*
import org.junit.Test

class GrpcArtifactsTest {

    // ── Artifact ──

    @Test
    fun testArtifact_coordinate() {
        val a = Artifact("io.grpc", "grpc-core")
        assertEquals("io.grpc:grpc-core", a.coordinate)
    }

    @Test
    fun testArtifact_groupPath() {
        val a = Artifact("io.grpc", "grpc-core")
        assertEquals("io/grpc", a.groupPath)
    }

    @Test
    fun testArtifact_jarName() {
        val a = Artifact("io.grpc", "grpc-core")
        assertEquals("grpc-core-1.60.0.jar", a.jarName("1.60.0"))
    }

    @Test
    fun testArtifact_mavenUrl() {
        val a = Artifact("io.grpc", "grpc-core")
        val url = a.mavenUrl("https://repo1.maven.org/maven2", "1.60.0")
        assertEquals("https://repo1.maven.org/maven2/io/grpc/grpc-core/1.60.0/grpc-core-1.60.0.jar", url)
    }

    @Test
    fun testArtifact_toString() {
        val a = Artifact("io.grpc", "grpc-core")
        assertEquals("io.grpc:grpc-core", a.toString())
    }

    @Test
    fun testArtifact_parse() {
        val a = Artifact.parse("io.grpc:grpc-core")
        assertNotNull(a)
        assertEquals("io.grpc", a!!.groupId)
        assertEquals("grpc-core", a.artifactId)
    }

    @Test
    fun testArtifact_parse_invalid() {
        assertNull(Artifact.parse("invalid"))
        assertNull(Artifact.parse("a:b:c"))
    }

    // ── QualifiedArtifact ──

    @Test
    fun testQualifiedArtifact_coordinate() {
        val qa = QualifiedArtifact(Artifact("io.grpc", "grpc-core"), "1.60.0")
        assertEquals("io.grpc:grpc-core:1.60.0", qa.coordinate)
    }

    @Test
    fun testQualifiedArtifact_delegatedProperties() {
        val qa = QualifiedArtifact(Artifact("io.grpc", "grpc-core"), "1.60.0")
        assertEquals("io.grpc", qa.groupId)
        assertEquals("grpc-core", qa.artifactId)
    }

    @Test
    fun testQualifiedArtifact_toString() {
        val qa = QualifiedArtifact(Artifact("io.grpc", "grpc-core"), "1.60.0")
        assertEquals("io.grpc:grpc-core:1.60.0", qa.toString())
    }

    @Test
    fun testQualifiedArtifact_parse() {
        val qa = QualifiedArtifact.parse("io.grpc:grpc-core:1.60.0")
        assertNotNull(qa)
        assertEquals("io.grpc", qa!!.groupId)
        assertEquals("grpc-core", qa.artifactId)
        assertEquals("1.60.0", qa.version)
    }

    @Test
    fun testQualifiedArtifact_parse_invalid() {
        assertNull(QualifiedArtifact.parse("a:b"))
        assertNull(QualifiedArtifact.parse("single"))
    }

    // ── ArtifactVersionMode ──

    @Test
    fun testArtifactVersionMode_values() {
        assertEquals(2, ArtifactVersionMode.values().size)
        assertNotNull(ArtifactVersionMode.LATEST)
        assertNotNull(ArtifactVersionMode.FIXED)
    }

    // ── GrpcArtifactConfig ──

    @Test
    fun testGrpcArtifactConfig_defaults() {
        val config = GrpcArtifactConfig(Artifact("io.grpc", "grpc-core"))
        assertEquals(ArtifactVersionMode.LATEST, config.versionMode)
        assertNull(config.fixedVersion)
        assertTrue(config.enabled)
        assertNull(config.resolvedVersion)
    }

    @Test
    fun testGrpcArtifactConfig_effectiveVersion_latest() {
        val config = GrpcArtifactConfig(Artifact("io.grpc", "grpc-core"))
        assertEquals("1.60.0", config.effectiveVersion("1.60.0"))
    }

    @Test
    fun testGrpcArtifactConfig_effectiveVersion_latestResolved() {
        val config = GrpcArtifactConfig(Artifact("io.grpc", "grpc-core"))
        config.resolvedVersion = "1.61.0"
        assertEquals("1.61.0", config.effectiveVersion("1.60.0"))
    }

    @Test
    fun testGrpcArtifactConfig_effectiveVersion_fixed() {
        val config = GrpcArtifactConfig(
            Artifact("io.grpc", "grpc-core"),
            versionMode = ArtifactVersionMode.FIXED,
            fixedVersion = "1.55.0"
        )
        assertEquals("1.55.0", config.effectiveVersion("1.60.0"))
    }

    @Test
    fun testGrpcArtifactConfig_coordinate() {
        val config = GrpcArtifactConfig(Artifact("io.grpc", "grpc-core"))
        assertEquals("io.grpc:grpc-core", config.coordinate)
    }

    @Test
    fun testGrpcArtifactConfig_toString_latest() {
        val config = GrpcArtifactConfig(Artifact("io.grpc", "grpc-core"))
        assertEquals("io.grpc:grpc-core:latest", config.toString())
    }

    @Test
    fun testGrpcArtifactConfig_toString_resolved() {
        val config = GrpcArtifactConfig(Artifact("io.grpc", "grpc-core"))
        config.resolvedVersion = "1.60.0"
        assertEquals("io.grpc:grpc-core:1.60.0", config.toString())
    }

    @Test
    fun testGrpcArtifactConfig_parse_twoparts() {
        val config = GrpcArtifactConfig.parse("io.grpc:grpc-core")
        assertNotNull(config)
        assertEquals("io.grpc:grpc-core", config!!.coordinate)
        assertEquals(ArtifactVersionMode.LATEST, config.versionMode)
    }

    @Test
    fun testGrpcArtifactConfig_parse_threeparts_latest() {
        val config = GrpcArtifactConfig.parse("io.grpc:grpc-core:latest")
        assertNotNull(config)
        assertEquals(ArtifactVersionMode.LATEST, config!!.versionMode)
    }

    @Test
    fun testGrpcArtifactConfig_parse_threeparts_fixed() {
        val config = GrpcArtifactConfig.parse("io.grpc:grpc-core:1.55.0")
        assertNotNull(config)
        assertEquals(ArtifactVersionMode.FIXED, config!!.versionMode)
        assertEquals("1.55.0", config.fixedVersion)
    }

    @Test
    fun testGrpcArtifactConfig_parse_invalid() {
        assertNull(GrpcArtifactConfig.parse("single"))
    }

    // ── GrpcRequiredArtifacts ──

    @Test
    fun testGrpcRequiredArtifacts_all() {
        assertEquals(10, GrpcRequiredArtifacts.ALL.size)
        assertTrue(GrpcRequiredArtifacts.ALL.contains(GrpcRequiredArtifacts.GRPC_CORE))
        assertTrue(GrpcRequiredArtifacts.ALL.contains(GrpcRequiredArtifacts.GRPC_NETTY_SHADED))
    }

    @Test
    fun testGrpcRequiredArtifacts_artifactIds() {
        assertTrue(GrpcRequiredArtifacts.GRPC_ARTIFACT_IDS.contains("grpc-core"))
        assertTrue(GrpcRequiredArtifacts.GRPC_ARTIFACT_IDS.contains("grpc-netty-shaded"))
        assertEquals(10, GrpcRequiredArtifacts.GRPC_ARTIFACT_IDS.size)
    }

    @Test
    fun testGrpcRequiredArtifacts_required() {
        assertEquals(4, GrpcRequiredArtifacts.REQUIRED_GRPC_ARTIFACTS.size)
    }

    @Test
    fun testGrpcRequiredArtifacts_defaultConfigs() {
        val configs = GrpcRequiredArtifacts.defaultConfigs()
        assertEquals(10, configs.size)
        configs.forEach {
            assertEquals(ArtifactVersionMode.LATEST, it.versionMode)
            assertTrue(it.enabled)
        }
    }

    @Test
    fun testGrpcRequiredArtifacts_mergeWithDefaults_null() {
        val merged = GrpcRequiredArtifacts.mergeWithDefaults(null)
        assertEquals(10, merged.size)
    }

    @Test
    fun testGrpcRequiredArtifacts_mergeWithDefaults_empty() {
        val merged = GrpcRequiredArtifacts.mergeWithDefaults(emptyList())
        assertEquals(10, merged.size)
    }

    @Test
    fun testGrpcRequiredArtifacts_mergeWithDefaults_userOverride() {
        val userConfig = GrpcArtifactConfig(
            GrpcRequiredArtifacts.GRPC_CORE,
            versionMode = ArtifactVersionMode.FIXED,
            fixedVersion = "1.55.0"
        )
        val merged = GrpcRequiredArtifacts.mergeWithDefaults(listOf(userConfig))
        val grpcCore = merged.find { it.coordinate == "io.grpc:grpc-core" }
        assertNotNull(grpcCore)
        assertEquals(ArtifactVersionMode.FIXED, grpcCore!!.versionMode)
        assertEquals("1.55.0", grpcCore.fixedVersion)
    }

    @Test
    fun testGrpcRequiredArtifacts_mergeWithDefaults_additionalArtifacts() {
        val extra = Artifact("com.example", "extra-lib")
        val merged = GrpcRequiredArtifacts.mergeWithDefaults(null, listOf(extra))
        assertEquals(11, merged.size)
        assertTrue(merged.any { it.coordinate == "com.example:extra-lib" })
    }
}
