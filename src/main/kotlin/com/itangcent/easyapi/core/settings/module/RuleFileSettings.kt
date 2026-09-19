package com.itangcent.easyapi.core.settings.module

import com.itangcent.easyapi.core.extension.ExtensionConfigRegistry
import com.itangcent.easyapi.core.settings.Scope
import com.itangcent.easyapi.core.settings.Settings
import com.itangcent.easyapi.core.settings.StorageScope

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
 * Persisted via the unified state components ([com.itangcent.easyapi.core.settings.state.UnifiedAppSettingsState] / [com.itangcent.easyapi.core.settings.state.UnifiedProjectSettingsState]).
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

/**
 * The raw `extensionConfigs` codes — positive codes mixed with `-<code>`
 * exclusions, exactly as persisted.
 *
 * This is the form [com.itangcent.easyapi.core.config.source.ExtensionConfigSource]
 * consumes. Do **not** hand it [enabledExtensionCodes] instead: that expansion
 * drops the `-<code>` entries, and an excluded default extension is then absent
 * from the set too, so the source's `defaultEnabled` fallback re-enables it and
 * issue #1461 comes straight back.
 */
fun RuleFileSettings.extensionCodes(): Array<String> =
    ExtensionConfigRegistry.stringToCodes(extensionConfigs)

/**
 * The extension codes that are actually enabled — [extensionCodes] expanded
 * through `defaultEnabled` and its `-<code>` exclusions. This is the form the
 * Extensions tab shows and edits; persist an edit with [updateExtensionCodes].
 */
fun RuleFileSettings.enabledExtensionCodes(): List<String> =
    ExtensionConfigRegistry.selectedCodes(extensionCodes()).toList()

/**
 * Persists [checkedCodes] into `extensionConfigs` — the encode counterpart of
 * [enabledExtensionCodes], delegated to [ExtensionConfigRegistry.encodeSelection].
 *
 * The grammar deliberately stays in the catalogue. Encoding a selection means
 * knowing which *unchecked* codes are on by default, so an encoder written here
 * would have to read `ExtensionConfig.defaultEnabled` itself and become a second
 * class deciding the same question — which is how the Extensions tab and the rule
 * engine ended up with divergent copies in the first place (#1461). This function
 * exists only so callers do not have to name the field.
 */
fun RuleFileSettings.updateExtensionCodes(checkedCodes: Collection<String>) {
    extensionConfigs = ExtensionConfigRegistry.encodeSelection(checkedCodes)
}
