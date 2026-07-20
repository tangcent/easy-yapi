package com.itangcent.easyapi.core.ai.agent

/**
 * Tuning knobs for the agent's loop-safety and chat-retry subsystems.
 *
 * Defaults are MVP values; settings-UI exposure is deferred. Constructing a
 * config with out-of-range values fails fast so a misconfiguration is caught
 * at the call site rather than degrading loop detection at runtime.
 *
 * @param repetitionThreshold Consecutive duplicates / output stagnation /
 * reasoning repetitions before a turn is terminated.
 * @param cycleRepetitions Full call-sequence cycles (e.g. A→B→A→B) before
 * termination.
 * @param maxCyclePeriod Longest cycle period examined by the cycle detector.
 * @param debounceEnabled Whether pre-dispatch debounce blocks identical calls.
 * @param chatMaxRetries Maximum chat retries on transient failures (0 = no retry).
 * @param chatBackoffBaseMs Base exponential-backoff delay in milliseconds.
 * @param chatBackoffMaxMs Cap on the exponential-backoff delay in milliseconds.
 */
data class LoopSafetyConfig(
    val repetitionThreshold: Int = 3,
    val cycleRepetitions: Int = 2,
    val maxCyclePeriod: Int = 4,
    val debounceEnabled: Boolean = true,
    val chatMaxRetries: Int = 2,
    val chatBackoffBaseMs: Long = 1_000,
    val chatBackoffMaxMs: Long = 8_000
) {
    init {
        // Clamp all configured values to safe ranges.
        require(repetitionThreshold >= 1) {
            "repetitionThreshold must be >= 1 but was $repetitionThreshold"
        }
        require(cycleRepetitions >= 1) {
            "cycleRepetitions must be >= 1 but was $cycleRepetitions"
        }
        require(maxCyclePeriod in 1..16) {
            "maxCyclePeriod must be in 1..16 but was $maxCyclePeriod"
        }
        require(chatMaxRetries in 0..5) {
            "chatMaxRetries must be in 0..5 but was $chatMaxRetries"
        }
        require(chatBackoffBaseMs >= 0) {
            "chatBackoffBaseMs must be >= 0 but was $chatBackoffBaseMs"
        }
        require(chatBackoffMaxMs >= chatBackoffBaseMs) {
            "chatBackoffMaxMs ($chatBackoffMaxMs) must be >= chatBackoffBaseMs ($chatBackoffBaseMs)"
        }
    }
}
