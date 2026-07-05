package com.itangcent.easyapi.settings.module

import com.itangcent.easyapi.settings.Scope
import com.itangcent.easyapi.settings.Settings
import com.itangcent.easyapi.settings.StorageScope

/**
 * Parsing & output settings: return-type inference, enum-field inference,
 * URL templating, expanded query/form panels, path multi.
 *
 * Tab-aligned with the "Parsing & Output" settings tab.
 *
 * Persisted at APPLICATION scope via the unified [com.itangcent.easyapi.settings.state.UnifiedAppSettingsState].
 */
data class ParsingOutputSettings(
    @StorageScope(Scope.APPLICATION) var inferReturnMain: Boolean = true,
    @StorageScope(Scope.APPLICATION) var enableUrlTemplating: Boolean = true,
    @StorageScope(Scope.APPLICATION) var queryExpanded: Boolean = true,
    @StorageScope(Scope.APPLICATION) var formExpanded: Boolean = true,
    @StorageScope(Scope.APPLICATION) var pathMulti: String = "ALL",
    @StorageScope(Scope.APPLICATION) var enumFieldAutoInferEnabled: Boolean = false
) : Settings
