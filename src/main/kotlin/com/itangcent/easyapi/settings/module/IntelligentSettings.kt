package com.itangcent.easyapi.settings.module

import com.itangcent.easyapi.settings.Scope
import com.itangcent.easyapi.settings.Settings
import com.itangcent.easyapi.settings.StorageScope

/**
 * Intelligent inference settings: return-type inference, URL templating,
 * expanded query/form panels, path multi, global environments.
 *
 * Tab-aligned with the "Intelligent" settings tab.
 *
 * Persisted at APPLICATION scope via the unified [com.itangcent.easyapi.settings.state.UnifiedAppSettingsState].
 */
data class IntelligentSettings(
    @StorageScope(Scope.APPLICATION) var inferReturnMain: Boolean = true,
    @StorageScope(Scope.APPLICATION) var enableUrlTemplating: Boolean = true,
    @StorageScope(Scope.APPLICATION) var queryExpanded: Boolean = true,
    @StorageScope(Scope.APPLICATION) var formExpanded: Boolean = true,
    @StorageScope(Scope.APPLICATION) var pathMulti: String = "ALL",
    @StorageScope(Scope.APPLICATION) var globalEnvironments: String = ""
) : Settings
