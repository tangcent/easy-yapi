package com.itangcent.easyapi.settings.state

import com.itangcent.easyapi.settings.Settings
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.Assert.*

class XmlSettingBinderTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testReadReturnsSettings() {
        val binder = XmlSettingBinder(project)
        val settings = binder.read()

        assertNotNull("Settings should not be null", settings)
    }

    fun testTryReadReturnsSettings() {
        val binder = XmlSettingBinder(project)
        val settings = binder.tryRead()

        assertNotNull("Settings should not be null", settings)
    }

    fun testSaveAndReadRoundTrip() {
        val binder = XmlSettingBinder(project)

        val original = Settings(
            feignEnable = true,
            httpTimeOut = 30,
            postmanWorkspace = "test-workspace"
        )

        binder.save(original)

        val loaded = binder.read()

        assertEquals("feignEnable should match", original.feignEnable, loaded.feignEnable)
        assertEquals("httpTimeOut should match", original.httpTimeOut, loaded.httpTimeOut)
        assertEquals("postmanWorkspace should match", original.postmanWorkspace, loaded.postmanWorkspace)
    }

    fun testReadReturnsDefaultSettingsWhenEmpty() {
        val binder = XmlSettingBinder(project)
        val settings = binder.read()

        assertNotNull("Settings should not be null", settings)
    }
}
