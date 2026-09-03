package com.itangcent.easyapi.core.ai.tools

import com.google.gson.JsonParser
import com.itangcent.easyapi.core.ai.AiProvider
import com.itangcent.easyapi.core.ai.AiRuntimeConfig
import com.itangcent.easyapi.core.ai.agent.AgentMemory
import com.itangcent.easyapi.core.ai.agent.ApprovalGate
import com.itangcent.easyapi.core.ai.agent.KnowledgeState
import com.itangcent.easyapi.core.config.ConfigReader
import com.itangcent.easyapi.core.config.source.RuleFileResolver
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * Tests for [GetRuleContextTool] — the AI-facing entry point that turns a rule
 * key into either a compact `§keyContexts` state entry (default) or the full
 * inline profile (`expand=true`).
 *
 * The tool resolves keys through [com.itangcent.easyapi.core.rule.RuleKeyRegistry],
 * so every case here exercises the real registry rather than a stub: aliases
 * must collapse onto their canonical key, and unknown keys must be rejected
 * instead of silently producing an empty profile.
 */
class GetRuleContextToolTest : EasyApiLightCodeInsightFixtureTestCase() {

    private val tool = GetRuleContextTool()

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

    private fun call(vararg args: Pair<String, Any?>): ToolResult =
        runBlocking { tool.execute(mapOf(*args), ctx()) }

    private fun errorOf(vararg args: Pair<String, Any?>): ToolResult.Error {
        val result = call(*args)
        return result as? ToolResult.Error
            ?: throw AssertionError("expected ToolResult.Error, got $result")
    }

    private fun statefulOf(vararg args: Pair<String, Any?>): ToolResult.Stateful {
        val result = call(*args)
        return result as? ToolResult.Stateful
            ?: throw AssertionError("expected ToolResult.Stateful, got $result")
    }

    // --- parameter validation ---

    fun testMissingKeyParameterIsRejected() {
        val error = errorOf()
        assertTrue(
            "should name the missing parameter: ${error.message}",
            error.message.contains("missing required parameter: key")
        )
    }

    fun testNonStringKeyIsRejected() {
        // A non-string `key` (LLM sends a number/object) must be rejected as a
        // missing parameter, not coerced into a lookup that would fail later.
        val error = errorOf("key" to 42)
        assertTrue(error.message.contains("missing required parameter: key"))
    }

    fun testBlankKeyIsRejected() {
        val error = errorOf("key" to "   ")
        assertTrue(error.message.contains("missing required parameter: key"))
    }

    fun testUnknownKeyIsRejected() {
        val error = errorOf("key" to "no.such.key")
        assertEquals("unknown rule key: no.such.key", error.message)
    }

    fun testSurroundingWhitespaceIsTrimmedBeforeLookup() {
        val stateful = statefulOf("key" to "  field.ignore  ")
        assertEquals("field.ignore", stateful.entries.single().id)
    }

    // --- stateful (default) output ---

    fun testCanonicalKeyFilesOneEntryIntoKeyContexts() {
        val stateful = statefulOf("key" to "field.ignore")

        assertEquals(KnowledgeState.SECTION_KEY_CONTEXTS, stateful.section)
        val entry = stateful.entries.single()
        assertEquals("field.ignore", entry.id)
        assertTrue(
            "entry should be a compact one-liner, not a JSON blob: ${entry.renderedLine}",
            entry.renderedLine.startsWith("field.ignore | ")
        )
        assertTrue("entry should state its execution mode: ${entry.renderedLine}", entry.renderedLine.contains("dynamic"))
        assertTrue("entry should list refs: ${entry.renderedLine}", entry.renderedLine.contains("refs: ["))
    }

    fun testReceiptPointsAtTheObjectApiToolForSharedObjects() {
        val stateful = statefulOf("key" to "field.ignore")

        assertTrue(
            "receipt must tell the model how to fetch signatures: ${stateful.receiptNote}",
            stateful.receiptNote.contains("get_script_object_api(ids=[...])")
        )
        assertTrue(
            "receipt must count the refs: ${stateful.receiptNote}",
            stateful.receiptNote.contains("object refs: ")
        )
    }

    fun testAliasIsResolvedToCanonicalKeyAndFlaggedInTheReceipt() {
        // `doc.param` is a legacy spelling of `param.doc`. The entry is filed
        // under the canonical name so a second call with the canonical name
        // upserts (replaces) instead of appending a duplicate line, and the
        // receipt explicitly says the alias must not be authored.
        val stateful = statefulOf("key" to "doc.param")

        assertEquals("param.doc", stateful.entries.single().id)
        assertTrue(
            "receipt must name the alias: ${stateful.receiptNote}",
            stateful.receiptNote.contains("requested as 'doc.param'")
        )
        assertTrue(
            "receipt must warn against authoring the alias: ${stateful.receiptNote}",
            stateful.receiptNote.contains("write the canonical key 'param.doc' in the rule file")
        )
    }

    fun testCanonicalKeyOmitsTheAliasClause() {
        val stateful = statefulOf("key" to "param.doc")

        assertFalse(
            "no alias was used, so the receipt must not mention one: ${stateful.receiptNote}",
            stateful.receiptNote.contains("backward compatibility")
        )
    }

    fun testStaticConfigurationKeyHasNoRefs() {
        // markdown.template is read as static config: nothing is scripted, so
        // the rendered line must omit the refs part entirely rather than
        // rendering an empty bracket.
        val stateful = statefulOf("key" to "markdown.template")

        val line = stateful.entries.single().renderedLine
        assertTrue("static key should state its mode: $line", line.contains("static-configuration"))
        assertFalse("static key has no object refs: $line", line.contains("refs:"))
        assertFalse(
            "no refs means nothing to fetch: ${stateful.receiptNote}",
            stateful.receiptNote.contains("object refs:")
        )
    }

    // --- expand=true (legacy inline profile) ---

    fun testExpandInlinesTheReferencedObjects() {
        val result = call("key" to "field.ignore", "expand" to true)
        val text = result as? ToolResult.Text
            ?: throw AssertionError("expand=true should return ToolResult.Text, got $result")

        val json = JsonParser.parseString(text.value).asJsonObject
        assertEquals("field.ignore", json.get("key").asString)
        assertEquals("dynamic", json.get("executionMode").asString)

        val objects = json.getAsJsonArray("objects")
        assertTrue("expanded profile should carry its objects inline", objects.size() > 0)

        // Inline objects keep full method signatures — that is the whole point
        // of the legacy mode (the stateful path degrades them to ids).
        val field = objects.first { it.asJsonObject.get("id").asString == "field" }
        val methods = field.asJsonObject.getAsJsonArray("methods")
        assertTrue(
            "expanded object should list callable methods, not just a count",
            methods.any { it.asJsonObject.get("name").asString == "type" }
        )
    }

    fun testExpandResolvesAliasesToo() {
        val result = call("key" to "doc.param", "expand" to true)
        val text = result as? ToolResult.Text
            ?: throw AssertionError("expected ToolResult.Text, got $result")

        val json = JsonParser.parseString(text.value).asJsonObject
        assertEquals("the expanded profile is still filed under the canonical key", "param.doc", json.get("key").asString)
    }

    fun testExpandAcceptsOnlyABoolean() {
        // A string "true" from the model is not a boolean: it must fall back to
        // the stateful path rather than being coerced into legacy mode.
        val result = call("key" to "field.ignore", "expand" to "true")
        assertTrue(
            "non-boolean expand should not switch to the legacy inline shape",
            result is ToolResult.Stateful
        )
    }

    private class NoOpApprovalGate : ApprovalGate {
        override suspend fun await(toolName: String, args: Map<String, Any?>): Boolean = true
    }
}
