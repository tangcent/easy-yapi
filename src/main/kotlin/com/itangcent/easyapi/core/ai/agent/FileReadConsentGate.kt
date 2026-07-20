package com.itangcent.easyapi.core.ai.agent

/**
 * Suspends `read_rule_file` until the user grants or denies one-time consent
 * to read a file outside the allowed rule directories.
 *
 * Mirrors [ClarificationGate]: the chat UI provides an implementation that
 * surfaces an [AgentEvent.FileReadConsentRequested], renders an inline
 * Approve/Reject card, and resumes the suspended coroutine with the user's
 * decision. Test code uses [NOOP] (denies) or a fake that returns a scripted
 * answer.
 */
interface FileReadConsentGate {

    /**
     * Await the user's decision on whether to read [requestedPath].
     *
     * @return `true` if the user approved the one-time read; `false` if denied.
     */
    suspend fun await(requestedPath: String): Boolean

    companion object {
        /**
         * A gate that denies every request.
         *
         * The safe default — used for [com.itangcent.easyapi.core.ai.tools.ToolContext]
         * so contexts that never wire a gate (e.g. most tests) preserve the
         * original refuse-outside-allow-list behavior.
         */
        val NOOP: FileReadConsentGate = object : FileReadConsentGate {
            override suspend fun await(requestedPath: String): Boolean = false
        }
    }
}
