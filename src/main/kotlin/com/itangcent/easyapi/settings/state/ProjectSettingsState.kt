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
 * - Project-specific YAPI tokens
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
        override var projectEnvironments: String = "",
        override var yapiTokens: String? = null,
        override var disabledAutoRuleFiles: Array<String> = emptyArray(),
        var builtInConfig: Boolean = true,
        var remoteConfig: String? = null,
        var recommendConfig: String? = null,
        var postmanToken: String? = null,
        var yapiServer: String? = null,
        var yapiToken: String? = null
    ) : ProjectSettingsSupport {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as State
            if (postmanWorkspace != other.postmanWorkspace) return false
            if (postmanExportMode != other.postmanExportMode) return false
            if (postmanCollections != other.postmanCollections) return false
            if (postmanBuildExample != other.postmanBuildExample) return false
            if (projectEnvironments != other.projectEnvironments) return false
            if (yapiTokens != other.yapiTokens) return false
            if (!disabledAutoRuleFiles.contentEquals(other.disabledAutoRuleFiles)) return false
            if (builtInConfig != other.builtInConfig) return false
            if (remoteConfig != other.remoteConfig) return false
            if (recommendConfig != other.recommendConfig) return false
            if (postmanToken != other.postmanToken) return false
            if (yapiServer != other.yapiServer) return false
            if (yapiToken != other.yapiToken) return false
            return true
        }

        override fun hashCode(): Int {
            var result = postmanWorkspace?.hashCode() ?: 0
            result = 31 * result + (postmanExportMode?.hashCode() ?: 0)
            result = 31 * result + (postmanCollections?.hashCode() ?: 0)
            result = 31 * result + postmanBuildExample.hashCode()
            result = 31 * result + projectEnvironments.hashCode()
            result = 31 * result + (yapiTokens?.hashCode() ?: 0)
            result = 31 * result + disabledAutoRuleFiles.contentHashCode()
            result = 31 * result + builtInConfig.hashCode()
            result = 31 * result + (remoteConfig?.hashCode() ?: 0)
            result = 31 * result + (recommendConfig?.hashCode() ?: 0)
            result = 31 * result + (postmanToken?.hashCode() ?: 0)
            result = 31 * result + (yapiServer?.hashCode() ?: 0)
            result = 31 * result + (yapiToken?.hashCode() ?: 0)
            return result
        }
    }

    private var state: State = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }
}
