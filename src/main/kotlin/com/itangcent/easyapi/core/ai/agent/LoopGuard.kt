package com.itangcent.easyapi.core.ai.agent

import com.itangcent.easyapi.core.ai.AiMessage
import com.itangcent.easyapi.core.ai.AiToolCall
import com.itangcent.easyapi.core.ai.tools.ToolResult
import com.itangcent.easyapi.core.util.json.GsonUtils
import java.util.TreeMap

/**
 * Per-turn loop detector. Construct one fresh at the start of each agent
 * turn so detection state never leaks across turns.
 *
 * The guard observes tool calls, tool results, and reasoning text, and
 * returns a [Verdict] telling the agent loop whether to proceed, block a
 * provably-identical repeat, or terminate the turn. Detection is pure logic
 * over fingerprints — no suspend calls — so it is trivially unit-testable.
 *
 * @param cfg Tuning knobs for thresholds and debounce.
 */
class LoopGuard(private val cfg: LoopSafetyConfig) {

    /** A comparable fingerprint of a tool call: (toolName, argsHash). */
    private data class CallFp(val tool: String, val argsHash: Int)

    private val callHistory = ArrayList<CallFp>()
    private var lastCall: CallFp? = null
    private var consecutiveDup = 0
    private var lastResultHash: Int? = null
    private var stagnationCount = 0
    private val reasoningHashes = ArrayList<Int>()

    /**
     * Pre-dispatch verdict: debounce an identical-to-last call.
     *
     * If debounce is enabled and the fingerprint of [tc] matches the last
     * observed call, returns [Verdict.Block] with an instructive error that
     * names the tool but never the argument bodies. The streak counter is
     * NOT advanced here — the caller must still invoke
     * [observeResult] with the blocked result so consecutive/stagnation
     * counters advance consistently.
     *
     * @param tc The tool call about to be dispatched.
     * @return [Verdict.Proceed] to dispatch, or [Verdict.Block] to skip
     * dispatch and feed a synthetic result back to the model.
     */
    fun checkBeforeDispatch(tc: AiToolCall): Verdict {
        if (!cfg.debounceEnabled) return Verdict.Proceed
        val fp = CallFp(tc.name, normalizeArgs(tc.arguments))
        if (fp == lastCall) {
            return Verdict.Block(
                ToolResult.Error(
                    "Duplicate call to '${tc.name}' with identical arguments; " +
                        "the previous result is already in context. " +
                        "Change your approach or communicate."
                )
            )
        }
        return Verdict.Proceed
    }

    /**
     * Post-dispatch verdict: record the call + result and run consecutive,
     * cycle, and stagnation detection (in that order — cheapest first).
     *
     * @param tc The tool call that was dispatched.
     * @param result The result produced (or the synthetic debounce result).
     * @return [Verdict.Proceed] to continue, or [Verdict.Terminate] to end
     * the turn.
     */
    fun observeResult(tc: AiToolCall, result: ToolResult): Verdict {
        val fp = CallFp(tc.name, normalizeArgs(tc.arguments))

        // --- A. Consecutive duplicate (streak) ---
        if (fp == lastCall) {
            consecutiveDup++
        } else {
            consecutiveDup = 1
        }
        lastCall = fp
        callHistory.add(fp)
        if (consecutiveDup >= cfg.repetitionThreshold) {
            return Verdict.Terminate(LoopReason.ConsecutiveDuplicate(tc.name, consecutiveDup))
        }

        // --- B. Call-sequence cycle (A→B→A→B) ---
        // Period-1 is handled by the consecutive detector above; the cycle
        // detector examines periods 2..maxCyclePeriod as a backstop for
        // longer repeating sequences.
        detectCycle()?.let { return Verdict.Terminate(it) }

        // --- C. Output stagnation (identical results) ---
        val h = resultFingerprint(result)
        if (h == lastResultHash) {
            stagnationCount++
        } else {
            stagnationCount = 1
        }
        lastResultHash = h
        if (stagnationCount >= cfg.repetitionThreshold) {
            return Verdict.Terminate(LoopReason.OutputStagnation(tc.name, stagnationCount))
        }

        return Verdict.Proceed
    }

    /**
     * Post-assistant-message verdict: record reasoning text and run
     * reasoning-repetition detection.
     *
     * Blank content (a tool-call-only step) neither advances nor resets the
     * counter — it is silently skipped.
     *
     * @param assistant The assistant message just produced.
     * @return [Verdict.Proceed] to continue, or [Verdict.Terminate] to end
     * the turn.
     */
    fun observeReasoning(assistant: AiMessage.Assistant): Verdict {
        val content = assistant.content
        if (content.isNullOrBlank()) return Verdict.Proceed

        val h = normalizeReasoning(content)
        reasoningHashes.add(h)

        // Count the trailing run of identical hashes.
        var run = 1
        var i = reasoningHashes.size - 2
        while (i >= 0 && reasoningHashes[i] == h) {
            run++
            i--
        }
        if (run >= cfg.repetitionThreshold) {
            return Verdict.Terminate(LoopReason.ReasoningRepetition(run))
        }
        return Verdict.Proceed
    }

    /**
     * Check whether [callHistory] ends with a repeating cycle of period
     * `p` (for `p` in `2..maxCyclePeriod`). Returns the matching
     * [LoopReason.CallCycle] or `null`.
     */
    private fun detectCycle(): LoopReason.CallCycle? {
        val n = callHistory.size
        for (p in 2..cfg.maxCyclePeriod) {
            val window = minOf(n, p * cfg.cycleRepetitions)
            if (window < p * cfg.cycleRepetitions) continue
            val start = n - window
            var isCycle = true
            for (i in 0..<window - p) {
                if (callHistory[start + i] != callHistory[start + i + p]) {
                    isCycle = false
                    break
                }
            }
            if (isCycle) {
                val sequence = callHistory.subList(n - p, n).map { it.tool }
                val repetitions = window / p
                return LoopReason.CallCycle(sequence, p, repetitions)
            }
        }
        return null
    }

    /** Outcome of consulting the guard. */
    sealed class Verdict {
        /** No objection; the agent loop continues. */
        object Proceed : Verdict()

        /** Skip dispatch and feed [result] back to the model. */
        data class Block(val result: ToolResult) : Verdict()

        /** Stop the turn — a loop was detected. */
        data class Terminate(val reason: LoopReason) : Verdict()
    }

    /** Why the guard terminated the turn. */
    sealed class LoopReason {
        /** The same tool call (name + args) was repeated consecutively. */
        data class ConsecutiveDuplicate(val tool: String, val count: Int) : LoopReason()

        /** The call sequence formed a repeating cycle (e.g. A→B→A→B). */
        data class CallCycle(val sequence: List<String>, val period: Int, val repetitions: Int) : LoopReason()

        /** Consecutive calls returned identical results. */
        data class OutputStagnation(val tool: String, val count: Int) : LoopReason()

        /** The model repeated the same reasoning text. */
        data class ReasoningRepetition(val count: Int) : LoopReason()
    }

    /**
     * Human-readable loop-type label for this reason, used in loop-detected
     * signals and logs. Names the loop kind only — never argument bodies.
     */
    fun LoopReason.describe(): String = when (this) {
        is LoopReason.ConsecutiveDuplicate -> "consecutive duplicate"
        is LoopReason.CallCycle -> "call cycle"
        is LoopReason.OutputStagnation -> "output stagnation"
        is LoopReason.ReasoningRepetition -> "reasoning repetition"
    }
}

// ------------------------------------------------------------------
// Normalization & fingerprinting helpers (Phase 2, task 9)
// ------------------------------------------------------------------

/**
 * Sentinel prefix guaranteeing an [ToolResult.Error] fingerprint never
 * collides with a [ToolResult.Text] fingerprint. Canonical JSON never
 * starts with a NUL byte, so this prefix is unambiguous.
 */
private const val ERROR_SENTINEL = "\u0000__LOOPGUARD_ERROR__\u0000"

/**
 * Canonicalize a JSON arguments string into a stable form: parse to a map,
 * sort keys recursively, re-serialize. Blank or unparseable input is treated
 * as an empty map (`{}`).
 *
 * @return the canonical JSON string.
 */
private fun canonicalizeJson(json: String): String {
    if (json.isBlank()) return "{}"
    val parsed = runCatching {
        @Suppress("UNCHECKED_CAST")
        GsonUtils.fromJson(json, Map::class.java) as Map<String, Any?>
    }.getOrNull() ?: return "{}"
    return GsonUtils.toJson(sortKeys(parsed))
}

/**
 * Recursively sort map keys so key order does not affect the fingerprint.
 */
private fun sortKeys(value: Any?): Any? = when (value) {
    is Map<*, *> -> {
        val sorted = TreeMap<String, Any?>()
        for ((k, v) in value) {
            sorted[k.toString()] = sortKeys(v)
        }
        sorted
    }
    is List<*> -> value.map { sortKeys(it) }
    else -> value
}

/**
 * Normalize tool-call arguments into a stable hash. Blank, whitespace-only,
 * and unparseable inputs all collapse to the empty-map fingerprint. Key-order
 * and whitespace differences collapse to the same hash.
 *
 * @param arguments raw JSON arguments string from an [AiToolCall].
 * @return hash of the canonical form.
 */
internal fun normalizeArgs(arguments: String): Int {
    return canonicalizeJson(arguments).hashCode()
}

/**
 * Compute a fingerprint for a [ToolResult]. [ToolResult.Text] content is
 * canonicalized: valid JSON is sorted and re-serialized; non-JSON text is
 * trimmed and hashed as-is so that distinct free-text results are not
 * falsely flagged as stagnation. Blank content collapses to the empty-map
 * fingerprint. [ToolResult.Error] uses a distinct sentinel so an error is
 * never identical to a success, while two errors with the same message ARE
 * identical (stagnation).
 *
 * @param result the tool result to fingerprint.
 * @return hash of the normalized form.
 */
internal fun resultFingerprint(result: ToolResult): Int = when (result) {
    is ToolResult.Text -> canonicalizeResultContent(result.value).hashCode()
    is ToolResult.Error -> (ERROR_SENTINEL + result.message.trim()).hashCode()
}

/**
 * Canonicalize [ToolResult.Text] content. Valid JSON objects are canonicalized
 * (keys sorted recursively); non-JSON or non-object text is trimmed and used
 * as-is. Blank content collapses to `{}`. This differs from
 * [canonicalizeJson] because tool results may be free text, and collapsing
 * all non-JSON text to the same fingerprint would cause false stagnation.
 */
private fun canonicalizeResultContent(content: String): String {
    if (content.isBlank()) return "{}"
    val parsed = runCatching {
        @Suppress("UNCHECKED_CAST")
        GsonUtils.fromJson(content, Map::class.java) as Map<String, Any?>
    }.getOrNull() ?: return content.trim()
    return GsonUtils.toJson(sortKeys(parsed))
}

/**
 * Normalize reasoning text: trim, collapse internal whitespace runs to a
 * single space, lowercase. Returns the hash of the normalized text.
 *
 * @param content raw reasoning text from an [AiMessage.Assistant].
 * @return hash of the normalized form.
 */
internal fun normalizeReasoning(content: String): Int {
    val normalized = content.trim().replace(Regex("\\s+"), " ").lowercase()
    return normalized.hashCode()
}
