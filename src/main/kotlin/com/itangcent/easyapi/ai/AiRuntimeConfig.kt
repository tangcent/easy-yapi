package com.itangcent.easyapi.ai

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.settings.module.AiSettings
import com.itangcent.easyapi.settings.settings

/**
 * Resolved AI configuration ready for use by the agent subsystem.
 *
 * The API key is loaded from [PasswordSafe] (encrypted per-platform) and never
 * persisted in [AiSettings].
 *
 * @param provider The selected provider.
 * @param baseUrl Resolved base URL (settings override → provider default).
 * @param apiKey Decrypted API key (may be blank for providers that don't require one).
 * @param model Resolved model identifier.
 * @param requestTimeoutSec HTTP request timeout in seconds.
 * @param maxRequests Maximum requests per agent turn; asks to confirm when the limit is reached.
 * @param contextWindow Approximate context window (in tokens) of [model],
 * used to derive the agent's token budget. Defaults to [AiProvider.contextWindow]
 * of [provider]; pass an explicit value when a per-model window is known.
 */
data class AiRuntimeConfig(
    val provider: AiProvider,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val requestTimeoutSec: Int,
    val maxRequests: Int,
    val contextWindow: Int = provider.contextWindow
) {
    companion object {
        /**
         * Loads AI settings from [SettingBinder] + [AiApiKeyStore].
         *
         * @return `null` if the provider requires an API key but none is stored,
         * or if the base URL / model cannot be resolved.
         */
        fun load(project: Project): AiRuntimeConfig? {
            val s = project.settings<AiSettings>()
            val provider = runCatching { AiProvider.valueOf(s.aiProvider) }
                .getOrDefault(AiProvider.OPENAI)
            val baseUrl = s.aiBaseUrl.takeIf { it.isNotBlank() }
                ?: provider.defaultBaseUrl ?: return null
            val apiKey = AiApiKeyStore.loadApiKey()
            if (provider.requiresApiKey && apiKey.isBlank()) return null
            val model = s.aiModel.takeIf { it.isNotBlank() }
                ?: provider.defaultModel ?: return null
            // A legacy persisted `0` (the old "Auto" sentinel, before the
            // default became DEFAULT_CONTEXT_WINDOW) is treated as the
            // provider's per-model default so old installs migrate cleanly.
            val contextWindow = s.aiContextWindow.takeIf { it > 0 }
                ?: provider.contextWindow
            return AiRuntimeConfig(
                provider, baseUrl, apiKey, model,
                s.aiRequestTimeoutSec, s.aiMaxRequests, contextWindow
            )
        }
    }
}
