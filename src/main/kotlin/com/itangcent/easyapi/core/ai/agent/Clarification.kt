package com.itangcent.easyapi.core.ai.agent

/**
 * Structured clarifying-question support.
 *
 * The agent uses the `ask_clarification` tool to pose one or more questions
 * to the user when a request is ambiguous. These types are the neutral domain
 * model: the tool builds a [Clarification] from its arguments, the chat UI
 * renders it as a single interactive card, and the user's choices come back as
 * [ClarificationAnswers].
 */

/** How a single clarification question is answered. */
enum class QuestionKind {
    /** Exactly one option (rendered as a radio-button group). */
    SINGLE_CHOICE,

    /** Zero or more options (rendered as a checkbox list). */
    MULTI_CHOICE,

    /** Free-form text (rendered as a text field; [ClarificationQuestion.options] is empty). */
    FREE_TEXT
}

/**
 * A selectable option for a choice question.
 *
 * @param value the value sent back to the agent when chosen.
 * @param label the human-facing label shown in the card.
 * @param isDefault whether this option is pre-selected.
 */
data class QuestionOption(
    val value: String,
    val label: String,
    val isDefault: Boolean = false
)

/**
 * One question within a [Clarification].
 *
 * @param id stable key the answer is filed under in [ClarificationAnswers].
 * @param text the question shown to the user.
 * @param kind how it is answered.
 * @param options the choices for [QuestionKind.SINGLE_CHOICE] / [QuestionKind.MULTI_CHOICE];
 * empty for [QuestionKind.FREE_TEXT].
 */
data class ClarificationQuestion(
    val id: String,
    val text: String,
    val kind: QuestionKind,
    val options: List<QuestionOption> = emptyList()
)

/**
 * A set of clarifying questions the agent wants answered before proceeding.
 *
 * @param prompt optional intro line shown above the questions.
 * @param questions the questions (at least one expected).
 */
data class Clarification(
    val prompt: String?,
    val questions: List<ClarificationQuestion>
)

/**
 * The user's answers, keyed by [ClarificationQuestion.id].
 *
 * A list per question covers [QuestionKind.MULTI_CHOICE] selections and an
 * optional "Other…" free-text value. A typed free-form reply that resolves a
 * pending card is filed under [RAW_KEY].
 */
data class ClarificationAnswers(val answers: Map<String, List<String>>) {
    companion object {
        /** Key used for a raw, typed free-form reply (not tied to a specific question). */
        const val RAW_KEY: String = "_raw"
    }
}
