package com.itangcent.easyapi.settings.module

import com.itangcent.easyapi.extension.ExtensionConfigRegistry
import com.itangcent.easyapi.settings.Scope
import com.itangcent.easyapi.settings.Settings
import com.itangcent.easyapi.settings.StorageScope

/**
 * Rule file settings: extension configs, built-in config, remote config,
 * disabled rule files.
 *
 * Mixed-scope module:
 * - APP fields: `extensionConfigs`, `builtInConfig` (String?), `remoteConfig` (Array<String>), `disabledGlobalRuleFiles`
 * - PROJ fields: `projectBuiltInConfigEnabled` (renamed from `builtInConfig:Boolean`),
 *   `projectRemoteConfig` (renamed from `remoteConfig:String?`), `recommendConfig`
 *
 * The PROJ `builtInConfig`/`remoteConfig` are *different* settings from the APP forms
 * of the same name — renamed here to disambiguate. Migration maps old keys.
 *
 * Tab-aligned with the "RuleFile" settings tab.
 *
 * Persisted via the unified state components ([com.itangcent.easyapi.settings.state.UnifiedAppSettingsState] / [com.itangcent.easyapi.settings.state.UnifiedProjectSettingsState]).
 */
data class RuleFileSettings(
    // ---- APPLICATION scope ----
    @StorageScope(Scope.APPLICATION) var extensionConfigs: String = defaultExtensionCodes(),
    @StorageScope(Scope.APPLICATION) var builtInConfig: String? = null,
    @StorageScope(Scope.APPLICATION) var remoteConfig: Array<String> = emptyArray(),
    @StorageScope(Scope.APPLICATION) var disabledGlobalRuleFiles: Array<String> = emptyArray(),

    // ---- PROJECT scope (renamed to disambiguate from APP forms) ----
    @StorageScope(Scope.PROJECT) var projectBuiltInConfigEnabled: Boolean = true,
    @StorageScope(Scope.PROJECT) var projectRemoteConfig: String? = null,
    @StorageScope(Scope.PROJECT) var recommendConfig: String? = null
) : Settings {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RuleFileSettings
        if (extensionConfigs != other.extensionConfigs) return false
        if (builtInConfig != other.builtInConfig) return false
        if (!remoteConfig.contentEquals(other.remoteConfig)) return false
        if (!disabledGlobalRuleFiles.contentEquals(other.disabledGlobalRuleFiles)) return false
        if (projectBuiltInConfigEnabled != other.projectBuiltInConfigEnabled) return false
        if (projectRemoteConfig != other.projectRemoteConfig) return false
        if (recommendConfig != other.recommendConfig) return false
        return true
    }

    override fun hashCode(): Int {
        var result = extensionConfigs.hashCode()
        result = 31 * result + (builtInConfig?.hashCode() ?: 0)
        result = 31 * result + remoteConfig.contentHashCode()
        result = 31 * result + disabledGlobalRuleFiles.contentHashCode()
        result = 31 * result + projectBuiltInConfigEnabled.hashCode()
        result = 31 * result + (projectRemoteConfig?.hashCode() ?: 0)
        result = 31 * result + (recommendConfig?.hashCode() ?: 0)
        return result
    }

    companion object {
        private fun defaultExtensionCodes(): String =
            ExtensionConfigRegistry.codesToString(ExtensionConfigRegistry.defaultCodes())
    }
}
