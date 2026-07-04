package com.itangcent.easyapi.settings.module

import com.itangcent.easyapi.settings.Scope
import com.itangcent.easyapi.settings.Settings
import com.itangcent.easyapi.settings.StorageScope

/**
 * Environment settings: project environments, disabled auto rule files.
 *
 * Persisted at PROJECT scope via the unified [com.itangcent.easyapi.settings.state.UnifiedProjectSettingsState].
 */
data class EnvironmentSettings(
    @StorageScope(Scope.PROJECT) var projectEnvironments: String = "",
    @StorageScope(Scope.PROJECT) var disabledAutoRuleFiles: Array<String> = emptyArray()
) : Settings {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EnvironmentSettings
        if (projectEnvironments != other.projectEnvironments) return false
        if (!disabledAutoRuleFiles.contentEquals(other.disabledAutoRuleFiles)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = projectEnvironments.hashCode()
        result = 31 * result + disabledAutoRuleFiles.contentHashCode()
        return result
    }
}
