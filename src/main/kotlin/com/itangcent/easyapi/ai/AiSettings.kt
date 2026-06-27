package com.itangcent.easyapi.ai

import com.intellij.openapi.project.Project
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.itangcent.easyapi.settings.SettingBinder

/**
 * Resolved AI configuration ready for use by the agent subsystem.
 *
 * The API key is loaded from [PasswordSafe] (encrypted per-platform) and never
 * persisted in [com.itangcent.easyapi.settings.Settings].
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
data class AiSettings(
    val provider: AiProvider,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val requestTimeoutSec: Int,
    val maxRequests: Int,
    val contextWindow: Int = provider.contextWindow
) {
    companion object {
        private const val API_KEY_STORE_KEY = "ai-api-key"

        /**
         * Loads AI settings from [SettingBinder] + [PasswordSafe].
         *
         * @return `null` if the provider requires an API key but none is stored,
         * or if the base URL / model cannot be resolved.
         */
        fun load(project: Project): AiSettings? {
            val s = SettingBinder.getInstance(project).read()
            val provider = runCatching { AiProvider.valueOf(s.aiProvider) }
.getOrDefault(AiProvider.OPENAI)
            val baseUrl = s.aiBaseUrl.takeIf { it.isNotBlank() }
                ?: provider.defaultBaseUrl ?: return null
            val apiKey = passwordSafe().getPassword(
                null, AiSettings::class.java, API_KEY_STORE_KEY
            )?.toString() ?: ""
            if (provider.requiresApiKey && apiKey.isBlank()) return null
            val model = s.aiModel.takeIf { it.isNotBlank() }
                ?: provider.defaultModel ?: return null
            // 0 (or any non-positive value) means "auto": use the provider's
            // per-model default. A user-set value overrides it.
            val contextWindow = s.aiContextWindow.takeIf { it > 0 }
                ?: provider.contextWindow
            return AiSettings(
                provider, baseUrl, apiKey, model,
                s.aiRequestTimeoutSec, s.aiMaxRequests, contextWindow
            )
        }

        /** Resolve PasswordSafe via the application service container. */
        private fun passwordSafe(): PasswordSafe =
            ApplicationManager.getApplication().getService(PasswordSafe::class.java)
    }
}
