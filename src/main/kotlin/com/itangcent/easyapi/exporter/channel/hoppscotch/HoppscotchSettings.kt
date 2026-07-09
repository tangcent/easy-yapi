package com.itangcent.easyapi.exporter.channel.hoppscotch

import com.itangcent.easyapi.settings.Scope
import com.itangcent.easyapi.settings.Settings
import com.itangcent.easyapi.settings.StorageScope

/**
 * Hoppscotch channel settings.
 *
 * All fields are APPLICATION scope, persisted via the unified [com.itangcent.easyapi.settings.state.UnifiedAppSettingsState].
 *
 * Replaces the old `var Settings.hoppscotchToken` extension accessors.
 */
data class HoppscotchSettings(
    @StorageScope(Scope.APPLICATION) var hoppscotchToken: String? = null,
    @StorageScope(Scope.APPLICATION) var hoppscotchServerUrl: String? = "https://hoppscotch.io",
    @StorageScope(Scope.APPLICATION) var hoppscotchBackendUrl: String? = null,
    @StorageScope(Scope.APPLICATION) var hoppscotchRefreshToken: String? = null
) : Settings
