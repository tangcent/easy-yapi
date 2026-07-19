package com.itangcent.easyapi.core.settings

import com.intellij.openapi.util.Disposer
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.Assert.*

class SettingsChangeListenerTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testOnSettingsChangedWithDisposableIsCalled() {
        var callCount = 0
        project.onSettingsChanged(Disposer.newDisposable()) {
            callCount++
        }

        project.messageBus.syncPublisher(SettingsChangeListener.TOPIC).settingsChanged()
        assertEquals("Handler should be invoked when settings change", 1, callCount)

        project.messageBus.syncPublisher(SettingsChangeListener.TOPIC).settingsChanged()
        assertEquals("Handler should be invoked on each settings change", 2, callCount)
    }

    fun testOnSettingsChangedWithoutDisposableIsCalled() {
        var callCount = 0
        project.onSettingsChanged {
            callCount++
        }

        project.messageBus.syncPublisher(SettingsChangeListener.TOPIC).settingsChanged()
        assertEquals("Handler should be invoked when settings change", 1, callCount)
    }

    fun testOnSettingsChangedFiresOnSettingBinderSave() {
        var callCount = 0
        project.onSettingsChanged(Disposer.newDisposable()) {
            callCount++
        }

        settingBinder.save(GeneralSettings())
        assertEquals("Saving settings should fire settingsChanged", 1, callCount)
    }
}
