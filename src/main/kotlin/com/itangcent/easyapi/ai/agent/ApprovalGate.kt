package com.itangcent.easyapi.ai.agent

/**
 * Suspends an ACTION tool until the user approves or rejects it
 *.
 *
 * The chat UI provides an implementation that surfaces an
 * [AgentEvent.ApprovalRequested], waits for the user's decision, and resumes
 * the suspended coroutine. Test code can use a fake that auto-approves.
 */
interface ApprovalGate {

    /**
     * Await the user's decision on whether to run [toolName] with [args].
     *
     * @return `true` if the user approved; `false` if rejected.
     */
    suspend fun await(toolName: String, args: Map<String, Any?>): Boolean
}
