package com.itangcent.easyapi.settings.ui

import com.itangcent.easyapi.exporter.channel.postman.PostmanSettings
import com.itangcent.easyapi.settings.module.HttpSettings
import org.junit.Assert.*
import org.junit.Test

class EnhancedSettingsPanelsTest {
    
    @Test
    fun testEnhancedGeneralSettingsPanel() {
        val panel = EnhancedGeneralSettingsPanel()
        assertNotNull("Panel should be created", panel)
        assertNotNull("Component should not be null", panel.component)
        
        panel.resetFrom(null)
        
        val settings = PostmanSettings().apply {
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
        
        val settings = HttpSettings().apply {
            unsafeSsl = false
            httpTimeOut = 30
        }
        
        panel.resetFrom(settings)
        assertFalse("Panel should not be modified initially", panel.isModified(settings))
    }
}
