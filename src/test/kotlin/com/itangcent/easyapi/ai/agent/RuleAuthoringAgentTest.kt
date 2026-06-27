package com.itangcent.easyapi.ai.agent

import com.itangcent.easyapi.ai.AiSettings
import com.itangcent.easyapi.ai.AiMessage
import com.itangcent.easyapi.ai.AiToolCall
import com.itangcent.easyapi.ai.AiProvider
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancelAndJoin

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
            aiSettings = AiSettings(
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
            AiToolCall("c2", "propose_rule_content",
                """{"content":"# rule","suggestedFileName":"rules.easy.api.config"}"""),
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
     */
    fun testStepLimitHit() = runBlocking {
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
            AiToolCall("c2", "propose_rule_content",
                """{"content":"# rule","suggestedFileName":"r.easy.api.config"}""")
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
            AiToolCall("c1", "write_rule_file",
                """{"path":"/tmp/x.easy.api.config","content":"# x"}""")
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
        assertFalse("write_rule_file must not execute when approval is rejected",
            writeTool.executed)

        // Event sequence: ApprovalRequested present, Observed carries an Error.
        val phases = events.collected.map { it::class.simpleName }
        assertTrue("ApprovalRequested must be emitted for gated ACTION tools",
            phases.contains("ApprovalRequested"))
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
        assertTrue("turn 2 transcript must include turn 1 history",
            requests[1].messages.size > requests[0].messages.size)
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

    private fun scope() = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined)

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
}
