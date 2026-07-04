package com.itangcent.easyapi.exporter.channel.postman

import com.itangcent.easyapi.settings.PostmanExportMode
import com.itangcent.easyapi.settings.PostmanJson5FormatType
import org.junit.Assert.*
import org.junit.Test

/**
 * Coverage-focused tests for [PostmanSettingsPanel].
 *
 * Fills gaps left by [com.itangcent.easyapi.settings.ui.PostmanSettingsPanelLogicTest]
 * and [com.itangcent.easyapi.settings.ui.PostmanSettingsPanelTest]:
 * - `resetFrom` with invalid enum values (fallback behavior)
 * - `applyTo` edge cases: blank token/collections → null, workspace ID extraction
 * - `isModified` for workspace, exportMode, json5FormatType, collections fields
 * - Round-trip coverage for all enum values
 * - Null-vs-empty equivalence for nullable string fields
 *
 * Uses plain JUnit (no IntelliJ Platform test framework) because
 * [PostmanSettingsPanel] has a no-arg constructor and does not require a [com.intellij.openapi.project.Project].
 */
class PostmanSettingsPanelCoverageTest {

    // --- resetFrom with invalid enum values (falls back to defaults) ---

    @Test
    fun testResetFrom_invalidExportMode_fallsBackToCreateNew() {
        val panel = PostmanSettingsPanel()
        val settings = PostmanSettings().apply {
            postmanExportMode = "INVALID_MODE"
        }
        panel.resetFrom(settings)
        // Panel defaults to CREATE_NEW, but settings has "INVALID_MODE" → modified
        assertTrue(panel.isModified(settings))
    }

    @Test
    fun testResetFrom_invalidJson5FormatType_fallsBackToExampleOnly() {
        val panel = PostmanSettingsPanel()
        val settings = PostmanSettings().apply {
            postmanJson5FormatType = "INVALID_TYPE"
        }
        panel.resetFrom(settings)
        // Panel defaults to EXAMPLE_ONLY, but settings has "INVALID_TYPE" → modified
        assertTrue(panel.isModified(settings))
    }

    // --- applyTo edge cases: blank values become null ---

    @Test
    fun testApplyTo_blankToken_setsNull() {
        val panel = PostmanSettingsPanel()
        val settings = PostmanSettings().apply {
            postmanToken = "   " // whitespace only
        }
        panel.resetFrom(settings)

        val target = PostmanSettings()
        panel.applyTo(target)
        assertNull(target.postmanToken)
    }

    @Test
    fun testApplyTo_blankCollections_setsNull() {
        val panel = PostmanSettingsPanel()
        val settings = PostmanSettings().apply {
            postmanCollections = "   " // whitespace only
        }
        panel.resetFrom(settings)

        val target = PostmanSettings()
        panel.applyTo(target)
        assertNull(target.postmanCollections)
    }

    // --- applyTo workspace ID extraction ---

    @Test
    fun testApplyTo_workspaceWithNameAndId_extractsId() {
        val panel = PostmanSettingsPanel()
        val settings = PostmanSettings().apply {
            postmanWorkspace = "My Workspace (ws-12345)"
        }
        panel.resetFrom(settings)

        val target = PostmanSettings()
        panel.applyTo(target)
        assertEquals("ws-12345", target.postmanWorkspace)
    }

    @Test
    fun testApplyTo_plainWorkspaceId_preservedAsIs() {
        val panel = PostmanSettingsPanel()
        val settings = PostmanSettings().apply {
            postmanWorkspace = "plain-workspace-id"
        }
        panel.resetFrom(settings)

        val target = PostmanSettings()
        panel.applyTo(target)
        assertEquals("plain-workspace-id", target.postmanWorkspace)
    }

    @Test
    fun testApplyTo_emptyWorkspace_setsNull() {
        val panel = PostmanSettingsPanel()
        panel.resetFrom(PostmanSettings())

        val target = PostmanSettings()
        panel.applyTo(target)
        assertNull(target.postmanWorkspace)
    }

    // --- applyTo after resetFrom(null) uses panel defaults ---

    @Test
    fun testApplyTo_afterNullReset_defaultsToJson5ExampleOnly() {
        val panel = PostmanSettingsPanel()
        panel.resetFrom(null)

        val target = PostmanSettings()
        panel.applyTo(target)
        assertEquals(PostmanJson5FormatType.EXAMPLE_ONLY.name, target.postmanJson5FormatType)
    }

    @Test
    fun testApplyTo_afterNullReset_defaultsToExportModeCreateNew() {
        val panel = PostmanSettingsPanel()
        panel.resetFrom(null)

        val target = PostmanSettings()
        panel.applyTo(target)
        assertEquals(PostmanExportMode.CREATE_NEW.name, target.postmanExportMode)
    }

    @Test
    fun testApplyTo_afterNullReset_tokenAndCollectionsNull() {
        val panel = PostmanSettingsPanel()
        panel.resetFrom(null)

        val target = PostmanSettings()
        panel.applyTo(target)
        assertNull(target.postmanToken)
        assertNull(target.postmanCollections)
    }

    // --- isModified for fields not covered by existing tests ---

    @Test
    fun testIsModified_differentWorkspace() {
        val panel = PostmanSettingsPanel()
        val settings = PostmanSettings().apply { postmanWorkspace = "ws-a" }
        panel.resetFrom(settings)

        val differentSettings = PostmanSettings().apply { postmanWorkspace = "ws-b" }
        panel.resetFrom(differentSettings)
        assertTrue(panel.isModified(settings))
    }

    @Test
    fun testIsModified_differentExportMode() {
        val panel = PostmanSettingsPanel()
        val settings = PostmanSettings().apply {
            postmanExportMode = PostmanExportMode.CREATE_NEW.name
        }
        panel.resetFrom(settings)

        val differentSettings = PostmanSettings().apply {
            postmanExportMode = PostmanExportMode.UPDATE_EXISTING.name
        }
        panel.resetFrom(differentSettings)
        assertTrue(panel.isModified(settings))
    }

    @Test
    fun testIsModified_differentJson5FormatType() {
        val panel = PostmanSettingsPanel()
        val settings = PostmanSettings().apply {
            postmanJson5FormatType = PostmanJson5FormatType.NONE.name
        }
        panel.resetFrom(settings)

        val differentSettings = PostmanSettings().apply {
            postmanJson5FormatType = PostmanJson5FormatType.ALL.name
        }
        panel.resetFrom(differentSettings)
        assertTrue(panel.isModified(settings))
    }

    @Test
    fun testIsModified_differentCollections() {
        val panel = PostmanSettingsPanel()
        val settings = PostmanSettings().apply { postmanCollections = "coll-a" }
        panel.resetFrom(settings)

        val differentSettings = PostmanSettings().apply { postmanCollections = "coll-b" }
        panel.resetFrom(differentSettings)
        assertTrue(panel.isModified(settings))
    }

    // --- isModified null-vs-empty equivalence ---

    @Test
    fun testIsModified_nullVsEmptyToken_notModified() {
        val panel = PostmanSettingsPanel()
        val settings = PostmanSettings().apply { postmanToken = null }
        panel.resetFrom(settings)
        assertFalse(panel.isModified(settings))
    }

    @Test
    fun testIsModified_nullVsEmptyCollections_notModified() {
        val panel = PostmanSettingsPanel()
        val settings = PostmanSettings().apply { postmanCollections = null }
        panel.resetFrom(settings)
        assertFalse(panel.isModified(settings))
    }

    @Test
    fun testIsModified_nullVsEmptyWorkspace_notModified() {
        val panel = PostmanSettingsPanel()
        val settings = PostmanSettings().apply { postmanWorkspace = null }
        panel.resetFrom(settings)
        assertFalse(panel.isModified(settings))
    }

    // --- Round-trip for all enum values ---

    @Test
    fun testRoundTrip_allJson5FormatTypes() {
        for (type in PostmanJson5FormatType.values()) {
            val panel = PostmanSettingsPanel()
            val settings = PostmanSettings().apply {
                postmanJson5FormatType = type.name
            }
            panel.resetFrom(settings)
            assertFalse(
                "Panel should not be modified after reset for $type",
                panel.isModified(settings)
            )

            val target = PostmanSettings()
            panel.applyTo(target)
            assertEquals(
                "Round-trip should preserve $type",
                type.name,
                target.postmanJson5FormatType
            )
        }
    }

    @Test
    fun testRoundTrip_allExportModes() {
        for (mode in PostmanExportMode.values()) {
            val panel = PostmanSettingsPanel()
            val settings = PostmanSettings().apply {
                postmanExportMode = mode.name
            }
            panel.resetFrom(settings)
            assertFalse(
                "Panel should not be modified after reset for $mode",
                panel.isModified(settings)
            )

            val target = PostmanSettings()
            panel.applyTo(target)
            assertEquals(
                "Round-trip should preserve $mode",
                mode.name,
                target.postmanExportMode
            )
        }
    }

    // --- Comprehensive round-trip ---

    @Test
    fun testApplyTo_allFieldsRoundTrip() {
        val panel = PostmanSettingsPanel()
        val original = PostmanSettings().apply {
            postmanToken = "token-abc"
            postmanWorkspace = "ws-999"
            postmanExportMode = PostmanExportMode.UPDATE_EXISTING.name
            postmanBuildExample = false
            wrapCollection = true
            autoMergeScript = true
            postmanJson5FormatType = PostmanJson5FormatType.ALL.name
            postmanCollections = "module1:coll1\nmodule2:coll2"
        }
        panel.resetFrom(original)

        val target = PostmanSettings()
        panel.applyTo(target)

        assertEquals("token-abc", target.postmanToken)
        assertEquals("ws-999", target.postmanWorkspace)
        assertEquals(PostmanExportMode.UPDATE_EXISTING.name, target.postmanExportMode)
        assertFalse(target.postmanBuildExample)
        assertTrue(target.wrapCollection)
        assertTrue(target.autoMergeScript)
        assertEquals(PostmanJson5FormatType.ALL.name, target.postmanJson5FormatType)
        assertEquals("module1:coll1\nmodule2:coll2", target.postmanCollections)
    }

    // --- Multiple resetFrom calls ---

    @Test
    fun testMultipleResetFrom_lastOneWins() {
        val panel = PostmanSettingsPanel()
        val settings1 = PostmanSettings().apply {
            postmanToken = "token-1"
            postmanBuildExample = false
            wrapCollection = true
        }
        panel.resetFrom(settings1)

        val settings2 = PostmanSettings().apply {
            postmanToken = "token-2"
            postmanBuildExample = true
            wrapCollection = false
        }
        panel.resetFrom(settings2)

        assertFalse(panel.isModified(settings2))
        assertTrue(panel.isModified(settings1))
    }

    // --- Component not null ---

    @Test
    fun testComponentNotNull() {
        val panel = PostmanSettingsPanel()
        assertNotNull(panel.component)
    }
}
