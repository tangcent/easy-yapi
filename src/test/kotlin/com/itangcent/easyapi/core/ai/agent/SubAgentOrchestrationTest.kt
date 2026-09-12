package com.itangcent.easyapi.core.ai.agent

import com.itangcent.easyapi.core.ai.AiMessage
import com.itangcent.easyapi.core.ai.AiProvider
import com.itangcent.easyapi.core.ai.AiRuntimeConfig
import com.itangcent.easyapi.core.ai.AiToolCall
import com.itangcent.easyapi.core.ai.AIService
import com.itangcent.easyapi.core.ai.agent.RuleAuthoringAgent.Companion.PROPOSE_RULE_CONTENT
import com.itangcent.easyapi.core.ai.agent.RuleAuthoringAgent.Companion.REPORT_FINDINGS
import com.itangcent.easyapi.core.ai.tools.AiTool
import com.itangcent.easyapi.core.ai.tools.OrchestratorProposeRuleContentTool
import com.itangcent.easyapi.core.ai.tools.ToolContext
import com.itangcent.easyapi.core.ai.tools.ToolKind
import com.itangcent.easyapi.core.ai.tools.ToolRegistry
import com.itangcent.easyapi.core.ai.tools.ToolResult
import com.itangcent.easyapi.core.ai.tools.UpdateTaskTool
import com.itangcent.easyapi.core.ai.tools.subAgentToolRegistry
import com.itangcent.easyapi.core.config.ConfigReader
import com.itangcent.easyapi.core.config.source.RuleFileResolver
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert

/**
 * Phase-3 sub-agent orchestration tests (design §3.6 / FR-3.* / T3.8–T3.11).
 *
 * These tests exercise the orchestrator + sub-agent execution model
 * introduced in Phase 3:
 *
 * - **T3.8** — the orchestrator merges results from three sub-agents into
 *   a single `propose_rule_content` call, with each detection tagged
 *   `source: detection:<id>`.
 * - **T3.9** — a failing sub-agent does not abort the run; the orchestrator
 *   marks the failed task FAILED, continues with the remaining tasks, and
 *   still calls `propose_rule_content` once at the end.
 * - **T3.10** — each sub-agent gets a fresh `AgentMemory`; tool results
 *   from a sibling sub-agent's perception tools never appear in another
 *   sub-agent's LLM transcript.
 * - **T3.11** — calling `report_findings` ends the sub-agent turn; no
 *   further tool calls are dispatched in that sub-agent's turn.
 *
 * The tests come in two flavours:
 *
 * 1. **Stubbed `run_sub_agent`** (T3.8, T3.9) — uses [FakeRunSubAgentTool]
 *    to bypass real sub-agent spawning. The merge and failure-isolation
 *    behaviours live in [OrchestratorProposeRuleContentTool] +
 *    [UpdateTaskTool] + the orchestrator LLM's tool-call sequence, so
 *    stubbing the spawn keeps the tests focused.
 * 2. **Real `RunSubAgentTool`** (T3.10, T3.11) — uses the production
 *    [com.itangcent.easyapi.core.ai.tools.RunSubAgentTool] with a scripted
 *    [FakeAIService] so each sub-agent's LLM transcript is captured and
 *    inspectable. Required because T3.10/T3.11 assert on the sub-agent's
 *    internal state (memory isolation, terminal-action behaviour).
 */
class SubAgentOrchestrationTest : EasyApiLightCodeInsightFixtureTestCase() {

    // ------------------------------------------------------------------
    // T3.8 — testSubAgentRunMergesThreeResults
    // ------------------------------------------------------------------

    /**
     * FR-3.8 / AC-3 — three seeded tasks; each sub-agent returns a
     * `TaskResult(detected=true, ...)`; the orchestrator calls
     * `propose_rule_content` exactly once, and the merged content contains
     * all three `source: detection:<id>` tags.
     *
     * Uses [FakeRunSubAgentTool] so the merge logic in
     * [OrchestratorProposeRuleContentTool] is exercised without spawning
     * real sub-agents. The orchestrator LLM is scripted (via
     * [FakeAIService]) to call `run_sub_agent` for each task in one step,
     * then `propose_rule_content` in the next.
     *
     * The merge is deterministic (concatenate-with-tags, design §3.9 / D3
     * — "no LLM round-trip"), so the test asserts the merged payload
     * directly on `memory.proposal.content`.
     */
    fun testSubAgentRunMergesThreeResults() = runBlocking {
        val events = captureEvents()
        val memory = AgentMemory().apply {
            taskList = TaskList(listOf(
                Task("t1", "first detection", "cue-1"),
                Task("t2", "second detection", "cue-2"),
                Task("t3", "third detection", "cue-3")
            ))
        }
        val ctx = buildCtx(memory, events.flow)
        val orchestratorTools = ToolRegistry(listOf(
            UpdateTaskTool(),
            FakeRunSubAgentTool(),
            OrchestratorProposeRuleContentTool()
        ))
        val aiService = FakeAIService()
        // Step 1: orchestrator runs all three sub-agents in one batch.
        aiService.enqueueToolCalls(
            AiToolCall("c1", "run_sub_agent", """{"taskId":"t1"}"""),
            AiToolCall("c2", "run_sub_agent", """{"taskId":"t2"}"""),
            AiToolCall("c3", "run_sub_agent", """{"taskId":"t3"}""")
        )
        // Step 2: orchestrator proposes the merged content (no `content`
        // arg — the tool merges from collectedSubAgentResults).
        aiService.enqueueToolCalls(
            AiToolCall(
                "c4", "propose_rule_content",
                """{"suggestedFileName":"merged.rules"}"""
            )
        )

        val agent = RuleAuthoringAgent(aiService, orchestratorTools, ctx, events.flow)
        val outcome = agent.runTurn(
            "walk the seeded task list",
            memory,
            entryPath = EntryPath.TASK_LIST_PROGRAMMATIC
        )
        events.cancelAndCollect()

        // Turn ended with a proposal.
        Assert.assertEquals(TurnOutcome.Proposed, outcome)
        Assert.assertNotNull("proposal should be staged", memory.proposal)
        Assert.assertEquals("merged.rules", memory.proposal?.suggestedFileName)

        // Exactly one propose_rule_content call was made. The assistant
        // message carrying it is in memory.messages (added before tool
        // execution); it is NOT in any aiService.requests() entry because
        // propose_rule_content is terminal — no subsequent LLM call carries
        // it as a prior-message context.
        val proposeCalls = memory.messages
            .filterIsInstance<AiMessage.Assistant>()
            .flatMap { it.toolCalls.orEmpty() }
            .filter { it.name == PROPOSE_RULE_CONTENT }
        Assert.assertEquals(
            "exactly one propose_rule_content call expected, got ${proposeCalls.size}",
            1, proposeCalls.size
        )

        // The merged content contains all three source tags.
        val content = memory.proposal?.content.orEmpty()
        Assert.assertTrue(
            "merged content should contain 'source: detection:t1':\n$content",
            content.contains("source: detection:t1")
        )
        Assert.assertTrue(
            "merged content should contain 'source: detection:t2':\n$content",
            content.contains("source: detection:t2")
        )
        Assert.assertTrue(
            "merged content should contain 'source: detection:t3':\n$content",
            content.contains("source: detection:t3")
        )
        // The findings text from each sub-agent should be present.
        Assert.assertTrue(content.contains("findings for t1"))
        Assert.assertTrue(content.contains("findings for t2"))
        Assert.assertTrue(content.contains("findings for t3"))
        // Three sub-agent results were collected.
        Assert.assertEquals(
            "collectedSubAgentResults should have 3 entries",
            3, memory.collectedSubAgentResults.size
        )
    }

    // ------------------------------------------------------------------
    // T3.9 — testFailingSubAgentMarksTaskFailedAndContinues
    // ------------------------------------------------------------------

    /**
     * FR-3.7 / AC-4 — when a sub-agent throws, the orchestrator marks the
     * task FAILED via `update_task`, continues with the remaining tasks,
     * and still calls `propose_rule_content` once at the end.
     *
     * The merge must NOT include the failed task (it has no
     * `TaskResult` staged), so the merged content contains `source:`
     * tags for the successful tasks only.
     */
    fun testFailingSubAgentMarksTaskFailedAndContinues() = runBlocking {
        val events = captureEvents()
        val memory = AgentMemory().apply {
            taskList = TaskList(listOf(
                Task("t1", "first detection", "cue-1"),
                Task("t2", "failing detection", "cue-2"),
                Task("t3", "third detection", "cue-3")
            ))
        }
        val ctx = buildCtx(memory, events.flow)
        val orchestratorTools = ToolRegistry(listOf(
            UpdateTaskTool(),
            // FakeRunSubAgentTool throws when running task t2.
            FakeRunSubAgentTool(failTaskIds = setOf("t2")),
            OrchestratorProposeRuleContentTool()
        ))
        val aiService = FakeAIService()
        // Step 1: orchestrator runs t1 (success).
        aiService.enqueueToolCalls(
            AiToolCall("c1", "run_sub_agent", """{"taskId":"t1"}"""),
            AiToolCall("c2", "update_task", """{"taskId":"t1","status":"completed"}""")
        )
        // Step 2: orchestrator runs t2 (fails) + marks it failed.
        aiService.enqueueToolCalls(
            AiToolCall("c3", "run_sub_agent", """{"taskId":"t2"}"""),
            AiToolCall(
                "c4", "update_task",
                """{"taskId":"t2","status":"failed","reason":"sub-agent threw"}"""
            )
        )
        // Step 3: orchestrator runs t3 (success).
        aiService.enqueueToolCalls(
            AiToolCall("c5", "run_sub_agent", """{"taskId":"t3"}"""),
            AiToolCall("c6", "update_task", """{"taskId":"t3","status":"completed"}""")
        )
        // Step 4: orchestrator proposes the merged content.
        aiService.enqueueToolCalls(
            AiToolCall(
                "c7", "propose_rule_content",
                """{"suggestedFileName":"merged.rules"}"""
            )
        )

        val agent = RuleAuthoringAgent(aiService, orchestratorTools, ctx, events.flow)
        val outcome = agent.runTurn(
            "walk the seeded task list (one will fail)",
            memory,
            entryPath = EntryPath.TASK_LIST_PROGRAMMATIC
        )
        events.cancelAndCollect()

        // Turn ended with a proposal despite the failure.
        Assert.assertEquals(TurnOutcome.Proposed, outcome)
        Assert.assertNotNull("proposal should still be staged", memory.proposal)

        // Task statuses: t1 COMPLETED, t2 FAILED, t3 COMPLETED.
        val taskList = memory.taskList!!
        Assert.assertEquals(TaskStatus.COMPLETED, taskList.byId("t1")!!.status)
        Assert.assertEquals(TaskStatus.FAILED, taskList.byId("t2")!!.status)
        Assert.assertEquals(TaskStatus.COMPLETED, taskList.byId("t3")!!.status)

        // Exactly one propose_rule_content call. The assistant message
        // carrying it is in memory.messages (added before tool execution);
        // it is NOT in any aiService.requests() entry because
        // propose_rule_content is terminal.
        val proposeCalls = memory.messages
            .filterIsInstance<AiMessage.Assistant>()
            .flatMap { it.toolCalls.orEmpty() }
            .filter { it.name == PROPOSE_RULE_CONTENT }
        Assert.assertEquals(
            "exactly one propose_rule_content call expected, got ${proposeCalls.size}",
            1, proposeCalls.size
        )

        // The failed task is NOT in collectedSubAgentResults (it threw
        // before staging a result).
        Assert.assertEquals(
            "collectedSubAgentResults should have 2 entries (t1, t3 — t2 failed)",
            2, memory.collectedSubAgentResults.size
        )
        val collectedTaskIds = memory.collectedSubAgentResults.map { it.first.id }
        Assert.assertTrue("t1 should be in collected results", collectedTaskIds.contains("t1"))
        Assert.assertTrue("t3 should be in collected results", collectedTaskIds.contains("t3"))
        Assert.assertFalse(
            "t2 should NOT be in collected results (it failed)",
            collectedTaskIds.contains("t2")
        )

        // The merged content contains source tags for t1 and t3 only.
        val content = memory.proposal?.content.orEmpty()
        Assert.assertTrue(
            "merged content should contain 'source: detection:t1':\n$content",
            content.contains("source: detection:t1")
        )
        Assert.assertTrue(
            "merged content should contain 'source: detection:t3':\n$content",
            content.contains("source: detection:t3")
        )
        Assert.assertFalse(
            "merged content should NOT contain 'source: detection:t2' (task failed):\n$content",
            content.contains("source: detection:t2")
        )
    }

    // ------------------------------------------------------------------
    // T3.10 — testSubAgentGetsFreshMemory
    // ------------------------------------------------------------------

    /**
     * FR-3.1 / G3 — each sub-agent gets a fresh `AgentMemory`; tool
     * results from a sibling sub-agent's perception tools never appear in
     * another sub-agent's LLM transcript.
     *
     * Uses the real [com.itangcent.easyapi.core.ai.tools.RunSubAgentTool]
     * with a scripted [FakeAIService] so each sub-agent's LLM transcript
     * is captured. Two sub-agents are spawned; each calls
     * `find_classes_by_annotation` (returns `[]` from the empty fixture)
     * then `report_findings`. The test asserts that sub-agent 2's LLM
     * request messages do NOT contain the `find_classes_by_annotation`
     * tool result text from sub-agent 1's transcript.
     *
     * Detection ids used: `spring-filters-interceptors` and
     * `jaxrs-filters` (real catalog entries — required because
     * [com.itangcent.easyapi.core.ai.tools.RunSubAgentTool] fetches the
     * detection recipe in-process from
     * [com.itangcent.easyapi.core.ai.agent.PromptCatalog]).
     */
    fun testSubAgentGetsFreshMemory() = runBlocking {
        val events = captureEvents()
        val memory = AgentMemory().apply {
            taskList = TaskList(listOf(
                Task(
                    "detect_spring_filters_interceptors",
                    "Spring filters, interceptors, web filters",
                    "classes extending OncePerRequestFilter / HandlerInterceptor / WebFilter"
                ),
                Task(
                    "detect_jaxrs_filters",
                    "JAX-RS filters",
                    "classes implementing ContainerRequestFilter / ContainerResponseFilter"
                )
            ))
        }
        val ctx = buildCtx(memory, events.flow)
        val subAgentTools = ToolRegistry(subAgentToolRegistry())
        // The TracingAIService delegates to a FakeAIService but snapshots
        // each request so the test can inspect every LLM transcript
        // (orchestrator + sub-agents) after the run.
        val fake = FakeAIService()
        val tracing = TracingAIService(fake)
        val orchestratorTools = ToolRegistry(
            listOf(
                UpdateTaskTool(),
                com.itangcent.easyapi.core.ai.tools.RunSubAgentTool(
                    aiService = tracing,
                    subAgentTools = subAgentTools
                ),
                OrchestratorProposeRuleContentTool()
            )
        )

        // Step 1 (orchestrator): run sub-agent 1.
        fake.enqueueToolCalls(
            AiToolCall("o1", "run_sub_agent",
                """{"taskId":"detect_spring_filters_interceptors"}""")
        )
        // Step 2 (sub-agent 1, LLM call 1): perceive then report.
        fake.enqueueToolCalls(
            AiToolCall(
                "s1a", "find_classes_by_annotation",
                """{"annotationFqn":"jakarta.servlet.annotation.WebFilter"}"""
            )
        )
        // Step 3 (sub-agent 1, LLM call 2): report_findings (terminal).
        fake.enqueueToolCalls(
            AiToolCall(
                "s1b", "report_findings",
                """{"detected":true,"findings":"SPRING_FILTER_FINDINGS_FOR_T1","proposedRules":[{"key":"method.additional.header","rules":"method.additional.header={\"name\":\"X-Spring-Test\",\"value\":\"1\",\"desc\":\"\",\"required\":true}"}]}"""
            )
        )
        // Step 4 (orchestrator): mark t1 done + run sub-agent 2.
        fake.enqueueToolCalls(
            AiToolCall("o2", "update_task",
                """{"taskId":"detect_spring_filters_interceptors","status":"completed"}"""),
            AiToolCall("o3", "run_sub_agent",
                """{"taskId":"detect_jaxrs_filters"}""")
        )
        // Step 5 (sub-agent 2, LLM call 1): perceive then report.
        fake.enqueueToolCalls(
            AiToolCall(
                "s2a", "find_classes_by_annotation",
                """{"annotationFqn":"jakarta.ws.rs.NameBinding"}"""
            )
        )
        // Step 6 (sub-agent 2, LLM call 2): report_findings (terminal).
        fake.enqueueToolCalls(
            AiToolCall(
                "s2b", "report_findings",
                """{"detected":true,"findings":"JAXRS_FILTER_FINDINGS_FOR_T2","proposedRules":[{"key":"method.additional.header","rules":"method.additional.header={\"name\":\"X-Jaxrs-Test\",\"value\":\"2\",\"desc\":\"\",\"required\":true}"}]}"""
            )
        )
        // Step 7 (orchestrator): mark t2 done + propose merged content.
        fake.enqueueToolCalls(
            AiToolCall("o4", "update_task",
                """{"taskId":"detect_jaxrs_filters","status":"completed"}"""),
            AiToolCall(
                "o5", "propose_rule_content",
                """{"suggestedFileName":"merged.rules"}"""
            )
        )

        val agent = RuleAuthoringAgent(tracing, orchestratorTools, ctx, events.flow)
        val outcome = agent.runTurn(
            "walk the seeded task list",
            memory,
            entryPath = EntryPath.TASK_LIST_PROGRAMMATIC
        )
        events.cancelAndCollect()

        // Sanity: the turn completed with a proposal.
        Assert.assertEquals(TurnOutcome.Proposed, outcome)
        Assert.assertNotNull(memory.proposal)

        // Identify each sub-agent's LLM request by inspecting the user
        // message — RunSubAgentTool builds the sub-agent's instruction
        // with the task title in it ("You are a sub-agent running one
        // detection task: <title>").
        val subAgent1Requests = tracing.requests.filter { req ->
            req.messages.any {
                it is AiMessage.User &&
                    it.content.contains("Spring filters, interceptors, web filters")
            }
        }
        val subAgent2Requests = tracing.requests.filter { req ->
            req.messages.any {
                it is AiMessage.User &&
                    it.content.contains("JAX-RS filters")
            }
        }
        Assert.assertTrue(
            "should capture at least one sub-agent 1 request",
            subAgent1Requests.isNotEmpty()
        )
        Assert.assertTrue(
            "should capture at least one sub-agent 2 request",
            subAgent2Requests.isNotEmpty()
        )

        // Sub-agent 1's findings appear in the orchestrator's transcript
        // (via the run_sub_agent ToolResult that serialises the TaskResult)
        // and in the merged proposal — NOT in the sub-agent's own LLM
        // requests, because report_findings is terminal and its ToolResult
        // is never sent to the LLM. The orchestrator's requests are the
        // ones that do NOT contain the sub-agent seed user message.
        val orchestratorRequests = tracing.requests.filterNot { req ->
            req.messages.any {
                it is AiMessage.User &&
                    (it.content.contains("Spring filters, interceptors") ||
                        it.content.contains("JAX-RS filters"))
            }
        }
        val orchestratorTranscript = orchestratorRequests
            .flatMap { it.messages }.joinToString("\n")
        Assert.assertTrue(
            "orchestrator transcript should contain sub-agent 1's findings: $orchestratorTranscript",
            orchestratorTranscript.contains("SPRING_FILTER_FINDINGS_FOR_T1")
        )

        // Sub-agent 2's transcript should NOT contain sub-agent 1's
        // findings text or any of sub-agent 1's tool results.
        val sub2Transcript = subAgent2Requests.flatMap { it.messages }.joinToString("\n")
        Assert.assertFalse(
            "sub-agent 2 transcript must NOT contain sub-agent 1's findings: $sub2Transcript",
            sub2Transcript.contains("SPRING_FILTER_FINDINGS_FOR_T1")
        )
        Assert.assertFalse(
            "sub-agent 2 transcript must NOT contain sub-agent 1's tool-call id s1a: $sub2Transcript",
            sub2Transcript.contains("s1a")
        )
        Assert.assertFalse(
            "sub-agent 2 transcript must NOT contain sub-agent 1's tool-call id s1b: $sub2Transcript",
            sub2Transcript.contains("s1b")
        )
        // Sub-agent 2's own findings ("JAXRS_FILTER_FINDINGS_FOR_T2") are
        // in the report_findings response (not in any LLM request, since
        // report_findings is terminal) and in the merged proposal below.
        // The "should NOT contain sub-agent 1's data" assertions above are
        // the real memory-isolation checks.

        // The merged content should contain both sub-agents' findings
        // (no cross-contamination at the orchestrator level either).
        val content = memory.proposal?.content.orEmpty()
        Assert.assertTrue(
            "merged content should contain t1 findings",
            content.contains("SPRING_FILTER_FINDINGS_FOR_T1")
        )
        Assert.assertTrue(
            "merged content should contain t2 findings",
            content.contains("JAXRS_FILTER_FINDINGS_FOR_T2")
        )
    }

    // ------------------------------------------------------------------
    // T3.11 — testReportFindingsIsTerminalForSubAgent
    // ------------------------------------------------------------------

    /**
     * FR-3.4 — calling `report_findings` ends the sub-agent turn; no
     * further tool calls are dispatched in that sub-agent's turn.
     *
     * Spawns one real sub-agent (via `RunSubAgentTool`). The sub-agent's
     * LLM is scripted to call `report_findings` first, then (in the same
     * assistant message) issue a second tool call (`list_rule_keys`) that
     * should NEVER be dispatched — the agent loop exits after
     * `report_findings` returns. Asserts the `list_rule_keys` tool was
     * never executed (its result text is absent from the sub-agent's
     * transcript).
     */
    fun testReportFindingsIsTerminalForSubAgent() = runBlocking {
        val events = captureEvents()
        val memory = AgentMemory().apply {
            taskList = TaskList(listOf(
                Task(
                    "detect_static_auth",
                    "Static auth",
                    "hardcoded credentials / static auth headers"
                )
            ))
        }
        val ctx = buildCtx(memory, events.flow)

        // A fake perception tool that records when it's executed.
        val sentinelTool = SentinelPerceptionTool(
            name = "list_rule_keys",
            markerText = "LIST_RULE_KEYS_WAS_DISPATCHED"
        )
        val subAgentTools = ToolRegistry(
            listOf(sentinelTool, com.itangcent.easyapi.core.ai.tools.ReportFindingsTool())
        )
        val fake = FakeAIService()
        val orchestratorTools = ToolRegistry(
            listOf(
                UpdateTaskTool(),
                com.itangcent.easyapi.core.ai.tools.RunSubAgentTool(fake, subAgentTools),
                OrchestratorProposeRuleContentTool()
            )
        )

        // Step 1 (orchestrator): run sub-agent 1.
        fake.enqueueToolCalls(
            AiToolCall("o1", "run_sub_agent",
                """{"taskId":"detect_static_auth"}""")
        )
        // Step 2 (sub-agent 1): emit report_findings + list_rule_keys in
        // the SAME assistant message. The agent loop dispatches
        // report_findings first (terminal) and must NOT dispatch
        // list_rule_keys afterwards.
        fake.enqueueToolCalls(
            AiToolCall(
                "s1a", "report_findings",
                """{"detected":true,"findings":"STATIC_AUTH_FINDINGS","proposedRules":[{"key":"method.additional.header","rules":"method.additional.header={\"name\":\"X-Api-Key\",\"value\":\"k\",\"desc\":\"\",\"required\":true}"}]}"""
            ),
            AiToolCall("s1b", "list_rule_keys", "{}")
        )
        // Step 3 (orchestrator): propose the merged content.
        fake.enqueueToolCalls(
            AiToolCall(
                "o2", "propose_rule_content",
                """{"suggestedFileName":"merged.rules"}"""
            )
        )

        val agent = RuleAuthoringAgent(fake, orchestratorTools, ctx, events.flow)
        val outcome = agent.runTurn(
            "walk the seeded task list",
            memory,
            entryPath = EntryPath.TASK_LIST_PROGRAMMATIC
        )
        events.cancelAndCollect()

        // Sanity: turn ended with a proposal.
        Assert.assertEquals(TurnOutcome.Proposed, outcome)
        Assert.assertNotNull(memory.proposal)

        // The sentinel perception tool was NEVER executed — the sub-agent's
        // turn ended at report_findings before list_rule_keys could be
        // dispatched.
        Assert.assertFalse(
            "sentinel perception tool must NOT be executed (report_findings is terminal)",
            sentinelTool.executed
        )

        // The sub-agent's transcript should NOT contain the sentinel
        // tool's result text — only the report_findings result.
        val allTranscripts = fake.requests().flatMap { it.messages }.joinToString("\n")
        Assert.assertFalse(
            "sentinel tool's marker text must NOT appear in any transcript: $allTranscripts",
            allTranscripts.contains("LIST_RULE_KEYS_WAS_DISPATCHED")
        )
        // report_findings was dispatched — verify via the events flow
        // (the Observed event is emitted before the terminal check). Its
        // ToolResult text ("findings reported") is NOT in any LLM request
        // because report_findings is terminal: the sub-agent's turn ends
        // before a subsequent LLM call could carry the ToolResult as context.
        val observedReportFindings = events.collected
            .filterIsInstance<AgentEvent.Observed>()
            .any { it.tool == REPORT_FINDINGS }
        Assert.assertTrue(
            "an Observed event for report_findings should be in the events flow",
            observedReportFindings
        )

        // The merged proposal contains the sub-agent's findings.
        val content = memory.proposal?.content.orEmpty()
        Assert.assertTrue(
            "merged content should contain the sub-agent's findings: $content",
            content.contains("STATIC_AUTH_FINDINGS")
        )
        Assert.assertTrue(
            "merged content should contain the source tag",
            content.contains("source: detection:detect_static_auth")
        )

        // Exactly one propose_rule_content call (the orchestrator's). The
        // assistant message carrying it is in the orchestrator's
        // memory.messages (added before tool execution); it is NOT in any
        // fake.requests() entry because propose_rule_content is terminal.
        val proposeCalls = memory.messages
            .filterIsInstance<AiMessage.Assistant>()
            .flatMap { it.toolCalls.orEmpty() }
            .filter { it.name == PROPOSE_RULE_CONTENT }
        Assert.assertEquals(
            "exactly one propose_rule_content call expected",
            1, proposeCalls.size
        )
        // And exactly one report_findings call (the sub-agent's). The
        // sub-agent uses fresh memory (not the orchestrator's memory), so
        // we verify via the events flow instead.
        val reportCallEvents = events.collected
            .filterIsInstance<AgentEvent.Acting>()
            .count { it.tool == REPORT_FINDINGS }
        Assert.assertEquals(
            "exactly one report_findings call expected",
            1, reportCallEvents
        )
    }

    // ------------------------------------------------------------------
    // T5.3 — testNotDetectedSubAgentMarkedCompleted
    // ------------------------------------------------------------------

    /**
     * FR-3.5 (post-ship fix-up) / AC-8 — when a sub-agent returns
     * `detected=false` (ran successfully but found nothing), the
     * orchestrator marks the task `COMPLETED`, NOT `SKIPPED`. "Nothing
     * detected" is a valid finding; the task ran to completion.
     *
     * Uses [FakeRunSubAgentTool] with [FakeRunSubAgentTool.notDetectedTaskIds]
     * to stage `detected=false` for task t1. The orchestrator LLM is
     * scripted (via [FakeAIService]) to call `run_sub_agent` then
     * `update_task(status="completed")` — the directive in
     * [MagicInstructionBuilder.detectionInstruction] now instructs this
     * status for a successful-but-empty detection.
     */
    fun testNotDetectedSubAgentMarkedCompleted() = runBlocking {
        val events = captureEvents()
        val memory = AgentMemory().apply {
            taskList = TaskList(listOf(
                Task("t1", "first detection", "cue-1"),
                Task("t2", "second detection", "cue-2")
            ))
        }
        val ctx = buildCtx(memory, events.flow)
        val orchestratorTools = ToolRegistry(listOf(
            UpdateTaskTool(),
            // t1 returns detected=false; t2 returns detected=true.
            FakeRunSubAgentTool(notDetectedTaskIds = setOf("t1")),
            OrchestratorProposeRuleContentTool()
        ))
        val aiService = FakeAIService()
        // Step 1: orchestrator runs t1 (not detected) + t2 (detected).
        aiService.enqueueToolCalls(
            AiToolCall("c1", "run_sub_agent", """{"taskId":"t1"}"""),
            AiToolCall("c2", "run_sub_agent", """{"taskId":"t2"}""")
        )
        // Step 2: orchestrator marks both completed (NOT skipped for t1).
        aiService.enqueueToolCalls(
            AiToolCall(
                "c3", "update_task",
                """{"taskId":"t1","status":"completed","reason":"No pattern found"}"""
            ),
            AiToolCall(
                "c4", "update_task",
                """{"taskId":"t2","status":"completed"}"""
            )
        )
        // Step 3: orchestrator proposes the merged content.
        aiService.enqueueToolCalls(
            AiToolCall(
                "c5", "propose_rule_content",
                """{"suggestedFileName":"merged.rules"}"""
            )
        )

        val agent = RuleAuthoringAgent(aiService, orchestratorTools, ctx, events.flow)
        val outcome = agent.runTurn(
            "walk the seeded task list",
            memory,
            entryPath = EntryPath.TASK_LIST_PROGRAMMATIC
        )
        events.cancelAndCollect()

        // Both tasks should be COMPLETED — t1 is NOT SKIPPED despite
        // detected=false (FR-3.5 post-ship fix-up).
        val taskList = memory.taskList!!
        Assert.assertEquals(
            "t1 (detected=false) should be COMPLETED, not SKIPPED",
            TaskStatus.COMPLETED, taskList.byId("t1")!!.status
        )
        Assert.assertEquals(
            "t2 (detected=true) should be COMPLETED",
            TaskStatus.COMPLETED, taskList.byId("t2")!!.status
        )

        // The turn ended with a proposal (t2 was detected, so the merged
        // content is non-empty).
        Assert.assertEquals(TurnOutcome.Proposed, outcome)
        Assert.assertNotNull(memory.proposal)
        // The merged content contains t2's findings (detected=true) but
        // NOT t1's (detected=false, filtered out by mergeTaskResults).
        val content = memory.proposal?.content.orEmpty()
        Assert.assertTrue(
            "merged content should contain t2's findings:\n$content",
            content.contains("source: detection:t2")
        )
        Assert.assertFalse(
            "merged content should NOT contain t1's findings (detected=false):\n$content",
            content.contains("source: detection:t1")
        )
    }

    // ------------------------------------------------------------------
    // T5.5 — testAllNotDetectedDoesNotStageProposal
    // ------------------------------------------------------------------

    /**
     * FR-2.5 (post-ship fix-up) / AC-9 — when every sub-agent returns
     * `detected=false`, the merged content is blank. The orchestrator's
     * `propose_rule_content` call stages NO proposal (leaves
     * `workingMemory.proposal` null), emits NO `ProposalReady` event,
     * and the turn ends with `TurnOutcome.Answered`. The user is never
     * prompted to apply an empty proposition.
     */
    fun testAllNotDetectedDoesNotStageProposal() = runBlocking {
        val events = captureEvents()
        val memory = AgentMemory().apply {
            taskList = TaskList(listOf(
                Task("t1", "first detection", "cue-1"),
                Task("t2", "second detection", "cue-2")
            ))
        }
        val ctx = buildCtx(memory, events.flow)
        val orchestratorTools = ToolRegistry(listOf(
            UpdateTaskTool(),
            // Both sub-agents return detected=false.
            FakeRunSubAgentTool(notDetectedTaskIds = setOf("t1", "t2")),
            OrchestratorProposeRuleContentTool()
        ))
        val aiService = FakeAIService()
        // Step 1: orchestrator runs both sub-agents (both not detected).
        aiService.enqueueToolCalls(
            AiToolCall("c1", "run_sub_agent", """{"taskId":"t1"}"""),
            AiToolCall("c2", "run_sub_agent", """{"taskId":"t2"}""")
        )
        // Step 2: orchestrator marks both completed (FR-3.5 fix-up).
        aiService.enqueueToolCalls(
            AiToolCall(
                "c3", "update_task",
                """{"taskId":"t1","status":"completed","reason":"No pattern found"}"""
            ),
            AiToolCall(
                "c4", "update_task",
                """{"taskId":"t2","status":"completed","reason":"No pattern found"}"""
            )
        )
        // Step 3: orchestrator calls propose_rule_content (merged content
        // will be blank — both detected=false).
        aiService.enqueueToolCalls(
            AiToolCall(
                "c5", "propose_rule_content",
                """{"suggestedFileName":"merged.rules"}"""
            )
        )

        val agent = RuleAuthoringAgent(aiService, orchestratorTools, ctx, events.flow)
        val outcome = agent.runTurn(
            "walk the seeded task list",
            memory,
            entryPath = EntryPath.TASK_LIST_PROGRAMMATIC
        )
        events.cancelAndCollect()

        // No proposal staged — the merged content was blank.
        Assert.assertNull(
            "no proposal should be staged when merged content is blank",
            memory.proposal
        )
        // Turn outcome is Answered (not Proposed) — finish() returns
        // Answered when memory.proposal is null.
        Assert.assertEquals(
            "turn should end with TurnOutcome.Answered (no proposal staged)",
            TurnOutcome.Answered, outcome
        )
        // No ProposalReady event was emitted.
        val proposalReadyEvents = events.collected
            .filterIsInstance<AgentEvent.ProposalReady>()
        Assert.assertTrue(
            "no ProposalReady event should be emitted when merged content is blank",
            proposalReadyEvents.isEmpty()
        )
        // TurnComplete WAS emitted — the turn ends normally.
        val turnCompleteEvents = events.collected
            .filterIsInstance<AgentEvent.TurnComplete>()
        Assert.assertEquals(
            "TurnComplete should be emitted exactly once",
            1, turnCompleteEvents.size
        )
        // Both tasks are COMPLETED (FR-3.5 fix-up).
        val taskList = memory.taskList!!
        Assert.assertEquals(TaskStatus.COMPLETED, taskList.byId("t1")!!.status)
        Assert.assertEquals(TaskStatus.COMPLETED, taskList.byId("t2")!!.status)
    }

    // ------------------------------------------------------------------
    // T5.7 — testRealRunSubAgentAutoMarksCompletedWhenNotDetected
    // ------------------------------------------------------------------

    /**
     * FR-3.5 / FR-3.6 post-ship fix-up — the REAL
     * [com.itangcent.easyapi.core.ai.tools.RunSubAgentTool] auto-marks
     * the task `COMPLETED` when the sub-agent runs successfully,
     * regardless of `detected`. The UI checkbox updates deterministically
     * — it does NOT depend on the orchestrator LLM calling `update_task`
     * afterwards.
     *
     * This test uses the REAL `RunSubAgentTool` (not the fake) with a
     * scripted [FakeAIService]. The sub-agent calls
     * `report_findings(detected=false, ...)`. The orchestrator is scripted
     * to call `run_sub_agent` then `propose_rule_content` — it does NOT
     * call `update_task`. The test asserts:
     * - The task is `COMPLETED` (auto-marked by `run_sub_agent`).
     * - A `TaskCompleted` event was emitted (so the UI checkbox ticks).
     * - No proposal is staged (merged content is blank — FR-2.5).
     * - The turn ends with `TurnOutcome.Answered`.
     *
     * This reproduces and verifies the fix for the reported bug: "while a
     * task completed with detected=false, the task is still not marked as
     * completed, the UI still shows an open checkbox."
     */
    fun testRealRunSubAgentAutoMarksCompletedWhenNotDetected() = runBlocking {
        val events = captureEvents()
        val memory = AgentMemory().apply {
            taskList = TaskList(listOf(
                Task(
                    "detect_static_auth",
                    "Static auth",
                    "hardcoded credentials / static auth headers"
                )
            ))
        }
        val ctx = buildCtx(memory, events.flow)

        // Real sub-agent tool registry: perception tools + report_findings.
        val subAgentTools = ToolRegistry(subAgentToolRegistry())
        val fake = FakeAIService()
        val orchestratorTools = ToolRegistry(
            listOf(
                UpdateTaskTool(),
                com.itangcent.easyapi.core.ai.tools.RunSubAgentTool(fake, subAgentTools),
                OrchestratorProposeRuleContentTool()
            )
        )

        // Step 1 (orchestrator): run sub-agent. NOTE: no update_task is
        // scripted — the auto-mark should tick the checkbox without it.
        fake.enqueueToolCalls(
            AiToolCall("o1", "run_sub_agent",
                """{"taskId":"detect_static_auth"}""")
        )
        // Step 2 (sub-agent): perceive then report detected=false.
        fake.enqueueToolCalls(
            AiToolCall(
                "s1a", "find_classes_by_annotation",
                """{"annotationFqn":"org.springframework.web.bind.annotation.RestController"}"""
            )
        )
        fake.enqueueToolCalls(
            AiToolCall(
                "s1b", "report_findings",
                """{"detected":false,"findings":"No static auth pattern found"}"""
            )
        )
        // Step 3 (orchestrator): propose the merged content (will be
        // blank — detected=false → stages no proposal, FR-2.5).
        fake.enqueueToolCalls(
            AiToolCall(
                "o2", "propose_rule_content",
                """{"suggestedFileName":"merged.rules"}"""
            )
        )

        val agent = RuleAuthoringAgent(fake, orchestratorTools, ctx, events.flow)
        val outcome = agent.runTurn(
            "walk the seeded task list",
            memory,
            entryPath = EntryPath.TASK_LIST_PROGRAMMATIC
        )
        events.cancelAndCollect()

        // THE BUG FIX — the task is COMPLETED even though detected=false
        // and the orchestrator never called update_task. The auto-mark in
        // RunSubAgentTool did it.
        val task = memory.taskList!!.byId("detect_static_auth")!!
        Assert.assertEquals(
            "task should be COMPLETED (auto-marked by run_sub_agent, " +
                "detected=false is a valid finding, not a skip)",
            TaskStatus.COMPLETED, task.status
        )

        // A TaskCompleted event was emitted — the UI checkbox ticks.
        val taskCompletedEvents = events.collected
            .filterIsInstance<AgentEvent.TaskCompleted>()
            .filter { it.taskId == "detect_static_auth" }
        Assert.assertEquals(
            "exactly one TaskCompleted event should be emitted for the task " +
                "(auto-marked by run_sub_agent)",
            1, taskCompletedEvents.size
        )

        // FR-2.5 — no proposal staged (merged content blank), turn ends
        // with TurnOutcome.Answered.
        Assert.assertNull(
            "no proposal should be staged when detected=false",
            memory.proposal
        )
        Assert.assertEquals(
            "turn should end with TurnOutcome.Answered (no proposal staged)",
            TurnOutcome.Answered, outcome
        )
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun buildCtx(
        memory: AgentMemory,
        events: MutableSharedFlow<AgentEvent>
    ): ToolContext = ToolContext(
        project = project,
        configReader = ConfigReader.getInstance(project),
        aiSettings = AiRuntimeConfig(
            provider = AiProvider.OPENAI,
            baseUrl = "", apiKey = "", model = "",
            requestTimeoutSec = 30,
            // Generous budget so multi-step orchestrator + sub-agent
            // turns never hit the step ceiling.
            maxRequests = 12
        ),
        ruleFileResolver = RuleFileResolver(project),
        workingMemory = memory,
        approvals = FakeApprovalGate(),
        events = events
    )

    private fun captureEvents(): EventCapture {
        val flow = MutableSharedFlow<AgentEvent>(
            replay = Int.MAX_VALUE,
            extraBufferCapacity = 0
        )
        val collected = mutableListOf<AgentEvent>()
        val job = scope().launch(
            context = Dispatchers.Unconfined,
            start = CoroutineStart.UNDISPATCHED
        ) {
            flow.collect { collected.add(it) }
        }
        return EventCapture(flow, job, collected)
    }

    private fun scope() =
        CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private class EventCapture(
        val flow: MutableSharedFlow<AgentEvent>,
        private val job: Job,
        val collected: List<AgentEvent>
    ) {
        suspend fun cancelAndCollect() {
            job.cancelAndJoin()
        }
    }

    /**
     * Fake `run_sub_agent` tool for T3.8 / T3.9 / T5.3 / T5.5 — bypasses
     * real sub-agent spawning. Looks up the task, optionally throws (for
     * T3.9), otherwise stages a deterministic [TaskResult] in
     * `ctx.workingMemory.collectedSubAgentResults` so the orchestrator's
     * [OrchestratorProposeRuleContentTool] can merge it.
     *
     * The staged result's `findings` text is `"findings for <id>"` so the
     * test can assert it appears in the merged content. By default
     * `detected=true` so the result survives [mergeTaskResults]'s
     * `detected` filter; pass [notDetectedTaskIds] to stage
     * `detected=false` for specific tasks (T5.3 / T5.5).
     */
    private class FakeRunSubAgentTool(
        private val failTaskIds: Set<String> = emptySet(),
        private val notDetectedTaskIds: Set<String> = emptySet()
    ) : AiTool {
        override val name: String = "run_sub_agent"
        override val description: String = "Fake run_sub_agent for testing."
        override val kind: ToolKind = ToolKind.ACTION
        override val requiresApproval: Boolean = false
        override val parametersSchema: Map<String, Any?> = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "taskId" to mapOf("type" to "string")
            ),
            "required" to listOf("taskId")
        )

        override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
            val taskId = args["taskId"] as? String
                ?: return ToolResult.Error("missing taskId")
            val taskList = ctx.workingMemory.taskList
                ?: return ToolResult.Error("no task list")
            val task = taskList.byId(taskId)
                ?: return ToolResult.Error("unknown task id: $taskId")

            if (task.id in failTaskIds) {
                // Mirrors RunSubAgentTool's behaviour: an internal
                // exception becomes a ToolResult.Error via ToolRegistry's
                // runCatching. The orchestrator's LLM sees the error and
                // can mark the task failed + continue.
                throw RuntimeException("sub-agent for ${task.id} failed (test stub)")
            }

            val detected = task.id !in notDetectedTaskIds
            val result = TaskResult(
                detected = detected,
                findings = "findings for ${task.id}",
                proposedRules = if (detected) listOf(
                    RuleProposal(
                        key = "method.additional.header",
                        // Must be a COMPLETE rule line: the orchestrator
                        // concatenates it verbatim and the merged content now
                        // goes through CompositeRuleValidator, so a fragment
                        // would be rejected (and no proposal staged).
                        rules = "method.additional.header[\$class:com.example.TestController]=" +
                            "{\"name\":\"X-Test\",\"value\":\"${task.id}\",\"desc\":\"\",\"required\":true}"
                    )
                ) else emptyList()
            )
            // Mirrors RunSubAgentTool's collection step — the orchestrator's
            // propose_rule_content reads this list and merges.
            ctx.workingMemory.collectedSubAgentResults.add(task to result)
            return ToolResult.Text(
                "sub-agent for ${task.id} done: detected=${result.detected}"
            )
        }
    }

    /**
     * A perception tool with a configurable name that records when it's
     * executed and returns a marker text. Used by T3.11 to verify that
     * `report_findings` is terminal — the sentinel tool is placed AFTER
     * `report_findings` in the same assistant message and must NEVER be
     * dispatched.
     */
    private class SentinelPerceptionTool(
        override val name: String,
        private val markerText: String
    ) : AiTool {
        override val description: String = "Sentinel perception tool for testing."
        override val kind: ToolKind = ToolKind.PERCEPTION
        override val parametersSchema: Map<String, Any?> = emptyMap()

        @Volatile
        var executed: Boolean = false
            private set

        override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
            executed = true
            return ToolResult.Text(markerText)
        }
    }

    /**
     * AIService wrapper that delegates to a [FakeAIService] but snapshots
     * every request into a list the test can inspect. Used by T3.10 to
     * capture each sub-agent's LLM transcript separately.
     *
     * Without this wrapper, [FakeAIService.requests] returns a flat list
     * and the test would have to disambiguate orchestrator vs sub-agent
     * calls by index — fragile. The wrapper doesn't change behaviour, it
     * just makes the captured requests directly accessible.
     */
    private class TracingAIService(private val delegate: FakeAIService) : AIService {
        val requests: MutableList<com.itangcent.easyapi.core.ai.AiChatRequest> = mutableListOf()

        override suspend fun chat(request: com.itangcent.easyapi.core.ai.AiChatRequest):
            com.itangcent.easyapi.core.ai.AiChatResponse {
            // Snapshot — the caller mutates the same list across turns.
            requests.add(request.copy(messages = request.messages.toList()))
            return delegate.chat(request)
        }

        override suspend fun testConnection(): Result<String> =
            delegate.testConnection()
    }
}
