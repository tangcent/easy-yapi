package com.itangcent.easyapi.ai.agent

/**
 * Test double for [ClarificationGate] — test helper.
 *
 * Returns the scripted [answers] immediately and records the request it was
 * asked about, so tests can assert what the agent posed and that the answers
 * flow back into the loop.
 */
class FakeClarificationGate(
    private val answers: ClarificationAnswers = ClarificationAnswers(emptyMap())
) : ClarificationGate {

    /** Whether [await] was called at all. */
    var wasConsulted: Boolean = false
        private set

    /** The last [Clarification] passed to [await], or `null` if never called. */
    var lastRequest: Clarification? = null
        private set

    override suspend fun await(request: Clarification): ClarificationAnswers {
        wasConsulted = true
        lastRequest = request
        return answers
    }
}
