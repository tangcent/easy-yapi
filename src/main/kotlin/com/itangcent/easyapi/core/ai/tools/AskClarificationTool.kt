package com.itangcent.easyapi.core.ai.tools

import com.itangcent.easyapi.core.ai.agent.Clarification
import com.itangcent.easyapi.core.ai.agent.ClarificationAnswers
import com.itangcent.easyapi.core.ai.agent.ClarificationQuestion
import com.itangcent.easyapi.core.ai.agent.QuestionKind
import com.itangcent.easyapi.core.ai.agent.QuestionOption

/**
 * Perception tool that asks the user one or more structured clarifying
 * questions.
 *
 * `kind = PERCEPTION` — asking the user is a way of gathering information, so
 * it runs immediately (no approval gate). It is **non-terminal**: it suspends
 * on [ToolContext.clarifications] until the user answers, then returns the
 * answers as a labeled [ToolResult.Text] so the agent can continue without
 * re-parsing prose.
 */
class AskClarificationTool : AiTool {

    override val name: String = NAME

    override val kind: ToolKind = ToolKind.PERCEPTION

    override val requiresApproval: Boolean = false

    /**
     * The tool suspends on [ToolContext.clarifications] until the user
     * answers, which can take minutes. Use a 5-minute timeout instead of the
     * 30-second default so the user has time to think and respond.
     */
    override val timeoutMs: Long = 5L * 60L * 1000L

    override val description: String =
        "Ask the user one or more clarifying questions when the request is " +
            "ambiguous. Provide concrete options for single_choice/multi_choice " +
            "questions so the user can answer with a click. Returns the user's " +
            "answers. This tool is NOT terminal — continue once you have them."

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "prompt" to mapOf(
                "type" to "string",
                "description" to "Optional intro line shown above the questions."
            ),
            "questions" to mapOf(
                "type" to "array",
                "description" to "One or more questions to ask the user.",
                "items" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "id" to mapOf(
                            "type" to "string",
                            "description" to "Stable identifier the answer is filed under."
                        ),
                        "text" to mapOf(
                            "type" to "string",
                            "description" to "The question text."
                        ),
                        "kind" to mapOf(
                            "type" to "string",
                            "enum" to listOf("single_choice", "multi_choice", "free_text"),
                            "description" to "How the question is answered."
                        ),
                        "options" to mapOf(
                            "type" to "array",
                            "description" to "Choices for single_choice/multi_choice questions.",
                            "items" to mapOf(
                                "type" to "object",
                                "properties" to mapOf(
                                    "value" to mapOf("type" to "string"),
                                    "label" to mapOf("type" to "string"),
                                    "default" to mapOf("type" to "boolean")
                                ),
                                "required" to listOf("value", "label")
                            )
                        )
                    ),
                    "required" to listOf("text", "kind")
                )
            )
        ),
        "required" to listOf("questions")
    )

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val request = parseClarification(args)
        if (request.questions.isEmpty()) {
            return ToolResult.Error("ask_clarification requires at least one question")
        }
        val answers = ctx.clarifications.await(request)
        return ToolResult.Text(formatAnswers(request, answers))
    }

    // --- parsing ---------------------------------------------------------

    private fun parseClarification(args: Map<String, Any?>): Clarification {
        val prompt = (args["prompt"] as? String)?.takeIf { it.isNotBlank() }
        val rawQuestions = args["questions"] as? List<*> ?: emptyList<Any?>()
        val questions = rawQuestions.mapIndexedNotNull { idx, raw ->
            val q = raw as? Map<*, *> ?: return@mapIndexedNotNull null
            val text = (q["text"] as? String)?.takeIf { it.isNotBlank() }
                ?: return@mapIndexedNotNull null
            val id = (q["id"] as? String)?.takeIf { it.isNotBlank() } ?: "q${idx + 1}"
            val options = parseOptions(q["options"])
            val kind = parseKind(q["kind"] as? String, options.isNotEmpty())
            ClarificationQuestion(id = id, text = text, kind = kind, options = options)
        }
        return Clarification(prompt, questions)
    }

    private fun parseOptions(raw: Any?): List<QuestionOption> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapNotNull { o ->
            val om = o as? Map<*, *> ?: return@mapNotNull null
            val value = (om["value"] as? String)?.takeIf { it.isNotBlank() }
                ?: (om["label"] as? String)?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val label = (om["label"] as? String)?.takeIf { it.isNotBlank() } ?: value
            val isDefault = (om["default"] as? Boolean) ?: (om["isDefault"] as? Boolean) ?: false
            QuestionOption(value = value, label = label, isDefault = isDefault)
        }
    }

    private fun parseKind(raw: String?, hasOptions: Boolean): QuestionKind {
        return when (raw?.lowercase()?.replace('-', '_')?.trim()) {
            "single_choice", "single", "choice", "radio" -> QuestionKind.SINGLE_CHOICE
            "multi_choice", "multiple_choice", "multi", "multiple", "checkbox" -> QuestionKind.MULTI_CHOICE
            "free_text", "freetext", "text", "string" -> QuestionKind.FREE_TEXT
            // Unknown / missing: infer from the presence of options.
            else -> if (hasOptions) QuestionKind.SINGLE_CHOICE else QuestionKind.FREE_TEXT
        }
    }

    // --- formatting ------------------------------------------------------

    private fun formatAnswers(request: Clarification, answers: ClarificationAnswers): String {
        val lines = request.questions.map { q ->
            val a = answers.answers[q.id].orEmpty().filter { it.isNotBlank() }
            "${q.text} -> ${if (a.isEmpty()) "(no answer)" else a.joinToString(", ")}"
        }.toMutableList()

        // Any answers not tied to a question (e.g. a raw typed reply).
        val extra = answers.answers
.filterKeys { key -> request.questions.none { it.id == key } }
.values.flatten().filter { it.isNotBlank() }
        if (extra.isNotEmpty()) {
            lines += "Additional: ${extra.joinToString(", ")}"
        }
        return lines.joinToString("\n")
    }

    companion object {
        /** The tool name exposed to the LLM. */
        const val NAME: String = "ask_clarification"
    }
}
