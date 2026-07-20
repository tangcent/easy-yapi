package com.itangcent.easyapi.core.settings.ui

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.extension.ExtensionConfigRegistry
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.core.settings.module.ParsingOutputSettings
import com.itangcent.easyapi.core.settings.module.RuleFileSettings
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * Coverage-focused tests for the panels in [SettingsPanels.kt] that can be
 * tested without the IntelliJ Platform test framework.
 *
 * Tests use plain JUnit (no LightCodeInsightFixtureTestCase). The only
 * IntelliJ-specific seam is a Mockito-mocked [Project] for [BackupSettingsPanel],
 * whose no-op methods never touch the project.
 *
 * Existing coverage in SettingsPanelsLogicTest / SettingsPanelsParityTest /
 * GeneralSettingsPanelLogicTest is not duplicated here.
 */
class SettingsPanelsCoverageTest {

    // =====================================================================
    // ParsingOutputSettingsPanel — enum field null handling + round-trip
    // =====================================================================

    @Test
    fun testParsingOutputSettingsPanel_resetFromNull_enumFieldDefault() {
        val panel = ParsingOutputSettingsPanel()
        panel.resetFrom(null)
        // null → enum checkbox defaults to false; default ParsingOutputSettings also has false
        assertFalse(panel.isModified(ParsingOutputSettings()))
    }

    @Test
    fun testParsingOutputSettingsPanel_enumFieldRoundTrip_disabled() {
        val source = ParsingOutputSettings().apply { enumFieldAutoInferEnabled = false }
        val panel = ParsingOutputSettingsPanel()
        panel.resetFrom(source)
        assertFalse(panel.isModified(source))

        val target = ParsingOutputSettings().apply { enumFieldAutoInferEnabled = true }
        panel.applyTo(target)
        assertFalse(target.enumFieldAutoInferEnabled)
    }

    @Test
    fun testParsingOutputSettingsPanel_enumFieldRoundTrip_enabled() {
        val source = ParsingOutputSettings().apply { enumFieldAutoInferEnabled = true }
        val panel = ParsingOutputSettingsPanel()
        panel.resetFrom(source)
        assertFalse(panel.isModified(source))

        val target = ParsingOutputSettings().apply { enumFieldAutoInferEnabled = false }
        panel.applyTo(target)
        assertTrue(target.enumFieldAutoInferEnabled)
    }

    @Test
    fun testParsingOutputSettingsPanel_resetFromNull_doesNotThrow() {
        val panel = ParsingOutputSettingsPanel()
        panel.resetFrom(null)
        // After resetFrom(null), isModified(null) returns false
        assertFalse(panel.isModified(null))
    }

    @Test
    fun testParsingOutputSettingsPanel_fullRoundTrip_allFieldsNonDefault() {
        val source = ParsingOutputSettings().apply {
            queryExpanded = false
            formExpanded = false
            inferReturnMain = false
            enableUrlTemplating = false
            pathMulti = "LAST"
            enumFieldAutoInferEnabled = true
        }
        val panel = ParsingOutputSettingsPanel()
        panel.resetFrom(source)
        assertFalse(panel.isModified(source))

        val target = ParsingOutputSettings().apply {
            queryExpanded = true
            formExpanded = true
            inferReturnMain = true
            enableUrlTemplating = true
            pathMulti = "ALL"
            enumFieldAutoInferEnabled = false
        }
        panel.applyTo(target)

        assertEquals(source.queryExpanded, target.queryExpanded)
        assertEquals(source.formExpanded, target.formExpanded)
        assertEquals(source.inferReturnMain, target.inferReturnMain)
        assertEquals(source.enableUrlTemplating, target.enableUrlTemplating)
        assertEquals(source.pathMulti, target.pathMulti)
        assertEquals(source.enumFieldAutoInferEnabled, target.enumFieldAutoInferEnabled)
    }

    @Test
    fun testParsingOutputSettingsPanel_fullRoundTrip_defaultSettings() {
        val source = ParsingOutputSettings()
        val panel = ParsingOutputSettingsPanel()
        panel.resetFrom(source)
        assertFalse(panel.isModified(source))

        val target = ParsingOutputSettings().apply {
            queryExpanded = false
            formExpanded = false
            pathMulti = "FIRST"
            enumFieldAutoInferEnabled = true
        }
        panel.applyTo(target)

        assertEquals(source.queryExpanded, target.queryExpanded)
        assertEquals(source.formExpanded, target.formExpanded)
        assertEquals(source.inferReturnMain, target.inferReturnMain)
        assertEquals(source.enableUrlTemplating, target.enableUrlTemplating)
        assertEquals(source.pathMulti, target.pathMulti)
        assertEquals(source.enumFieldAutoInferEnabled, target.enumFieldAutoInferEnabled)
    }

    // =====================================================================
    // ExtensionConfigPanel — null/default/custom + round-trip stability
    // =====================================================================

    @Test
    fun testExtensionConfigPanel_componentNotNull() {
        val panel = ExtensionConfigPanel()
        assertNotNull(panel.component)
    }

    @Test
    fun testExtensionConfigPanel_isModified_nullSettings() {
        val panel = ExtensionConfigPanel()
        assertFalse(panel.isModified(null))
    }

    @Test
    fun testExtensionConfigPanel_resetFromNull_notModifiedAgainstDefault() {
        val panel = ExtensionConfigPanel()
        panel.resetFrom(null)
        // resetFrom(null) selects default-enabled extensions;
        // default RuleFileSettings also has default-enabled codes → not modified
        val defaultSettings = RuleFileSettings()
        assertFalse(panel.isModified(defaultSettings))
    }

    @Test
    fun testExtensionConfigPanel_resetFromDefault_notModified() {
        val panel = ExtensionConfigPanel()
        val settings = RuleFileSettings()
        panel.resetFrom(settings)
        assertFalse(panel.isModified(settings))
    }

    @Test
    fun testExtensionConfigPanel_resetFromAllCodes_notModified() {
        val panel = ExtensionConfigPanel()
        val allCodes = ExtensionConfigRegistry.allExtensions().map { it.code }.toTypedArray()
        val settings = RuleFileSettings().apply {
            extensionConfigs = ExtensionConfigRegistry.codesToString(allCodes)
        }
        panel.resetFrom(settings)
        assertFalse(panel.isModified(settings))
    }

    @Test
    fun testExtensionConfigPanel_roundTrip_stableAfterApply() {
        val source = RuleFileSettings()
        val panel = ExtensionConfigPanel()
        panel.resetFrom(source)

        val target = RuleFileSettings().apply {
            extensionConfigs = ""
        }
        panel.applyTo(target)
        // After applyTo, the panel should not be modified relative to target
        assertFalse(panel.isModified(target))
    }

    @Test
    fun testExtensionConfigPanel_applyTo_populatesExtensionConfigs() {
        val panel = ExtensionConfigPanel()
        panel.resetFrom(null)

        val target = RuleFileSettings().apply {
            extensionConfigs = ""
        }
        panel.applyTo(target)
        // Default-enabled extensions exist (swagger, jackson, gson, spring, etc.)
        assertFalse(target.extensionConfigs.isEmpty())
    }

    // =====================================================================
    // RemoteConfigPanel — null/default/disabled URLs + round-trip
    // =====================================================================

    @Test
    fun testRemoteConfigPanel_resetFromNull_notModified() {
        val panel = RemoteConfigPanel()
        panel.resetFrom(null)
        val defaultSettings = RuleFileSettings()
        assertFalse(panel.isModified(defaultSettings))
    }

    @Test
    fun testRemoteConfigPanel_resetFromDefault_notModified() {
        val panel = RemoteConfigPanel()
        val settings = RuleFileSettings()
        panel.resetFrom(settings)
        assertFalse(panel.isModified(settings))
    }

    @Test
    fun testRemoteConfigPanel_roundTrip_mixedEnabledDisabledUrls() {
        val source = RuleFileSettings().apply {
            remoteConfig = arrayOf(
                "https://a.example/config",
                "!https://b.example/config",
                "https://c.example/config"
            )
        }
        val panel = RemoteConfigPanel()
        panel.resetFrom(source)
        assertFalse(panel.isModified(source))

        val target = RuleFileSettings()
        panel.applyTo(target)

        assertArrayEquals(source.remoteConfig, target.remoteConfig)
    }

    @Test
    fun testRemoteConfigPanel_roundTrip_allDisabledUrls() {
        val source = RuleFileSettings().apply {
            remoteConfig = arrayOf(
                "!https://disabled1.example/config",
                "!https://disabled2.example/config"
            )
        }
        val panel = RemoteConfigPanel()
        panel.resetFrom(source)
        assertFalse(panel.isModified(source))

        val target = RuleFileSettings()
        panel.applyTo(target)

        assertArrayEquals(source.remoteConfig, target.remoteConfig)
    }

    @Test
    fun testRemoteConfigPanel_roundTrip_emptyUrlsClearsTarget() {
        val source = RuleFileSettings().apply {
            remoteConfig = emptyArray()
        }
        val panel = RemoteConfigPanel()
        panel.resetFrom(source)
        assertFalse(panel.isModified(source))

        val target = RuleFileSettings().apply {
            remoteConfig = arrayOf("https://should-be-cleared.example/config")
        }
        panel.applyTo(target)

        assertTrue(target.remoteConfig.isEmpty())
    }

    @Test
    fun testRemoteConfigPanel_isModified_differentUrls() {
        val panel = RemoteConfigPanel()
        val settings = RuleFileSettings().apply {
            remoteConfig = arrayOf("https://a.example/config")
        }
        panel.resetFrom(settings)

        val differentSettings = RuleFileSettings().apply {
            remoteConfig = arrayOf("https://b.example/config")
        }
        assertTrue(panel.isModified(differentSettings))
    }

    @Test
    fun testRemoteConfigPanel_isModified_emptyVsNonEmpty() {
        val panel = RemoteConfigPanel()
        val settings = RuleFileSettings().apply {
            remoteConfig = arrayOf("https://a.example/config")
        }
        panel.resetFrom(settings)

        val emptySettings = RuleFileSettings()
        assertTrue(panel.isModified(emptySettings))
    }

    @Test
    fun testRemoteConfigPanel_resetFrom_filtersBlankUrls() {
        val source = RuleFileSettings().apply {
            remoteConfig = arrayOf("https://valid.example/config", "  ", "")
        }
        val panel = RemoteConfigPanel()
        panel.resetFrom(source)

        val target = RuleFileSettings()
        panel.applyTo(target)

        assertEquals(1, target.remoteConfig.size)
        assertEquals("https://valid.example/config", target.remoteConfig[0])
    }

    @Test
    fun testRemoteConfigPanel_resetFrom_trimsUrls() {
        val source = RuleFileSettings().apply {
            remoteConfig = arrayOf("  https://padded.example/config  ")
        }
        val panel = RemoteConfigPanel()
        panel.resetFrom(source)

        val target = RuleFileSettings()
        panel.applyTo(target)

        assertEquals(1, target.remoteConfig.size)
        assertEquals("https://padded.example/config", target.remoteConfig[0])
    }

    @Test
    fun testRemoteConfigPanel_resetFrom_trimsDisabledUrls() {
        val source = RuleFileSettings().apply {
            remoteConfig = arrayOf("!  https://disabled.example/config  ")
        }
        val panel = RemoteConfigPanel()
        panel.resetFrom(source)

        val target = RuleFileSettings()
        panel.applyTo(target)

        assertEquals(1, target.remoteConfig.size)
        assertEquals("!https://disabled.example/config", target.remoteConfig[0])
    }

    // =====================================================================
    // BackupSettingsPanel — no-op methods (requires Project, mocked)
    // =====================================================================

    @Test
    fun testBackupSettingsPanel_componentNotNull() {
        val project: Project = mock()
        val panel = BackupSettingsPanel(project)
        assertNotNull(panel.component)
    }

    @Test
    fun testBackupSettingsPanel_resetFrom_doesNotThrow() {
        val project: Project = mock()
        val panel = BackupSettingsPanel(project)
        panel.resetFrom(GeneralSettings())
    }

    @Test
    fun testBackupSettingsPanel_resetFromNull_doesNotThrow() {
        val project: Project = mock()
        val panel = BackupSettingsPanel(project)
        panel.resetFrom(null)
    }

    @Test
    fun testBackupSettingsPanel_applyTo_doesNotThrow() {
        val project: Project = mock()
        val panel = BackupSettingsPanel(project)
        val settings = GeneralSettings()
        panel.applyTo(settings)
    }

    @Test
    fun testBackupSettingsPanel_isModified_nullSettings_returnsFalse() {
        val project: Project = mock()
        val panel = BackupSettingsPanel(project)
        assertFalse(panel.isModified(null))
    }

    @Test
    fun testBackupSettingsPanel_isModified_returnsFalse() {
        val project: Project = mock()
        val panel = BackupSettingsPanel(project)
        assertFalse(panel.isModified(GeneralSettings()))
    }

    // =====================================================================
    // CommonSettingsHelper.VerbosityLevel — brief verification
    // (comprehensively tested in GeneralSettingsPanelLogicTest)
    // =====================================================================

    @Test
    fun testVerbosityLevel_sixLevelsExist() {
        assertEquals(6, CommonSettingsHelper.VerbosityLevel.values().size)
    }

    @Test
    fun testVerbosityLevel_roundTrip_allLevels() {
        for (level in CommonSettingsHelper.VerbosityLevel.values()) {
            val resolved = CommonSettingsHelper.VerbosityLevel.toLevel(level.level)
            assertEquals(level, resolved)
        }
    }
}
