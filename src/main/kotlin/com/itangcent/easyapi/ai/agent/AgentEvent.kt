package com.itangcent.easyapi.ai.agent

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

    /** The current turn completed normally. */
    object TurnComplete : AgentEvent()
}
