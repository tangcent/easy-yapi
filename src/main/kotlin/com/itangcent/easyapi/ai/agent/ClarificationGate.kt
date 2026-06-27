package com.itangcent.easyapi.ai.agent

/**
 * Suspends the `ask_clarification` tool until the user answers.
 *
 * Mirrors [ApprovalGate]: the chat UI provides an implementation that surfaces
 * an [AgentEvent.ClarificationRequested], renders an interactive card, and
 * resumes the suspended coroutine when the user submits (or types a free-form
 * reply). Test code uses a fake that returns scripted answers.
 */
interface ClarificationGate {

    /**
     * Await the user's answers to [request].
     *
     * @return the answers keyed by [ClarificationQuestion.id].
     */
    suspend fun await(request: Clarification): ClarificationAnswers

    companion object {
        /**
         * A no-op gate that resolves immediately with no answers.
         *
         * Used as the default for [com.itangcent.easyapi.ai.tools.ToolContext]
         * so contexts that never ask for clarification (e.g. most tests) need
         * not wire a gate.
         */
        val NOOP: ClarificationGate = object : ClarificationGate {
            override suspend fun await(request: Clarification): ClarificationAnswers =
                ClarificationAnswers(emptyMap())
        }
    }
}
