package com.itangcent.idea.plugin.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "EasyApiSetting",
        storages = [Storage("EasyApiSetting.xml")])
class XmlSettingBinder : PersistentStateComponent<Settings>, SettingBinder {
    override fun tryRead(): Settings? {
        return state
    }

    override fun read(): Settings {
        return state ?: Settings()
    }

    override fun save(t: Settings?) {
        loadState(t)
    }

    private var settings: Settings? = null

    override fun getState(): Settings? {
        return settings
    }

    override fun loadState(state: Settings?) {
        this.settings = state?.copy()
    }

    override fun noStateLoaded() {
        settings = Settings()
    }
}