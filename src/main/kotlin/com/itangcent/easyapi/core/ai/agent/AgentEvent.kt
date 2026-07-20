package com.itangcent.easyapi.core.ai.agent

/**
 * Events emitted by the agent loop, consumed by the chat UI and tests
 *.
 */
sealed class AgentEvent {
    /** Reasoning phase started for step [step]. */
    data class Thinking(val step: Int) : AgentEvent()

    /** A perception tool started. */
    data class Perceiving(val tool: String, val args: String) : AgentEvent()

    /** An action tool started. */
    data class Acting(val tool: String, val args: String) : AgentEvent()

    /** A tool completed; [resultSummary] is a short description for the UI. */
    data class Observed(val tool: String, val resultSummary: String) : AgentEvent()

    /** An action tool is awaiting user approval. */
    data class ApprovalRequested(val tool: String, val args: String) : AgentEvent()

    /** The agent is asking the user one or more structured clarifying questions. */
    data class ClarificationRequested(val clarification: Clarification) : AgentEvent()

    /**
     * `read_rule_file` wants to read a file outside the allowed rule
     * directories and is awaiting a one-time user consent decision.
     */
    data class FileReadConsentRequested(val requestedPath: String) : AgentEvent()

    /** The assistant produced a text message for the user. */
    data class Message(val content: String) : AgentEvent()

    /** A rule proposal is staged and ready for the user to save. */
    data class ProposalReady(val proposal: Proposal) : AgentEvent()

    /** The loop failed terminally. */
    data class Failed(val reason: String) : AgentEvent()

    /**
     * The agent was detected repeating itself and the turn was terminated.
     *
     * Terminal — comparable to [Failed]: the turn has ended abnormally and
     * no [TurnComplete] / [ProposalReady] will follow. The UI should offer
     * loop-specific recovery.
     *
     * @param reason Human-readable loop-type label (e.g. "consecutive duplicate").
     * @param tool The tool involved in the loop, if any (`null` for
     * reasoning repetition).
     * @param count Repetition count derived from the triggering reason.
     */
    data class LoopDetected(val reason: String, val tool: String?, val count: Int) : AgentEvent()

    /**
     * A transient chat failure is being retried.
     *
     * Non-terminal — the turn is still in progress. The UI must NOT treat
     * this as a turn-end signal (no recovery dialog, no terminal handling);
     * the indicator is cleared on the next non-retry event. [Failed] is
     * reserved for terminal exhaustion only.
     *
     * @param attempt The current attempt number (1-based).
     * @param maxRetries The configured maximum number of retries.
     */
    data class Retrying(val attempt: Int, val maxRetries: Int) : AgentEvent()

    /** The current turn completed normally. */
    object TurnComplete : AgentEvent()
}
