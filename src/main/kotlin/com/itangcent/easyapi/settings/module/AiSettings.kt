package com.itangcent.easyapi.settings.module

import com.itangcent.easyapi.ai.AiProvider
import com.itangcent.easyapi.settings.Scope
import com.itangcent.easyapi.settings.Settings
import com.itangcent.easyapi.settings.StorageScope

/**
 * AI provider settings: provider name, base URL, model, timeout, request limits, context window.
 *
 * Tab-aligned with the "AI" settings tab.
 *
 * Persisted at APPLICATION scope via the unified [com.itangcent.easyapi.settings.state.UnifiedAppSettingsState].
 *
 * Final name is `AiSettings` (the persistent state module). The runtime resolved config is `com.itangcent.easyapi.ai.AiRuntimeConfig`.
 */
data class AiSettings(
    @StorageScope(Scope.APPLICATION) var aiProvider: String = "OPENAI",
    @StorageScope(Scope.APPLICATION) var aiBaseUrl: String = "",
    @StorageScope(Scope.APPLICATION) var aiModel: String = "",
    @StorageScope(Scope.APPLICATION) var aiRequestTimeoutSec: Int = 60,
    @StorageScope(Scope.APPLICATION) var aiMaxRequests: Int = 100,
    @StorageScope(Scope.APPLICATION) var aiContextWindow: Int = AiProvider.DEFAULT_CONTEXT_WINDOW
) : Settings
