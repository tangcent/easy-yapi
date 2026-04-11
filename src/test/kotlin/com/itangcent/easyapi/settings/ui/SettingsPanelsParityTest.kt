package com.itangcent.easyapi.settings.ui

import com.itangcent.easyapi.settings.Settings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPanelsParityTest {

    @Test
    fun testExtensionPanelSerializationFlow() {
        val settings = Settings().apply {
            extensionConfigs = "jackson,gson"
        }
        val panel = ExtensionConfigPanel()
        panel.resetFrom(settings)
        assertFalse(panel.isModified(settings))
        panel.applyTo(settings)
        assertTrue(!settings.extensionConfigs.isNullOrBlank())
    }

    @Test
    fun testRemotePanelSerializationFlow() {
        val settings = Settings().apply {
            remoteConfig = arrayOf("https://a.example/config", "!https://b.example/config")
        }
        val panel = RemoteConfigPanel()
        panel.resetFrom(settings)
        assertFalse(panel.isModified(settings))
        panel.applyTo(settings)
        assertTrue(settings.remoteConfig.isNotEmpty())
    }

    @Test
    fun testBuiltInPanelApplyReset() {
        val settings = Settings().apply {
            builtInConfig = "field.name=@x#v"
        }
        val builtIn = BuiltInConfigPanel()
        builtIn.resetFrom(settings)
        assertFalse(builtIn.isModified(settings))
        builtIn.applyTo(settings)
    }
}
