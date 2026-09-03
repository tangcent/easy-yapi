package com.itangcent.easyapi.core.ai.agent

import com.itangcent.easyapi.core.ai.AiChatRequest
import com.itangcent.easyapi.core.ai.AiMessage
import com.itangcent.easyapi.core.ai.AIService
import com.itangcent.easyapi.core.ai.tools.ToolContext
import com.itangcent.easyapi.core.ai.tools.ToolKind
import com.itangcent.easyapi.core.ai.tools.ToolRegistry
import com.itangcent.easyapi.core.ai.tools.ToolResult
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.util.json.GsonUtils
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
     * @param entryPath The entry path selecting the seed-prompt shape (design
     * C9). Defaults to [EntryPath.REACTIVE] so existing callers (plain chat)
     * are unchanged; `runTaskList` passes
     * [EntryPath.TASK_LIST_PROGRAMMATIC]. The path only affects which system
     * messages seed an empty transcript — the loop body is shared.
     */
    suspend fun runTurn(
        userMessage: String,
        memory: AgentMemory,
        ambient: Ambient? = null,
        entryPath: EntryPath = EntryPath.REACTIVE
    ): TurnOutcome {
        // PERCEPTION (ambient) — captured BEFORE the preamble block because
        // SystemPromptBuilder.build(entryPath, amb) needs the enabled-feature
        // sets to filter the derived indexes (design C9 reorder). Use the
        // caller-supplied perception, or capture an editing-file-less one
        // for this project.
        val amb = ambient ?: AmbientPerception.capture(ctx.project)
        val previousAmbient = memory.ambient
        memory.ambient = amb

        // First-turn setup: the role/policy preamble (and entry-path-specific
        // indexes for the Reactive path) is added once at the start of a
        // conversation (and re-asserted after a reset()).
        if (memory.messages.isEmpty()) {
            val opening = SystemPromptBuilder.build(entryPath, amb)
            opening.forEach { memory.messages.add(it) }
            memory.openingSystemCount = opening.size
        } else {
            // Later turns: re-capture the enablement-sensitive knowledge when
            // the enabled channel/format/framework sets changed (spec §3.5).
            refreshStaleKnowledge(memory, previousAmbient, amb, entryPath)
        }

        memory.messages.add(SystemPromptBuilder.ambient(amb))
        memory.messages.add(AiMessage.User(userMessage))

        LOG.info("agent turn start: project=${amb.projectName} " +
            "editingRuleFile=${amb.editingRuleFile} " +
            "budget=${ctx.aiSettings.maxRequests}")

        // Fresh per-turn loop guard + retry policy so detection state never
        // leaks across turns.
        val guard = LoopGuard(ctx.aiSettings.loopSafety)
        val retry = ChatRetry(ctx.aiSettings.loopSafety)

        var step = 0
        val tokenBudget = contextWindowToBudget(ctx.aiSettings.contextWindow)
        while (step < ctx.aiSettings.maxRequests) {
            events.emit(AgentEvent.Thinking(step + 1))
            // Reserve tokens for the knowledge state block injected at request
            // time. The state block is NOT in memory.messages, so trim the
            // history budget accordingly.
            val effectiveBudget = tokenBudget - memory.knowledgeState.estimatedTokens()
            trimToTokenBudget(memory, effectiveBudget)
            LOG.info("agent step ${step + 1}/${ctx.aiSettings.maxRequests}: " +
                "messages=${memory.messages.size} tools=${tools.schemas().size}")

            // Inject the knowledge state block as a System message at request
            // time. This is NOT stored in memory.messages — it is rendered fresh
            // from memory.knowledgeState before each chat call.
            val chatMessages = buildChatMessages(memory)
            val resp = try {
                retry.chatWithRetry(
                    { aiService.chat(AiChatRequest(chatMessages, tools.schemas())) },
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
                is LoopGuard.Verdict.Terminate -> {
                    // The assistant message may carry tool_calls that now have
                    // no corresponding tool result messages. Fill in synthetic
                    // results for ALL of them so the conversation history stays
                    // valid for the next chat request (the API requires every
                    // tool_call_id to have a matching tool result message).
                    assistant.toolCalls?.let { fillSkippedToolResults(it, 0, memory) }
                    return endLoopDetected(guard, v.reason, memory)
                }
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

            // Mark the start of a new tool-call batch so the LoopGuard's
            // output-stagnation detector only fires across batch boundaries
            // (not within a batch of parallel probes).
            guard.beginBatch()

            for (i in calls.indices) {
                val tc = calls[i]
                // Debounce: skip dispatch for provably-identical repeats and
                // feed an instructive error back to the model instead. The
                // streak counter still advances via observeResult below.
                when (val pre = guard.checkBeforeDispatch(tc)) {
                    is LoopGuard.Verdict.Block -> {
                        memory.messages.add(AiMessage.ToolResult(tc.id, tc.name, pre.result.toJson()))
                        events.emit(AgentEvent.Observed(tc.name, pre.result.summary()))
                        LOG.info("agent tool debounced: ${tc.name}")
                        when (val post = guard.observeResult(tc, pre.result)) {
                            is LoopGuard.Verdict.Terminate -> {
                                fillSkippedToolResults(calls, i + 1, memory)
                                return endLoopDetected(guard, post.reason, memory)
                            }
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
                // Stateful results update the KnowledgeState and are replaced
                // with a short receipt in the transcript.
                val finalResult = result.toReceiptIfStateful(memory)
                memory.messages.add(AiMessage.ToolResult(tc.id, tc.name, finalResult.toJson()))
                events.emit(AgentEvent.Observed(tc.name, finalResult.summary()))
                // Log only the result kind + a size hint — never the body.
                LOG.info("agent tool result: ${tc.name} -> ${finalResult.resultKind()}")

                // Loop detection: consecutive duplicate / call cycle / output stagnation.
                when (val post = guard.observeResult(tc, finalResult)) {
                    is LoopGuard.Verdict.Terminate -> {
                        fillSkippedToolResults(calls, i + 1, memory)
                        return endLoopDetected(guard, post.reason, memory)
                    }
                    else -> { }
                }

                if (tc.name == PROPOSE_RULE_CONTENT || tc.name == REPORT_FINDINGS) {
                    // Terminal action — proposal is staged in working memory
                    // (orchestrator via propose_rule_content), or findings are
                    // staged in the sub-agent result slot (sub-agent via
                    // report_findings). Each role's tool registry includes
                    // only its own terminal action, so this check is harmless
                    // for the other role.
                    return finish(memory)
                }
            }
            step++
        }
        LOG.info("agent turn ended: request budget exhausted (${ctx.aiSettings.maxRequests})")
        return logOutcome(TurnOutcome.StepLimitHit)
    }

    /**
     * Drop the knowledge cached under the previous ambient when the enabled
     * channel/format/framework sets changed between turns (spec §3.5).
     *
     * `§keys` is filtered by enablement and the L0 indexes are too, so both go
     * stale together: a channel switched off in Settings would otherwise keep
     * its key lines in the state block while every turn's ambient line says the
     * channel is disabled. `§keyContexts` is invalidated alongside it — a
     * disabled key's context is equally stale. `§objects` is reflected from
     * code and never invalidated.
     */
    private fun refreshStaleKnowledge(
        memory: AgentMemory,
        previous: Ambient?,
        current: Ambient,
        entryPath: EntryPath
    ) {
        if (previous == null || enablementSignature(previous) == enablementSignature(current)) return

        LOG.info("agent knowledge invalidated: enablement changed " +
            "(${enablementSignature(previous)} -> ${enablementSignature(current)})")
        memory.knowledgeState.invalidate(KnowledgeState.SECTION_KEYS)
        memory.knowledgeState.invalidate(KnowledgeState.SECTION_KEY_CONTEXTS)

        // Rebuild the L0 indexes in place — they are the leading System block.
        val opening = SystemPromptBuilder.build(entryPath, current)
        var removable = 0
        while (removable < memory.openingSystemCount &&
            removable < memory.messages.size &&
            memory.messages[removable] is AiMessage.System
        ) {
            removable++
        }
        repeat(removable) { memory.messages.removeAt(0) }
        memory.messages.addAll(0, opening)
        memory.openingSystemCount = opening.size
    }

    /** Enablement fingerprint used to detect staleness across turns. */
    private fun enablementSignature(amb: Ambient): String = listOf(
        amb.enabledChannels.sorted(),
        amb.enabledFormats.sorted(),
        amb.frameworkHints.sorted()
    ).joinToString("|")

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

    /**
     * Fill in synthetic tool-result messages for tool calls in [calls] that
     * were not dispatched (indices [startIndex] onward), because the
     * [LoopGuard] terminated the turn mid-batch.
     *
     * The OpenAI API requires every `tool_call_id` in an assistant message to
     * have a corresponding tool-result message immediately following. When the
     * guard terminates mid-batch, the remaining tool calls in the batch have
     * no results — without this fill-in, the next `aiService.chat()` call
     * fails with "insufficient tool messages following tool_calls message".
     *
     * Each skipped call gets a synthetic [ToolResult.Error] explaining that the
     * turn was terminated; the model sees the error and can adjust on retry.
     */
    private fun fillSkippedToolResults(
        calls: List<com.itangcent.easyapi.core.ai.AiToolCall>,
        startIndex: Int,
        memory: AgentMemory
    ) {
        if (startIndex >= calls.size) return
        for (i in startIndex until calls.size) {
            val tc = calls[i]
            val skipped = ToolResult.Error(
                "Tool call skipped: the agent turn was terminated (loop detected) " +
                    "before this call was dispatched."
            )
            memory.messages.add(AiMessage.ToolResult(tc.id, tc.name, skipped.toJson()))
            LOG.info("agent tool skipped (loop termination): ${tc.name}")
        }
    }

    companion object {
        /** The terminal staging action's tool name. */
        const val PROPOSE_RULE_CONTENT = "propose_rule_content"

        /**
         * The sub-agent's terminal action's tool name. Sub-agents stage their
         * [TaskResult] via `report_findings` (see
         * [com.itangcent.easyapi.core.ai.tools.ReportFindingsTool]); the
         * orchestrator's `run_sub_agent` tool awaits it. Terminal for
         * sub-agents only — never registered in the orchestrator's tool set.
         */
        const val REPORT_FINDINGS = "report_findings"
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
    is ToolResult.Stateful ->
        // Stateful results are converted to receipts before serialization,
        // so this path is unreachable under normal operation. Return a
        // placeholder for completeness.
        GsonUtils.toJson(mapOf("error" to "internal: Stateful result not converted to receipt"))
}

/** Short summary for the chat UI's `Observed` card. */
private fun ToolResult.summary(): String = when (this) {
    is ToolResult.Text -> if (value.length > 200) value.take(200) + "…" else value
    is ToolResult.Error -> "Error: $message"
    is ToolResult.Stateful -> "Stateful: $receiptNote"
}

/**
 * Compact, body-free label for diagnostic logging — never log bodies.
 * `Text` carries a length hint so a huge tool result is still visible as
 * "text(len=12000)" without dumping it to the log.
 */
private fun ToolResult.resultKind(): String = when (this) {
    is ToolResult.Text -> "text(len=${value.length})"
    is ToolResult.Error -> "error($message)"
    is ToolResult.Stateful -> "stateful($section, n=${entries.size})"
}

/** Stable name for a [TurnOutcome] in log lines. */
private fun TurnOutcome.outcomeName(): String = when (this) {
    TurnOutcome.Proposed -> "proposed"
    TurnOutcome.Answered -> "answered"
    TurnOutcome.StepLimitHit -> "step_limit_hit"
    TurnOutcome.LoopDetected -> "loop_detected"
}

/**
 * Build the final chat message list by injecting the rendered knowledge state
 * block after the leading System messages and before the conversation history.
 *
 * This keeps the state block near the head where the model sees it first,
 * and preserves the invariant that the history is pure User/Assistant/
 * ToolResult with no System state block appended on every turn.
 */
private fun buildChatMessages(memory: AgentMemory): List<AiMessage> {
    val stateRendered = memory.knowledgeState.render()
    if (stateRendered.isEmpty()) return memory.messages

    val messages = memory.messages
    val idxFirstNonSystem = messages.indexOfFirst { it !is AiMessage.System }

    return when {
        idxFirstNonSystem < 0 -> {
            // All messages are System → append the state
            messages + AiMessage.System(stateRendered)
        }
        idxFirstNonSystem == 0 -> {
            // No leading System → prepend the state
            listOf(AiMessage.System(stateRendered)) + messages
        }
        else -> {
            // Leading System → insert state between leading System and history
            val leading = messages.subList(0, idxFirstNonSystem)
            val history = messages.subList(idxFirstNonSystem, messages.size)
            leading + AiMessage.System(stateRendered) + history
        }
    }
}

/**
 * If the result is [ToolResult.Stateful], apply it to [memory.knowledgeState]
 * and return a short [ToolResult.Text] receipt for the transcript.
 *
 * If it is not Stateful, return it unchanged. This preserves the required
 * tool_call_id → ToolResult pairing invariant.
 */
private fun ToolResult.toReceiptIfStateful(memory: AgentMemory): ToolResult = when (this) {
    is ToolResult.Stateful -> {
        val upsertResult = memory.knowledgeState.upsert(section, entries)
        val receiptJson = when (upsertResult) {
            is KnowledgeState.UpsertResult.Changed -> GsonUtils.toJson(mapOf(
                "knowledge" to mapOf(
                    "section" to section,
                    "version" to memory.knowledgeState.sectionVersion(section),
                    "added" to upsertResult.added,
                    "updated" to upsertResult.updated,
                    "unchanged" to upsertResult.unchanged,
                    "note" to receiptNote
                )
            ))
            is KnowledgeState.UpsertResult.NoChange -> GsonUtils.toJson(mapOf(
                "knowledge" to mapOf(
                    "section" to section,
                    "version" to memory.knowledgeState.sectionVersion(section),
                    "noChange" to true,
                    "unchanged" to upsertResult.unchanged,
                    "note" to "All ${upsertResult.unchanged} entries are already up-to-date; " +
                        "content is already in the Knowledge State block at the top of the " +
                        "conversation. Do not re-request these entries."
                )
            ))
        }
        ToolResult.Text(receiptJson)
    }
    else -> this
}
