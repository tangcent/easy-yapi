package com.itangcent.easyapi.ai.agent

import com.itangcent.easyapi.ai.AiChatRequest
import com.itangcent.easyapi.ai.AiMessage
import com.itangcent.easyapi.ai.AIService
import com.itangcent.easyapi.ai.tools.ToolContext
import com.itangcent.easyapi.ai.tools.ToolKind
import com.itangcent.easyapi.ai.tools.ToolRegistry
import com.itangcent.easyapi.ai.tools.ToolResult
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.util.json.GsonUtils
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * The Perception→Reasoning→Action agent that authors EasyApi rule files
 *.
 *
 * - **Provider-agnostic** — depends only on [AiService]; no knowledge of
 * LangChain4j or any specific provider.
 * - **Multi-turn** — [runTurn] is called once per user message; the
 * supplied [AgentMemory] persists across turns so the conversation is a
 * real dialogue.
 * - **Phase-typed events** — every perception, action, observation,
 * approval request, and message is an [AgentEvent] emitted into [events];
 * the chat UI renders these as distinct cards and tests assert them as a
 * sequence.
 * - **Single risky gate** — only `ACTION` tools can change state, and
 * [ToolRegistry.dispatch] routes them through `ApprovalGate`. Perception
 * is free-running.
 *
 * @param aiService Provider-neutral LLM backend.
 * @param tools The agent's capabilities (perception + the staging-only
 * `propose_rule_content` action).
 * @param ctx Per-conversation context passed to every tool.
 * @param events Sink for [AgentEvent]s emitted by the loop.
 */
class RuleAuthoringAgent(
    private val aiService: AIService,
    private val tools: ToolRegistry,
    private val ctx: ToolContext,
    private val events: MutableSharedFlow<AgentEvent>
) : IdeaLog {

    /**
     * Run one user turn: perceive → reason → act, looping until the agent
     * communicates, calls `propose_rule_content`, or the step budget is
     * exhausted.
     *
     * Mutates [memory] (appends messages + stores [ambient]). Emits a
     * sequence of [AgentEvent]s into [events]. Returns the terminal
     * [TurnOutcome] for this turn.
     *
     * @param ambient The ambient perception for this turn. Callers that know
     * which rule file is being edited (the rule-file edit dialog) should
     * pass an [Ambient] captured with that file path; `null` (the default)
     * captures an editing-file-less ambient for [ctx.project].
     */
    suspend fun runTurn(
        userMessage: String,
        memory: AgentMemory,
        ambient: Ambient? = null
    ): TurnOutcome {
        // First-turn setup: the role/policy preamble is added once at the
        // start of a conversation (and re-asserted after a reset()).
        if (memory.messages.isEmpty()) {
            memory.messages.add(SystemPromptBuilder.build())
        }

        // PERCEPTION (ambient) — use the caller-supplied perception, or
        // capture an editing-file-less one for this project.
        val amb = ambient ?: AmbientPerception.capture(ctx.project)
        memory.ambient = amb
        memory.messages.add(SystemPromptBuilder.ambient(amb))
        memory.messages.add(AiMessage.User(userMessage))

        LOG.info("agent turn start: project=${amb.projectName} " +
            "editingRuleFile=${amb.editingRuleFile} " +
            "budget=${ctx.aiSettings.maxRequests}")

        // Fresh per-turn loop guard + retry policy so detection state never
        // leaks across turns (REQ-1 AC-4).
        val guard = LoopGuard(ctx.aiSettings.loopSafety)
        val retry = ChatRetry(ctx.aiSettings.loopSafety)

        var step = 0
        val tokenBudget = contextWindowToBudget(ctx.aiSettings.contextWindow)
        while (step < ctx.aiSettings.maxRequests) {
            events.emit(AgentEvent.Thinking(step + 1))
            trimToTokenBudget(memory, tokenBudget)
            LOG.info("agent step ${step + 1}/${ctx.aiSettings.maxRequests}: " +
                "messages=${memory.messages.size} tools=${tools.schemas().size}")

            val resp = try {
                retry.chatWithRetry(
                    { aiService.chat(AiChatRequest(memory.messages, tools.schemas())) },
                    onFailure = { a, e ->
                        LOG.warn("agent chat attempt $a failed: ${e::class.simpleName}", e)
                        // Emit a non-terminal retry-progress signal only for
                        // transient failures (an actual retry will follow).
                        // Non-transient failures fail-fast — no Retrying event.
                        // tryEmit is used because this callback is non-suspending;
                        // the SharedFlow's replay buffer absorbs the event.
                        if (retry.isTransient(e)) {
                            events.tryEmit(
                                AgentEvent.Retrying(a, ctx.aiSettings.loopSafety.chatMaxRetries)
                            )
                        }
                    },
                    onRecovery = { attempts ->
                        LOG.info("agent chat recovered after $attempts retry attempt(s)")
                    }
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Control-flow exception — must be rethrown, never logged.
                throw e
            } catch (e: ChatRetriesExhausted) {
                LOG.warn("agent chat failed at step ${step + 1} after retries exhausted", e)
                events.emit(
                    AgentEvent.Failed(
                        "chat failed after ${e.attempts} attempt(s): " +
                            "${e.cause?.let { it::class.simpleName } ?: e::class.simpleName}"
                    )
                )
                return logOutcome(TurnOutcome.Answered)
            } catch (e: Exception) {
                // Safety net for any exception that escapes chatWithRetry unwrapped.
                LOG.warn("agent chat failed at step ${step + 1}", e)
                events.emit(AgentEvent.Failed("chat failed: ${e::class.simpleName}"))
                return logOutcome(TurnOutcome.Answered)
            }
            val assistant = resp.message as? AiMessage.Assistant
                ?: run {
                    LOG.warn("AI agent received non-Assistant message: ${resp.message::class.simpleName}")
                    events.emit(AgentEvent.Failed("Unexpected response shape"))
                    return logOutcome(TurnOutcome.Answered)
                }
            memory.messages.add(assistant)

            // Reasoning repetition — check before tool-call dispatch so a
            // looping assistant message is caught even if it carries tool calls.
            when (val v = guard.observeReasoning(assistant)) {
                is LoopGuard.Verdict.Terminate -> return endLoopDetected(guard, v.reason, memory)
                else -> { }
            }

            val calls = assistant.toolCalls
            if (calls.isNullOrEmpty()) {
                // intent = COMMUNICATE
                val text = assistant.content.orEmpty()
                LOG.info("agent step ${step + 1}: model answered with plain text " +
                    "(len=${text.length}), ending turn")
                events.emit(AgentEvent.Message(text))
                return finish(memory)
            }

            LOG.info("agent step ${step + 1}: model requested ${calls.size} tool call(s): " +
                calls.joinToString(",") { it.name })

            for (tc in calls!!) {
                // Debounce: skip dispatch for provably-identical repeats and
                // feed an instructive error back to the model instead. The
                // streak counter still advances via observeResult below.
                when (val pre = guard.checkBeforeDispatch(tc)) {
                    is LoopGuard.Verdict.Block -> {
                        memory.messages.add(AiMessage.ToolResult(tc.id, tc.name, pre.result.toJson()))
                        events.emit(AgentEvent.Observed(tc.name, pre.result.summary()))
                        LOG.info("agent tool debounced: ${tc.name}")
                        when (val post = guard.observeResult(tc, pre.result)) {
                            is LoopGuard.Verdict.Terminate ->
                                return endLoopDetected(guard, post.reason, memory)
                            else -> { }
                        }
                        continue
                    }
                    else -> { }
                }

                val args = parseArgs(tc.arguments)
                val kind = tools.kindOf(tc.name)
                when (kind) {
                    ToolKind.PERCEPTION ->
                        events.emit(AgentEvent.Perceiving(tc.name, tc.arguments))
                    ToolKind.ACTION ->
                        events.emit(AgentEvent.Acting(tc.name, tc.arguments))
                    null ->
                        // Unknown tool — still dispatch (returns Error from
                        // the registry) but emit an Acting card so the user
                        // sees something happened.
                        events.emit(AgentEvent.Acting(tc.name, tc.arguments))
                }
                // Log the tool name only — never log arguments (bodies are private).
                LOG.info("AI agent ${kind?.name?.lowercase() ?: "unknown"}: ${tc.name}")

                // For ACTION tools gated by approval, signal the UI before
                // dispatch suspends inside `ToolRegistry.dispatch`.
                if (kind == ToolKind.ACTION && tools.requiresApproval(tc.name)) {
                    events.emit(AgentEvent.ApprovalRequested(tc.name, tc.arguments))
                }

                val result = tools.dispatch(tc.name, args, ctx)
                memory.messages.add(AiMessage.ToolResult(tc.id, tc.name, result.toJson()))
                events.emit(AgentEvent.Observed(tc.name, result.summary()))
                // Log only the result kind + a size hint — never the body.
                LOG.info("agent tool result: ${tc.name} -> ${result.resultKind()}")

                // Loop detection: consecutive duplicate / call cycle / output stagnation.
                when (val post = guard.observeResult(tc, result)) {
                    is LoopGuard.Verdict.Terminate ->
                        return endLoopDetected(guard, post.reason, memory)
                    else -> { }
                }

                if (tc.name == PROPOSE_RULE_CONTENT) {
                    // Terminal action — proposal is staged in working memory.
                    return finish(memory)
                }
            }
            step++
        }
        LOG.info("agent turn ended: request budget exhausted (${ctx.aiSettings.maxRequests})")
        return logOutcome(TurnOutcome.StepLimitHit)
    }

    /**
     * Terminate the turn abnormally because the [LoopGuard] detected a loop.
     *
     * Derives the tool name + repetition count from the [LoopGuard.LoopReason]
     * subtype, emits a terminal [AgentEvent.LoopDetected], and returns
     * [TurnOutcome.LoopDetected] via [logOutcome] (NOT [finish] — no
     * `TurnComplete` / `ProposalReady` is emitted for an abnormal exit).
     *
     * @param guard The per-turn guard (used to access [LoopGuard.describe]).
     * @param reason Why the guard terminated the turn.
     * @param memory The agent memory (unused but reserved for future telemetry).
     */
    private suspend fun endLoopDetected(
        guard: LoopGuard,
        reason: LoopGuard.LoopReason,
        @Suppress("UNUSED_PARAMETER") memory: AgentMemory
    ): TurnOutcome {
        val tool = when (reason) {
            is LoopGuard.LoopReason.ConsecutiveDuplicate -> reason.tool
            is LoopGuard.LoopReason.OutputStagnation -> reason.tool
            is LoopGuard.LoopReason.CallCycle -> reason.sequence.firstOrNull()
            is LoopGuard.LoopReason.ReasoningRepetition -> null
        }
        val count = when (reason) {
            is LoopGuard.LoopReason.ConsecutiveDuplicate -> reason.count
            is LoopGuard.LoopReason.CallCycle -> reason.repetitions
            is LoopGuard.LoopReason.OutputStagnation -> reason.count
            is LoopGuard.LoopReason.ReasoningRepetition -> reason.count
        }
        LOG.info("agent turn end: loop detected (${reason::class.simpleName})")
        val description = with(guard) { reason.describe() }
        events.emit(AgentEvent.LoopDetected(description, tool, count))
        return logOutcome(TurnOutcome.LoopDetected)
    }

    /**
     * Stamp a terminal outcome into the log without running [finish].
     *
     * Used by the abnormal exits (chat failure, step-limit exhaustion) that
     * must NOT emit `TurnComplete`/`ProposalReady`. The natural exits
     * (communicate, propose) go through [finish] instead.
     */
    private fun logOutcome(outcome: TurnOutcome): TurnOutcome {
        LOG.info("agent turn end: outcome=${outcome.outcomeName()}")
        return outcome
    }

    private suspend fun finish(memory: AgentMemory): TurnOutcome {
        memory.proposal?.let { events.emit(AgentEvent.ProposalReady(it)) }
        events.emit(AgentEvent.TurnComplete)
        val outcome = if (memory.proposal != null) TurnOutcome.Proposed else TurnOutcome.Answered
        LOG.info("agent turn end: outcome=${outcome.outcomeName()}")
        return outcome
    }

    private fun parseArgs(arguments: String): Map<String, Any?> {
        if (arguments.isBlank()) return emptyMap()
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            GsonUtils.fromJson(arguments, Map::class.java) as Map<String, Any?>
        }.getOrDefault(emptyMap())
    }

    companion object {
        /** The terminal staging action's tool name. */
        const val PROPOSE_RULE_CONTENT = "propose_rule_content"
    }
}

/**
 * Outcome of a single agent turn.
 *
 * - [Proposed] → a rule proposal is staged; the UI enables "Save…".
 * - [Answered] → the agent produced a plain answer / clarifying question;
 * the user may reply again.
 * - [StepLimitHit] → the step budget was exhausted; the UI offers
 * Continue / Cancel.
 * - [LoopDetected] → the agent was repeating itself and the turn was
 * terminated; the UI offers loop-specific recovery. This is an abnormal
 * exit — no `TurnComplete` / `ProposalReady` is emitted.
 */
sealed class TurnOutcome {
    object Proposed : TurnOutcome()
    object Answered : TurnOutcome()
    object StepLimitHit : TurnOutcome()
    object LoopDetected : TurnOutcome()
}

/** Serialise a [ToolResult] to the JSON string fed back to the LLM. */
private fun ToolResult.toJson(): String = when (this) {
    is ToolResult.Text -> GsonUtils.toJson(mapOf("value" to value))
    is ToolResult.Error -> GsonUtils.toJson(mapOf("error" to message))
}

/** Short summary for the chat UI's `Observed` card. */
private fun ToolResult.summary(): String = when (this) {
    is ToolResult.Text -> if (value.length > 200) value.take(200) + "…" else value
    is ToolResult.Error -> "Error: $message"
}

/**
 * Compact, body-free label for diagnostic logging — never log bodies.
 * `Text` carries a length hint so a huge tool result is still visible as
 * "text(len=12000)" without dumping it to the log.
 */
private fun ToolResult.resultKind(): String = when (this) {
    is ToolResult.Text -> "text(len=${value.length})"
    is ToolResult.Error -> "error($message)"
}

/** Stable name for a [TurnOutcome] in log lines. */
private fun TurnOutcome.outcomeName(): String = when (this) {
    TurnOutcome.Proposed -> "proposed"
    TurnOutcome.Answered -> "answered"
    TurnOutcome.StepLimitHit -> "step_limit_hit"
    TurnOutcome.LoopDetected -> "loop_detected"
}
