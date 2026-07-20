package com.itangcent.easyapi.core.settings.ui

import com.itangcent.easyapi.core.settings.module.HttpSettings
import org.junit.Assert.*
import org.junit.Test

class EnhancedSettingsPanelsTest {

    @Test
    fun testEnhancedGeneralSettingsPanel() {
        val panel = EnhancedGeneralSettingsPanel()
        assertNotNull("Panel should be created", panel)
        assertNotNull("Component should not be null", panel.component)

        panel.resetFrom(null)

        // EnhancedGeneralSettingsPanel.resetFrom() is a no-op (self-contained panel),
        // so any Settings subtype is acceptable as the fixture. HttpSettings avoids a
        // concrete channel.<id>.* import from core.* (DAG rule per Decision CO3).
        val settings = HttpSettings()

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
