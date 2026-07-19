package com.itangcent.easyapi.core.settings.module

import com.itangcent.easyapi.core.settings.HttpClientType
import com.itangcent.easyapi.core.settings.Scope
import com.itangcent.easyapi.core.settings.Settings
import com.itangcent.easyapi.core.settings.StorageScope

/**
 * HTTP client configuration: timeout, SSL, client type.
 *
 * Tab-aligned with the "HTTP" settings tab.
 *
 * Persisted at APPLICATION scope via the unified [com.itangcent.easyapi.core.settings.state.UnifiedAppSettingsState].
 */
data class HttpSettings(
    @StorageScope(Scope.APPLICATION) var httpTimeOut: Int = 30,
    @StorageScope(Scope.APPLICATION) var unsafeSsl: Boolean = false,
    @StorageScope(Scope.APPLICATION) var httpClient: String = HttpClientType.APACHE.value
) : Settings
