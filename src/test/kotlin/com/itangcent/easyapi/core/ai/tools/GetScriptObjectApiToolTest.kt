package com.itangcent.easyapi.core.ai.tools

import com.itangcent.easyapi.core.ai.AiProvider
import com.itangcent.easyapi.core.ai.AiRuntimeConfig
import com.itangcent.easyapi.core.ai.agent.AgentMemory
import com.itangcent.easyapi.core.ai.agent.ApprovalGate
import com.itangcent.easyapi.core.ai.agent.KnowledgeState
import com.itangcent.easyapi.core.config.ConfigReader
import com.itangcent.easyapi.core.config.source.RuleFileResolver
import com.itangcent.easyapi.core.rule.context.RuleKeyScriptProfiler
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Tests for [GetScriptObjectApiTool] — the only writer of `§objects`.
 *
 * `get_rule_context` hands the model object *ids*; this tool is what resolves
 * them into full method signatures. Because it is the single writer, its
 * behaviour on partial misses matters: a batch where some ids are unknown must
 * still deliver the known ones rather than failing the whole call.
 */
class GetScriptObjectApiToolTest : EasyApiLightCodeInsightFixtureTestCase() {

    private val tool = GetScriptObjectApiTool()

    private fun ctx(): ToolContext = ToolContext(
        project = project,
        configReader = ConfigReader.getInstance(project),
        aiSettings = AiRuntimeConfig(
            provider = AiProvider.OPENAI,
            baseUrl = "", apiKey = "", model = "",
            requestTimeoutSec = 30, maxRequests = 8
        ),
        ruleFileResolver = RuleFileResolver(project),
        workingMemory = AgentMemory(),
        approvals = NoOpApprovalGate(),
        events = MutableSharedFlow(extraBufferCapacity = 64)
    )

    private fun call(ids: Any?): ToolResult = runBlocking { tool.execute(mapOf("ids" to ids), ctx()) }

    /**
     * The ids the tool can actually resolve: the profiler's static object
     * dictionary (every it-context object, common helper, and wrapped
     * additional binding), independent of which rule keys exist or are
     * enabled. This is the ground truth every assertion below is written
     * against (instead of hard-coding "logger", which would couple the test
     * to registry ordering).
     */
    private fun resolvableIds(): List<String> =
        RuleKeyScriptProfiler.allScriptObjects().map { it.id }

    private fun statefulOf(ids: Any?): ToolResult.Stateful {
        val result = call(ids)
        return result as? ToolResult.Stateful
            ?: throw AssertionError("expected ToolResult.Stateful, got $result")
    }

    private fun errorOf(ids: Any?): ToolResult.Error {
        val result = call(ids)
        return result as? ToolResult.Error
            ?: throw AssertionError("expected ToolResult.Error, got $result")
    }

    // --- parameter validation ---

    fun testMissingIdsIsRejected() {
        val result = runBlocking { tool.execute(emptyMap(), ctx()) }
        val error = result as? ToolResult.Error
            ?: throw AssertionError("expected ToolResult.Error, got $result")
        assertTrue(
            "should name the missing parameter: ${error.message}",
            error.message.contains("missing or invalid parameter: ids")
        )
    }

    fun testNonCollectionIdsIsRejected() {
        val error = errorOf(42)
        assertTrue(error.message.contains("missing or invalid parameter: ids"))
    }

    fun testEmptyIdsIsRejected() {
        val error = errorOf(emptyList<String>())
        assertEquals("ids must not be empty", error.message)
    }

    fun testAllNonStringEntriesAreRejectedAsEmpty() {
        // A list of numbers filters down to nothing — reported as empty rather
        // than being treated as "no filter" (which would dump every object).
        val error = errorOf(listOf(1, 2, 3))
        assertEquals("ids must not be empty", error.message)
    }

    // --- id formats ---

    fun testCommaSeparatedStringIsAccepted() {
        // Models frequently send a bare string instead of an array.
        val ids = resolvableIds().take(2)
        val stateful = statefulOf(ids.joinToString(","))

        assertEquals(ids, stateful.entries.map { it.id })
    }

    fun testCommaSeparatedStringTrimsAndDropsBlanks() {
        val first = resolvableIds().first()
        val stateful = statefulOf("  $first , , ")

        assertEquals("blank segments must be filtered out", listOf(first), stateful.entries.map { it.id })
    }

    // --- resolution ---

    fun testKnownIdsAreFiledIntoTheObjectsSection() {
        val id = resolvableIds().first()
        val stateful = statefulOf(listOf(id))

        assertEquals(KnowledgeState.SECTION_OBJECTS, stateful.section)
        val entry = stateful.entries.single()
        assertEquals(id, entry.id)
        assertTrue(
            "entry should be a compact one-liner: ${entry.renderedLine}",
            entry.renderedLine.startsWith("$id | ")
        )
        assertEquals("1 object APIs added/updated", stateful.receiptNote)
    }

    fun testPartiallyUnknownIdsStillDeliverTheKnownOnes() {
        // A batch miss must degrade, not fail: the model asked for several
        // objects and misspelled one — it should still get the good ones
        // instead of losing the whole call. The receipt names the ignored id
        // so the model can correct it in one round trip.
        val known = resolvableIds().first()
        val stateful = statefulOf(listOf(known, "definitely.not.an.object"))

        assertEquals("only the resolvable id should be delivered", listOf(known), stateful.entries.map { it.id })
        assertEquals(
            "1 object APIs added/updated; unknown ids ignored: definitely.not.an.object",
            stateful.receiptNote
        )
    }

    fun testItContextObjectsResolveRegardlessOfEnabledKeys() {
        // F1 regression (live-run 2026-09-03): the dictionary must not depend
        // on which rule keys are registered/enabled — the model asked for
        // class/method/field/parameter and only 2 resolved. Every it-context
        // and wrapped additional object id must resolve in any project state.
        val itContextIds = listOf("class", "method", "field", "parameter", "request", "response", "api")
        val stateful = statefulOf(itContextIds)

        assertEquals(
            "every id must resolve against the static dictionary: ${stateful.receiptNote}",
            itContextIds,
            stateful.entries.map { it.id }
        )
    }

    fun testAllUnknownIdsReportTheKnownOptions() {
        // Nothing resolved: tell the model what it could have asked for,
        // otherwise it can only guess ids.
        val error = errorOf(listOf("nope.one", "nope.two"))

        assertTrue(error.message.startsWith("all requested object ids are unknown: nope.one,nope.two"))
        val known = resolvableIds().sorted().joinToString(",")
        assertTrue(
            "the error should list the resolvable ids: ${error.message}",
            error.message.contains(known)
        )
    }

    private class NoOpApprovalGate : ApprovalGate {
        override suspend fun await(toolName: String, args: Map<String, Any?>): Boolean = true
    }
}
