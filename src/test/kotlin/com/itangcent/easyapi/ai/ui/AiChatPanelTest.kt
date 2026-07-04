package com.itangcent.easyapi.ai.ui

import com.itangcent.easyapi.ai.AiMessage
import com.itangcent.easyapi.ai.agent.AgentEvent
import com.itangcent.easyapi.ai.agent.AgentMemory
import com.itangcent.easyapi.ai.agent.Clarification
import com.itangcent.easyapi.ai.agent.ClarificationAnswers
import com.itangcent.easyapi.ai.agent.ClarificationQuestion
import com.itangcent.easyapi.ai.agent.Proposal
import com.itangcent.easyapi.ai.agent.QuestionKind
import com.itangcent.easyapi.ai.agent.QuestionOption
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Tests for [AiChatPanel] event rendering.
 *
 * Verifies that rendering [AgentEvent]s produces the expected UI rows
 * (messages, tool-activity cards, approval cards, proposal card).
 *
 * The full 2-round conversation + save flow is covered by
 * [com.itangcent.easyapi.ai.AiAssistantServiceTest] (which exercises the
 * agent end-to-end via a fake service); here we test the panel's rendering
 * seams directly.
 */
class AiChatPanelTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testMessageEventRendersRow() {
        val panel = AiChatPanel(project)
        val before = panel.transcriptComponentCount()
        panel.renderEventForTest(AgentEvent.Message("Hello, world!"))
        val after = panel.transcriptComponentCount()
        assertTrue("transcript should gain a row for a message",
            after > before)
        panel.dispose()
    }

    fun testPerceivingEventRendersCard() {
        val panel = AiChatPanel(project)
        val before = panel.transcriptComponentCount()
        panel.renderEventForTest(AgentEvent.Perceiving("list_rule_keys", "{}"))
        val after = panel.transcriptComponentCount()
        assertTrue("transcript should gain a row for a perception event",
            after > before)
        panel.dispose()
    }

    fun testActingEventRendersCard() {
        val panel = AiChatPanel(project)
        val before = panel.transcriptComponentCount()
        panel.renderEventForTest(AgentEvent.Acting("propose_rule_content", "{}"))
        val after = panel.transcriptComponentCount()
        assertTrue("transcript should gain a row for an action event",
            after > before)
        panel.dispose()
    }

    fun testApprovalRequestedRendersApprovalCard() {
        val panel = AiChatPanel(project)
        // Bind a fake session so the approval card can find the approvals gate.
        val events = MutableSharedFlow<AgentEvent>(replay = 64, extraBufferCapacity = 64)
        val memory = AgentMemory()
        val gate = com.itangcent.easyapi.ai.UiApprovalGate()
        val sess = com.itangcent.easyapi.ai.ConversationSession(
            agent = mockAgent(),
            memory = memory,
            events = events,
            approvals = gate
        )
        panel.bindSessionForTest(sess)
        val before = panel.transcriptComponentCount()
        panel.renderEventForTest(AgentEvent.ApprovalRequested("write_rule_file", "{}"))
        val after = panel.transcriptComponentCount()
        assertTrue("transcript should gain an approval card",
            after > before)
        panel.dispose()
    }

    fun testProposalReadyRendersProposalCard() {
        val panel = AiChatPanel(project)
        val before = panel.transcriptComponentCount()
        panel.renderEventForTest(
            AgentEvent.ProposalReady(
                Proposal("# my rule\napi.name=cool", "custom.rules")
            )
        )
        val after = panel.transcriptComponentCount()
        assertTrue("transcript should gain a proposal card",
            after > before)
        panel.dispose()
    }

    fun testObservedEventRendersObservation() {
        val panel = AiChatPanel(project)
        val before = panel.transcriptComponentCount()
        panel.renderEventForTest(AgentEvent.Observed("list_rule_keys", "12 keys found"))
        val after = panel.transcriptComponentCount()
        assertTrue("transcript should gain an observation row",
            after > before)
        panel.dispose()
    }

    fun testThinkingEventDoesNotAddRow() {
        val panel = AiChatPanel(project)
        val before = panel.transcriptComponentCount()
        panel.renderEventForTest(AgentEvent.Thinking(1))
        val after = panel.transcriptComponentCount()
        assertEquals("Thinking should not add a transcript row",
            before, after)
        panel.dispose()
    }

    fun testProposalApplyInvokesCallback() {
        val panel = AiChatPanel(project)
        var applied: String? = null
        panel.onApplyProposal = { applied = it }
        panel.renderEventForTest(
            AgentEvent.ProposalReady(Proposal("api.name=cool", "custom.rules"))
        )
        assertTrue(
            "Apply-to-editor button should be present when onApplyProposal is set",
            panel.clickApplyToEditorForTest()
        )
        assertEquals("api.name=cool", applied)
        // The proposal is consumed — its actions must be removed so it can't be
        // applied again (it's now stale).
        assertFalse(
            "Apply-to-editor button should be gone after it was applied",
            panel.clickApplyToEditorForTest()
        )
        panel.dispose()
    }

    fun testNewProposalFreezesPreviousProposal() {
        val panel = AiChatPanel(project)
        val applied = mutableListOf<String>()
        panel.onApplyProposal = { applied.add(it) }
        panel.renderEventForTest(
            AgentEvent.ProposalReady(Proposal("api.name=first", "custom.rules"))
        )
        // Render a second proposal — it supersedes the first.
        panel.renderEventForTest(
            AgentEvent.ProposalReady(Proposal("api.name=second", "custom.rules"))
        )
        // Only the latest proposal's apply button should remain; clicking it
        // applies "second", proving the first proposal's actions were frozen.
        assertTrue(
            "latest proposal's apply button should still be present",
            panel.clickApplyToEditorForTest()
        )
        assertEquals(
            "second apply should win after the first was superseded",
            listOf("api.name=second"),
            applied
        )
        panel.dispose()
    }

    fun testNewMessageFreezesPendingProposal() {
        val panel = AiChatPanel(project)
        val applied = mutableListOf<String>()
        panel.onApplyProposal = { applied.add(it) }
        panel.renderEventForTest(
            AgentEvent.ProposalReady(Proposal("api.name=cool", "custom.rules"))
        )
        // Sending a new message supersedes the pending proposal: its apply
        // button must be gone even if the new turn never runs (no session).
        panel.typeAndSendForTest("anything")
        assertFalse(
            "apply button should be gone once a new message is sent",
            panel.clickApplyToEditorForTest()
        )
        assertTrue("no apply should have fired", applied.isEmpty())
        panel.dispose()
    }

    fun testNoApplyButtonWithoutCallback() {
        val panel = AiChatPanel(project)
        panel.renderEventForTest(
            AgentEvent.ProposalReady(Proposal("api.name=cool", "custom.rules"))
        )
        assertFalse(
            "Apply-to-editor button should be absent when onApplyProposal is null",
            panel.clickApplyToEditorForTest()
        )
        panel.dispose()
    }

    // --- structured clarification card ---

    fun testClarificationCardSubmitCompletesGate() = runBlocking {
        val panel = AiChatPanel(project)
        val events = MutableSharedFlow<AgentEvent>(replay = 64, extraBufferCapacity = 64)
        val gate = com.itangcent.easyapi.ai.UiClarificationGate(events)
        val sess = com.itangcent.easyapi.ai.ConversationSession(
            agent = mockAgent(),
            memory = AgentMemory(),
            events = events,
            approvals = com.itangcent.easyapi.ai.UiApprovalGate(),
            clarifications = gate
        )
        panel.bindSessionForTest(sess)

        val clar = Clarification(
            prompt = "Could you clarify:",
            questions = listOf(
                ClarificationQuestion(
                    "scope", "Scope?", QuestionKind.SINGLE_CHOICE,
                    listOf(
                        QuestionOption("global", "Globally"),
                        QuestionOption("controllers", "Specific controllers", isDefault = true)
                    )
                )
            )
        )

        var result: ClarificationAnswers? = null
        val waiter = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            result = gate.await(clar)
        }
        assertTrue("gate should be pending", gate.isPending())

        val before = panel.transcriptComponentCount()
        panel.renderEventForTest(AgentEvent.ClarificationRequested(clar))
        assertTrue("clarification card should render", panel.transcriptComponentCount() > before)
        assertTrue(panel.isClarificationPendingForTest())

        assertTrue(panel.clickSubmitClarificationForTest())
        assertFalse(panel.isClarificationPendingForTest())

        assertNotNull("gate should have resolved", result)
        // The default-flagged option is pre-selected.
        assertEquals(listOf("controllers"), result!!.answers["scope"])

        waiter.cancel()
        panel.dispose()
    }

    fun testTypedReplyResolvesPendingClarification() = runBlocking {
        val panel = AiChatPanel(project)
        val events = MutableSharedFlow<AgentEvent>(replay = 64, extraBufferCapacity = 64)
        val gate = com.itangcent.easyapi.ai.UiClarificationGate(events)
        val sess = com.itangcent.easyapi.ai.ConversationSession(
            agent = mockAgent(),
            memory = AgentMemory(),
            events = events,
            approvals = com.itangcent.easyapi.ai.UiApprovalGate(),
            clarifications = gate
        )
        panel.bindSessionForTest(sess)

        val clar = Clarification(
            prompt = null,
            questions = listOf(ClarificationQuestion("q1", "Scope?", QuestionKind.FREE_TEXT))
        )
        var result: ClarificationAnswers? = null
        val waiter = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            result = gate.await(clar)
        }
        panel.renderEventForTest(AgentEvent.ClarificationRequested(clar))
        assertTrue(panel.isClarificationPendingForTest())

        // Typing a free-form reply resolves the pending card instead of starting a turn.
        panel.typeAndSendForTest("just globally")
        assertFalse(panel.isClarificationPendingForTest())

        assertNotNull(result)
        assertEquals(listOf("just globally"), result!!.answers[ClarificationAnswers.RAW_KEY])

        waiter.cancel()
        panel.dispose()
    }

    /** Build a minimal dummy agent — the panel never calls it in these tests. */
    private fun mockAgent(): com.itangcent.easyapi.ai.agent.RuleAuthoringAgent {
        // The panel tests don't drive the agent; they only render events.
        // We use reflection-free construction via a fake AIService + empty tool registry.
        val fakeService = object : com.itangcent.easyapi.ai.AIService {
            override suspend fun chat(request: com.itangcent.easyapi.ai.AiChatRequest) =
                com.itangcent.easyapi.ai.AiChatResponse(
                    AiMessage.Assistant("stub", null), "stop"
                )
            override suspend fun testConnection() = Result.success("ok")
        }
        val tools = com.itangcent.easyapi.ai.tools.ToolRegistry(emptyList())
        val ctx = com.itangcent.easyapi.ai.tools.ToolContext(
            project = project,
            configReader = com.itangcent.easyapi.config.ConfigReader.getInstance(project),
            aiSettings = com.itangcent.easyapi.ai.AiRuntimeConfig(
                provider = com.itangcent.easyapi.ai.AiProvider.OLLAMA,
                baseUrl = "", apiKey = "", model = "",
                requestTimeoutSec = 30, maxRequests = 8
            ),
            ruleFileResolver = com.itangcent.easyapi.config.source.RuleFileResolver(project),
            workingMemory = AgentMemory(),
            approvals = com.itangcent.easyapi.ai.UiApprovalGate()
        )
        val events = MutableSharedFlow<AgentEvent>(replay = 64, extraBufferCapacity = 64)
        return com.itangcent.easyapi.ai.agent.RuleAuthoringAgent(fakeService, tools, ctx, events)
    }

    // --- additional event-coverage tests ---

    fun testFailedEventDoesNotCrash() {
        val panel = AiChatPanel(project)
        val before = panel.transcriptComponentCount()
        // Failed updates the status label + fires a notification — it must
        // not throw and must not add a transcript row.
        panel.renderEventForTest(AgentEvent.Failed("boom"))
        assertEquals(
            "Failed event should not add a transcript row",
            before, panel.transcriptComponentCount()
        )
        panel.dispose()
    }

    fun testTurnCompleteDoesNotAddRow() {
        val panel = AiChatPanel(project)
        val before = panel.transcriptComponentCount()
        panel.renderEventForTest(AgentEvent.TurnComplete)
        assertEquals(
            "TurnComplete should not add a transcript row",
            before, panel.transcriptComponentCount()
        )
        panel.dispose()
    }

    fun testFileReadConsentCardRenders() {
        val panel = AiChatPanel(project)
        val events = MutableSharedFlow<AgentEvent>(replay = 64, extraBufferCapacity = 64)
        val sess = com.itangcent.easyapi.ai.ConversationSession(
            agent = mockAgent(),
            memory = AgentMemory(),
            events = events,
            approvals = com.itangcent.easyapi.ai.UiApprovalGate()
        )
        panel.bindSessionForTest(sess)
        val before = panel.transcriptComponentCount()
        panel.renderEventForTest(
            AgentEvent.FileReadConsentRequested("/tmp/external.properties")
        )
        val after = panel.transcriptComponentCount()
        assertTrue(
            "transcript should gain a read-consent card",
            after > before
        )
        panel.dispose()
    }

    fun testFileReadConsentCardWithoutSessionIsNoop() {
        // Without a bound session, the card must silently return rather
        // than NPE on the gate.
        val panel = AiChatPanel(project)
        val before = panel.transcriptComponentCount()
        panel.renderEventForTest(
            AgentEvent.FileReadConsentRequested("/tmp/external.properties")
        )
        assertEquals(
            "read-consent card without session should not add a row",
            before, panel.transcriptComponentCount()
        )
        panel.dispose()
    }

    // --- MULTI_CHOICE clarification card ---

    fun testMultiChoiceClarificationSubmit() = runBlocking {
        val panel = AiChatPanel(project)
        val events = MutableSharedFlow<AgentEvent>(replay = 64, extraBufferCapacity = 64)
        val gate = com.itangcent.easyapi.ai.UiClarificationGate(events)
        val sess = com.itangcent.easyapi.ai.ConversationSession(
            agent = mockAgent(),
            memory = AgentMemory(),
            events = events,
            approvals = com.itangcent.easyapi.ai.UiApprovalGate(),
            clarifications = gate
        )
        panel.bindSessionForTest(sess)

        val clar = Clarification(
            prompt = "Pick features:",
            questions = listOf(
                ClarificationQuestion(
                    "features", "Features?", QuestionKind.MULTI_CHOICE,
                    listOf(
                        QuestionOption("auth", "Auth"),
                        QuestionOption("logging", "Logging", isDefault = true),
                        QuestionOption("cache", "Cache")
                    )
                )
            )
        )
        var result: ClarificationAnswers? = null
        val waiter = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            result = gate.await(clar)
        }
        panel.renderEventForTest(AgentEvent.ClarificationRequested(clar))
        assertTrue(panel.isClarificationPendingForTest())

        assertTrue(panel.clickSubmitClarificationForTest())
        assertFalse(panel.isClarificationPendingForTest())

        assertNotNull(result)
        // The default-flagged option ("logging") should be pre-selected.
        assertTrue(
            "default option should be in answers",
            result!!.answers["features"]?.contains("logging") == true
        )

        waiter.cancel()
        panel.dispose()
    }

    // --- FREE_TEXT clarification card via Submit button ---

    fun testFreeTextClarificationSubmitViaButton() = runBlocking {
        val panel = AiChatPanel(project)
        val events = MutableSharedFlow<AgentEvent>(replay = 64, extraBufferCapacity = 64)
        val gate = com.itangcent.easyapi.ai.UiClarificationGate(events)
        val sess = com.itangcent.easyapi.ai.ConversationSession(
            agent = mockAgent(),
            memory = AgentMemory(),
            events = events,
            approvals = com.itangcent.easyapi.ai.UiApprovalGate(),
            clarifications = gate
        )
        panel.bindSessionForTest(sess)

        val clar = Clarification(
            prompt = null,
            questions = listOf(
                ClarificationQuestion("note", "Any notes?", QuestionKind.FREE_TEXT)
            )
        )
        var result: ClarificationAnswers? = null
        val waiter = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            result = gate.await(clar)
        }
        panel.renderEventForTest(AgentEvent.ClarificationRequested(clar))
        assertTrue(panel.isClarificationPendingForTest())

        // Submit without typing anything → empty answer list for that key.
        assertTrue(panel.clickSubmitClarificationForTest())
        assertFalse(panel.isClarificationPendingForTest())

        assertNotNull(result)
        // Free-text with no input → empty list (not null, not absent).
        assertTrue(
            "free-text with no input should yield empty list",
            result!!.answers["note"]?.isEmpty() == true
        )

        waiter.cancel()
        panel.dispose()
    }

    // --- multiple questions in one card ---

    fun testMultipleQuestionsInSingleCard() = runBlocking {
        val panel = AiChatPanel(project)
        val events = MutableSharedFlow<AgentEvent>(replay = 64, extraBufferCapacity = 64)
        val gate = com.itangcent.easyapi.ai.UiClarificationGate(events)
        val sess = com.itangcent.easyapi.ai.ConversationSession(
            agent = mockAgent(),
            memory = AgentMemory(),
            events = events,
            approvals = com.itangcent.easyapi.ai.UiApprovalGate(),
            clarifications = gate
        )
        panel.bindSessionForTest(sess)

        val clar = Clarification(
            prompt = "Two questions:",
            questions = listOf(
                ClarificationQuestion(
                    "scope", "Scope?", QuestionKind.SINGLE_CHOICE,
                    listOf(QuestionOption("global", "Global"), QuestionOption("project", "Project"))
                ),
                ClarificationQuestion(
                    "note", "Notes?", QuestionKind.FREE_TEXT
                )
            )
        )
        var result: ClarificationAnswers? = null
        val waiter = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            result = gate.await(clar)
        }
        panel.renderEventForTest(AgentEvent.ClarificationRequested(clar))
        assertTrue(panel.isClarificationPendingForTest())

        assertTrue(panel.clickSubmitClarificationForTest())
        assertFalse(panel.isClarificationPendingForTest())

        assertNotNull(result)
        // Both question IDs should be present in the answers map.
        assertTrue("scope answer should be present", result!!.answers.containsKey("scope"))
        assertTrue("note answer should be present", result.answers.containsKey("note"))

        waiter.cancel()
        panel.dispose()
    }
}
