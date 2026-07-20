package com.itangcent.easyapi.core.settings.module

import com.itangcent.easyapi.core.extension.ExtensionConfigRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RuleFileSettingsTest {

    @Before
    fun setUp() {
        ExtensionConfigRegistry.loadExtensions()
    }

    @Test
    fun testDefaults() {
        val settings = RuleFileSettings()
        assertEquals(
            ExtensionConfigRegistry.codesToString(ExtensionConfigRegistry.defaultCodes()),
            settings.extensionConfigs
        )
        assertNull(settings.builtInConfig)
        assertArrayEquals(emptyArray(), settings.remoteConfig)
        assertArrayEquals(emptyArray(), settings.disabledGlobalRuleFiles)
        assertTrue(settings.projectBuiltInConfigEnabled)
        assertNull(settings.projectRemoteConfig)
        assertNull(settings.recommendConfig)
    }

    @Test
    fun testCustomValues() {
        val settings = RuleFileSettings(
            extensionConfigs = "spring,mvc",
            builtInConfig = "custom-config",
            remoteConfig = arrayOf("http://example.com/rules"),
            disabledGlobalRuleFiles = arrayOf("/path/to/rules"),
            projectBuiltInConfigEnabled = false,
            projectRemoteConfig = "http://project.example.com",
            recommendConfig = "spring"
        )
        assertEquals("spring,mvc", settings.extensionConfigs)
        assertEquals("custom-config", settings.builtInConfig)
        assertArrayEquals(arrayOf("http://example.com/rules"), settings.remoteConfig)
        assertArrayEquals(arrayOf("/path/to/rules"), settings.disabledGlobalRuleFiles)
        assertFalse(settings.projectBuiltInConfigEnabled)
        assertEquals("http://project.example.com", settings.projectRemoteConfig)
        assertEquals("spring", settings.recommendConfig)
    }

    // ── equals ──

    @Test
    fun testEqualsSameInstance() {
        val settings = RuleFileSettings()
        assertEquals(settings, settings)
    }

    @Test
    fun testEqualsNull() {
        val settings = RuleFileSettings()
        assertNotEquals(settings, null)
    }

    @Test
    fun testEqualsDifferentType() {
        val settings = RuleFileSettings()
        assertNotEquals(settings, "not a RuleFileSettings")
    }

    @Test
    fun testEqualsSameValues() {
        val s1 = RuleFileSettings(
            extensionConfigs = "spring",
            builtInConfig = "config",
            remoteConfig = arrayOf("a", "b"),
            disabledGlobalRuleFiles = arrayOf("/x"),
            projectBuiltInConfigEnabled = false,
            projectRemoteConfig = "remote",
            recommendConfig = "rec"
        )
        val s2 = RuleFileSettings(
            extensionConfigs = "spring",
            builtInConfig = "config",
            remoteConfig = arrayOf("a", "b"),
            disabledGlobalRuleFiles = arrayOf("/x"),
            projectBuiltInConfigEnabled = false,
            projectRemoteConfig = "remote",
            recommendConfig = "rec"
        )
        assertEquals(s1, s2)
    }

    @Test
    fun testEqualsDifferentExtensionConfigs() {
        val s1 = RuleFileSettings(extensionConfigs = "spring")
        val s2 = RuleFileSettings(extensionConfigs = "mvc")
        assertNotEquals(s1, s2)
    }

    @Test
    fun testEqualsDifferentBuiltInConfig() {
        val s1 = RuleFileSettings(builtInConfig = "a")
        val s2 = RuleFileSettings(builtInConfig = "b")
        assertNotEquals(s1, s2)
    }

    @Test
    fun testEqualsDifferentBuiltInConfigNullVsValue() {
        val s1 = RuleFileSettings(builtInConfig = null)
        val s2 = RuleFileSettings(builtInConfig = "config")
        assertNotEquals(s1, s2)
    }

    @Test
    fun testEqualsDifferentRemoteConfig() {
        val s1 = RuleFileSettings(remoteConfig = arrayOf("a"))
        val s2 = RuleFileSettings(remoteConfig = arrayOf("b"))
        assertNotEquals(s1, s2)
    }

    @Test
    fun testEqualsDifferentDisabledGlobalRuleFiles() {
        val s1 = RuleFileSettings(disabledGlobalRuleFiles = arrayOf("/a"))
        val s2 = RuleFileSettings(disabledGlobalRuleFiles = arrayOf("/b"))
        assertNotEquals(s1, s2)
    }

    @Test
    fun testEqualsDifferentProjectBuiltInConfigEnabled() {
        val s1 = RuleFileSettings(projectBuiltInConfigEnabled = true)
        val s2 = RuleFileSettings(projectBuiltInConfigEnabled = false)
        assertNotEquals(s1, s2)
    }

    @Test
    fun testEqualsDifferentProjectRemoteConfig() {
        val s1 = RuleFileSettings(projectRemoteConfig = "a")
        val s2 = RuleFileSettings(projectRemoteConfig = "b")
        assertNotEquals(s1, s2)
    }

    @Test
    fun testEqualsDifferentRecommendConfig() {
        val s1 = RuleFileSettings(recommendConfig = "a")
        val s2 = RuleFileSettings(recommendConfig = "b")
        assertNotEquals(s1, s2)
    }

    @Test
    fun testEqualsArraysWithSameContentAreEqual() {
        val s1 = RuleFileSettings(remoteConfig = arrayOf("x", "y"))
        val s2 = RuleFileSettings(remoteConfig = arrayOf("x", "y"))
        assertEquals(s1, s2)
    }

    // ── hashCode ──

    @Test
    fun testHashCodeConsistentWithEquals() {
        val s1 = RuleFileSettings(
            extensionConfigs = "spring",
            builtInConfig = "config",
            remoteConfig = arrayOf("a"),
            disabledGlobalRuleFiles = arrayOf("/x"),
            projectBuiltInConfigEnabled = false,
            projectRemoteConfig = "remote",
            recommendConfig = "rec"
        )
        val s2 = RuleFileSettings(
            extensionConfigs = "spring",
            builtInConfig = "config",
            remoteConfig = arrayOf("a"),
            disabledGlobalRuleFiles = arrayOf("/x"),
            projectBuiltInConfigEnabled = false,
            projectRemoteConfig = "remote",
            recommendConfig = "rec"
        )
        assertEquals(s1, s2)
        assertEquals(s1.hashCode(), s2.hashCode())
    }

    @Test
    fun testHashCodeWithNullFields() {
        val settings = RuleFileSettings(
            builtInConfig = null,
            projectRemoteConfig = null,
            recommendConfig = null
        )
        val hc = settings.hashCode()
        // Should not throw; consistent across calls
        assertEquals(hc, settings.hashCode())
    }

    @Test
    fun testHashCodeDifferentForDifferentValues() {
        val s1 = RuleFileSettings(extensionConfigs = "spring")
        val s2 = RuleFileSettings(extensionConfigs = "mvc")
        assertNotEquals(s1.hashCode(), s2.hashCode())
    }

    // ── copy (data class) ──

    @Test
    fun testCopyProducesEqualInstance() {
        val original = RuleFileSettings(
            extensionConfigs = "spring",
            builtInConfig = "config",
            remoteConfig = arrayOf("a"),
            projectBuiltInConfigEnabled = false
        )
        val copy = original.copy()
        assertEquals(original, copy)
        assertEquals(original.hashCode(), copy.hashCode())
    }

    @Test
    fun testCopyWithModification() {
        val original = RuleFileSettings(projectBuiltInConfigEnabled = true)
        val modified = original.copy(projectBuiltInConfigEnabled = false)
        assertNotEquals(original, modified)
        assertFalse(modified.projectBuiltInConfigEnabled)
    }
}
