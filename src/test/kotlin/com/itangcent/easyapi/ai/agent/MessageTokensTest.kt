package com.itangcent.easyapi.ai.agent

import com.itangcent.easyapi.ai.AiMessage
import com.itangcent.easyapi.ai.AiToolCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [contentLength], [estimatedTokens], and [trimToTokenBudget].
 *
 * These helpers are pure character-heuristic logic with no PSI/IntelliJ
 * dependencies, so they can be exercised as plain JUnit tests. The
 * critical-correctness property under test is that a matched
 * (Assistant-with-tool-calls + all its ToolResults) pair is never split
 * during trimming — orphaning either side yields a provider 400 error.
 */
class MessageTokensTest {

    /**
     * Budget used by the trimming tests below. Picked to match the historical
     * magic constant so these behavioural tests are budget-agnostic: they
     * exercise *pairing* and *preservation* invariants, not the exact
     * per-model budget derivation (see the [contextWindowToBudget] tests for
     * that).
     */
    private val testBudget = 32_000

    // ---------------- contextWindowToBudget ----------------

    @Test
    fun contextWindowToBudgetReservesOutputAndTakesFraction() {
        // (128_000 - 4_000) * 0.4 = 49_600
        assertEquals(49_600, contextWindowToBudget(128_000))
    }

    @Test
    fun contextWindowToBudgetClampsToFloorForTinyWindows() {
        // 8k local: (8_192 - 4_000) * 0.4 = 1_676 -> clamped to MIN (2_000).
        assertEquals(2_000, contextWindowToBudget(8_192))
    }

    @Test
    fun contextWindowToBudgetClampsToCeilingForHugeWindows() {
        // 1M Gemini: would be ~398_400 -> clamped to MAX (64_000).
        assertEquals(64_000, contextWindowToBudget(1_000_000))
    }

    @Test
    fun contextWindowToBudgetNeverGoesNegativeOrBelowFloor() {
        // Degenerate zero/negative window still yields the floor.
        assertEquals(2_000, contextWindowToBudget(0))
        assertEquals(2_000, contextWindowToBudget(-1))
    }

    // ---------------- contentLength ----------------

    @Test
    fun contentLengthOfSystemIsContentLength() {
        assertEquals(5, AiMessage.System("hello").contentLength())
    }

    @Test
    fun contentLengthOfUserIsContentLength() {
        assertEquals(7, AiMessage.User("hello!!").contentLength())
    }

    @Test
    fun contentLengthOfToolResultIsContentLength() {
        assertEquals(
            3,
            AiMessage.ToolResult(toolCallId = "c1", name = "n", content = "abc").contentLength()
        )
    }

    @Test
    fun contentLengthOfAssistantWithTextOnlyIsContentLength() {
        assertEquals(4, AiMessage.Assistant("text", null).contentLength())
    }

    @Test
    fun contentLengthOfAssistantWithNullContentAndNoToolCallsIsZero() {
        assertEquals(0, AiMessage.Assistant(null, null).contentLength())
    }

    @Test
    fun contentLengthOfAssistantWithToolCallsIncludesCallFields() {
        val msg = AiMessage.Assistant(
            content = "ab",
            toolCalls = listOf(
                AiToolCall(id = "id1", name = "nm", arguments = "{}"),
                AiToolCall(id = "i2", name = "n", arguments = "{\"a\":1}")
            )
        )
        // 2 (content) + (3+2+2) + (2+1+7) = 2 + 7 + 10 = 19
        assertEquals(19, msg.contentLength())
    }

    @Test
    fun contentLengthOfAssistantWithEmptyToolCallsTreatsAsZero() {
        // Empty list is non-null but contributes nothing.
        assertEquals(3, AiMessage.Assistant("abc", emptyList()).contentLength())
    }

    // ---------------- estimatedTokens ----------------

    @Test
    fun estimatedTokensCeilsToNextToken() {
        // 1 char → 1 token (ceil(1/4)).
        assertEquals(1, AiMessage.System("a").estimatedTokens())
        // 4 chars → 1 token.
        assertEquals(1, AiMessage.System("abcd").estimatedTokens())
        // 5 chars → 2 tokens.
        assertEquals(2, AiMessage.System("abcde").estimatedTokens())
    }

    @Test
    fun estimatedTokensOfEmptyMessageIsZero() {
        assertEquals(0, AiMessage.System("").estimatedTokens())
        assertEquals(0, AiMessage.Assistant(null, null).estimatedTokens())
    }

    // ---------------- trimToTokenBudget ----------------

    /**
     * Builds a user-message string of roughly [chars] characters by repeating
     * 'x' — used to push memory over the 32 000-token (~128 000-char) budget
     * without allocating 100 000 small message objects.
     */
    private fun bigUser(chars: Int): AiMessage.User = AiMessage.User("x".repeat(chars))

    @Test
    fun trimIsNoOpWhenUnderBudget() {
        val mem = AgentMemory()
        mem.messages += AiMessage.System("sys")
        mem.messages += AiMessage.User("hi")
        val before = mem.messages.toList()
        trimToTokenBudget(mem, testBudget)
        assertEquals(before, mem.messages.toList())
    }

    @Test
    fun trimPreservesLeadingSystemMessages() {
        val mem = AgentMemory()
        mem.messages += AiMessage.System("preamble")
        // Pad to force trimming: 4 user messages each ~40 000 chars (~10 000 tokens).
        mem.messages += bigUser(40_000)
        mem.messages += bigUser(40_000)
        mem.messages += bigUser(40_000)
        mem.messages += bigUser(40_000)
        trimToTokenBudget(mem, testBudget)
        // The leading System message must always survive.
        assertTrue(
            "first message must remain the System preamble",
            mem.messages.first() is AiMessage.System
        )
    }

    @Test
    fun trimPreservesLastUserMessage() {
        val mem = AgentMemory()
        mem.messages += AiMessage.System("sys")
        mem.messages += bigUser(40_000)
        mem.messages += bigUser(40_000)
        mem.messages += bigUser(40_000)
        mem.messages += bigUser(40_000)
        mem.messages += AiMessage.User("the current turn")
        trimToTokenBudget(mem, testBudget)
        // The last User message ("the current turn") must survive.
        val last = mem.messages.last()
        assertTrue("last message must be the User turn", last is AiMessage.User)
        assertEquals("the current turn", (last as AiMessage.User).content)
    }

    @Test
    fun trimDropsOldestNonPreservedMessagesFirst() {
        val mem = AgentMemory()
        mem.messages += AiMessage.System("sys")
        // Oldest user msg — droppable.
        mem.messages += AiMessage.User("oldest-droppable")
        // 4 * ~10 000 tokens = ~40 000 tokens, well over the 32 000 budget.
        mem.messages += bigUser(40_000)
        mem.messages += bigUser(40_000)
        mem.messages += bigUser(40_000)
        mem.messages += bigUser(40_000)
        mem.messages += AiMessage.User("last-turn")
        trimToTokenBudget(mem, testBudget)
        // The small "oldest-droppable" should be gone (it was the first
        // droppable range and contributed little, but trimming continues
        // until under budget — verify it's not in the survivors).
        assertTrue(
            "oldest droppable should be dropped",
            mem.messages.none {
                it is AiMessage.User && it.content == "oldest-droppable"
            }
        )
    }

    @Test
    fun trimKeepsAssistantToolCallsPairedWithToolResults() {
        val mem = AgentMemory()
        mem.messages += AiMessage.System("sys")
        mem.messages += bigUser(40_000)
        mem.messages += bigUser(40_000)
        // A paired (Assistant-with-tool-calls + its ToolResults). Since it's
        // the *last* pair, it's preserved by invariant #3 — but we also
        // verify the pairing is intact (Assistant followed by ToolResults).
        val call = AiToolCall(id = "c1", name = "tool", arguments = "{}")
        mem.messages += AiMessage.Assistant(content = null, toolCalls = listOf(call))
        mem.messages += AiMessage.ToolResult("c1", "tool", "{\"ok\":true}")
        mem.messages += AiMessage.User("final")
        trimToTokenBudget(mem, testBudget)
        // Find the Assistant-with-tool-calls in the survivors.
        val asstIdx = mem.messages.indexOfFirst {
            it is AiMessage.Assistant && !it.toolCalls.isNullOrEmpty()
        }
        assertTrue(
            "paired Assistant must survive (it's the last pair)",
            asstIdx >= 0
        )
        // Every message after the Assistant, up to the next non-ToolResult,
        // must be a ToolResult — the pair was kept together.
        var j = asstIdx + 1
        while (j < mem.messages.size && mem.messages[j] is AiMessage.ToolResult) j++
        assertTrue(
            "at least one ToolResult must follow the Assistant",
            j > asstIdx + 1
        )
        // And the next non-ToolResult after the pair (if any) must not be
        // an orphan ToolResult that belonged to a dropped Assistant.
        if (j < mem.messages.size) {
            assertTrue(
                "no orphan ToolResult after the pair",
                mem.messages[j] !is AiMessage.ToolResult
            )
        }
    }

    @Test
    fun trimDropsPairedAssistantAndToolResultsTogether() {
        // Construct memory where a *non-last* paired Assistant+ToolResults
        // must be dropped to fit. The pair must be dropped atomically —
        // never the Assistant alone (which would orphan the ToolResult).
        val mem = AgentMemory()
        mem.messages += AiMessage.System("sys")
        mem.messages += AiMessage.User("turn-1")
        // Old pair (droppable).
        val call1 = AiToolCall(id = "c1", name = "tool", arguments = "{}")
        mem.messages += AiMessage.Assistant(content = null, toolCalls = listOf(call1))
        mem.messages += AiMessage.ToolResult("c1", "tool", bigUser(40_000).content)
        mem.messages += AiMessage.ToolResult("c1", "tool", bigUser(40_000).content)
        // Padding to force the trim to actually drop something.
        mem.messages += bigUser(40_000)
        mem.messages += bigUser(40_000)
        // Last pair (preserved by invariant #3).
        val call2 = AiToolCall(id = "c2", name = "tool", arguments = "{}")
        mem.messages += AiMessage.Assistant(content = null, toolCalls = listOf(call2))
        mem.messages += AiMessage.ToolResult("c2", "tool", "ok")
        mem.messages += AiMessage.User("final")
        trimToTokenBudget(mem, testBudget)
        // The old pair (c1) must be entirely absent — no Assistant-with-c1
        // and no ToolResult with id c1.
        assertTrue(
            "old Assistant must be dropped",
            mem.messages.none {
                it is AiMessage.Assistant &&
                    it.toolCalls?.any { c -> c.id == "c1" } == true
            }
        )
        assertTrue(
            "old ToolResult must be dropped together with its Assistant",
            mem.messages.none { it is AiMessage.ToolResult && it.toolCallId == "c1" }
        )
        // The last pair (c2) survives intact.
        assertTrue(
            "last Assistant must survive",
            mem.messages.any {
                it is AiMessage.Assistant &&
                    it.toolCalls?.any { c -> c.id == "c2" } == true
            }
        )
        assertTrue(
            "last ToolResult must survive with its Assistant",
            mem.messages.any { it is AiMessage.ToolResult && it.toolCallId == "c2" }
        )
    }

    @Test
    fun trimResultingMemoryIsUnderBudget() {
        val mem = AgentMemory()
        mem.messages += AiMessage.System("sys")
        mem.messages += bigUser(60_000)
        mem.messages += bigUser(60_000)
        mem.messages += bigUser(60_000)
        mem.messages += AiMessage.User("final")
        trimToTokenBudget(mem, testBudget)
        val total = mem.messages.sumOf { it.estimatedTokens() }
        assertTrue(
            "after trim, total tokens ($total) must be <= 32000",
            total <= 32_000
        )
    }

    @Test
    fun trimKeepsStandaloneAssistantMessagesDroppable() {
        // An Assistant with no tool calls is a standalone droppable range,
        // not a pair — verify it can be dropped without affecting neighbours.
        val mem = AgentMemory()
        mem.messages += AiMessage.System("sys")
        mem.messages += AiMessage.User("turn-1")
        mem.messages += AiMessage.Assistant("droppable standalone", null)
        mem.messages += bigUser(60_000)
        mem.messages += bigUser(60_000)
        mem.messages += bigUser(60_000)
        mem.messages += AiMessage.User("final")
        trimToTokenBudget(mem, testBudget)
        assertTrue(
            "standalone Assistant should be dropped",
            mem.messages.none {
                it is AiMessage.Assistant && it.content == "droppable standalone"
            }
        )
    }

    @Test
    fun trimWithOnlySystemAndLastUserKeepsBothWhenOverBudget() {
        // Edge case: System + one huge User. The huge User is the last user
        // (preserved). The System is the leading block (preserved). Nothing
        // is droppable — trim is a no-op even though over budget.
        val mem = AgentMemory()
        mem.messages += AiMessage.System("sys")
        mem.messages += bigUser(200_000) // ~50 000 tokens, over budget
        trimToTokenBudget(mem, testBudget)
        assertEquals(2, mem.messages.size)
        assertTrue(mem.messages[0] is AiMessage.System)
        assertTrue(mem.messages[1] is AiMessage.User)
    }

    @Test
    fun trimEngagesForSmallWindowBudget() {
        // Regression for the moonshot-v1-8k / ollama-llama3 case: an 8k
        // window derives a ~2k budget (see contextWindowToBudget). Before
        // this change, trimToTokenBudget used a hardcoded 32k and was inert
        // for these models — the provider would 400 before trimming fired.
        // Now the small budget must actually trim droppable history.
        val smallBudget = contextWindowToBudget(8_192) // -> 2_000
        val mem = AgentMemory()
        mem.messages += AiMessage.System("sys")
        // Big droppable history in the middle...
        mem.messages += bigUser(20_000) // ~5_000 tokens
        // ...and a small, preserved last user turn.
        mem.messages += AiMessage.User("current turn")
        trimToTokenBudget(mem, smallBudget)
        val total = mem.messages.sumOf { it.estimatedTokens() }
        assertTrue(
            "small-window budget must trim droppable history under $smallBudget, was $total",
            total <= smallBudget
        )
        // System preamble + last user turn must survive regardless.
        assertTrue(mem.messages.first() is AiMessage.System)
        assertTrue(mem.messages.last() is AiMessage.User)
    }
}
