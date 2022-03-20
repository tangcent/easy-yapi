package com.itangcent.idea.plugin.settings.xml

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "EasyApiProjectSetting",
    storages = [Storage("EasyApiProjectSetting.xml")]
)
class ProjectSettingsComponent : PersistentStateComponent<ProjectSettings> {

    private var projectSettings: ProjectSettings? = null

    override fun getState(): ProjectSettings? {
        return projectSettings?.copy()
    }

    override fun loadState(state: ProjectSettings) {
        this.projectSettings = state
    }
}