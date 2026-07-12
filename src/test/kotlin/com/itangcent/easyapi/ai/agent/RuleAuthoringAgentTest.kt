package com.itangcent.easyapi.ai.agent

import com.itangcent.easyapi.ai.AiRuntimeConfig
import com.itangcent.easyapi.ai.AiMessage
import com.itangcent.easyapi.ai.AiToolCall
import com.itangcent.easyapi.ai.AiProvider
import com.itangcent.easyapi.ai.ChatTimeoutException
import com.itangcent.easyapi.ai.tools.AiTool
import com.itangcent.easyapi.ai.tools.AskClarificationTool
import com.itangcent.easyapi.ai.tools.ToolContext
import com.itangcent.easyapi.ai.tools.ToolKind
import com.itangcent.easyapi.ai.tools.ToolRegistry
import com.itangcent.easyapi.ai.tools.ToolResult
import com.itangcent.easyapi.config.source.RuleFileResolver
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancelAndJoin
import java.io.IOException

/**
 * Tests for [RuleAuthoringAgent].
 *
 * Each test enqueues scripted responses in [FakeAIService], runs one turn
 * of the agent, and asserts the [TurnOutcome] + emitted [AgentEvent] sequence
 * (the PRA trace).
 */
class RuleAuthoringAgentTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var aiService: FakeAIService
    private lateinit var approvalGate: FakeApprovalGate
    private lateinit var memory: AgentMemory
    private lateinit var ctx: ToolContext

    override fun setUp() {
        super.setUp()
        aiService = FakeAIService()
        approvalGate = FakeApprovalGate()
        memory = AgentMemory()
        ctx = ToolContext(
            project = project,
            configReader = com.itangcent.easyapi.config.ConfigReader.getInstance(project),
            aiSettings = AiRuntimeConfig(
                provider = AiProvider.OPENAI,
                baseUrl = "", apiKey = "", model = "",
                requestTimeoutSec = 30,
                // Small budget keeps the step-limit test snappy.
                maxRequests = 3
            ),
            ruleFileResolver = RuleFileResolver(project),
            workingMemory = memory,
            approvals = approvalGate
        )
    }

    // --- scripts ---

    /**
     * Script 1: LLM perceives (`list_rule_keys`) then acts
     * (`propose_rule_content`) → events include
     * `[Thinking, Perceiving, Observed, Acting, Observed, ProposalReady]`,
     * `TurnOutcome.Proposed`.
     */
    fun testPerceiveThenPropose() = runBlocking {
        val tools = ToolRegistry(listOf(ListRuleKeysFakeTool(), ProposeRuleContentFakeTool()))
        val events = captureEvents()
        val agent = RuleAuthoringAgent(aiService, tools, ctx, events.flow)

        // Step 1: LLM calls list_rule_keys.
        aiService.enqueueToolCalls(
            AiToolCall("c1", "list_rule_keys", "{}")
        )
        // Step 2: LLM calls propose_rule_content with the staged rule.
        aiService.enqueueToolCalls(
            AiToolCall(
                "c2", "propose_rule_content",
                """{"content":"# rule","suggestedFileName":"rules.easy.api.config"}"""
            ),
            content = null
        )

        val outcome = agent.runTurn("add a header to every POST", memory)

        events.cancelAndCollect()
        assertEquals(TurnOutcome.Proposed, outcome)
        assertNotNull(memory.proposal)
        assertEquals("rules.easy.api.config", memory.proposal?.suggestedFileName)

        // Verify the PRA trace.
        val phases = events.collected.map { it::class.simpleName }
        assertTrue("should start with Thinking", phases.first() == "Thinking")
        assertTrue("should include Perceiving", phases.contains("Perceiving"))
        assertTrue("should include Acting", phases.contains("Acting"))
        assertTrue("should include ProposalReady", phases.contains("ProposalReady"))
        // Two steps means two Thinking events.
        assertEquals(2, phases.count { it == "Thinking" })
        // Terminal action emits ProposalReady exactly once.
        assertEquals(1, phases.count { it == "ProposalReady" })
    }

    /**
     * Script 2: LLM returns plain text with no tool call → events
     * `[Thinking, Message]`, `TurnOutcome.Answered`.
     */
    fun testPlainAnswer() = runBlocking {
        val tools = ToolRegistry(listOf(ListRuleKeysFakeTool()))
        val events = captureEvents()
        val agent = RuleAuthoringAgent(aiService, tools, ctx, events.flow)

        aiService.enqueueText("Could you clarify which POST endpoints you mean?")

        val outcome = agent.runTurn("add a header", memory)
        events.cancelAndCollect()

        assertEquals(TurnOutcome.Answered, outcome)
        assertNull(memory.proposal)
        val phases = events.collected.map { it::class.simpleName }
        assertEquals(listOf("Thinking", "Message", "TurnComplete"), phases)
        val msg = events.collected.filterIsInstance<AgentEvent.Message>().single()
        assertTrue(msg.content.contains("clarify"))
    }

    /**
     * Script 3: LLM loops on a non-terminal perception tool `maxRequests`
     * times → `TurnOutcome.StepLimitHit`.
     *
     * The guard config is overridden so the loop detector does NOT fire
     * within the small step budget (repetitionThreshold=10 > maxRequests=3,
     * debounce off) — this preserves the test's intent of exercising the
     * step-limit path rather than the loop-detector path.
     */
    fun testStepLimitHit() = runBlocking {
        ctx = ctx.copy(
            aiSettings = ctx.aiSettings.copy(
                loopSafety = LoopSafetyConfig(
                    repetitionThreshold = 10,
                    debounceEnabled = false
                )
            )
        )
        val tools = ToolRegistry(listOf(ListRuleKeysFakeTool()))
        val events = captureEvents()
        val agent = RuleAuthoringAgent(aiService, tools, ctx, events.flow)

        // Each step the LLM re-calls list_rule_keys (non-terminal).
        repeat(ctx.aiSettings.maxRequests) {
            aiService.enqueueToolCalls(AiToolCall("c$it", "list_rule_keys", "{}"))
        }

        val outcome = agent.runTurn("loop forever please", memory)
        events.cancelAndCollect()

        assertEquals(TurnOutcome.StepLimitHit, outcome)
        // maxRequests Thinking events emitted, no ProposalReady.
        val phases = events.collected.map { it::class.simpleName }
        assertEquals(ctx.aiSettings.maxRequests, phases.count { it == "Thinking" })
        assertEquals(0, phases.count { it == "ProposalReady" })
    }

    /**
     * Script 4: LLM calls `propose_rule_content` mid-loop after other tools
     * → loop stops immediately on the terminal action (no further Thinking
     * event after the proposal).
     */
    fun testProposeIsTerminalMidLoop() = runBlocking {
        val tools = ToolRegistry(listOf(ListRuleKeysFakeTool(), ProposeRuleContentFakeTool()))
        val events = captureEvents()
        val agent = RuleAuthoringAgent(aiService, tools, ctx, events.flow)

        // Step 1: list_rule_keys + propose_rule_content in the SAME response.
        aiService.enqueueToolCalls(
            AiToolCall("c1", "list_rule_keys", "{}"),
            AiToolCall(
                "c2", "propose_rule_content",
                """{"content":"# rule","suggestedFileName":"r.easy.api.config"}"""
            )
        )

        val outcome = agent.runTurn("perceive then propose", memory)
        events.cancelAndCollect()

        assertEquals(TurnOutcome.Proposed, outcome)
        // Only one Thinking event — the second tool was in the same step.
        val phases = events.collected.map { it::class.simpleName }
        assertEquals(1, phases.count { it == "Thinking" })
        // Both tools ran in this step (Perceiving then Acting).
        assertTrue(phases.indexOf("Perceiving") < phases.indexOf("Acting"))
        // ProposalReady emitted.
        assertTrue(phases.contains("ProposalReady"))
    }

    /**
     * Script 5 (approval): an ACTION tool with `requiresApproval=true` is
     * NOT executed when the fake gate rejects → `ApprovalRequested` emitted,
     * dispatch returns Error, agent continues with a plain answer.
     */
    fun testApprovalRejectedToolNotExecuted() = runBlocking {
        val tools = ToolRegistry(listOf(WriteRuleFileFakeTool(), ListRuleKeysFakeTool()))
        approvalGate.shouldApprove = false
        val events = captureEvents()
        val agent = RuleAuthoringAgent(aiService, tools, ctx, events.flow)

        // Step 1: LLM asks to write_rule_file (gated ACTION). Gate rejects.
        aiService.enqueueToolCalls(
            AiToolCall(
                "c1", "write_rule_file",
                """{"path":"/tmp/x.easy.api.config","content":"# x"}"""
            )
        )
        // Step 2: LLM gives up and answers with plain text.
        aiService.enqueueText("OK I won't write that file.")

        val outcome = agent.runTurn("write a file", memory)
        events.cancelAndCollect()

        assertEquals(TurnOutcome.Answered, outcome)
        // The fake tool recorded no execution.
        val writeTool = tools.let { reg ->
            // recover the fake to assert it wasn't called
            (reg.javaClass.getDeclaredField("tools").apply { isAccessible = true }
                .get(reg) as List<AiTool>)
                .filterIsInstance<WriteRuleFileFakeTool>().single()
        }
        assertFalse(
            "write_rule_file must not execute when approval is rejected",
            writeTool.executed
        )

        // Event sequence: ApprovalRequested present, Observed carries an Error.
        val phases = events.collected.map { it::class.simpleName }
        assertTrue(
            "ApprovalRequested must be emitted for gated ACTION tools",
            phases.contains("ApprovalRequested")
        )
        // Approval gate was actually consulted.
        assertTrue(approvalGate.wasConsulted)
        // The observed result should be an error.
        val observed = events.collected.filterIsInstance<AgentEvent.Observed>().single()
        assertTrue(observed.resultSummary.startsWith("Error"))
    }

    /**
     * Multi-turn: two sequential `runTurn` calls share one [AgentMemory];
     * the second turn sees the first turn's transcript (real dialogue).
     */
    fun testMultiTurnSharesTranscript() = runBlocking {
        val tools = ToolRegistry(listOf(ListRuleKeysFakeTool()))
        val events = captureEvents()
        val agent = RuleAuthoringAgent(aiService, tools, ctx, events.flow)

        // Turn 1: agent answers plain text.
        aiService.enqueueText("first answer")
        val o1 = agent.runTurn("hello", memory)
        assertEquals(TurnOutcome.Answered, o1)

        // Turn 2: agent answers plain text again. The fake records every
        // request it received, so we can inspect what the agent sent.
        aiService.enqueueText("second answer")
        val o2 = agent.runTurn("again", memory)
        assertEquals(TurnOutcome.Answered, o2)
        events.cancelAndCollect()

        // Two requests were sent — turn 2's request includes turn 1's messages.
        val requests = aiService.requests()
        assertEquals(2, requests.size)
        // The second request carries more messages than the first.
        assertTrue(
            "turn 2 transcript must include turn 1 history",
            requests[1].messages.size > requests[0].messages.size
        )
        // The second request contains the first turn's user "hello".
        assertTrue(requests[1].messages.any {
            it is com.itangcent.easyapi.ai.AiMessage.User && it.content == "hello"
        })
        // And the second turn's user "again".
        assertTrue(requests[1].messages.any {
            it is com.itangcent.easyapi.ai.AiMessage.User && it.content == "again"
        })
    }

    /**
     * The LLM calls `ask_clarification` (non-terminal PERCEPTION), the gate
     * answers, then the LLM calls `propose_rule_content`. Proves the
     * clarification is non-terminal and the answers flow back into the loop.
     */
    fun testAskClarificationThenPropose() = runBlocking {
        val clarGate = FakeClarificationGate(
            ClarificationAnswers(mapOf("scope" to listOf("controllers")))
        )
        val clarCtx = ctx.copy(clarifications = clarGate)
        val tools = ToolRegistry(listOf(AskClarificationTool(), ProposeRuleContentFakeTool()))
        val events = captureEvents()
        val agent = RuleAuthoringAgent(aiService, tools, clarCtx, events.flow)

        // Step 1: ask_clarification (PERCEPTION, non-terminal).
        aiService.enqueueToolCalls(
            AiToolCall(
                "c1", "ask_clarification",
                """{"prompt":"Could you clarify:","questions":[{"id":"scope","text":"Scope?","kind":"single_choice","options":[{"value":"global","label":"Globally"},{"value":"controllers","label":"Specific controllers"}]}]}"""
            )
        )
        // Step 2: propose_rule_content (terminal).
        aiService.enqueueToolCalls(
            AiToolCall(
                "c2", "propose_rule_content",
                """{"content":"# rule","suggestedFileName":"r.easy.api.config"}"""
            )
        )

        val outcome = agent.runTurn("add a header to every POST", memory)
        events.cancelAndCollect()

        assertEquals(TurnOutcome.Proposed, outcome)
        assertTrue("clarification gate must be consulted", clarGate.wasConsulted)

        val phases = events.collected.map { it::class.simpleName }
        // ask_clarification is PERCEPTION → Perceiving; non-terminal so the
        // loop runs a second step (two Thinking events) ending in a proposal.
        assertTrue("should include Perceiving", phases.contains("Perceiving"))
        assertEquals(2, phases.count { it == "Thinking" })
        assertTrue("should include ProposalReady", phases.contains("ProposalReady"))

        // The answers flowed back: turn-2 request carries a ToolResult with them.
        val req2 = aiService.requests()[1]
        assertTrue(
            "the clarification answers must be fed back to the model",
            req2.messages.any { it is AiMessage.ToolResult && it.content.contains("controllers") }
        )
    }

    // --- Markdown language-template proposals  ---

    /**
     * Script 6 : ambient `userLanguage=zh-CN` → the ambient
     * System message sent to the LLM includes the `user language: zh-CN`
     * hint, and a scripted `propose_rule_content` call staging
     * `markdown.template.language=zh-CN` is staged in `memory.proposal`.
     *
     * This is the end-to-end integration test for the locale-detection →
     * ambient-hint → agent-proposal chain.
     */
    fun testProposesLanguageRuleWhenAmbientNonEnglish() = runBlocking {
        val tools = ToolRegistry(listOf(ListRuleKeysFakeTool(), ProposeRuleContentFakeTool()))
        val events = captureEvents()
        val agent = RuleAuthoringAgent(aiService, tools, ctx, events.flow)

        // Step 1: LLM perceives (list_rule_keys).
        aiService.enqueueToolCalls(
            AiToolCall("c1", "list_rule_keys", "{}")
        )
        // Step 2: LLM proposes a language rule (terminal).
        aiService.enqueueToolCalls(
            AiToolCall(
                "c2", "propose_rule_content",
                """{"content":"markdown.template.language=zh-CN","suggestedFileName":"markdown-language.rules"}"""
            ),
            content = null
        )

        val ambient = Ambient(
            projectName = project.name,
            editingRuleFile = null,
            existingRuleFiles = emptyList(),
            userLanguage = "zh-CN"
        )
        val outcome = agent.runTurn(
            "help me export Markdown docs in Chinese", memory, ambient
        )
        events.cancelAndCollect()

        assertEquals(TurnOutcome.Proposed, outcome)
        assertNotNull(memory.proposal)
        assertEquals("markdown-language.rules", memory.proposal?.suggestedFileName)
        assertTrue(
            "proposal content should include markdown.template.language=zh-CN: ${memory.proposal?.content}",
            memory.proposal?.content?.contains("markdown.template.language=zh-CN") == true
        )

        // The ambient hint reached the LLM: the ambient System message
        // (the one starting with "Context: project") contains "user language: zh-CN".
        // We filter to the ambient message specifically — the preamble also mentions
        // "user language" in its instruction text, so checking all System messages
        // would give a false positive.
        val firstRequest = aiService.requests().first()
        val ambientMsg = firstRequest.messages
            .filterIsInstance<AiMessage.System>()
            .firstOrNull { it.content.startsWith("Context: project") }
        assertNotNull("ambient message should be present in the first request", ambientMsg)
        assertTrue(
            "ambient message should include 'user language: zh-CN' hint: ${ambientMsg?.content}",
            ambientMsg!!.content.contains("user language: zh-CN", ignoreCase = true)
        )
    }

    /**
     * Script 7 : ambient `userLanguage=null` (English) → the
     * ambient System message does NOT include a `user language` hint, so
     * the LLM has no signal to propose a language rule. A scripted non-
     * language proposal is staged correctly (the flow still works —
     * English just means no hint).
     */
    fun testDoesNotSurfaceLanguageHintWhenAmbientEnglish() = runBlocking {
        val tools = ToolRegistry(listOf(ListRuleKeysFakeTool(), ProposeRuleContentFakeTool()))
        val events = captureEvents()
        val agent = RuleAuthoringAgent(aiService, tools, ctx, events.flow)

        // Step 1: LLM perceives.
        aiService.enqueueToolCalls(
            AiToolCall("c1", "list_rule_keys", "{}")
        )
        // Step 2: LLM proposes a non-language rule (e.g. a header rule).
        aiService.enqueueToolCalls(
            AiToolCall(
                "c2", "propose_rule_content",
                """{"content":"method.additional.header={\"name\":\"X-Foo\",\"value\":\"bar\"}","suggestedFileName":"headers.rules"}"""
            ),
            content = null
        )

        val ambient = Ambient(
            projectName = project.name,
            editingRuleFile = null,
            existingRuleFiles = emptyList(),
            userLanguage = null  // English / undetermined
        )
        val outcome = agent.runTurn("add a header to every POST", memory, ambient)
        events.cancelAndCollect()

        assertEquals(TurnOutcome.Proposed, outcome)
        assertNotNull(memory.proposal)
        // The proposal does NOT contain a language rule.
        assertFalse(
            "English ambient should not prompt a language-rule proposal: ${memory.proposal?.content}",
            memory.proposal?.content?.contains("markdown.template.language=") == true
        )

        // The ambient message sent to the LLM does NOT include the hint.
        // Filter to the ambient message specifically (starts with "Context: project")
        // — the preamble also mentions "user language" in its instruction text.
        val firstRequest = aiService.requests().first()
        val ambientMsg = firstRequest.messages
            .filterIsInstance<AiMessage.System>()
            .firstOrNull { it.content.startsWith("Context: project") }
        assertNotNull("ambient message should be present in the first request", ambientMsg)
        assertFalse(
            "ambient message should NOT include 'user language' hint when userLanguage is null: ${ambientMsg?.content}",
            ambientMsg!!.content.contains("user language", ignoreCase = true)
        )
    }

    /**
     * Script 8 : the language-rule proposal flows through
     * `propose_rule_content` (the user-approval gate), NOT through a silent
     * `write_rule_file`. Even when the LLM proposes a rule, the file is not
     * written to disk — the proposal is staged for the user to review.
     */
    fun testLanguageRuleProposalStagedNotWritten() = runBlocking {
        val writeTool = WriteRuleFileFakeTool()
        val tools = ToolRegistry(
            listOf(ListRuleKeysFakeTool(), ProposeRuleContentFakeTool(), writeTool)
        )
        val events = captureEvents()
        val agent = RuleAuthoringAgent(aiService, tools, ctx, events.flow)

        // Step 1: LLM perceives.
        aiService.enqueueToolCalls(
            AiToolCall("c1", "list_rule_keys", "{}")
        )
        // Step 2: LLM proposes a language rule via the staging action.
        aiService.enqueueToolCalls(
            AiToolCall(
                "c2", "propose_rule_content",
                """{"content":"markdown.template.language=zh-CN","suggestedFileName":"markdown-language.rules"}"""
            ),
            content = null
        )

        val ambient = Ambient(
            projectName = project.name,
            editingRuleFile = null,
            existingRuleFiles = emptyList(),
            userLanguage = "zh-CN"
        )
        val outcome = agent.runTurn(
            "help me export Markdown docs in Chinese", memory, ambient
        )
        events.cancelAndCollect()

        assertEquals(TurnOutcome.Proposed, outcome)
        // The write tool was never called — the proposal is staged, not written.
        assertFalse(
            "write_rule_file must not execute — proposal flows through propose_rule_content",
            writeTool.executed
        )
        // The proposal is staged for user review.
        assertNotNull(memory.proposal)
        assertTrue(
            "proposal should contain the language rule",
            memory.proposal?.content?.contains("markdown.template.language=zh-CN") == true
        )
    }

    // --- Loop-detection integration tests (Phase 5, Task 25) ---

    /**
     * 3 identical `list_rule_keys` calls → `TurnOutcome.LoopDetected` via
     * consecutive-duplicate detection. The 2nd and 3rd calls are debounced
     * (default `debounceEnabled=true`); the streak counter advances via
     * `observeResult` on the blocked results and terminates at 3.
     */
    fun testLoopDetectedConsecutive() = runBlocking {
        val tools = ToolRegistry(listOf(ListRuleKeysFakeTool()))
        val events = captureEvents()
        val agent = RuleAuthoringAgent(aiService, tools, ctx, events.flow)

        repeat(3) {
            aiService.enqueueToolCalls(AiToolCall("c$it", "list_rule_keys", "{}"))
        }

        val outcome = agent.runTurn("loop", memory)
        events.cancelAndCollect()

        assertEquals(TurnOutcome.LoopDetected, outcome)
        val loopEvent = events.collected.filterIsInstance<AgentEvent.LoopDetected>().single()
        assertTrue("reason should contain 'consecutive': ${loopEvent.reason}",
            loopEvent.reason.contains("consecutive"))
        assertEquals("list_rule_keys", loopEvent.tool)
        assertEquals(3, loopEvent.count)
    }

    /**
     * Alternating `list_rule_keys` → `read_rule_file` (period 2, 2
     * repetitions) → `TurnOutcome.LoopDetected` via call-cycle detection.
     */
    fun testLoopDetectedCycle() = runBlocking {
        // Need 4 steps for a period-2 cycle with 2 repetitions.
        ctx = ctx.copy(aiSettings = ctx.aiSettings.copy(maxRequests = 5))
        val tools = ToolRegistry(listOf(
            ListRuleKeysFakeTool(),
            NamedFakeTool("read_rule_file")
        ))
        val events = captureEvents()
        val agent = RuleAuthoringAgent(aiService, tools, ctx, events.flow)

        aiService.enqueueToolCalls(AiToolCall("c1", "list_rule_keys", "{}"))
        aiService.enqueueToolCalls(AiToolCall("c2", "read_rule_file", "{}"))
        aiService.enqueueToolCalls(AiToolCall("c3", "list_rule_keys", "{}"))
        aiService.enqueueToolCalls(AiToolCall("c4", "read_rule_file", "{}"))

        val outcome = agent.runTurn("loop cycle", memory)
        events.cancelAndCollect()

        assertEquals(TurnOutcome.LoopDetected, outcome)
        val loopEvent = events.collected.filterIsInstance<AgentEvent.LoopDetected>().single()
        assertTrue("reason should contain 'cycle': ${loopEvent.reason}",
            loopEvent.reason.contains("cycle"))
    }

    /**
     * Same tool, different args, but identical `ToolResult.Text` content 3
     * times → `TurnOutcome.LoopDetected` via output-stagnation detection.
     */
    fun testLoopDetectedStagnation() = runBlocking {
        val tools = ToolRegistry(listOf(ListRuleKeysFakeTool()))
        val events = captureEvents()
        val agent = RuleAuthoringAgent(aiService, tools, ctx, events.flow)

        aiService.enqueueToolCalls(AiToolCall("c1", "list_rule_keys", """{"a":1}"""))
        aiService.enqueueToolCalls(AiToolCall("c2", "list_rule_keys", """{"b":2}"""))
        aiService.enqueueToolCalls(AiToolCall("c3", "list_rule_keys", """{"c":3}"""))

        val outcome = agent.runTurn("loop stagnation", memory)
        events.cancelAndCollect()

        assertEquals(TurnOutcome.LoopDetected, outcome)
        val loopEvent = events.collected.filterIsInstance<AgentEvent.LoopDetected>().single()
        assertTrue("reason should contain 'stagnation': ${loopEvent.reason}",
            loopEvent.reason.contains("stagnation"))
        assertEquals("list_rule_keys", loopEvent.tool)
        assertEquals(3, loopEvent.count)
    }

    /**
     * Identical non-blank assistant text WITH tool calls (so the turn
     * doesn't end via COMMUNICATE) 3 times → `TurnOutcome.LoopDetected` via
     * reasoning-repetition detection. Different args so consecutive/stagnation
     * don't fire first.
     */
    fun testLoopDetectedReasoning() = runBlocking {
        val tools = ToolRegistry(listOf(ListRuleKeysFakeTool()))
        val events = captureEvents()
        val agent = RuleAuthoringAgent(aiService, tools, ctx, events.flow)

        repeat(3) { i ->
            aiService.enqueueToolCalls(
                AiToolCall("c$i", "list_rule_keys", """{"arg":$i}"""),
                content = "Let me check the rules."
            )
        }

        val outcome = agent.runTurn("loop reasoning", memory)
        events.cancelAndCollect()

        assertEquals(TurnOutcome.LoopDetected, outcome)
        val loopEvent = events.collected.filterIsInstance<AgentEvent.LoopDetected>().single()
        assertTrue("reason should contain 'reasoning': ${loopEvent.reason}",
            loopEvent.reason.contains("reasoning"))
        assertNull("tool should be null for reasoning repetition", loopEvent.tool)
        assertEquals(3, loopEvent.count)
    }

    /**
     * 2 identical calls with debounce ON → 2nd call is debounced (Observed
     * carries an Error mentioning "Duplicate"). With `repetitionThreshold=3`,
     * the 3rd identical call terminates.
     */
    fun testDebounceBlocksDuplicate() = runBlocking {
        val tools = ToolRegistry(listOf(ListRuleKeysFakeTool()))
        val events = captureEvents()
        val agent = RuleAuthoringAgent(aiService, tools, ctx, events.flow)

        repeat(3) {
            aiService.enqueueToolCalls(AiToolCall("c$it", "list_rule_keys", "{}"))
        }

        val outcome = agent.runTurn("loop debounce", memory)
        events.cancelAndCollect()

        assertEquals(TurnOutcome.LoopDetected, outcome)

        val observed = events.collected.filterIsInstance<AgentEvent.Observed>()
        assertEquals("should have 3 Observed events", 3, observed.size)
        // 1st Observed is the actual result.
        assertFalse(
            "1st Observed should not mention 'Duplicate': ${observed[0].resultSummary}",
            observed[0].resultSummary.contains("Duplicate")
        )
        // 2nd and 3rd are debounced.
        assertTrue(
            "2nd Observed should mention 'Duplicate': ${observed[1].resultSummary}",
            observed[1].resultSummary.contains("Duplicate")
        )
        assertTrue(
            "3rd Observed should mention 'Duplicate': ${observed[2].resultSummary}",
            observed[2].resultSummary.contains("Duplicate")
        )
    }

    /**
     * On `LoopDetected`, the event sequence must NOT contain `TurnComplete`
     * or `ProposalReady` (abnormal exit — REQ-2 AC-2).
     */
    fun testLoopDetectedEmitsNoTurnComplete() = runBlocking {
        val tools = ToolRegistry(listOf(ListRuleKeysFakeTool()))
        val events = captureEvents()
        val agent = RuleAuthoringAgent(aiService, tools, ctx, events.flow)

        repeat(3) {
            aiService.enqueueToolCalls(AiToolCall("c$it", "list_rule_keys", "{}"))
        }

        val outcome = agent.runTurn("loop", memory)
        events.cancelAndCollect()

        assertEquals(TurnOutcome.LoopDetected, outcome)
        val phases = events.collected.map { it::class.simpleName }
        assertFalse("LoopDetected should NOT emit TurnComplete",
            phases.contains("TurnComplete"))
        assertFalse("LoopDetected should NOT emit ProposalReady",
            phases.contains("ProposalReady"))
    }

    /**
     * Two sequential `runTurn` calls: turn 1 loops (LoopDetected), turn 2
     * starts with a fresh `LoopGuard` (counter reset) and can call the same
     * tool once without immediate termination (REQ-1 AC-4).
     */
    fun testPerTurnFreshness() = runBlocking {
        val tools = ToolRegistry(listOf(ListRuleKeysFakeTool()))
        val events = captureEvents()
        val agent = RuleAuthoringAgent(aiService, tools, ctx, events.flow)

        // Turn 1: 3 identical calls → LoopDetected.
        repeat(3) {
            aiService.enqueueToolCalls(AiToolCall("c$it", "list_rule_keys", "{}"))
        }
        val o1 = agent.runTurn("loop", memory)
        assertEquals(TurnOutcome.LoopDetected, o1)

        // Turn 2: fresh guard — 1 call then answer → Answered.
        aiService.enqueueToolCalls(AiToolCall("d1", "list_rule_keys", "{}"))
        aiService.enqueueText("done")
        val o2 = agent.runTurn("again", memory)
        assertEquals(TurnOutcome.Answered, o2)

        events.cancelAndCollect()
    }

    // --- Retry integration tests (Phase 5, Task 26) ---

    /**
     * Transient `IOException` on the first attempt, then a plain-text answer.
     * Asserts `TurnOutcome.Answered`, `Retrying(1, 2)` emitted, no `Failed`.
     */
    fun testRetryOnTransient() = runBlocking {
        val tools = ToolRegistry(listOf(ListRuleKeysFakeTool()))
        val rctx = retryCtx()
        val events = captureEvents()
        val agent = RuleAuthoringAgent(aiService, tools, rctx, events.flow)

        aiService.enqueueThrow(IOException("connection reset"))
        aiService.enqueueText("recovered answer")

        val outcome = agent.runTurn("test", memory)
        events.cancelAndCollect()

        assertEquals(TurnOutcome.Answered, outcome)
        val phases = events.collected.map { it::class.simpleName }
        assertTrue("should emit Retrying", phases.contains("Retrying"))
        assertFalse("should NOT emit Failed", phases.contains("Failed"))
        assertTrue("should emit Message (recovery)", phases.contains("Message"))
        val retrying = events.collected.filterIsInstance<AgentEvent.Retrying>().single()
        assertEquals(1, retrying.attempt)
        assertEquals(2, retrying.maxRetries)
    }

    /**
     * Non-transient `IllegalArgumentException("401 unauthorized")` → fail
     * fast. Asserts `TurnOutcome.Answered`, `Failed` with "attempt(s)", NO
     * `Retrying` (REQ-4 AC-3, NFR-2).
     */
    fun testNoRetryOnAuth() = runBlocking {
        val tools = ToolRegistry(listOf(ListRuleKeysFakeTool()))
        val rctx = retryCtx()
        val events = captureEvents()
        val agent = RuleAuthoringAgent(aiService, tools, rctx, events.flow)

        aiService.enqueueThrow(IllegalArgumentException("401 unauthorized"))

        val outcome = agent.runTurn("test", memory)
        events.cancelAndCollect()

        assertEquals(TurnOutcome.Answered, outcome)
        val phases = events.collected.map { it::class.simpleName }
        assertFalse("should NOT emit Retrying", phases.contains("Retrying"))
        val failed = events.collected.filterIsInstance<AgentEvent.Failed>().single()
        assertTrue("Failed reason should contain 'attempt(s)': ${failed.reason}",
            failed.reason.contains("attempt(s)"))
    }

    /**
     * `ChatTimeoutException` on the first attempt, then a plain-text answer.
     * Asserts the timeout is classified as transient and retried (end-to-end
     * test for the Phase 4 fix — REQ-4 Decision 4, NFR-2).
     */
    fun testTimeoutRetriedAsTransient() = runBlocking {
        val tools = ToolRegistry(listOf(ListRuleKeysFakeTool()))
        val rctx = retryCtx()
        val events = captureEvents()
        val agent = RuleAuthoringAgent(aiService, tools, rctx, events.flow)

        aiService.enqueueThrow(ChatTimeoutException(5000L, RuntimeException("inner")))
        aiService.enqueueText("recovered after timeout")

        val outcome = agent.runTurn("test", memory)
        events.cancelAndCollect()

        assertEquals(TurnOutcome.Answered, outcome)
        val phases = events.collected.map { it::class.simpleName }
        assertTrue("should emit Retrying (timeout is transient)", phases.contains("Retrying"))
        assertFalse("should NOT emit Failed", phases.contains("Failed"))
    }

    /**
     * `1 + chatMaxRetries` transient failures → retries exhausted. Asserts
     * `TurnOutcome.Answered`, `Failed` with attempt count, `Retrying` per
     * attempt (REQ-4 AC-4, AC-8).
     */
    fun testRetriesExhaustedTerminal() = runBlocking {
        val tools = ToolRegistry(listOf(ListRuleKeysFakeTool()))
        val rctx = retryCtx(maxRetries = 2)
        val events = captureEvents()
        val agent = RuleAuthoringAgent(aiService, tools, rctx, events.flow)

        // 1 + 2 = 3 transient failures → exhausted.
        repeat(3) { aiService.enqueueThrow(IOException("timeout $it")) }

        val outcome = agent.runTurn("test", memory)
        events.cancelAndCollect()

        assertEquals(TurnOutcome.Answered, outcome)
        val failed = events.collected.filterIsInstance<AgentEvent.Failed>().single()
        assertTrue("Failed reason should contain 'after 3 attempt(s)': ${failed.reason}",
            failed.reason.contains("after 3 attempt(s)"))
        val retrying = events.collected.filterIsInstance<AgentEvent.Retrying>()
        assertEquals("should emit Retrying 3 times", 3, retrying.size)
    }

    /**
     * Assert `Retrying(attempt, maxRetries)` is emitted on each transient
     * failure that is retried, and NOT on success or non-transient failures
     * (REQ-4 AC-8).
     */
    fun testRetryingEventEmitted() = runBlocking {
        val tools = ToolRegistry(listOf(ListRuleKeysFakeTool()))
        val rctx = retryCtx(maxRetries = 2)
        val events = captureEvents()
        val agent = RuleAuthoringAgent(aiService, tools, rctx, events.flow)

        // 2 transient failures then success.
        aiService.enqueueThrow(IOException("fail 1"))
        aiService.enqueueThrow(IOException("fail 2"))
        aiService.enqueueText("recovered")

        val outcome = agent.runTurn("test", memory)
        events.cancelAndCollect()

        assertEquals(TurnOutcome.Answered, outcome)
        val retrying = events.collected.filterIsInstance<AgentEvent.Retrying>()
        assertEquals("should emit Retrying 2 times", 2, retrying.size)
        assertEquals(1, retrying[0].attempt)
        assertEquals(2, retrying[1].attempt)
        assertEquals(2, retrying[0].maxRetries)
        val phases = events.collected.map { it::class.simpleName }
        assertFalse("should NOT emit Failed", phases.contains("Failed"))
    }

    /**
     * Assert `Failed.reason` contains "after N attempt(s)" on terminal
     * exhaustion (REQ-4 AC-4).
     */
    fun testTerminalFailedHasAttemptCount() = runBlocking {
        val tools = ToolRegistry(listOf(ListRuleKeysFakeTool()))
        val rctx = retryCtx(maxRetries = 1)
        val events = captureEvents()
        val agent = RuleAuthoringAgent(aiService, tools, rctx, events.flow)

        // 1 + 1 = 2 transient failures → exhausted.
        repeat(2) { aiService.enqueueThrow(IOException("fail $it")) }

        val outcome = agent.runTurn("test", memory)
        events.cancelAndCollect()

        assertEquals(TurnOutcome.Answered, outcome)
        val failed = events.collected.filterIsInstance<AgentEvent.Failed>().single()
        assertTrue("Failed reason should contain 'after 2 attempt(s)': ${failed.reason}",
            failed.reason.contains("after 2 attempt(s)"))
    }

    /**
     * Cancel the turn job mid-retry backoff → `CancellationException` must
     * propagate (not swallowed by the retry policy). Asserts the job is
     * cancelled and no `Failed` event was emitted (REQ-4 AC-5).
     *
     * NOTE: tested with `runBlocking` + a long backoff. The agent job is
     * launched UNDISPATCHED so it runs synchronously until it suspends in
     * `delay`; the test then cancels during the backoff window.
     */
    fun testCancellationNotSwallowed() = runBlocking {
        // Long backoff so we can reliably cancel mid-backoff.
        val cancelCtx = ctx.copy(
            aiSettings = ctx.aiSettings.copy(
                loopSafety = LoopSafetyConfig(
                    chatMaxRetries = 2,
                    chatBackoffBaseMs = 10_000,
                    chatBackoffMaxMs = 10_000
                )
            )
        )
        val tools = ToolRegistry(listOf(ListRuleKeysFakeTool()))
        val events = captureEvents()
        val agent = RuleAuthoringAgent(aiService, tools, cancelCtx, events.flow)

        aiService.enqueueThrow(IOException("timeout"))
        aiService.enqueueText("recovered")

        val job = scope().launch(
            context = kotlinx.coroutines.Dispatchers.Unconfined,
            start = CoroutineStart.UNDISPATCHED
        ) {
            agent.runTurn("test", memory)
        }
        // The agent has run synchronously until the backoff `delay` —
        // give a short breather then cancel.
        delay(100)
        job.cancelAndJoin()

        assertTrue("job should be cancelled (CancellationException propagated)",
            job.isCancelled)
        val phases = events.collected.map { it::class.simpleName }
        assertFalse("cancellation should not emit Failed", phases.contains("Failed"))
        events.cancelAndCollect()
    }

    // --- Helpers ---

    /**
     * Captures [AgentEvent]s from a fresh SharedFlow into a list. Returns
     * the flow + a handle that cancels the collector and exposes collected
     * events. Uses a large replay buffer so emits are kept even if the
     * collector hasn't started yet (defends against dispatcher races).
     */
    private fun captureEvents(): EventCapture {
        val flow = MutableSharedFlow<AgentEvent>(
            replay = Int.MAX_VALUE,
            extraBufferCapacity = 0
        )
        val collected = mutableListOf<AgentEvent>()
        // UNDISPATCHED + Unconfined: emit runs the collector inline on the
        // emitter's thread, so the captured list is synchronously up-to-date
        // when runTurn returns. Without Unconfined the collector runs on a
        // separate dispatcher and the test becomes timing-dependent.
        val job = scope().launch(
            context = kotlinx.coroutines.Dispatchers.Unconfined,
            start = CoroutineStart.UNDISPATCHED
        ) {
            flow.collect { collected.add(it) }
        }
        return EventCapture(flow, job, collected)
    }

    private fun scope() =
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined)

    /**
     * Build a [ToolContext] tuned for retry tests: short backoff (1–10 ms)
     * so retried turns stay snappy, with a configurable [chatMaxRetries].
     * All other loop-safety knobs keep their defaults.
     */
    private fun retryCtx(maxRetries: Int = 2): ToolContext {
        return ctx.copy(
            aiSettings = ctx.aiSettings.copy(
                loopSafety = LoopSafetyConfig(
                    chatMaxRetries = maxRetries,
                    chatBackoffBaseMs = 1,
                    chatBackoffMaxMs = 10
                )
            )
        )
    }

    private class EventCapture(
        val flow: MutableSharedFlow<AgentEvent>,
        private val job: Job,
        val collected: List<AgentEvent>
    ) {
        suspend fun cancelAndCollect() {
            job.cancelAndJoin()
        }
    }

    // --- Fake tools ---

    /** A perception tool named `list_rule_keys` returning a stub catalog. */
    private class ListRuleKeysFakeTool : AiTool {
        override val name = "list_rule_keys"
        override val description = "List available rule keys."
        override val kind = ToolKind.PERCEPTION
        override val parametersSchema: Map<String, Any?> = emptyMap()
        override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult =
            ToolResult.Text("[]")
    }

    /** The terminal staging action `propose_rule_content`. */
    private class ProposeRuleContentFakeTool : AiTool {
        override val name = "propose_rule_content"
        override val description = "Stage a rule proposal for the user to save."
        override val kind = ToolKind.ACTION
        override val requiresApproval = false
        override val parametersSchema: Map<String, Any?> = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "content" to mapOf("type" to "string"),
                "suggestedFileName" to mapOf("type" to "string")
            ),
            "required" to listOf("content", "suggestedFileName")
        )

        override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
            val content = args["content"] as? String
            val suggestedFileName = args["suggestedFileName"] as? String ?: "rules.easy.api.config"
            if (content.isNullOrBlank()) return ToolResult.Error("content is required")
            ctx.workingMemory.proposal = Proposal(content, suggestedFileName)
            return ToolResult.Text("proposal stored")
        }
    }

    /** A reserved `write_rule_file` action with `requiresApproval = true`. */
    private class WriteRuleFileFakeTool : AiTool {
        var executed = false
        override val name = "write_rule_file"
        override val description = "Write a rule file to disk (gated)."
        override val kind = ToolKind.ACTION
        override val requiresApproval = true
        override val parametersSchema: Map<String, Any?> = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "path" to mapOf("type" to "string"),
                "content" to mapOf("type" to "string")
            ),
            "required" to listOf("path", "content")
        )

        override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
            executed = true
            return ToolResult.Text("wrote")
        }
    }

    /**
     * A perception tool with a configurable name, returning a stub `ok`
     * result. Used by loop-detection tests that need a second tool (e.g.
     * `read_rule_file` for call-cycle detection).
     */
    private class NamedFakeTool(override val name: String) : AiTool {
        override val description = "A fake tool for testing."
        override val kind = ToolKind.PERCEPTION
        override val parametersSchema: Map<String, Any?> = emptyMap()
        override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult =
            ToolResult.Text("ok")
    }
}
