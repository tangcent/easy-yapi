package com.itangcent.easyapi.settings.ui

import com.itangcent.easyapi.settings.HttpClientType
import com.itangcent.easyapi.settings.Settings
import org.junit.Assert.*
import org.junit.Test

class HttpSettingsPanelLogicTest {

    @Test
    fun testHttpSettingsPanel_resetFromDefault_notModified() {
        val panel = HttpSettingsPanel()
        val settings = Settings()
        panel.resetFrom(settings)
        assertFalse(panel.isModified(settings))
    }

    @Test
    fun testHttpSettingsPanel_resetFromCustom_notModified() {
        val panel = HttpSettingsPanel()
        val settings = Settings().apply {
            httpClient = HttpClientType.DEFAULT.value
            httpTimeOut = 60
            unsafeSsl = true
        }
        panel.resetFrom(settings)
        assertFalse(panel.isModified(settings))
    }

    @Test
    fun testHttpSettingsPanel_applyTo_defaultSettings() {
        val panel = HttpSettingsPanel()
        val settings = Settings()
        panel.resetFrom(settings)

        val target = Settings()
        panel.applyTo(target)

        assertEquals(HttpClientType.APACHE.value, target.httpClient)
        assertFalse(target.unsafeSsl)
    }

    @Test
    fun testHttpSettingsPanel_applyTo_customSettings() {
        val panel = HttpSettingsPanel()
        val settings = Settings().apply {
            httpClient = HttpClientType.DEFAULT.value
            httpTimeOut = 120
            unsafeSsl = true
        }
        panel.resetFrom(settings)

        val target = Settings()
        panel.applyTo(target)

        assertEquals(HttpClientType.DEFAULT.value, target.httpClient)
        assertTrue(target.unsafeSsl)
    }

    @Test
    fun testHttpSettingsPanel_isModified_nullSettings() {
        val panel = HttpSettingsPanel()
        assertFalse(panel.isModified(null))
    }

    @Test
    fun testHttpSettingsPanel_isModified_differentHttpClient() {
        val panel = HttpSettingsPanel()
        val settings = Settings().apply { httpClient = HttpClientType.APACHE.value }
        panel.resetFrom(settings)

        val differentSettings = Settings().apply { httpClient = HttpClientType.DEFAULT.value }
        panel.resetFrom(differentSettings)
        assertTrue(panel.isModified(settings))
    }

    @Test
    fun testHttpSettingsPanel_isModified_differentTimeout() {
        val panel = HttpSettingsPanel()
        val settings = Settings().apply { httpTimeOut = 30 }
        panel.resetFrom(settings)

        val differentSettings = Settings().apply { httpTimeOut = 60 }
        panel.resetFrom(differentSettings)
        assertTrue(panel.isModified(settings))
    }

    @Test
    fun testHttpSettingsPanel_isModified_differentUnsafeSsl() {
        val panel = HttpSettingsPanel()
        val settings = Settings().apply { unsafeSsl = false }
        panel.resetFrom(settings)

        val differentSettings = Settings().apply { unsafeSsl = true }
        panel.resetFrom(differentSettings)
        assertTrue(panel.isModified(settings))
    }

    @Test
    fun testHttpSettingsPanel_componentNotNull() {
        val panel = HttpSettingsPanel()
        assertNotNull(panel.component)
    }

    @Test
    fun testHttpClientType_values() {
        val values = HttpClientType.values()
        assertEquals(2, values.size)
        assertEquals(HttpClientType.APACHE, values[0])
        assertEquals(HttpClientType.DEFAULT, values[1])
    }

    @Test
    fun testHttpClientType_value() {
        assertEquals("Apache", HttpClientType.APACHE.value)
        assertEquals("Default", HttpClientType.DEFAULT.value)
    }
}

class IntelligentSettingsPanelLogicTest {

    @Test
    fun testIntelligentSettingsPanel_resetFromDefault_notModified() {
        val panel = IntelligentSettingsPanel()
        val settings = Settings()
        panel.resetFrom(settings)
        assertFalse(panel.isModified(settings))
    }

    @Test
    fun testIntelligentSettingsPanel_resetFromCustom_notModified() {
        val panel = IntelligentSettingsPanel()
        val settings = Settings().apply {
            queryExpanded = false
            formExpanded = false
            inferReturnMain = false
            enableUrlTemplating = false
            pathMulti = "FIRST"
        }
        panel.resetFrom(settings)
        assertFalse(panel.isModified(settings))
    }

    @Test
    fun testIntelligentSettingsPanel_applyTo_defaultSettings() {
        val panel = IntelligentSettingsPanel()
        val settings = Settings()
        panel.resetFrom(settings)

        val target = Settings()
        panel.applyTo(target)

        assertTrue(target.queryExpanded)
        assertTrue(target.formExpanded)
        assertTrue(target.inferReturnMain)
        assertTrue(target.enableUrlTemplating)
        assertEquals("ALL", target.pathMulti)
    }

    @Test
    fun testIntelligentSettingsPanel_applyTo_customSettings() {
        val panel = IntelligentSettingsPanel()
        val settings = Settings().apply {
            queryExpanded = false
            formExpanded = false
            inferReturnMain = false
            enableUrlTemplating = false
            pathMulti = "FIRST"
        }
        panel.resetFrom(settings)

        val target = Settings()
        panel.applyTo(target)

        assertFalse(target.queryExpanded)
        assertFalse(target.formExpanded)
        assertFalse(target.inferReturnMain)
        assertFalse(target.enableUrlTemplating)
        assertEquals("FIRST", target.pathMulti)
    }

    @Test
    fun testIntelligentSettingsPanel_isModified_nullSettings() {
        val panel = IntelligentSettingsPanel()
        assertFalse(panel.isModified(null))
    }

    @Test
    fun testIntelligentSettingsPanel_isModified_differentQueryExpanded() {
        val panel = IntelligentSettingsPanel()
        val settings = Settings().apply { queryExpanded = true }
        panel.resetFrom(settings)

        val differentSettings = Settings().apply { queryExpanded = false }
        panel.resetFrom(differentSettings)
        assertTrue(panel.isModified(settings))
    }

    @Test
    fun testIntelligentSettingsPanel_isModified_differentFormExpanded() {
        val panel = IntelligentSettingsPanel()
        val settings = Settings().apply { formExpanded = true }
        panel.resetFrom(settings)

        val differentSettings = Settings().apply { formExpanded = false }
        panel.resetFrom(differentSettings)
        assertTrue(panel.isModified(settings))
    }

    @Test
    fun testIntelligentSettingsPanel_isModified_differentInferReturnMain() {
        val panel = IntelligentSettingsPanel()
        val settings = Settings().apply { inferReturnMain = true }
        panel.resetFrom(settings)

        val differentSettings = Settings().apply { inferReturnMain = false }
        panel.resetFrom(differentSettings)
        assertTrue(panel.isModified(settings))
    }

    @Test
    fun testIntelligentSettingsPanel_isModified_differentEnableUrlTemplating() {
        val panel = IntelligentSettingsPanel()
        val settings = Settings().apply { enableUrlTemplating = true }
        panel.resetFrom(settings)

        val differentSettings = Settings().apply { enableUrlTemplating = false }
        panel.resetFrom(differentSettings)
        assertTrue(panel.isModified(settings))
    }

    @Test
    fun testIntelligentSettingsPanel_isModified_differentPathMulti() {
        val panel = IntelligentSettingsPanel()
        val settings = Settings().apply { pathMulti = "ALL" }
        panel.resetFrom(settings)

        val differentSettings = Settings().apply { pathMulti = "FIRST" }
        panel.resetFrom(differentSettings)
        assertTrue(panel.isModified(settings))
    }

    @Test
    fun testIntelligentSettingsPanel_componentNotNull() {
        val panel = IntelligentSettingsPanel()
        assertNotNull(panel.component)
    }
}

class OtherSettingsPanelLogicTest {

    @Test
    fun testOtherSettingsPanel_resetFrom_notModified() {
        val panel = OtherSettingsPanel()
        val settings = Settings()
        panel.resetFrom(settings)
        assertFalse(panel.isModified(settings))
    }

    @Test
    fun testOtherSettingsPanel_isModified_alwaysFalse() {
        val panel = OtherSettingsPanel()
        val settings = Settings()
        panel.resetFrom(settings)
        assertFalse(panel.isModified(settings))
        assertFalse(panel.isModified(null))
    }

    @Test
    fun testOtherSettingsPanel_applyTo_doesNothing() {
        val panel = OtherSettingsPanel()
        val settings = Settings()
        panel.resetFrom(settings)

        val target = Settings()
        val before = target.copy()
        panel.applyTo(target)
        // applyTo should not modify settings
        assertEquals(before, target)
    }

    @Test
    fun testOtherSettingsPanel_componentNotNull() {
        val panel = OtherSettingsPanel()
        assertNotNull(panel.component)
    }
}

class RemoteConfigPanelLogicTest {

    @Test
    fun testRemoteConfigPanel_resetFrom_notModified() {
        val panel = RemoteConfigPanel()
        val settings = Settings(remoteConfig = arrayOf("https://a.example/config"))
        panel.resetFrom(settings)
        assertFalse(panel.isModified(settings))
    }

    @Test
    fun testRemoteConfigPanel_resetFrom_emptyConfig() {
        val panel = RemoteConfigPanel()
        val settings = Settings(remoteConfig = emptyArray())
        panel.resetFrom(settings)
        // When empty, both remoteItems and settings are empty, so panel is NOT modified
        assertFalse(panel.isModified(settings))
    }

    @Test
    fun testRemoteConfigPanel_isModified_nullSettings() {
        val panel = RemoteConfigPanel()
        assertFalse(panel.isModified(null))
    }

    @Test
    fun testRemoteConfigPanel_componentNotNull() {
        val panel = RemoteConfigPanel()
        assertNotNull(panel.component)
    }

    @Test
    fun testRemoteConfigPanel_applyTo() {
        val panel = RemoteConfigPanel()
        val settings = Settings(remoteConfig = arrayOf("https://a.example/config", "!https://b.example/config"))
        panel.resetFrom(settings)

        val target = Settings()
        panel.applyTo(target)

        assertTrue(target.remoteConfig.isNotEmpty())
    }
}
