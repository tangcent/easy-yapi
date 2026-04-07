package com.itangcent.easyapi.cache

import com.itangcent.easyapi.settings.SettingBinder
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
        val settings = SettingBinder.getInstance(project).read()
        settings.autoScanEnabled = false
        SettingBinder.getInstance(project).save(settings)

        listener.start()
        listener.after(mutableListOf())

        runBlocking {
            delay(100)
        }

        // Reset
        settings.autoScanEnabled = true
        SettingBinder.getInstance(project).save(settings)
    }
}
