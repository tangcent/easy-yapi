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

    // =====================================================================
    // extensionCodes / enabledExtensionCodes / updateExtensionCodes (#1461)
    // =====================================================================

    @Test
    fun testUpdateExtensionCodes_uncheckedDefaultExtension_writtenAsExclusion() {
        val defaultCode = ExtensionConfigRegistry.defaultCodes().first()
        val stillChecked = ExtensionConfigRegistry.defaultCodes().filter { it != defaultCode }

        val settings = RuleFileSettings()
        settings.updateExtensionCodes(stillChecked)

        assertTrue(
            "unchecking '$defaultCode' must be persisted as an exclusion, got: ${settings.extensionConfigs}",
            settings.extensionCodes().contains("-$defaultCode")
        )
    }

    @Test
    fun testUpdateExtensionCodes_allCheckedDefaultExtensions_matchesDefaultCodes() {
        val settings = RuleFileSettings().apply { extensionConfigs = "" }
        settings.updateExtensionCodes(ExtensionConfigRegistry.defaultCodes().toList())

        assertEquals(
            ExtensionConfigRegistry.codesToString(ExtensionConfigRegistry.defaultCodes()),
            settings.extensionConfigs
        )
    }

    @Test
    fun testUpdateExtensionCodes_roundTrip_preservesDeselection() {
        val defaultCode = ExtensionConfigRegistry.defaultCodes().first()
        val checked = ExtensionConfigRegistry.defaultCodes().filter { it != defaultCode }

        val settings = RuleFileSettings()
        settings.updateExtensionCodes(checked)

        val decoded = settings.enabledExtensionCodes()
        assertFalse(
            "'$defaultCode' must stay disabled after an encode/decode round trip",
            decoded.contains(defaultCode)
        )
        assertEquals(checked.toSet(), decoded.toSet())
    }

    @Test
    fun testUpdateExtensionCodes_blankCodesIgnored() {
        val settings = RuleFileSettings()
        settings.updateExtensionCodes(listOf("", "   "))
        assertFalse(settings.extensionConfigs.contains(",,"))
    }

    /**
     * The two accessors are not interchangeable: [extensionCodes] keeps the
     * `-<code>` exclusions, which is what `ExtensionConfigSource` needs, while
     * [enabledExtensionCodes] resolves them away. Passing the latter to the
     * source would let `defaultEnabled` re-enable the excluded extension —
     * issue #1461 would come back.
     */
    @Test
    fun testExtensionCodes_keepsExclusionThatEnabledCodesDrops() {
        val defaultCode = ExtensionConfigRegistry.defaultCodes().first()
        val settings = RuleFileSettings()
        settings.updateExtensionCodes(
            ExtensionConfigRegistry.defaultCodes().filter { it != defaultCode }
        )

        assertTrue(settings.extensionCodes().contains("-$defaultCode"))
        assertFalse(settings.enabledExtensionCodes().contains("-$defaultCode"))
        assertFalse(settings.enabledExtensionCodes().contains(defaultCode))
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
