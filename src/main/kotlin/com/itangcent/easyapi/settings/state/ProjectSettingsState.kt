package com.itangcent.easyapi.settings.state

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.itangcent.easyapi.settings.PostmanExportMode

/**
 * Project-level settings state for EasyAPI plugin.
 *
 * **Deprecated.** Kept only as a readable fallback for the one-time settings
 * migration ([com.itangcent.easyapi.settings.migration.SettingsMigrationActivity])
 * that reads legacy `easyapi.xml` and ports it into the unified map-backed
 * state ([UnifiedProjectSettingsState]).
 *
 * New code must NOT read or write this state — use
 * [com.itangcent.easyapi.settings.SettingBinder] with the appropriate
 * [com.itangcent.easyapi.settings.Settings] subtype instead. This class will
 * be removed once the migration window closes.
 *
 * Persisted in `easyapi.xml` within the project.
 *
 * Settings include:
 * - Postman workspace and collection preferences
 * - Built-in and remote configuration toggles
 */
@Deprecated(
    "Legacy state kept only for one-time settings migration; use SettingBinder with a Settings subtype instead",
    level = DeprecationLevel.WARNING
)
@State(name = "EasyApiProjectSettings", storages = [Storage("easyapi.xml")])
class ProjectSettingsState : PersistentStateComponent<ProjectSettingsState.State> {
    /**
     * Data class holding all project-level settings.
     */
    data class State(
        var postmanWorkspace: String? = null,
        var postmanExportMode: String? = PostmanExportMode.CREATE_NEW.name,
        var postmanCollections: String? = null,
        var postmanBuildExample: Boolean = true,
        var projectEnvironments: String = "",
        var disabledAutoRuleFiles: Array<String> = emptyArray(),
        var builtInConfig: Boolean = true,
        var remoteConfig: String? = null,
        var recommendConfig: String? = null,
        var postmanToken: String? = null
    ) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as State
            if (postmanWorkspace != other.postmanWorkspace) return false
            if (postmanExportMode != other.postmanExportMode) return false
            if (postmanCollections != other.postmanCollections) return false
            if (postmanBuildExample != other.postmanBuildExample) return false
            if (projectEnvironments != other.projectEnvironments) return false
            if (!disabledAutoRuleFiles.contentEquals(other.disabledAutoRuleFiles)) return false
            if (builtInConfig != other.builtInConfig) return false
            if (remoteConfig != other.remoteConfig) return false
            if (recommendConfig != other.recommendConfig) return false
            if (postmanToken != other.postmanToken) return false
            return true
        }

        override fun hashCode(): Int {
            var result = postmanWorkspace?.hashCode() ?: 0
            result = 31 * result + (postmanExportMode?.hashCode() ?: 0)
            result = 31 * result + (postmanCollections?.hashCode() ?: 0)
            result = 31 * result + postmanBuildExample.hashCode()
            result = 31 * result + projectEnvironments.hashCode()
            result = 31 * result + disabledAutoRuleFiles.contentHashCode()
            result = 31 * result + builtInConfig.hashCode()
            result = 31 * result + (remoteConfig?.hashCode() ?: 0)
            result = 31 * result + (recommendConfig?.hashCode() ?: 0)
            result = 31 * result + (postmanToken?.hashCode() ?: 0)
            return result
        }

        fun copyTo(target: State) {
            target.postmanWorkspace = postmanWorkspace
            target.postmanExportMode = postmanExportMode
            target.postmanCollections = postmanCollections
            target.postmanBuildExample = postmanBuildExample
            target.projectEnvironments = projectEnvironments
            target.disabledAutoRuleFiles = disabledAutoRuleFiles
            target.builtInConfig = builtInConfig
            target.remoteConfig = remoteConfig
            target.recommendConfig = recommendConfig
            target.postmanToken = postmanToken
        }
    }

    private var state: State = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }
}
