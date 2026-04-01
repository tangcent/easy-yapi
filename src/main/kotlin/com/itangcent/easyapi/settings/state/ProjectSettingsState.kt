package com.itangcent.easyapi.settings.state

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Project-level settings state for EasyAPI plugin.
 * 
 * Stores settings specific to individual projects,
 * persisted in `easyapi.xml` within the project.
 * 
 * Settings include:
 * - Postman workspace and collection preferences
 * - Built-in and remote configuration toggles
 */
@State(name = "EasyApiProjectSettings", storages = [Storage("easyapi.xml")])
class ProjectSettingsState : PersistentStateComponent<ProjectSettingsState.State> {
    /**
     * Data class holding all project-level settings.
     * Implements ProjectSettingsSupport for consistent access.
     */
    data class State(
        override var postmanWorkspace: String? = null,
        override var postmanExportMode: String? = defaultPostmanExportMode(),
        override var postmanCollections: String? = null,
        override var postmanBuildExample: Boolean = true,
        var builtInConfig: Boolean = true,
        var remoteConfig: String? = null,
        var recommendConfig: String? = null,
        var postmanToken: String? = null
    ) : ProjectSettingsSupport

    private var state: State = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }
}
