package com.itangcent.easyapi.ai

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.CredentialStore
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager

/**
 * Stores and retrieves the AI provider API key in IntelliJ's [PasswordSafe].
 *
 * The API key never lives in [com.itangcent.easyapi.settings.Settings]; it is
 * persisted encrypted via the platform credential store so it never leaks into
 * the plain-text settings XML or exported settings files.
 *
 * This object centralises all access so the key-derivation contract (the
 * `serviceName`/`userName` below) has a single owner, and so that callers stay
 * trivially testable — [CredentialStore] is a small interface that a test can
 * fake with an in-memory map.
 */
object AiApiKeyStore {

    /**
     * Credential attributes under which the AI API key is stored.
     *
     * `serviceName`/`userName` deliberately match the keys produced by the
     * legacy `PasswordStorage.getPassword(project, requesterClass, key)`
     * overload (service = `requesterClass.getName()`, user = `key`) so that keys
     * saved by existing installs keep resolving. Do NOT switch to
     * [com.intellij.credentialStore.generateServiceName] — that would change the
     * key and orphan previously stored credentials.
     */
    internal val attributes: CredentialAttributes = CredentialAttributes(
        serviceName = AiRuntimeConfig::class.java.name,
        userName = API_KEY_STORE_KEY
    )

    private const val API_KEY_STORE_KEY = "ai-api-key"

    /**
     * Reads the stored API key.
     *
     * @param store Credential store to read from. Defaults to the application
     * [PasswordSafe]; tests pass an in-memory fake.
     * @return the decrypted key, or an empty string when nothing is stored
     * (e.g. a fresh install, or a provider like OLLAMA that needs no key).
     */
    fun loadApiKey(store: CredentialStore = defaultStore()): String =
        store.getPassword(attributes) ?: ""

    /**
     * Persists the API key. A blank [apiKey] clears any stored value.
     *
     * @param store Credential store to write to. Defaults to the application
     * [PasswordSafe]; tests pass an in-memory fake.
     */
    fun saveApiKey(apiKey: String, store: CredentialStore = defaultStore()) {
        store.setPassword(attributes, apiKey)
    }

    /**
     * Resolves the application-wide [PasswordSafe] as the default store.
     *
     * Pulled into a function (not a property) so it is re-evaluated on each
     * call — the application/service container may not be initialised at
     * class-load time.
     */
    private fun defaultStore(): CredentialStore =
        ApplicationManager.getApplication().getService(PasswordSafe::class.java)
}
