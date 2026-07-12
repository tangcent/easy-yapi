package com.itangcent.easyapi.ai.agent

import com.itangcent.easyapi.ai.AiMessage
import com.itangcent.easyapi.ai.AiToolCall
import com.itangcent.easyapi.ai.tools.ToolResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure unit tests for [LoopGuard] detection logic.
 *
 * No IDE fixture needed — the guard is pure logic over fingerprints.
 * Uses plain JUnit 4.
 */
class LoopGuardTest {

    // ==================================================================
    // Task 9: Normalization & fingerprinting helpers
    // ==================================================================

    @Test
    fun testNormalizeArgsBlankCollapsesToEmptyMap() {
        val blank = normalizeArgs("")
        val whitespace = normalizeArgs("  ")
        val emptyObj = normalizeArgs("{}")
        val whitespaceObj = normalizeArgs(" {} ")
        assertEquals("blank and '{}' must collapse to the same fingerprint", blank, emptyObj)
        assertEquals("whitespace-only and blank must collapse to the same fingerprint", blank, whitespace)
        assertEquals("' {} ' and '{}' must collapse to the same fingerprint", emptyObj, whitespaceObj)
    }

    @Test
    fun testNormalizeArgsKeyOrderIndependence() {
        val a = normalizeArgs("""{"a":1,"b":2}""")
        val b = normalizeArgs("""{"b":2,"a":1}""")
        assertEquals("key order must not affect the fingerprint", a, b)
    }

    @Test
    fun testNormalizeArgsWhitespaceIndependence() {
        val compact = normalizeArgs("""{"a":1}""")
        val spaced = normalizeArgs("""{ "a" : 1 }""")
        assertEquals("whitespace differences must not affect the fingerprint", compact, spaced)
    }

    @Test
    fun testNormalizeArgsUnparseableTreatedAsEmptyMap() {
        val unparseable = normalizeArgs("not json")
        val empty = normalizeArgs("")
        assertEquals("unparseable arguments must collapse to the empty-map fingerprint", empty, unparseable)
    }

    @Test
    fun testNormalizeArgsNestedKeyOrderIndependence() {
        val a = normalizeArgs("""{"outer":{"x":1,"y":2},"z":3}""")
        val b = normalizeArgs("""{"z":3,"outer":{"y":2,"x":1}}""")
        assertEquals("nested key order must not affect the fingerprint", a, b)
    }

    @Test
    fun testResultFingerprintTextEquivalentJsonCollapses() {
        val a = resultFingerprint(ToolResult.Text("""{"a":1,"b":2}"""))
        val b = resultFingerprint(ToolResult.Text("""{"b":2,"a":1}"""))
        assertEquals("equivalent JSON-structured text results must collapse to the same fingerprint", a, b)
    }

    @Test
    fun testResultFingerprintErrorDistinctFromSuccess() {
        val errorFp = resultFingerprint(ToolResult.Error("some message"))
        val textFp = resultFingerprint(ToolResult.Text("some message"))
        assertFalse(
            "an Error must NOT have the same fingerprint as a success with the same text",
            errorFp == textFp
        )
    }

    @Test
    fun testResultFingerprintSameMessageErrorsAreSame() {
        val a = resultFingerprint(ToolResult.Error("disk full"))
        val b = resultFingerprint(ToolResult.Error("disk full"))
        assertEquals("two errors with the same message must be the same fingerprint", a, b)
    }

    @Test
    fun testResultFingerprintDifferentMessageErrorsAreDifferent() {
        val a = resultFingerprint(ToolResult.Error("disk full"))
        val b = resultFingerprint(ToolResult.Error("network down"))
        assertFalse("two errors with different messages must be different fingerprints", a == b)
    }

    @Test
    fun testNormalizeReasoningCaseAndWhitespace() {
        val a = normalizeReasoning("Hello  World")
        val b = normalizeReasoning("hello world")
        assertEquals("whitespace + case normalization must collapse to the same fingerprint", a, b)
    }

    @Test
    fun testNormalizeReasoningTrimAndCollapse() {
        val a = normalizeReasoning("  The   quick\tbrown\nfox  ")
        val b = normalizeReasoning("the quick brown fox")
        assertEquals("trim + whitespace collapse + lowercase must produce the same fingerprint", a, b)
    }

    // ==================================================================
    // Task 10: Consecutive duplicate detection
    // ==================================================================

    @Test
    fun testConsecutiveDuplicateTerminatesAtThreshold() {
        val guard = LoopGuard(LoopSafetyConfig())
        val tc = AiToolCall("c1", "list_rule_keys", "{}")
        val result = ToolResult.Text("ok")

        assertEquals(LoopGuard.Verdict.Proceed, guard.observeResult(tc, result))
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeResult(tc, result))
        val verdict = guard.observeResult(tc, result)
        assertTrue("3rd identical call must terminate", verdict is LoopGuard.Verdict.Terminate)
        val reason = (verdict as LoopGuard.Verdict.Terminate).reason
        assertTrue("reason must be ConsecutiveDuplicate", reason is LoopGuard.LoopReason.ConsecutiveDuplicate)
        val cd = reason as LoopGuard.LoopReason.ConsecutiveDuplicate
        assertEquals("list_rule_keys", cd.tool)
        assertEquals(3, cd.count)
    }

    @Test
    fun testConsecutiveDuplicateDifferentCallResetsStreak() {
        val guard = LoopGuard(LoopSafetyConfig())
        val a = AiToolCall("c1", "list_rule_keys", "{}")
        val b = AiToolCall("c2", "read_rule_file", """{"key":"x"}""")
        // Distinct results per tool so stagnation doesn't interfere.
        val resA = ToolResult.Text("""{"r":"a"}""")
        val resB = ToolResult.Text("""{"r":"b"}""")

        // A, A, B, A — no termination (max streak is 2)
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeResult(a, resA))
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeResult(a, resA))
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeResult(b, resB))
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeResult(a, resA))
    }

    @Test
    fun testConsecutiveDuplicateABAStaysAt1() {
        val guard = LoopGuard(LoopSafetyConfig())
        val a = AiToolCall("c1", "list_rule_keys", "{}")
        val b = AiToolCall("c2", "read_rule_file", """{"key":"x"}""")
        val resA = ToolResult.Text("""{"r":"a"}""")
        val resB = ToolResult.Text("""{"r":"b"}""")

        // A, B, A — two streaks of length 1, no termination
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeResult(a, resA))
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeResult(b, resB))
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeResult(a, resA))
    }

    @Test
    fun testConsecutiveDuplicatePerTurnFreshness() {
        val guard1 = LoopGuard(LoopSafetyConfig())
        val tc = AiToolCall("c1", "list_rule_keys", "{}")
        val result = ToolResult.Text("ok")

        // First guard: two calls (streak = 2, no termination)
        assertEquals(LoopGuard.Verdict.Proceed, guard1.observeResult(tc, result))
        assertEquals(LoopGuard.Verdict.Proceed, guard1.observeResult(tc, result))

        // Fresh guard: starts with zeroed counter
        val guard2 = LoopGuard(LoopSafetyConfig())
        assertEquals(LoopGuard.Verdict.Proceed, guard2.observeResult(tc, result))
    }

    // ==================================================================
    // Task 11: Call-sequence cycle detection
    // ==================================================================

    @Test
    fun testCallCyclePeriod2Detected() {
        val guard = LoopGuard(LoopSafetyConfig())
        val a = AiToolCall("c1", "list_rule_keys", "{}")
        val b = AiToolCall("c2", "read_rule_file", """{"key":"x"}""")
        // Distinct results per tool so stagnation doesn't interfere.
        val resA = ToolResult.Text("""{"r":"a"}""")
        val resB = ToolResult.Text("""{"r":"b"}""")

        // A, B, A, B → cycle of period 2, 2 repetitions
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeResult(a, resA))
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeResult(b, resB))
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeResult(a, resA))
        val verdict = guard.observeResult(b, resB)
        assertTrue("A,B,A,B must terminate as a cycle", verdict is LoopGuard.Verdict.Terminate)
        val reason = (verdict as LoopGuard.Verdict.Terminate).reason
        assertTrue("reason must be CallCycle", reason is LoopGuard.LoopReason.CallCycle)
        val cycle = reason as LoopGuard.LoopReason.CallCycle
        assertEquals(2, cycle.period)
        assertEquals(2, cycle.repetitions)
        assertEquals(listOf("list_rule_keys", "read_rule_file"), cycle.sequence)
    }

    @Test
    fun testCallCyclePeriod1CaughtByConsecutive() {
        // Period-1 cycle (A, A, A) is caught by the consecutive detector
        // (threshold=3), not the cycle detector.
        val guard = LoopGuard(LoopSafetyConfig())
        val a = AiToolCall("c1", "list_rule_keys", "{}")
        val result = ToolResult.Text("ok")

        assertEquals(LoopGuard.Verdict.Proceed, guard.observeResult(a, result))
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeResult(a, result))
        val verdict = guard.observeResult(a, result)
        assertTrue("A,A,A must terminate", verdict is LoopGuard.Verdict.Terminate)
        val reason = (verdict as LoopGuard.Verdict.Terminate).reason
        assertTrue(
            "period-1 cycle must be caught by ConsecutiveDuplicate, not CallCycle",
            reason is LoopGuard.LoopReason.ConsecutiveDuplicate
        )
    }

    @Test
    fun testCallCycleNonCyclicSequenceNoTermination() {
        val guard = LoopGuard(LoopSafetyConfig())
        val a = AiToolCall("c1", "list_rule_keys", "{}")
        val b = AiToolCall("c2", "read_rule_file", """{"key":"x"}""")
        val c = AiToolCall("c3", "get_psi_class_info", """{"cls":"X"}""")
        val d = AiToolCall("c4", "find_classes_by_annotation", """{"ann":"Y"}""")
        // Distinct results per tool so stagnation doesn't interfere.
        val resA = ToolResult.Text("""{"r":"a"}""")
        val resB = ToolResult.Text("""{"r":"b"}""")
        val resC = ToolResult.Text("""{"r":"c"}""")
        val resD = ToolResult.Text("""{"r":"d"}""")

        // A, B, C, D — no cycle
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeResult(a, resA))
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeResult(b, resB))
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeResult(c, resC))
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeResult(d, resD))
    }

    @Test
    fun testCallCyclePeriod3Detected() {
        val guard = LoopGuard(LoopSafetyConfig())
        val a = AiToolCall("c1", "list_rule_keys", "{}")
        val b = AiToolCall("c2", "read_rule_file", """{"key":"x"}""")
        val c = AiToolCall("c3", "get_psi_class_info", """{"cls":"X"}""")
        // Distinct results per tool so stagnation doesn't interfere.
        val resA = ToolResult.Text("""{"r":"a"}""")
        val resB = ToolResult.Text("""{"r":"b"}""")
        val resC = ToolResult.Text("""{"r":"c"}""")

        // A, B, C, A, B, C → cycle of period 3, 2 repetitions
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeResult(a, resA))
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeResult(b, resB))
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeResult(c, resC))
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeResult(a, resA))
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeResult(b, resB))
        val verdict = guard.observeResult(c, resC)
        assertTrue("A,B,C,A,B,C must terminate as a period-3 cycle", verdict is LoopGuard.Verdict.Terminate)
        val reason = (verdict as LoopGuard.Verdict.Terminate).reason
        assertTrue("reason must be CallCycle", reason is LoopGuard.LoopReason.CallCycle)
        val cycle = reason as LoopGuard.LoopReason.CallCycle
        assertEquals(3, cycle.period)
        assertEquals(2, cycle.repetitions)
    }

    @Test
    fun testCallCyclePerTurnFreshness() {
        val guard1 = LoopGuard(LoopSafetyConfig())
        val a = AiToolCall("c1", "list_rule_keys", "{}")
        val b = AiToolCall("c2", "read_rule_file", """{"key":"x"}""")
        val resA = ToolResult.Text("""{"r":"a"}""")
        val resB = ToolResult.Text("""{"r":"b"}""")

        // First guard: A, B, A (not yet a cycle — only 1.5 repetitions)
        assertEquals(LoopGuard.Verdict.Proceed, guard1.observeResult(a, resA))
        assertEquals(LoopGuard.Verdict.Proceed, guard1.observeResult(b, resB))
        assertEquals(LoopGuard.Verdict.Proceed, guard1.observeResult(a, resA))

        // Fresh guard: starts with empty history
        val guard2 = LoopGuard(LoopSafetyConfig())
        assertEquals(LoopGuard.Verdict.Proceed, guard2.observeResult(a, resA))
    }

    // ==================================================================
    // Task 12: Output stagnation detection
    // ==================================================================

    @Test
    fun testOutputStagnationIdenticalResultsTerminate() {
        val guard = LoopGuard(LoopSafetyConfig())
        // Different tools, same result content → stagnation
        val tc1 = AiToolCall("c1", "list_rule_keys", "{}")
        val tc2 = AiToolCall("c2", "read_rule_file", """{"key":"a"}""")
        val tc3 = AiToolCall("c3", "get_psi_class_info", """{"cls":"X"}""")
        val result = ToolResult.Text("""{"data":"same"}""")

        assertEquals(LoopGuard.Verdict.Proceed, guard.observeResult(tc1, result))
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeResult(tc2, result))
        val verdict = guard.observeResult(tc3, result)
        assertTrue("3 identical results must terminate as stagnation", verdict is LoopGuard.Verdict.Terminate)
        val reason = (verdict as LoopGuard.Verdict.Terminate).reason
        assertTrue("reason must be OutputStagnation", reason is LoopGuard.LoopReason.OutputStagnation)
        val os = reason as LoopGuard.LoopReason.OutputStagnation
        assertEquals(3, os.count)
    }

    @Test
    fun testOutputStagnationDifferentResultResetsCounter() {
        val guard = LoopGuard(LoopSafetyConfig())
        // Different tools so consecutive detection doesn't interfere.
        val tc1 = AiToolCall("c1", "list_rule_keys", "{}")
        val tc2 = AiToolCall("c2", "read_rule_file", """{"key":"a"}""")
        val tc3 = AiToolCall("c3", "get_psi_class_info", """{"cls":"X"}""")
        val tc4 = AiToolCall("c4", "find_classes_by_annotation", """{"ann":"Y"}""")
        val r1 = ToolResult.Text("""{"data":"first"}""")
        val r2 = ToolResult.Text("""{"data":"second"}""")

        assertEquals(LoopGuard.Verdict.Proceed, guard.observeResult(tc1, r1)) // stag=1
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeResult(tc2, r1)) // stag=2
        // Different result resets
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeResult(tc3, r2)) // stag=1
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeResult(tc4, r2)) // stag=2
    }

    @Test
    fun testOutputStagnationErrorDistinctFromSuccess() {
        val guard = LoopGuard(LoopSafetyConfig())
        // Different tools so consecutive detection doesn't interfere.
        val tc1 = AiToolCall("c1", "list_rule_keys", "{}")
        val tc2 = AiToolCall("c2", "read_rule_file", """{"key":"a"}""")
        val tc3 = AiToolCall("c3", "get_psi_class_info", """{"cls":"X"}""")
        // Error with the same text as a success must NOT count as identical
        val textResult = ToolResult.Text("disk full")
        val errorResult = ToolResult.Error("disk full")

        assertEquals(LoopGuard.Verdict.Proceed, guard.observeResult(tc1, textResult))  // stag=1 (text)
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeResult(tc2, errorResult)) // stag=1 (error ≠ text, reset)
        // text again — error ≠ text so this is also a reset, no stagnation
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeResult(tc3, textResult))  // stag=1 (text ≠ error, reset)
    }

    @Test
    fun testOutputStagnationSameMessageErrorsCount() {
        val guard = LoopGuard(LoopSafetyConfig())
        val tc1 = AiToolCall("c1", "list_rule_keys", "{}")
        val tc2 = AiToolCall("c2", "read_rule_file", """{"key":"a"}""")
        val tc3 = AiToolCall("c3", "get_psi_class_info", """{"cls":"X"}""")
        val error = ToolResult.Error("permission denied")

        assertEquals(LoopGuard.Verdict.Proceed, guard.observeResult(tc1, error))
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeResult(tc2, error))
        val verdict = guard.observeResult(tc3, error)
        assertTrue("3 identical errors must terminate as stagnation", verdict is LoopGuard.Verdict.Terminate)
        val reason = (verdict as LoopGuard.Verdict.Terminate).reason
        assertTrue("reason must be OutputStagnation", reason is LoopGuard.LoopReason.OutputStagnation)
    }

    // ==================================================================
    // Task 13: Reasoning repetition detection
    // ==================================================================

    @Test
    fun testReasoningRepetitionIdenticalTextTerminates() {
        val guard = LoopGuard(LoopSafetyConfig())
        val msg = AiMessage.Assistant("I should check the rules first.", null)

        assertEquals(LoopGuard.Verdict.Proceed, guard.observeReasoning(msg))
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeReasoning(msg))
        val verdict = guard.observeReasoning(msg)
        assertTrue("3 identical reasoning messages must terminate", verdict is LoopGuard.Verdict.Terminate)
        val reason = (verdict as LoopGuard.Verdict.Terminate).reason
        assertTrue("reason must be ReasoningRepetition", reason is LoopGuard.LoopReason.ReasoningRepetition)
        val rr = reason as LoopGuard.LoopReason.ReasoningRepetition
        assertEquals(3, rr.count)
    }

    @Test
    fun testReasoningRepetitionBlankSkips() {
        val guard = LoopGuard(LoopSafetyConfig())
        val blank = AiMessage.Assistant(null, null)
        val nonBlank = AiMessage.Assistant("thinking...", null)

        // Blank content neither advances nor resets
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeReasoning(blank))
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeReasoning(nonBlank))  // run=1
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeReasoning(blank))     // skip (no reset)
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeReasoning(nonBlank))  // run=2 (blank didn't reset)
        // Only 2 non-blank observations — no termination (threshold=3)
    }

    @Test
    fun testReasoningRepetitionDifferentTextResets() {
        val guard = LoopGuard(LoopSafetyConfig())
        val a = AiMessage.Assistant("Let me check the rules.", null)
        val b = AiMessage.Assistant("Now I will read the file.", null)

        assertEquals(LoopGuard.Verdict.Proceed, guard.observeReasoning(a))
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeReasoning(a))
        // Different text resets
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeReasoning(b))
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeReasoning(b))
    }

    @Test
    fun testReasoningRepetitionNormalization() {
        val guard = LoopGuard(LoopSafetyConfig())
        // "Hello  World" and "hello world" normalize to the same fingerprint
        val a = AiMessage.Assistant("Hello  World", null)
        val b = AiMessage.Assistant("hello world", null)

        assertEquals(LoopGuard.Verdict.Proceed, guard.observeReasoning(a))
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeReasoning(b))
        val verdict = guard.observeReasoning(a)
        assertTrue("normalized-identical reasoning must terminate after 3", verdict is LoopGuard.Verdict.Terminate)
        val reason = (verdict as LoopGuard.Verdict.Terminate).reason
        assertTrue("reason must be ReasoningRepetition", reason is LoopGuard.LoopReason.ReasoningRepetition)
    }

    @Test
    fun testReasoningRepetitionEmptyStringIsBlank() {
        val guard = LoopGuard(LoopSafetyConfig())
        val empty = AiMessage.Assistant("", null)
        val nonBlank = AiMessage.Assistant("thinking...", null)

        // Empty string is blank → skip (no advance, no reset)
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeReasoning(empty))
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeReasoning(nonBlank))  // run=1
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeReasoning(empty))     // skip (no reset)
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeReasoning(nonBlank))  // run=2
        // Only 2 non-blank → no termination (threshold=3)
    }

    // ==================================================================
    // Task 14: Debounce hook
    // ==================================================================

    @Test
    fun testDebounceIdenticalToLastBlocks() {
        val guard = LoopGuard(LoopSafetyConfig())
        val tc = AiToolCall("c1", "list_rule_keys", "{}")
        val result = ToolResult.Text("ok")

        // First call: observeResult sets lastCall
        guard.observeResult(tc, result)

        // Second identical call: checkBeforeDispatch must Block
        val verdict = guard.checkBeforeDispatch(tc)
        assertTrue("identical-to-last call must be blocked", verdict is LoopGuard.Verdict.Block)
        val block = verdict as LoopGuard.Verdict.Block
        assertTrue("blocked result must be an Error", block.result is ToolResult.Error)
        val error = block.result as ToolResult.Error
        assertTrue("error message must name the tool", error.message.contains("list_rule_keys"))
        // Must NOT contain the arguments body (security)
        assertFalse("error message must NOT contain arguments", error.message.contains("{}"))
    }

    @Test
    fun testDebounceDifferentCallProceeds() {
        val guard = LoopGuard(LoopSafetyConfig())
        val a = AiToolCall("c1", "list_rule_keys", "{}")
        val b = AiToolCall("c2", "read_rule_file", """{"key":"x"}""")
        val result = ToolResult.Text("ok")

        guard.observeResult(a, result)
        val verdict = guard.checkBeforeDispatch(b)
        assertEquals("different call must proceed", LoopGuard.Verdict.Proceed, verdict)
    }

    @Test
    fun testDebounceDisabledAlwaysProceeds() {
        val guard = LoopGuard(LoopSafetyConfig(debounceEnabled = false))
        val tc = AiToolCall("c1", "list_rule_keys", "{}")
        val result = ToolResult.Text("ok")

        guard.observeResult(tc, result)
        val verdict = guard.checkBeforeDispatch(tc)
        assertEquals("with debounce disabled, identical call must proceed", LoopGuard.Verdict.Proceed, verdict)
    }

    @Test
    fun testDebounceAfterBlockObserveResultAdvancesStreak() {
        val guard = LoopGuard(LoopSafetyConfig())
        val tc = AiToolCall("c1", "list_rule_keys", "{}")
        val result = ToolResult.Text("ok")

        // First call: streak = 1
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeResult(tc, result))

        // Second call: blocked by debounce
        val block = guard.checkBeforeDispatch(tc)
        assertTrue(block is LoopGuard.Verdict.Block)
        // observeResult is still called with the blocked result → streak = 2
        val blockedResult = (block as LoopGuard.Verdict.Block).result
        assertEquals(LoopGuard.Verdict.Proceed, guard.observeResult(tc, blockedResult))

        // Third call: blocked again
        val block2 = guard.checkBeforeDispatch(tc)
        assertTrue(block2 is LoopGuard.Verdict.Block)
        val blockedResult2 = (block2 as LoopGuard.Verdict.Block).result
        // observeResult → streak = 3 → Terminate
        val verdict = guard.observeResult(tc, blockedResult2)
        assertTrue("streak must reach threshold after blocks", verdict is LoopGuard.Verdict.Terminate)
        val reason = (verdict as LoopGuard.Verdict.Terminate).reason
        assertTrue(reason is LoopGuard.LoopReason.ConsecutiveDuplicate)
        assertEquals(3, (reason as LoopGuard.LoopReason.ConsecutiveDuplicate).count)
    }

    @Test
    fun testDebounceFirstCallProceeds() {
        val guard = LoopGuard(LoopSafetyConfig())
        val tc = AiToolCall("c1", "list_rule_keys", "{}")
        // First call (no lastCall) → Proceed
        val verdict = guard.checkBeforeDispatch(tc)
        assertEquals("first call must proceed", LoopGuard.Verdict.Proceed, verdict)
    }
}
