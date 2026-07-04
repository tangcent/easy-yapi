package com.itangcent.easyapi.settings.ui

import com.itangcent.easyapi.exporter.channel.postman.PostmanSettings
import com.itangcent.easyapi.exporter.channel.postman.PostmanSettingsPanel
import com.itangcent.easyapi.settings.PostmanExportMode
import com.itangcent.easyapi.settings.PostmanJson5FormatType
import org.junit.Assert.*
import org.junit.Test

class PostmanSettingsPanelLogicTest {

    // --- PostmanExportMode tests ---

    @Test
    fun testPostmanExportMode_values() {
        val values = PostmanExportMode.values()
        assertEquals(2, values.size)
        assertEquals(PostmanExportMode.CREATE_NEW, values[0])
        assertEquals(PostmanExportMode.UPDATE_EXISTING, values[1])
    }

    @Test
    fun testPostmanExportMode_valueOf() {
        assertEquals(PostmanExportMode.CREATE_NEW, PostmanExportMode.valueOf("CREATE_NEW"))
        assertEquals(PostmanExportMode.UPDATE_EXISTING, PostmanExportMode.valueOf("UPDATE_EXISTING"))
    }

    @Test
    fun testPostmanExportMode_desc() {
        assertNotNull(PostmanExportMode.CREATE_NEW.desc)
        assertNotNull(PostmanExportMode.UPDATE_EXISTING.desc)
    }

    // --- PostmanJson5FormatType tests ---

    @Test
    fun testPostmanJson5FormatType_values() {
        val values = PostmanJson5FormatType.values()
        assertEquals(5, values.size)
    }

    @Test
    fun testPostmanJson5FormatType_needUseJson5() {
        // NONE: never use json5
        assertFalse(PostmanJson5FormatType.NONE.needUseJson5(1))
        assertFalse(PostmanJson5FormatType.NONE.needUseJson5(4))
        assertFalse(PostmanJson5FormatType.NONE.needUseJson5(8))

        // ALL: always use json5
        assertTrue(PostmanJson5FormatType.ALL.needUseJson5(1))
        assertTrue(PostmanJson5FormatType.ALL.needUseJson5(4))
        assertTrue(PostmanJson5FormatType.ALL.needUseJson5(8))

        // REQUEST_ONLY: only type 1 and 4
        assertTrue(PostmanJson5FormatType.REQUEST_ONLY.needUseJson5(1))
        assertTrue(PostmanJson5FormatType.REQUEST_ONLY.needUseJson5(4))
        assertFalse(PostmanJson5FormatType.REQUEST_ONLY.needUseJson5(8))

        // RESPONSE_ONLY: only type 8
        assertFalse(PostmanJson5FormatType.RESPONSE_ONLY.needUseJson5(1))
        assertFalse(PostmanJson5FormatType.RESPONSE_ONLY.needUseJson5(4))
        assertTrue(PostmanJson5FormatType.RESPONSE_ONLY.needUseJson5(8))

        // EXAMPLE_ONLY: types 4 and 8
        assertFalse(PostmanJson5FormatType.EXAMPLE_ONLY.needUseJson5(1))
        assertTrue(PostmanJson5FormatType.EXAMPLE_ONLY.needUseJson5(4))
        assertTrue(PostmanJson5FormatType.EXAMPLE_ONLY.needUseJson5(8))
    }

    @Test
    fun testPostmanJson5FormatType_desc() {
        for (type in PostmanJson5FormatType.values()) {
            assertNotNull("desc should not be null for $type", type.desc)
        }
    }

    // --- PostmanSettingsPanel round-trip tests ---

    @Test
    fun testPostmanSettingsPanel_resetFromDefault_notModified() {
        val panel = PostmanSettingsPanel()
        val settings = PostmanSettings()
        panel.resetFrom(settings)
        assertFalse(panel.isModified(settings))
    }

    @Test
    fun testPostmanSettingsPanel_resetFromCustom_notModified() {
        val panel = PostmanSettingsPanel()
        val settings = PostmanSettings().apply {
            postmanToken = "test-token-12345678"
            postmanWorkspace = "workspace-456"
            postmanExportMode = PostmanExportMode.UPDATE_EXISTING.name
            postmanBuildExample = false
            wrapCollection = true
            autoMergeScript = true
            postmanJson5FormatType = PostmanJson5FormatType.ALL.name
            postmanCollections = "collection-id-789"
        }
        panel.resetFrom(settings)
        assertFalse(panel.isModified(settings))
    }

    @Test
    fun testPostmanSettingsPanel_applyTo_defaultSettings() {
        val panel = PostmanSettingsPanel()
        val settings = PostmanSettings()
        panel.resetFrom(settings)

        val target = PostmanSettings()
        panel.applyTo(target)

        // After resetFrom + applyTo, target should match original
        assertFalse(panel.isModified(target))
    }

    @Test
    fun testPostmanSettingsPanel_applyTo_customSettings() {
        val panel = PostmanSettingsPanel()
        val customSettings = PostmanSettings().apply {
            postmanToken = "my-token-12345678"
            postmanWorkspace = "my-workspace"
            postmanExportMode = PostmanExportMode.UPDATE_EXISTING.name
            postmanBuildExample = false
            wrapCollection = true
            autoMergeScript = true
            postmanJson5FormatType = PostmanJson5FormatType.ALL.name
            postmanCollections = "my-collection"
        }
        panel.resetFrom(customSettings)

        val target = PostmanSettings()
        panel.applyTo(target)

        assertEquals("my-token-12345678", target.postmanToken)
        assertFalse(target.postmanBuildExample)
        assertTrue(target.wrapCollection)
        assertTrue(target.autoMergeScript)
    }

    @Test
    fun testPostmanSettingsPanel_isModified_nullSettings() {
        val panel = PostmanSettingsPanel()
        assertFalse("isModified should return false for null settings", panel.isModified(null))
    }

    @Test
    fun testPostmanSettingsPanel_resetFrom_nullSettings() {
        val panel = PostmanSettingsPanel()
        panel.resetFrom(null)
        // Should not throw
        val settings = PostmanSettings()
        assertFalse(panel.isModified(settings))
    }

    @Test
    fun testPostmanSettingsPanel_componentNotNull() {
        val panel = PostmanSettingsPanel()
        assertNotNull(panel.component)
    }

    // --- extractWorkspaceId logic (tested indirectly through applyTo) ---

    @Test
    fun testPostmanSettingsPanel_workspaceIdExtraction() {
        // When workspace is set as "Name (id)" format, applyTo should extract the id
        val panel = PostmanSettingsPanel()
        val settings = PostmanSettings().apply {
            postmanWorkspace = "ws-12345"
        }
        panel.resetFrom(settings)

        val target = PostmanSettings()
        panel.applyTo(target)
        // The workspace should be extracted or preserved
        assertNotNull(target.postmanWorkspace)
    }

    // --- PostmanSettingsPanel isModified with different settings ---

    @Test
    fun testPostmanSettingsPanel_isModified_differentBuildExample() {
        val panel = PostmanSettingsPanel()
        val settings = PostmanSettings().apply { postmanBuildExample = true }
        panel.resetFrom(settings)

        val differentSettings = PostmanSettings().apply { postmanBuildExample = false }
        panel.resetFrom(differentSettings)
        assertTrue(panel.isModified(settings))
    }

    @Test
    fun testPostmanSettingsPanel_isModified_differentWrapCollection() {
        val panel = PostmanSettingsPanel()
        val settings = PostmanSettings().apply { wrapCollection = false }
        panel.resetFrom(settings)

        val differentSettings = PostmanSettings().apply { wrapCollection = true }
        panel.resetFrom(differentSettings)
        assertTrue(panel.isModified(settings))
    }

    @Test
    fun testPostmanSettingsPanel_isModified_differentAutoMergeScript() {
        val panel = PostmanSettingsPanel()
        val settings = PostmanSettings().apply { autoMergeScript = false }
        panel.resetFrom(settings)

        val differentSettings = PostmanSettings().apply { autoMergeScript = true }
        panel.resetFrom(differentSettings)
        assertTrue(panel.isModified(settings))
    }

    // --- Settings data class tests for Postman fields ---

    @Test
    fun testSettings_postmanFieldsDefault() {
        val settings = PostmanSettings()
        assertNull(settings.postmanToken)
        assertNull(settings.postmanWorkspace)
        assertEquals(PostmanExportMode.CREATE_NEW.name, settings.postmanExportMode)
        assertNull(settings.postmanCollections)
        assertTrue(settings.postmanBuildExample)
        assertFalse(settings.wrapCollection)
        assertFalse(settings.autoMergeScript)
        assertEquals(PostmanJson5FormatType.EXAMPLE_ONLY.name, settings.postmanJson5FormatType)
    }

    @Test
    fun testSettings_postmanFieldsEquality() {
        val s1 = PostmanSettings(postmanToken = "abc", postmanWorkspace = "ws1")
        val s2 = PostmanSettings(postmanToken = "abc", postmanWorkspace = "ws1")
        assertEquals(s1, s2)
    }

    @Test
    fun testSettings_postmanFieldsInequality() {
        val s1 = PostmanSettings(postmanToken = "abc")
        val s2 = PostmanSettings(postmanToken = "def")
        assertNotEquals(s1, s2)
    }
}
