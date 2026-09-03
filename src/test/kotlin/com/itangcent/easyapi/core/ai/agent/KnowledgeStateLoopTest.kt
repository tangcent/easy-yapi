package com.itangcent.easyapi.core.ai.agent

import com.itangcent.easyapi.core.ai.AiMessage
import com.itangcent.easyapi.core.ai.AiProvider
import com.itangcent.easyapi.core.ai.AiRuntimeConfig
import com.itangcent.easyapi.core.ai.AiToolCall
import com.itangcent.easyapi.core.ai.tools.AiTool
import com.itangcent.easyapi.core.ai.tools.ToolContext
import com.itangcent.easyapi.core.ai.tools.ToolKind
import com.itangcent.easyapi.core.ai.tools.ToolRegistry
import com.itangcent.easyapi.core.ai.tools.ToolResult
import com.itangcent.easyapi.core.config.source.RuleFileResolver
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking

/**
 * Loop-level contract tests for the external [KnowledgeState] (spec §3 / T3,
 * T4, T5, T8).
 *
 * These assert the properties that make duplicate knowledge injection
 * *structurally impossible*, not merely unlikely:
 *
 * - the state block is injected at request time and never stored in
 *   [AgentMemory.messages];
 * - a repeated perception call produces a `noChange` receipt and leaves the
 *   state block byte-identical;
 * - `tool_call_id` → `ToolResult` pairing survives the Stateful → receipt
 *   conversion;
 * - an enablement change between turns invalidates the stale sections and
 *   rebuilds the enablement-filtered L0 indexes.
 */
class KnowledgeStateLoopTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var aiService: FakeAIService
    private lateinit var memory: AgentMemory
    private lateinit var ctx: ToolContext

    override fun setUp() {
        super.setUp()
        aiService = FakeAIService()
        memory = AgentMemory()
        ctx = ToolContext(
            project = project,
            configReader = com.itangcent.easyapi.core.config.ConfigReader.getInstance(project),
            aiSettings = AiRuntimeConfig(
                provider = AiProvider.OPENAI,
                baseUrl = "", apiKey = "", model = "",
                requestTimeoutSec = 30,
                maxRequests = 5
            ),
            ruleFileResolver = RuleFileResolver(project),
            workingMemory = memory,
            approvals = FakeApprovalGate(),
            events = MutableSharedFlow(extraBufferCapacity = 64)
        )
    }

    // --- T3: repeated perception call ---

    fun testRepeatedCatalogCallAddsNoDuplicateKnowledge() = runBlocking {
        val agent = RuleAuthoringAgent(
            aiService,
            ToolRegistry(listOf(CatalogFakeTool())),
            ctx,
            MutableSharedFlow(extraBufferCapacity = 64)
        )

        // The second call carries different arguments, so the LoopGuard's
        // debounce layer does not intercept it and the upsert really runs —
        // that is the layer this test is about. (An argument-identical repeat
        // is debounced before dispatch; see
        // testIdenticalRepeatIsDebouncedBeforeDispatch.)
        aiService.enqueueToolCalls(AiToolCall("c1", "list_rule_keys", "{}"))
        aiService.enqueueToolCalls(AiToolCall("c2", "list_rule_keys", """{"scope":"general"}"""))
        aiService.enqueueText("done")

        agent.runTurn("list the keys", memory)

        val requests = aiService.requests()
        assertEquals(3, requests.size)

        // The transcript itself never holds the knowledge: no System message
        // in memory.messages carries the state block.
        assertTrue(
            "memory.messages must not contain the state block",
            memory.messages.filterIsInstance<AiMessage.System>()
                .none { it.content.contains(STATE_HEADER) }
        )

        // Nothing in the transcript carries the catalog content — only the
        // injected System block does.
        val transcripts = memory.messages.filterIsInstance<AiMessage.ToolResult>()
        assertEquals(2, transcripts.size)
        transcripts.forEach {
            assertFalse(
                "tool receipt must not carry the full catalog line: ${it.content}",
                it.content.contains(CATALOG_MARKER)
            )
        }

        // Request 1 has no state block yet (nothing perceived); requests 2 and
        // 3 carry exactly one each, identical.
        assertEquals(0, stateBlocks(requests[0].messages).size)
        assertEquals(1, stateBlocks(requests[1].messages).size)
        assertEquals(1, stateBlocks(requests[2].messages).size)
        assertEquals(stateBlocks(requests[1].messages).single(), stateBlocks(requests[2].messages).single())

        // First receipt reports additions, second reports "nothing changed".
        val firstReceipt = receiptBody(transcripts[0])
        val secondReceipt = receiptBody(transcripts[1])
        assertTrue("first receipt: $firstReceipt", firstReceipt.contains("\"added\":[\"alpha\",\"beta\"]"))
        assertTrue("second receipt: $secondReceipt", secondReceipt.contains("\"noChange\":true"))
        assertTrue("second receipt: $secondReceipt", secondReceipt.contains("\"unchanged\":2"))

        // Exactly one line per key in the rendered state.
        val rendered = memory.knowledgeState.render()
        assertEquals(1, countOccurrences(rendered, CATALOG_MARKER))
        assertEquals(1, countOccurrences(rendered, "beta | general | second summary"))
    }

    /**
     * Last resort of the three dedup layers: an argument-identical repeat is
     * rejected by the [LoopGuard] before the tool runs at all, so no second
     * receipt and no second upsert can happen.
     */
    fun testIdenticalRepeatIsDebouncedBeforeDispatch() = runBlocking {
        val agent = RuleAuthoringAgent(
            aiService,
            ToolRegistry(listOf(CatalogFakeTool())),
            ctx,
            MutableSharedFlow(extraBufferCapacity = 64)
        )

        aiService.enqueueToolCalls(AiToolCall("c1", "list_rule_keys", "{}"))
        aiService.enqueueToolCalls(AiToolCall("c2", "list_rule_keys", "{}"))
        aiService.enqueueText("done")

        agent.runTurn("list the keys", memory)

        val receipts = memory.messages.filterIsInstance<AiMessage.ToolResult>()
        assertEquals(2, receipts.size)
        assertTrue(
            "second call should be debounced: ${receipts[1].content}",
            receipts[1].content.contains("Duplicate call")
        )
        assertEquals(2, memory.knowledgeState.entries(KnowledgeState.SECTION_KEYS).size)
    }

    // --- T4: shared objects are rendered once ---

    fun testRepeatedObjectApiCallKeepsOneEntryPerObject() = runBlocking {
        val agent = RuleAuthoringAgent(
            aiService,
            ToolRegistry(listOf(ObjectApiFakeTool())),
            ctx,
            MutableSharedFlow(extraBufferCapacity = 64)
        )

        // The second call asks for a superset (session is unknown to the fake
        // and is filtered out), so it dispatches and re-upserts the same two
        // objects — exactly the "several keys share logger/request" case.
        aiService.enqueueToolCalls(AiToolCall("c1", "get_script_object_api", """{"ids":["logger","request"]}"""))
        aiService.enqueueToolCalls(AiToolCall("c2", "get_script_object_api", """{"ids":["logger","request","session"]}"""))
        aiService.enqueueText("done")

        agent.runTurn("what can logger do", memory)

        // Two calls, two objects — but only one entry per object.
        assertEquals(2, memory.knowledgeState.entries(KnowledgeState.SECTION_OBJECTS).size)
        val rendered = memory.knowledgeState.render()
        assertEquals(1, countOccurrences(rendered, "logger | Logger |"))
        assertEquals(1, countOccurrences(rendered, "request | HttpRequestWrapper |"))
        // Version stays at 1: the second call changed nothing.
        assertEquals(1, memory.knowledgeState.sectionVersion(KnowledgeState.SECTION_OBJECTS))
        val receipts = memory.messages.filterIsInstance<AiMessage.ToolResult>()
        assertTrue(
            "second receipt should report noChange: ${receiptBody(receipts[1])}",
            receiptBody(receipts[1]).contains("\"noChange\":true")
        )
    }

    // --- T5: tool_call pairing survives the receipt conversion ---

    fun testToolCallPairingHoldsAcrossStatefulReceipts() = runBlocking {
        val agent = RuleAuthoringAgent(
            aiService,
            ToolRegistry(listOf(CatalogFakeTool(), ObjectApiFakeTool())),
            ctx,
            MutableSharedFlow(extraBufferCapacity = 64)
        )

        aiService.enqueueToolCalls(
            AiToolCall("c1", "list_rule_keys", "{}"),
            AiToolCall("c2", "get_script_object_api", """{"ids":["logger"]}""")
        )
        aiService.enqueueText("done")

        agent.runTurn("perceive twice", memory)

        aiService.requests().forEachIndexed { index, request ->
            assertPairingHolds("request $index", request.messages)
        }
    }

    // --- T8: enablement change invalidates the stale knowledge ---

    fun testEnablementChangeInvalidatesKeysAndRebuildsIndexes() = runBlocking {
        val agent = RuleAuthoringAgent(
            aiService,
            ToolRegistry(listOf(CatalogFakeTool())),
            ctx,
            MutableSharedFlow(extraBufferCapacity = 64)
        )

        // Turn 1: postman enabled — the agent catalogs the keys.
        aiService.enqueueToolCalls(AiToolCall("c1", "list_rule_keys", "{}"))
        aiService.enqueueText("ok")
        agent.runTurn("list the keys", memory, ambient(enabledChannels = listOf("postman")))
        assertEquals(2, memory.knowledgeState.entries(KnowledgeState.SECTION_KEYS).size)

        val openingBefore = openingBlock(aiService.requests().first().messages)

        // Turn 2: the user switches to markdown only — no tool call, so any
        // surviving §keys entry is proof the invalidation did not fire.
        aiService.enqueueText("ok")
        agent.runTurn("now what", memory, ambient(enabledChannels = listOf("markdown")))

        assertTrue(
            "§keys must be invalidated when the enabled channels change",
            memory.knowledgeState.entries(KnowledgeState.SECTION_KEYS).isEmpty()
        )

        // The L0 indexes are enablement-filtered too — they are rebuilt in place.
        val openingAfter = openingBlock(aiService.requests().last().messages)
        assertEquals("opening block should still be 4 System messages", 4, openingAfter.size)
        assertFalse(
            "the L0 index must be rebuilt for the new enablement",
            openingBefore == openingAfter
        )
        assertEquals(
            "the rebuilt opening block must match a fresh build for the new ambient",
            SystemPromptBuilder.build(EntryPath.REACTIVE, ambient(enabledChannels = listOf("markdown")))
                .map { (it as AiMessage.System).content },
            openingAfter
        )
    }

    // --- helpers ---

    private fun ambient(
        enabledChannels: List<String> = emptyList(),
        enabledFormats: List<String> = emptyList(),
        frameworkHints: List<String> = emptyList()
    ): Ambient = Ambient(
        projectName = "demo",
        editingRuleFile = null,
        existingRuleFiles = emptyList(),
        enabledChannels = enabledChannels,
        enabledFormats = enabledFormats,
        frameworkHints = frameworkHints
    )

    /**
     * The receipt JSON, with the outer `{"value":"…"}` escaping undone so the
     * assertions can match the inner `knowledge` object literally.
     */
    private fun receiptBody(message: AiMessage.ToolResult): String =
        message.content.replace("\\\"", "\"")

    private fun stateBlocks(messages: List<AiMessage>): List<String> =
        messages.filterIsInstance<AiMessage.System>()
            .map { it.content }
            .filter { it.contains(STATE_HEADER) }

    /**
     * The leading [AgentMemory.openingSystemCount] System messages — the base
     * prompt plus the L0 indexes. (The per-turn ambient is also a System
     * message, so "leading System" alone is not a safe boundary.)
     */
    private fun openingBlock(messages: List<AiMessage>): List<String> =
        messages.take(memory.openingSystemCount)
            .filterIsInstance<AiMessage.System>()
            .map { it.content }

    /**
     * Every assistant `tool_calls` id must be followed by exactly one
     * `ToolResult` with that id — the API rejects a request that breaks it.
     */
    private fun assertPairingHolds(label: String, messages: List<AiMessage>) {
        var i = 0
        while (i < messages.size) {
            val message = messages[i]
            val calls = (message as? AiMessage.Assistant)?.toolCalls
            if (calls.isNullOrEmpty()) {
                i++
                continue
            }
            val expected = calls.map { it.id }
            val actual = messages.drop(i + 1)
                .takeWhile { it is AiMessage.ToolResult }
                .filterIsInstance<AiMessage.ToolResult>()
                .map { it.toolCallId }
            assertEquals("$label: tool_call ids must be answered in order", expected, actual)
            i += 1 + actual.size
        }
    }

    private fun countOccurrences(haystack: String, needle: String): Int =
        haystack.split(needle).size - 1

    /** Writes two compact directory lines into `§keys` (the list_rule_keys shape). */
    private class CatalogFakeTool : AiTool {
        override val name = "list_rule_keys"
        override val description = "List available rule keys."
        override val kind = ToolKind.PERCEPTION
        override val parametersSchema: Map<String, Any?> = emptyMap()
        override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult =
            ToolResult.Stateful(
                section = KnowledgeState.SECTION_KEYS,
                entries = listOf(
                    KnowledgeState.Entry("alpha", "alpha | general | $CATALOG_MARKER"),
                    KnowledgeState.Entry("beta", "beta | general | second summary")
                ),
                receiptNote = "2 keys catalogued"
            )
    }

    /** Writes two method-signature lines into `§objects` (the single-writer section). */
    private class ObjectApiFakeTool : AiTool {
        override val name = "get_script_object_api"
        override val description = "Fetch script object method signatures."
        override val kind = ToolKind.PERCEPTION
        override val parametersSchema: Map<String, Any?> = emptyMap()
        override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
            val ids = (args["ids"] as? List<*>).orEmpty().filterIsInstance<String>()
            val known = mapOf(
                "logger" to "logger | Logger | EasyAPI console logger | methods: info(msg):void, warn(msg):void",
                "request" to "request | HttpRequestWrapper | the outgoing request | methods: header(n):String"
            )
            val entries = ids.filter { it in known }
                .map { KnowledgeState.Entry(it, known[it]!!) }
            return ToolResult.Stateful(
                section = KnowledgeState.SECTION_OBJECTS,
                entries = entries,
                receiptNote = "${entries.size} object APIs added/updated"
            )
        }
    }

    private companion object {
        const val STATE_HEADER = "=== Knowledge State ==="
        const val CATALOG_MARKER = "first summary"
    }
}
