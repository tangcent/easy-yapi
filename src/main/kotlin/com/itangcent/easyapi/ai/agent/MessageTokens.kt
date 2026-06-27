package com.itangcent.easyapi.ai.agent

import com.itangcent.easyapi.ai.AiMessage

/**
 * Token-budget estimation + trimming helpers.
 *
 * The agent loop calls [trimToTokenBudget] before each `aiService.chat` call
 * to keep the conversation under the model's context window. The estimate is
 * a coarse character heuristic (~4 chars/token); this is documented as
 * approximate and avoids pulling in a `tiktoken` dependency.
 *
 * **Critical correctness property:** a matched
 * (Assistant-with-only-tool-calls + all its ToolResults) pair MUST be dropped
 * together. OpenAI/Azure/Anthropic require every assistant `tool_calls`
 * message to be followed by a matching `ToolResult` per `tool_call_id`;
 * orphaning either side yields a provider 400 error.
 */
private const val CHARS_PER_TOKEN = 4

/**
 * Derives the token budget used by [trimToTokenBudget] from a model's context
 * window.
 *
 * The budget is a *fraction* of the full window, not the whole window, because:
 * - The char heuristic under-counts real tokens (it excludes tool schemas,
 *   per-message envelope, and over-estimates compression for code/CJK).
 * - The window is shared between input and the model's *output*.
 *
 * We reserve [OUTPUT_RESERVE] tokens for the model's reply, then take
 * [BUDGET_FRACTION] of what remains. The result is clamped to
 * [MIN_TOKEN_BUDGET] / [MAX_TOKEN_BUDGET] so tiny windows (e.g. 8k locals)
 * still get a usable budget and huge windows (1M Gemini) don't keep a
 * sprawling transcript purely because they could.
 */
private const val OUTPUT_RESERVE = 4_000
private const val BUDGET_FRACTION = 0.4
private const val MIN_TOKEN_BUDGET = 2_000
private const val MAX_TOKEN_BUDGET = 64_000

/**
 * Convert a model's context window (in tokens) into the input token budget
 * used by [trimToTokenBudget]. Pure and public so the same derivation is used
 * everywhere a budget is needed (and so it can be unit-tested directly).
 */
fun contextWindowToBudget(contextWindow: Int): Int {
    val afterReserve = (contextWindow - OUTPUT_RESERVE).coerceAtLeast(0)
    val budget = (afterReserve * BUDGET_FRACTION).toInt()
    return budget.coerceIn(MIN_TOKEN_BUDGET, MAX_TOKEN_BUDGET)
}

/**
 * Heuristic content length used for token estimation.
 *
 * Sums the visible text a message contributes to the LLM:
 * - [AiMessage.System] / [AiMessage.User] → [content] length.
 * - [AiMessage.Assistant] → [content] length + serialised [toolCalls]
 * (id + name + arguments length per call).
 * - [AiMessage.ToolResult] → [content] length.
 */
fun AiMessage.contentLength(): Int = when (this) {
    is AiMessage.System -> content.length
    is AiMessage.User -> content.length
    is AiMessage.Assistant -> {
        val base = content?.length ?: 0
        val calls = toolCalls?.sumOf {
            it.id.length + it.name.length + it.arguments.length
        } ?: 0
        base + calls
    }
    is AiMessage.ToolResult -> content.length
}

/**
 * Rough token estimate for a single message (~4 chars/token).
 */
fun AiMessage.estimatedTokens(): Int = (contentLength() + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN

/**
 * Trim [memory.messages] oldest-first until the estimated token count is
 * under [tokenBudget] (typically derived via [contextWindowToBudget] from the
 * model's context window), preserving the invariants:
 * - Never split a (Assistant-with-tool-calls + its ToolResults) pair.
 * - Always preserve the leading system prompts, the current turn's user
 * message, and the last complete pair.
 *
 * Mutates [memory.messages] in place.
 *
 * @param tokenBudget Maximum estimated tokens to keep. Use
 * [contextWindowToBudget] to derive it from a model's context window.
 */
internal fun trimToTokenBudget(memory: AgentMemory, tokenBudget: Int) {
    val messages = memory.messages
    if (messages.sumOf { it.estimatedTokens() } <= tokenBudget) return

    // Build a "drop plan" as a list of (startIndex, endIndexInclusive) ranges
    // that may be dropped. Each range is either:
    // - A single non-paired message, or
    // - An (Assistant-with-tool-calls + all following ToolResults) pair,
    // kept together.
    val dropRanges = mutableListOf<Pair<Int, Int>>()
    var i = 0
    while (i < messages.size) {
        val end = when (val m = messages[i]) {
            is AiMessage.Assistant -> if (!m.toolCalls.isNullOrEmpty()) {
                // Pair: assistant + all following ToolResults.
                var j = i + 1
                while (j < messages.size && messages[j] is AiMessage.ToolResult) j++
                j - 1
            } else i
            else -> i
        }
        dropRanges += i to end
        i = end + 1
    }

    // Preserve invariants by index:
    // 1) The leading contiguous run of System messages (preamble + ambient).
    val preserve = BooleanArray(messages.size) { false }
    var idx = 0
    while (idx < messages.size && messages[idx] is AiMessage.System) {
        preserve[idx] = true
        idx++
    }
    // 2) The last User message (the current turn's prompt).
    for (k in messages.indices.reversed()) {
        if (messages[k] is AiMessage.User) { preserve[k] = true; break }
    }
    // 3) The last (Assistant-with-tool-calls + its ToolResults) pair.
    var lastPairStart = -1
    for (k in messages.indices.reversed()) {
        val m = messages[k]
        if (m is AiMessage.Assistant && !m.toolCalls.isNullOrEmpty()) {
            lastPairStart = k
            break
        }
    }
    if (lastPairStart >= 0) {
        var j = lastPairStart
        while (j < messages.size) {
            val m = messages[j]
            if (j == lastPairStart && m !is AiMessage.Assistant) break
            if (j > lastPairStart && m !is AiMessage.ToolResult) break
            preserve[j] = true
            j++
        }
    }

    // Drop oldest-first until under budget. A dropRange is droppable iff
    // every index it covers is non-preserved. We mark indices for drop and
    // rebuild the list at the end so indices stay stable during planning.
    val drop = BooleanArray(messages.size) { false }
    var estTokens = messages.sumOf { it.estimatedTokens() }
    for ((start, end) in dropRanges) {
        if (estTokens <= tokenBudget) break
        if ((start..end).any { preserve[it] }) continue
        val droppedTokens = (start..end).sumOf { messages[it].estimatedTokens() }
        for (k in start..end) drop[k] = true
        estTokens -= droppedTokens
    }
    if (drop.any { it }) {
        val survivors = messages.filterIndexed { i, _ -> !drop[i] }
        messages.clear()
        messages.addAll(survivors)
    }
    // Note: after dropping, the surviving messages are still in valid order
    // (System → User/Assistant/ToolResult interleaving) because we only
    // dropped whole pairs + standalone messages, and we always preserved
    // the leading System block, the last User message, and the last pair.
}
