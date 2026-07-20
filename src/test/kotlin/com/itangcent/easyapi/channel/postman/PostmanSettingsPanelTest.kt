package com.itangcent.easyapi.channel.postman

import com.itangcent.easyapi.core.settings.PostmanExportMode
import com.itangcent.easyapi.core.settings.PostmanJson5FormatType
import org.junit.Assert.*
import org.junit.Test

class PostmanSettingsPanelTest {

    @Test
    fun testResetFromWithDefaults() {
        val panel = PostmanSettingsPanel()
        val settings = PostmanSettings()
        panel.resetFrom(settings)
        assertFalse("Panel should not be modified after reset with defaults", panel.isModified(settings))
    }

    @Test
    fun testResetFromWithCustomSettings() {
        val panel = PostmanSettingsPanel()
        val settings = PostmanSettings().apply {
            postmanToken = "test-token-123"
            postmanWorkspace = "workspace-456"
            postmanExportMode = PostmanExportMode.UPDATE_EXISTING.name
            postmanBuildExample = false
            wrapCollection = true
            autoMergeScript = true
            postmanJson5FormatType = PostmanJson5FormatType.ALL.name
            postmanCollections = "collection-id-789"
        }
        panel.resetFrom(settings)
        assertFalse("Panel should not be modified after reset with custom values", panel.isModified(settings))
    }

    @Test
    fun testApplyTo() {
        val panel = PostmanSettingsPanel()
        val settings = PostmanSettings()

        val customSettings = PostmanSettings().apply {
            postmanToken = "my-token"
            postmanWorkspace = "my-workspace"
            postmanExportMode = PostmanExportMode.UPDATE_EXISTING.name
            postmanBuildExample = false
            wrapCollection = true
            autoMergeScript = true
            postmanJson5FormatType = PostmanJson5FormatType.ALL.name
            postmanCollections = "my-collection"
        }
        panel.resetFrom(customSettings)

        val targetSettings = PostmanSettings()
        panel.applyTo(targetSettings)

        assertEquals("my-token", targetSettings.postmanToken)
        assertFalse(targetSettings.postmanBuildExample)
        assertTrue(targetSettings.wrapCollection)
        assertTrue(targetSettings.autoMergeScript)
    }

    @Test
    fun testIsModifiedReturnsFalseWhenNoChanges() {
        val panel = PostmanSettingsPanel()
        val settings = PostmanSettings()
        panel.resetFrom(settings)
        assertFalse(panel.isModified(settings))
    }

    @Test
    fun testIsModifiedReturnsTrueWhenTokenChanged() {
        val panel = PostmanSettingsPanel()
        val settings = PostmanSettings().apply {
            postmanToken = "original-token"
        }
        panel.resetFrom(settings)

        // Modify the token field directly (simulating user input)
        // Since we can't directly access private fields, we test through the round-trip
        val newSettings = PostmanSettings().apply {
            postmanToken = "different-token"
        }
        panel.resetFrom(newSettings)
        assertTrue("Panel should be modified when settings differ", panel.isModified(settings))
    }

    @Test
    fun testIsModifiedReturnsTrueWhenBuildExampleChanged() {
        val panel = PostmanSettingsPanel()
        val settings = PostmanSettings().apply {
            postmanBuildExample = true
        }
        panel.resetFrom(settings)

        val newSettings = PostmanSettings().apply {
            postmanBuildExample = false
        }
        panel.resetFrom(newSettings)
        assertTrue("Panel should be modified when buildExample differs", panel.isModified(settings))
    }

    @Test
    fun testIsModifiedReturnsTrueWhenWrapCollectionChanged() {
        val panel = PostmanSettingsPanel()
        val settings = PostmanSettings().apply {
            wrapCollection = false
        }
        panel.resetFrom(settings)

        val newSettings = PostmanSettings().apply {
            wrapCollection = true
        }
        panel.resetFrom(newSettings)
        assertTrue("Panel should be modified when wrapCollection differs", panel.isModified(settings))
    }

    @Test
    fun testIsModifiedReturnsTrueWhenAutoMergeScriptChanged() {
        val panel = PostmanSettingsPanel()
        val settings = PostmanSettings().apply {
            autoMergeScript = false
        }
        panel.resetFrom(settings)

        val newSettings = PostmanSettings().apply {
            autoMergeScript = true
        }
        panel.resetFrom(newSettings)
        assertTrue("Panel should be modified when autoMergeScript differs", panel.isModified(settings))
    }

    @Test
    fun testIsModifiedReturnsFalseForNullSettings() {
        val panel = PostmanSettingsPanel()
        assertFalse("isModified should return false for null settings", panel.isModified(null))
    }

    @Test
    fun testResetFromWithNullSettings() {
        val panel = PostmanSettingsPanel()
        panel.resetFrom(null)
        // Should not throw and should handle null gracefully
        val settings = PostmanSettings()
        assertFalse(panel.isModified(settings))
    }

    @Test
    fun testComponentNotNull() {
        val panel = PostmanSettingsPanel()
        assertNotNull("Component should not be null", panel.component)
    }
}
