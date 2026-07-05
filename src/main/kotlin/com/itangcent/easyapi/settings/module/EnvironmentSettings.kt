package com.itangcent.easyapi.settings.module

import com.itangcent.easyapi.settings.Scope
import com.itangcent.easyapi.settings.Settings
import com.itangcent.easyapi.settings.StorageScope

/**
 * Environment settings: project environments, disabled auto rule files,
 * and global (cross-project) environments.
 *
 * Mixed-scope module:
 * - APP field: `globalEnvironments`
 * - PROJ fields: `projectEnvironments`, `disabledAutoRuleFiles`
 *
 * Persisted via the unified state components
 * ([com.itangcent.easyapi.settings.state.UnifiedAppSettingsState] /
 * [com.itangcent.easyapi.settings.state.UnifiedProjectSettingsState]).
 */
data class EnvironmentSettings(
    // ---- APPLICATION scope ----
    @StorageScope(Scope.APPLICATION) var globalEnvironments: String = "",

    // ---- PROJECT scope ----
    @StorageScope(Scope.PROJECT) var projectEnvironments: String = "",
    @StorageScope(Scope.PROJECT) var disabledAutoRuleFiles: Array<String> = emptyArray()
) : Settings {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EnvironmentSettings
        if (globalEnvironments != other.globalEnvironments) return false
        if (projectEnvironments != other.projectEnvironments) return false
        if (!disabledAutoRuleFiles.contentEquals(other.disabledAutoRuleFiles)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = globalEnvironments.hashCode()
        result = 31 * result + projectEnvironments.hashCode()
        result = 31 * result + disabledAutoRuleFiles.contentHashCode()
        return result
    }
}
