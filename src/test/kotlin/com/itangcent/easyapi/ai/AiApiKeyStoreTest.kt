package com.itangcent.easyapi.ai

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.CredentialStore
import com.intellij.credentialStore.Credentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure unit tests for [AiApiKeyStore].
 *
 * [AiApiKeyStore] talks to a [CredentialStore] (an interface), so tests inject
 * an in-memory fake — no IntelliJ application / `PasswordSafe` service needed.
 */
class AiApiKeyStoreTest {

    /** In-memory [CredentialStore] backed by a mutable map. */
    private class FakeCredentialStore : CredentialStore {
        val entries: MutableMap<CredentialAttributes, Credentials> = mutableMapOf()

        override fun get(attributes: CredentialAttributes): Credentials? = entries[attributes]

        override fun set(attributes: CredentialAttributes, credentials: Credentials?) {
            if (credentials == null) {
                entries.remove(attributes)
            } else {
                entries[attributes] = credentials
            }
        }
    }

    private fun newStore() = FakeCredentialStore()

    // --- loadApiKey: absent → empty string ---

    @Test
    fun testLoadReturnsEmptyStringWhenNothingStored() {
        val store = newStore()
        assertEquals("", AiApiKeyStore.loadApiKey(store))
    }

    // --- save + load round-trip ---

    @Test
    fun testSaveThenLoadRoundTripsKey() {
        val store = newStore()
        AiApiKeyStore.saveApiKey("sk-secret", store)
        assertEquals("sk-secret", AiApiKeyStore.loadApiKey(store))
    }

    @Test
    fun testOverwriteReplacesPreviousKey() {
        val store = newStore()
        AiApiKeyStore.saveApiKey("sk-first", store)
        AiApiKeyStore.saveApiKey("sk-second", store)

        assertEquals("sk-second", AiApiKeyStore.loadApiKey(store))
        assertEquals("only one entry should exist after overwrite", 1, store.entries.size)
    }

    // --- saveApiKey: blank is stored as an empty credential (legacy parity) ---
    //
    // The historical PasswordStorage#storePassword stored whatever string was
    // passed, including "". AiApiKeyStore preserves that exactly so an empty
    // field round-trips (save "" → load ""). Note this leaves an entry with an
    // empty password rather than removing it — matching prior behavior; a
    // "clear on blank" semantic would be a behaviour change, out of scope here.

    @Test
    fun testSaveBlankRoundTripsToEmpty() {
        val store = newStore()
        AiApiKeyStore.saveApiKey("sk-present", store)
        AiApiKeyStore.saveApiKey("", store)

        assertEquals("", AiApiKeyStore.loadApiKey(store))
    }

    @Test
    fun testSaveNullEquivalentClearsStoredKey() {
        val store = newStore()
        AiApiKeyStore.saveApiKey("sk-present", store)
        // setPassword with null should behave as a clear (matches CredentialStore contract).
        store.set(AiApiKeyStore.attributes, null)

        assertEquals("", AiApiKeyStore.loadApiKey(store))
        assertNull("explicit null removes the entry", store.get(AiApiKeyStore.attributes))
    }

    // --- Key-derivation contract (backward compatibility) ---
    //
    // These two tests pin the exact serviceName/userName so keys saved by
    // existing installs (via the legacy PasswordStorage overload) keep
    // resolving. If these ever change intentionally, a migration is required.

    @Test
    fun testServiceNameMatchesLegacyRequesterClassName() {
        // Legacy overload built CredentialAttributes(requesterClass.getName(), key, requesterClass).
        assertEquals(
            "serviceName must equal AiRuntimeConfig FQCN for backward compat",
            AiRuntimeConfig::class.java.name,
            AiApiKeyStore.attributes.serviceName
        )
    }

    @Test
    fun testUserNameMatchesLegacyStoreKey() {
        assertEquals(
            "userName must equal the historical store key 'ai-api-key'",
            "ai-api-key",
            AiApiKeyStore.attributes.userName
        )
    }

    /**
     * The strongest backward-compat guarantee: a credential written exactly as
     * the legacy overload did must be readable through [AiApiKeyStore].
     */
    @Test
    fun testLegacyWrittenKeyIsReadableThroughStore() {
        val store = newStore()
        // Mimic the legacy write path: new CredentialAttributes(FQCN, key) + Credentials(userName, password).
        val legacyAttrs = CredentialAttributes(
            serviceName = AiRuntimeConfig::class.java.name,
            userName = "ai-api-key"
        )
        store.set(legacyAttrs, Credentials(user = "ai-api-key", password = "sk-legacy"))

        assertEquals("sk-legacy", AiApiKeyStore.loadApiKey(store))
    }

    // --- empty string vs null in storage ---

    @Test
    fun testStoredEmptyPasswordLoadsAsEmptyString() {
        val store = newStore()
        AiApiKeyStore.saveApiKey("", store) // nothing written
        AiApiKeyStore.saveApiKey("sk-real", store)
        store.set(
            AiApiKeyStore.attributes,
            Credentials(user = "ai-api-key", password = "") // explicit empty
        )

        assertEquals("", AiApiKeyStore.loadApiKey(store))
    }

    @Test
    fun testAttributesAreStableAcrossCalls() {
        // The same attributes instance must always be used (single owner of the key).
        val a1 = AiApiKeyStore.attributes
        val a2 = AiApiKeyStore.attributes
        assertEquals("attributes must be a stable singleton", a1, a2)
    }

    @Test
    fun testLoadWithExplicitNullFromStoreReturnsEmpty() {
        val store = newStore()
        store.set(AiApiKeyStore.attributes, null)
        assertNull(store.get(AiApiKeyStore.attributes))
        assertEquals("", AiApiKeyStore.loadApiKey(store))
    }
}
