package com.itangcent.easyapi.settings.module

import com.itangcent.easyapi.settings.Scope
import com.itangcent.easyapi.settings.Settings
import com.itangcent.easyapi.settings.StorageScope

/**
 * General framework toggles, scan options, and output preferences.
 *
 * Tab-aligned with the "General" settings tab.
 *
 * Persisted at APPLICATION scope via the unified [com.itangcent.easyapi.settings.state.UnifiedAppSettingsState].
 */
data class GeneralSettings(
    @StorageScope(Scope.APPLICATION) var feignEnable: Boolean = false,
    @StorageScope(Scope.APPLICATION) var jaxrsEnable: Boolean = true,
    @StorageScope(Scope.APPLICATION) var actuatorEnable: Boolean = false,
    @StorageScope(Scope.APPLICATION) var autoScanEnabled: Boolean = true,
    @StorageScope(Scope.APPLICATION) var concurrentScanEnabled: Boolean = false,
    @StorageScope(Scope.APPLICATION) var gutterIconEnabled: Boolean = true,
    @StorageScope(Scope.APPLICATION) var switchNotice: Boolean = true,
    @StorageScope(Scope.APPLICATION) var enumFieldAutoInferEnabled: Boolean = false,
    @StorageScope(Scope.APPLICATION) var logLevel: Int = 100,
    @StorageScope(Scope.APPLICATION) var outputDemo: Boolean = true,
    @StorageScope(Scope.APPLICATION) var outputCharset: String = "UTF-8"
) : Settings
