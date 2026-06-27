package com.itangcent.easyapi.ai.tools

import com.itangcent.easyapi.ai.AiProvider
import com.itangcent.easyapi.ai.AiSettings
import com.itangcent.easyapi.ai.agent.AgentMemory
import com.itangcent.easyapi.ai.agent.ClarificationAnswers
import com.itangcent.easyapi.ai.agent.ClarificationGate
import com.itangcent.easyapi.ai.agent.FakeApprovalGate
import com.itangcent.easyapi.ai.agent.FakeClarificationGate
import com.itangcent.easyapi.ai.agent.QuestionKind
import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.config.source.RuleFileResolver
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking

/**
 * Tests for [AskClarificationTool].
 */
class AskClarificationToolTest : EasyApiLightCodeInsightFixtureTestCase() {

    private val tool = AskClarificationTool()

    private fun ctx(gate: ClarificationGate): ToolContext = ToolContext(
        project = project,
        configReader = ConfigReader.getInstance(project),
        aiSettings = AiSettings(
            provider = AiProvider.OLLAMA,
            baseUrl = "", apiKey = "", model = "llama3",
            requestTimeoutSec = 30, maxRequests = 8
        ),
        ruleFileResolver = RuleFileResolver(project),
        workingMemory = AgentMemory(),
        approvals = FakeApprovalGate(),
        clarifications = gate
    )

    fun testFormatsAnswersAndParsesKinds() = runBlocking {
        val gate = FakeClarificationGate(
            ClarificationAnswers(
                mapOf(
                    "scope" to listOf("controllers"),
                    "fmt" to listOf("json", "xml"),
                    "note" to listOf("be careful")
                )
            )
        )
        val args = mapOf(
            "prompt" to "Could you clarify:",
            "questions" to listOf(
                mapOf(
                    "id" to "scope", "text" to "Scope?", "kind" to "single_choice",
                    "options" to listOf(
                        mapOf("value" to "global", "label" to "Globally"),
                        mapOf("value" to "controllers", "label" to "Specific controllers")
                    )
                ),
                mapOf(
                    "id" to "fmt", "text" to "Formats?", "kind" to "multi_choice",
                    "options" to listOf(mapOf("value" to "json", "label" to "JSON"))
                ),
                mapOf("id" to "note", "text" to "Anything else?", "kind" to "free_text")
            )
        )

        val result = tool.execute(args, ctx(gate))

        assertTrue("result should be Text", result is ToolResult.Text)
        val text = (result as ToolResult.Text).value
        assertTrue(text.contains("Scope? -> controllers"))
        assertTrue(text.contains("Formats? -> json, xml"))
        assertTrue(text.contains("Anything else? -> be careful"))

        assertTrue(gate.wasConsulted)
        val qs = gate.lastRequest!!.questions
        assertEquals(QuestionKind.SINGLE_CHOICE, qs[0].kind)
        assertEquals(QuestionKind.MULTI_CHOICE, qs[1].kind)
        assertEquals(QuestionKind.FREE_TEXT, qs[2].kind)
        assertEquals("Could you clarify:", gate.lastRequest!!.prompt)
    }

    fun testEmptyQuestionsYieldsError() = runBlocking {
        val result = tool.execute(mapOf("questions" to emptyList<Any?>()), ctx(FakeClarificationGate()))
        assertTrue("empty questions should be an Error", result is ToolResult.Error)
    }

    fun testInfersKindFromOptionsAndAutoAssignsId() = runBlocking {
        val gate = FakeClarificationGate()
        val args = mapOf(
            "questions" to listOf(
                // no kind, but options present → single_choice
                mapOf("text" to "Pick one", "options" to listOf(mapOf("value" to "a", "label" to "A"))),
                // no kind, no options → free_text
                mapOf("text" to "Free form")
            )
        )

        tool.execute(args, ctx(gate))

        val qs = gate.lastRequest!!.questions
        assertEquals(QuestionKind.SINGLE_CHOICE, qs[0].kind)
        assertEquals(QuestionKind.FREE_TEXT, qs[1].kind)
        // auto-assigned ids when omitted
        assertEquals("q1", qs[0].id)
        assertEquals("q2", qs[1].id)
    }

    fun testSchemaDeclaresQuestionsRequired() {
        @Suppress("UNCHECKED_CAST")
        val required = tool.parametersSchema["required"] as List<String>
        assertTrue(required.contains("questions"))
        assertEquals(ToolKind.PERCEPTION, tool.kind)
        assertFalse(tool.requiresApproval)
    }
}
