package com.itangcent.easyapi.repository

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Paths

class RepositoryConfigTest {

    @Test
    fun testParseMavenRepository() {
        val config = RepositoryConfig.parse("maven:/path/to/.m2/repository")
        assertNotNull("Should parse maven config", config)
        assertEquals("Type should be MAVEN_LOCAL", RepositoryType.MAVEN_LOCAL, config!!.type)
        assertEquals("Path should be correct", "/path/to/.m2/repository", config.path)
        assertTrue("Should be enabled by default", config.enabled)
    }

    @Test
    fun testParseGradleRepository() {
        val config = RepositoryConfig.parse("gradle:/path/to/.gradle/caches")
        assertNotNull("Should parse gradle config", config)
        assertEquals("Type should be GRADLE_CACHE", RepositoryType.GRADLE_CACHE, config!!.type)
        assertEquals("Path should be correct", "/path/to/.gradle/caches", config.path)
    }

    @Test
    fun testParseCustomRepository() {
        val config = RepositoryConfig.parse("custom:/custom/path")
        assertNotNull("Should parse custom config", config)
        assertEquals("Type should be CUSTOM", RepositoryType.CUSTOM, config!!.type)
        assertEquals("Path should be correct", "/custom/path", config.path)
    }

    @Test
    fun testParseWithEnabledFlag() {
        val config = RepositoryConfig.parse("maven:true:/path/to/.m2/repository")
        assertNotNull("Should parse with enabled flag", config)
        assertTrue("Should be enabled", config!!.enabled)

        val disabledConfig = RepositoryConfig.parse("maven:false:/path/to/.m2/repository")
        assertNotNull("Should parse with disabled flag", disabledConfig)
        assertFalse("Should be disabled", disabledConfig!!.enabled)
    }

    @Test
    fun testParseInvalidFormat() {
        assertNull("Should return null for invalid format", RepositoryConfig.parse("invalid"))
        assertNull("Should return null for unknown type", RepositoryConfig.parse("unknown:/path"))
    }

    @Test
    fun testSerialize() {
        val config = RepositoryConfig(RepositoryType.MAVEN_LOCAL, "/path/to/.m2/repository", true)
        val serialized = RepositoryConfig.serialize(config)
        assertEquals("Serialized format should be correct", "maven:true:/path/to/.m2/repository", serialized)
    }

    @Test
    fun testSerializeAndParseRoundTrip() {
        val original = RepositoryConfig(RepositoryType.GRADLE_CACHE, "/path/to/gradle", false)
        val serialized = RepositoryConfig.serialize(original)
        val parsed = RepositoryConfig.parse(serialized)

        assertNotNull("Parsed config should not be null", parsed)
        assertEquals("Type should match", original.type, parsed!!.type)
        assertEquals("Path should match", original.path, parsed.path)
        assertEquals("Enabled should match", original.enabled, parsed.enabled)
    }

    @Test
    fun testToPath() {
        val config = RepositoryConfig(RepositoryType.MAVEN_LOCAL, "/path/to/repo")
        val path = config.toPath()
        assertEquals("Path should be correct", Paths.get("/path/to/repo"), path)
    }

    @Test
    fun testDisplayNameMaven() {
        val config = RepositoryConfig(RepositoryType.MAVEN_LOCAL, "/path/to/.m2/repository")
        assertEquals("Display name should be Maven Local", "Maven Local", config.displayName())
    }

    @Test
    fun testDisplayNameGradle() {
        val config = RepositoryConfig(RepositoryType.GRADLE_CACHE, "/path/to/.gradle/caches")
        assertEquals("Display name should be Gradle Cache", "Gradle Cache", config.displayName())
    }

    @Test
    fun testDisplayNameCustom() {
        val config = RepositoryConfig(RepositoryType.CUSTOM, "/custom/my-repo")
        assertEquals("Display name should contain repo name", "Custom: my-repo", config.displayName())
    }

    @Test
    fun testDataClassEquality() {
        val config1 = RepositoryConfig(RepositoryType.MAVEN_LOCAL, "/path", true)
        val config2 = RepositoryConfig(RepositoryType.MAVEN_LOCAL, "/path", true)
        assertEquals("Equal configs should be equal", config1, config2)
    }

    @Test
    fun testDataClassCopy() {
        val original = RepositoryConfig(RepositoryType.MAVEN_LOCAL, "/path", true)
        val copy = original.copy(enabled = false)

        assertTrue("Original should still be enabled", original.enabled)
        assertFalse("Copy should be disabled", copy.enabled)
    }
}
