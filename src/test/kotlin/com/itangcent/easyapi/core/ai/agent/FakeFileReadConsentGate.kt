package com.itangcent.easyapi.core.ai.agent

/**
 * Test double for [FileReadConsentGate].
 *
 * Returns the scripted [grant] decision and records the path it was asked
 * about, so tests can assert that consent was requested (or not) and which
 * path triggered it.
 */
class FakeFileReadConsentGate(
    private val grant: Boolean = false
) : FileReadConsentGate {

    /** Whether [await] was called at all. */
    var wasConsulted: Boolean = false
        private set

    /** The last path passed to [await], or `null` if never called. */
    var lastRequestedPath: String? = null
        private set

    override suspend fun await(requestedPath: String): Boolean {
        wasConsulted = true
        lastRequestedPath = requestedPath
        return grant
    }
}
