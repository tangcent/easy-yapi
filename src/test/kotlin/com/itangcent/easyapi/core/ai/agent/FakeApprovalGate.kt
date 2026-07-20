package com.itangcent.easyapi.core.ai.agent

/**
 * Test double for [ApprovalGate] — test helper.
 *
 * Configurable to auto-approve / auto-reject, and records the last
 * `(toolName, args)` it was asked about so the test can assert what the
 * agent requested approval for.
 *
 * For tests that need a sequence of decisions (approve, then reject, …),
 * set [responses] — each `await` call pops the head of the list. When
 * [responses] is non-empty it takes precedence over [shouldApprove].
 */
class FakeApprovalGate : ApprovalGate {

    /** Default decision when [responses] is empty. */
    var shouldApprove: Boolean = false

    /** Per-call decision queue. Takes precedence over [shouldApprove]. */
    val responses: ArrayDeque<Boolean> = ArrayDeque()

    /** Whether [await] was called at all. */
    var wasConsulted: Boolean = false
        private set

    /** The last `(toolName, args)` passed to [await], or `null` if never called. */
    var lastTool: String? = null
        private set
    var lastArgs: Map<String, Any?>? = null
        private set

    override suspend fun await(toolName: String, args: Map<String, Any?>): Boolean {
        wasConsulted = true
        lastTool = toolName
        lastArgs = args
        return if (responses.isNotEmpty()) responses.removeFirst() else shouldApprove
    }
}
