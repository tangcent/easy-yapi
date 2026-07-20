package com.itangcent.easyapi.core.settings.module

import com.itangcent.easyapi.core.ai.AiProvider
import com.itangcent.easyapi.core.settings.Scope
import com.itangcent.easyapi.core.settings.Settings
import com.itangcent.easyapi.core.settings.StorageScope

/**
 * AI provider settings: provider name, base URL, model, timeout, request limits, context window.
 *
 * Tab-aligned with the "AI" settings tab.
 *
 * Persisted at APPLICATION scope via the unified [com.itangcent.easyapi.core.settings.state.UnifiedAppSettingsState].
 *
 * Final name is `AiSettings` (the persistent state module). The runtime resolved config is `com.itangcent.easyapi.core.ai.AiRuntimeConfig`.
 */
data class AiSettings(
    @StorageScope(Scope.APPLICATION) var aiProvider: String = "OPENAI",
    @StorageScope(Scope.APPLICATION) var aiBaseUrl: String = "",
    @StorageScope(Scope.APPLICATION) var aiModel: String = "",
    @StorageScope(Scope.APPLICATION) var aiRequestTimeoutSec: Int = 60,
    @StorageScope(Scope.APPLICATION) var aiMaxRequests: Int = 100,
    @StorageScope(Scope.APPLICATION) var aiContextWindow: Int = AiProvider.DEFAULT_CONTEXT_WINDOW
) : Settings
