package com.itangcent.easyapi.core.cache.api

import com.itangcent.easyapi.core.settings.SettingBinder
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.core.settings.settings
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class ApiFileChangeListenerTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var listener: ApiFileChangeListener

    override fun setUp() {
        super.setUp()
        listener = ApiFileChangeListener.getInstance(project)
    }

    override fun tearDown() {
        runBlocking {
            listener.dispose()
        }
        super.tearDown()
    }

    fun testGetInstance() {
        assertNotNull(listener)
        assertSame(listener, ApiFileChangeListener.getInstance(project))
    }

    fun testStart() {
        listener.start()
    }

    fun testAfterWithNoEvents() {
        listener.start()
        listener.after(mutableListOf())
        runBlocking {
            delay(100)
        }
    }

    fun testAutoScanDisabled() {
        project.settings<GeneralSettings>().autoScanEnabled = false
        // The update via SettingBinder persists the mutated module state.
        SettingBinder.getInstance(project).save(project.settings<GeneralSettings>())

        listener.start()
        listener.after(mutableListOf())

        runBlocking {
            delay(100)
        }

        // Reset
        project.settings<GeneralSettings>().autoScanEnabled = true
        SettingBinder.getInstance(project).save(project.settings<GeneralSettings>())
    }
}
