package com.itangcent.easyapi.settings.ui

import com.itangcent.easyapi.settings.Settings
import org.junit.Assert.*
import org.junit.Test

class EnhancedSettingsPanelsTest {
    
    @Test
    fun testEnhancedGeneralSettingsPanel() {
        val panel = EnhancedGeneralSettingsPanel()
        assertNotNull("Panel should be created", panel)
        assertNotNull("Component should not be null", panel.component)
        
        panel.resetFrom(null)
        
        val settings = Settings().apply {
            postmanBuildExample = false
            autoMergeScript = true
        }
        
        panel.resetFrom(settings)
        assertFalse("Panel should not be modified initially", panel.isModified(settings))
    }
    
    @Test
    fun testEnhancedOtherSettingsPanel() {
        val panel = EnhancedOtherSettingsPanel()
        assertNotNull("Panel should be created", panel)
        assertNotNull("Component should not be null", panel.component)
        
        panel.resetFrom(null)
        
        val settings = Settings().apply {
            outputCharset = "UTF-8"
            unsafeSsl = false
            httpTimeOut = 30000
        }
        
        panel.resetFrom(settings)
        assertFalse("Panel should not be modified initially", panel.isModified(settings))
    }
}
