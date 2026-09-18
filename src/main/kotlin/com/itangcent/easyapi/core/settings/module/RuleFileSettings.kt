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
 * [enabledExtensionCodes].
 *
 * Checked extensions are written as plain codes. An extension that is unchecked
 * but enabled by default must be written as an explicit `-<code>` exclusion:
 * writing only the checked codes drops the deselection, and the next read falls
 * back to `defaultEnabled` and silently re-checks it (issue #1461). Unchecked
 * extensions that are disabled by default need no entry.
 */
fun RuleFileSettings.updateExtensionCodes(checkedCodes: Collection<String>) {
    val checked = checkedCodes.toSet()
    extensionConfigs = ExtensionConfigRegistry.allExtensions()
        .filter { it.code.isNotBlank() && (checked.contains(it.code) || it.defaultEnabled) }
        .joinToString(",") { if (checked.contains(it.code)) it.code else "-${it.code}" }
}
