package com.itangcent.idea.plugin.settings.xml

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "EasyApiSetting",
    storages = [Storage(value = "EasyApiSetting.xml", roamingType = RoamingType.DISABLED)]
)
class ApplicationSettingsComponent : PersistentStateComponent<ApplicationSettings> {

    private var applicationSettings: ApplicationSettings? = null

    override fun getState(): ApplicationSettings? {
        return applicationSettings?.copy()
    }

    override fun loadState(state: ApplicationSettings?) {
        this.applicationSettings = state
    }
}