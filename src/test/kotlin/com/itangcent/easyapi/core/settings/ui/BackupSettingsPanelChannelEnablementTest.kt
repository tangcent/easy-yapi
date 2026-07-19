package com.itangcent.easyapi.core.settings.ui

import com.google.gson.JsonParser
import com.itangcent.easyapi.core.settings.SettingBinder
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

/**
 * IDE-fixture tests for [BackupSettingsPanel] channel-enablement round-trip
 * (Task 5.1, "Additional Considerations — Settings backup/restore").
 *
 * Verifies that `enabledChannels` / `disabledChannels` are included in the
 * exported JSON, restored on import, and that a legacy JSON without those
 * arrays imports benignly (arrays default to empty → fall back to
 * `enabledByDefault`).
 */
class BackupSettingsPanelChannelEnablementTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var panel: BackupSettingsPanel

    override fun setUp() {
        super.setUp()
        panel = BackupSettingsPanel(project)
        // Start from a clean GeneralSettings so prior tests' state doesn't leak.
        SettingBinder.getInstance(project).save(GeneralSettings())
    }

    override fun tearDown() {
        try {
            SettingBinder.getInstance(project).save(GeneralSettings())
        } finally {
            super.tearDown()
        }
    }

    fun testExportSettings_includesEnabledAndDisabledChannels() {
        // Persist known channel arrays, then export and assert they round-trip
        // into the JSON.
        SettingBinder.getInstance(project).save(
            GeneralSettings(
                enabledChannels = arrayOf("hoppscotch"),
                disabledChannels = arrayOf("postman")
            )
        )
        val json = panel.exportSettings()
        val obj = JsonParser.parseString(json).asJsonObject

        assertTrue(
            "Exported JSON should contain an enabledChannels array. Got: $obj",
            obj.has("enabledChannels") && obj.get("enabledChannels").isJsonArray
        )
        val enabled = obj.get("enabledChannels").asJsonArray.map { it.asString }
        assertTrue(
            "enabledChannels should contain 'hoppscotch'. Got: $enabled",
            enabled.contains("hoppscotch")
        )

        assertTrue(
            "Exported JSON should contain a disabledChannels array. Got: $obj",
            obj.has("disabledChannels") && obj.get("disabledChannels").isJsonArray
        )
        val disabled = obj.get("disabledChannels").asJsonArray.map { it.asString }
        assertTrue(
            "disabledChannels should contain 'postman'. Got: $disabled",
            disabled.contains("postman")
        )
    }

    fun testImportSettings_restoresEnabledAndDisabledChannels() {
        val json = """
            {
              "feignEnable": true,
              "enabledChannels": ["hoppscotch", "http-client"],
              "disabledChannels": ["markdown"]
            }
        """.trimIndent()

        panel.applyImported(json)

        val restored = SettingBinder.getInstance(project).read(GeneralSettings::class)
        assertEquals(
            "enabledChannels should be restored from import. Got: ${restored.enabledChannels.toList()}",
            listOf("hoppscotch", "http-client"),
            restored.enabledChannels.toList()
        )
        assertEquals(
            "disabledChannels should be restored from import. Got: ${restored.disabledChannels.toList()}",
            listOf("markdown"),
            restored.disabledChannels.toList()
        )
        // Legacy `feignEnable=true` is translated to `enabledFrameworks += "Feign"`
        // by BackupSettingsPanel.applyImported (Feign is default-off).
        assertTrue(
            "Feign should be in enabledFrameworks (legacy feignEnable=true). Got: ${restored.enabledFrameworks.toList()}",
            restored.enabledFrameworks.contains("Feign")
        )
    }

    fun testImportSettings_legacyJsonWithoutChannels_isBenign() {
        // A legacy backup without enabledChannels/disabledChannels must import
        // without error and leave both arrays empty (→ fall back to enabledByDefault).
        val legacyJson = """
            {
              "feignEnable": false,
              "jaxrsEnable": true
            }
        """.trimIndent()

        panel.applyImported(legacyJson)

        val restored = SettingBinder.getInstance(project).read(GeneralSettings::class)
        assertEquals(
            "enabledChannels should remain empty for legacy JSON. Got: ${restored.enabledChannels.toList()}",
            emptyList<String>(),
            restored.enabledChannels.toList()
        )
        assertEquals(
            "disabledChannels should remain empty for legacy JSON. Got: ${restored.disabledChannels.toList()}",
            emptyList<String>(),
            restored.disabledChannels.toList()
        )
    }

    fun testExportSettings_thenImport_roundTripsChannelArrays() {
        // Full round-trip: set arrays, export, clear, import, verify.
        SettingBinder.getInstance(project).save(
            GeneralSettings(
                enabledChannels = arrayOf("hoppscotch"),
                disabledChannels = arrayOf("curl")
            )
        )
        val exported = panel.exportSettings()

        // Clear the settings.
        SettingBinder.getInstance(project).save(GeneralSettings())
        val cleared = SettingBinder.getInstance(project).read(GeneralSettings::class)
        assertTrue("After clear, enabledChannels should be empty", cleared.enabledChannels.isEmpty())

        // Import the exported JSON and verify the arrays are restored.
        panel.applyImported(exported)
        val restored = SettingBinder.getInstance(project).read(GeneralSettings::class)
        assertTrue(
            "After round-trip, hoppscotch should be in enabledChannels. Got: ${restored.enabledChannels.toList()}",
            restored.enabledChannels.contains("hoppscotch")
        )
        assertTrue(
            "After round-trip, curl should be in disabledChannels. Got: ${restored.disabledChannels.toList()}",
            restored.disabledChannels.contains("curl")
        )
    }

    // --- Task A.7: field-format channel arrays backup/restore ---

    fun testExportSettings_includesEnabledAndDisabledFieldFormatChannels() {
        // Persist known field-format arrays, then export and assert they round-trip
        // into the JSON.
        SettingBinder.getInstance(project).save(
            GeneralSettings(
                enabledFieldFormatChannels = arrayOf("json5"),
                disabledFieldFormatChannels = arrayOf("json")
            )
        )
        val json = panel.exportSettings()
        val obj = JsonParser.parseString(json).asJsonObject

        assertTrue(
            "Exported JSON should contain an enabledFieldFormatChannels array. Got: $obj",
            obj.has("enabledFieldFormatChannels") && obj.get("enabledFieldFormatChannels").isJsonArray
        )
        val enabled = obj.get("enabledFieldFormatChannels").asJsonArray.map { it.asString }
        assertTrue(
            "enabledFieldFormatChannels should contain 'json5'. Got: $enabled",
            enabled.contains("json5")
        )

        assertTrue(
            "Exported JSON should contain a disabledFieldFormatChannels array. Got: $obj",
            obj.has("disabledFieldFormatChannels") && obj.get("disabledFieldFormatChannels").isJsonArray
        )
        val disabled = obj.get("disabledFieldFormatChannels").asJsonArray.map { it.asString }
        assertTrue(
            "disabledFieldFormatChannels should contain 'json'. Got: $disabled",
            disabled.contains("json")
        )
    }

    fun testImportSettings_restoresEnabledAndDisabledFieldFormatChannels() {
        val json = """
            {
              "feignEnable": true,
              "enabledFieldFormatChannels": ["json5", "yaml"],
              "disabledFieldFormatChannels": ["json"]
            }
        """.trimIndent()

        panel.applyImported(json)

        val restored = SettingBinder.getInstance(project).read(GeneralSettings::class)
        assertEquals(
            "enabledFieldFormatChannels should be restored from import. Got: ${restored.enabledFieldFormatChannels.toList()}",
            listOf("json5", "yaml"),
            restored.enabledFieldFormatChannels.toList()
        )
        assertEquals(
            "disabledFieldFormatChannels should be restored from import. Got: ${restored.disabledFieldFormatChannels.toList()}",
            listOf("json"),
            restored.disabledFieldFormatChannels.toList()
        )
        // Legacy `feignEnable=true` is translated to `enabledFrameworks += "Feign"`
        // by BackupSettingsPanel.applyImported (Feign is default-off).
        assertTrue(
            "Feign should be in enabledFrameworks (legacy feignEnable=true). Got: ${restored.enabledFrameworks.toList()}",
            restored.enabledFrameworks.contains("Feign")
        )
    }

    fun testImportSettings_legacyJsonWithoutFieldFormatChannels_isBenign() {
        // A legacy backup without enabledFieldFormatChannels/disabledFieldFormatChannels
        // must import without error and leave both arrays empty (→ fall back to
        // enabledByDefault).
        val legacyJson = """
            {
              "feignEnable": false,
              "jaxrsEnable": true
            }
        """.trimIndent()

        panel.applyImported(legacyJson)

        val restored = SettingBinder.getInstance(project).read(GeneralSettings::class)
        assertEquals(
            "enabledFieldFormatChannels should remain empty for legacy JSON. Got: ${restored.enabledFieldFormatChannels.toList()}",
            emptyList<String>(),
            restored.enabledFieldFormatChannels.toList()
        )
        assertEquals(
            "disabledFieldFormatChannels should remain empty for legacy JSON. Got: ${restored.disabledFieldFormatChannels.toList()}",
            emptyList<String>(),
            restored.disabledFieldFormatChannels.toList()
        )
    }

    fun testExportSettings_thenImport_roundTripsFieldFormatChannelArrays() {
        // Full round-trip: set arrays, export, clear, import, verify.
        SettingBinder.getInstance(project).save(
            GeneralSettings(
                enabledFieldFormatChannels = arrayOf("json5"),
                disabledFieldFormatChannels = arrayOf("yaml")
            )
        )
        val exported = panel.exportSettings()

        // Clear the settings.
        SettingBinder.getInstance(project).save(GeneralSettings())
        val cleared = SettingBinder.getInstance(project).read(GeneralSettings::class)
        assertTrue("After clear, enabledFieldFormatChannels should be empty", cleared.enabledFieldFormatChannels.isEmpty())

        // Import the exported JSON and verify the arrays are restored.
        panel.applyImported(exported)
        val restored = SettingBinder.getInstance(project).read(GeneralSettings::class)
        assertTrue(
            "After round-trip, json5 should be in enabledFieldFormatChannels. Got: ${restored.enabledFieldFormatChannels.toList()}",
            restored.enabledFieldFormatChannels.contains("json5")
        )
        assertTrue(
            "After round-trip, yaml should be in disabledFieldFormatChannels. Got: ${restored.disabledFieldFormatChannels.toList()}",
            restored.disabledFieldFormatChannels.contains("yaml")
        )
    }
}
