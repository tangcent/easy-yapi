package com.itangcent.easyapi.repository

import org.junit.Assert.*
import org.junit.Test

class RepositoryConfigTest {

    @Test
    fun testParseMavenLocal() {
        val config = RepositoryConfig.parse("maven:/path/to/.m2/repository")
        assertNotNull("Should parse maven config", config)
        assertEquals(RepositoryType.MAVEN_LOCAL, config!!.type)
        assertEquals("/path/to/.m2/repository", config.path)
        assertTrue("Should be enabled by default", config.enabled)
    }

    @Test
    fun testParseGradleCache() {
        val config = RepositoryConfig.parse("gradle:/path/to/.gradle/caches")
        assertNotNull("Should parse gradle config", config)
        assertEquals(RepositoryType.GRADLE_CACHE, config!!.type)
        assertEquals("/path/to/.gradle/caches", config.path)
    }

    @Test
    fun testParseCustom() {
        val config = RepositoryConfig.parse("custom:/custom/path")
        assertNotNull("Should parse custom config", config)
        assertEquals(RepositoryType.CUSTOM, config!!.type)
    }

    @Test
    fun testParseWithEnabledFlag() {
        val config = RepositoryConfig.parse("maven:true:/path/to/.m2")
        assertNotNull("Should parse with enabled flag", config)
        assertTrue(config!!.enabled)
    }

    @Test
    fun testParseWithDisabledFlag() {
        val config = RepositoryConfig.parse("maven:false:/path/to/.m2")
        assertNotNull("Should parse with disabled flag", config)
        assertFalse(config!!.enabled)
    }

    @Test
    fun testParseInvalidReturnsNull() {
        assertNull("Should return null for empty string", RepositoryConfig.parse(""))
        assertNull("Should return null for single part", RepositoryConfig.parse("invalid"))
    }

    @Test
    fun testParseUnknownTypeReturnsNull() {
        assertNull("Should return null for unknown type", RepositoryConfig.parse("unknown:/path"))
    }

    @Test
    fun testSerialize() {
        val config = RepositoryConfig(RepositoryType.MAVEN_LOCAL, "/path/to/.m2", true)
        val serialized = RepositoryConfig.serialize(config)
        assertEquals("maven:true:/path/to/.m2", serialized)
    }

    @Test
    fun testSerializeDisabled() {
        val config = RepositoryConfig(RepositoryType.GRADLE_CACHE, "/path/to/.gradle", false)
        val serialized = RepositoryConfig.serialize(config)
        assertEquals("gradle:false:/path/to/.gradle", serialized)
    }

    @Test
    fun testRoundTrip() {
        val original = RepositoryConfig(RepositoryType.CUSTOM, "/custom/path", true)
        val serialized = RepositoryConfig.serialize(original)
        val parsed = RepositoryConfig.parse(serialized)
        assertNotNull("Round-trip should parse", parsed)
        assertEquals(original.type, parsed!!.type)
        assertEquals(original.path, parsed.path)
        assertEquals(original.enabled, parsed.enabled)
    }

    @Test
    fun testDisplayName() {
        assertEquals("Maven Local", RepositoryConfig(RepositoryType.MAVEN_LOCAL, "/path").displayName())
        assertEquals("Gradle Cache", RepositoryConfig(RepositoryType.GRADLE_CACHE, "/path").displayName())
        assertTrue("Custom should contain path", RepositoryConfig(RepositoryType.CUSTOM, "/custom/my-repo").displayName().contains("my-repo"))
    }

    @Test
    fun testToPath() {
        val config = RepositoryConfig(RepositoryType.MAVEN_LOCAL, "/path/to/repo")
        assertEquals(java.nio.file.Paths.get("/path/to/repo"), config.toPath())
    }
}
