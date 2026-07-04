package com.itangcent.easyapi.ai.tools

import com.itangcent.easyapi.ai.AiRuntimeConfig
import com.itangcent.easyapi.ai.agent.AgentMemory
import com.itangcent.easyapi.ai.agent.ApprovalGate
import com.itangcent.easyapi.config.source.RuleFileResolver
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking

class ToolRegistryTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var fakeApprovalGate: FakeApprovalGate
    private lateinit var ctx: ToolContext

    override fun setUp() {
        super.setUp()
        fakeApprovalGate = FakeApprovalGate()
        ctx = ToolContext(
            project = project,
            configReader = com.itangcent.easyapi.config.ConfigReader.getInstance(project),
            aiSettings = AiRuntimeConfig(
                provider = com.itangcent.easyapi.ai.AiProvider.OPENAI,
                baseUrl = "", apiKey = "", model = "",
                requestTimeoutSec = 30, maxRequests = 8
            ),
            ruleFileResolver = RuleFileResolver(project),
            workingMemory = AgentMemory(),
            approvals = fakeApprovalGate
        )
    }

    fun testSchemasShape() {
        val registry = ToolRegistry(listOf(FakePerceptionTool(), FakeActionTool()))
        val schemas = registry.schemas()
        assertEquals(2, schemas.size)
        assertEquals("fake_perception", schemas[0].name)
        assertEquals("fake_action", schemas[1].name)
        // description + parametersJsonSchema present
        assertTrue(schemas[0].description.isNotEmpty())
        assertTrue(schemas[0].parametersJsonSchema.isNotEmpty())
    }

    fun testKindOfReturnsRightKind() {
        val registry = ToolRegistry(listOf(FakePerceptionTool(), FakeActionTool()))
        assertEquals(ToolKind.PERCEPTION, registry.kindOf("fake_perception"))
        assertEquals(ToolKind.ACTION, registry.kindOf("fake_action"))
        assertNull(registry.kindOf("nonexistent"))
    }

    fun testDispatchPerceptionToolImmediately() {
        val registry = ToolRegistry(listOf(FakePerceptionTool()))
        val result = runBlocking { registry.dispatch("fake_perception", emptyMap(), ctx) }
        assertEquals("perceived", (result as ToolResult.Text).value)
        assertFalse("perception tools must not request approval", fakeApprovalGate.wasConsulted)
    }

    fun testDispatchActionToolAwaitsApproval() {
        fakeApprovalGate.shouldApprove = true
        val registry = ToolRegistry(listOf(FakeActionTool()))
        val result = runBlocking { registry.dispatch("fake_action", emptyMap(), ctx) }
        assertEquals("acted", (result as ToolResult.Text).value)
        assertTrue("action tools must consult the approval gate", fakeApprovalGate.wasConsulted)
    }

    fun testDispatchActionToolRejectedReturnsError() {
        fakeApprovalGate.shouldApprove = false
        val registry = ToolRegistry(listOf(FakeActionTool()))
        val result = runBlocking { registry.dispatch("fake_action", emptyMap(), ctx) }
        assertTrue("rejected action should be Error", result is ToolResult.Error)
        assertTrue(fakeApprovalGate.wasConsulted)
    }

    fun testDispatchUnknownToolReturnsError() {
        val registry = ToolRegistry(emptyList())
        val result = runBlocking { registry.dispatch("nope", emptyMap(), ctx) }
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("Unknown tool"))
    }

    fun testDispatchStagingActionSkipsApproval() {
        val registry = ToolRegistry(listOf(FakeStagingActionTool()))
        val result = runBlocking { registry.dispatch("fake_staging", emptyMap(), ctx) }
        assertEquals("staged", (result as ToolResult.Text).value)
        assertFalse("staging actions must not require approval", fakeApprovalGate.wasConsulted)
    }

    // --- Fakes ---

    private class FakePerceptionTool : AiTool {
        override val name = "fake_perception"
        override val description = "A fake perception tool for testing."
        override val kind = ToolKind.PERCEPTION
        override val parametersSchema: Map<String, Any?> = emptyMap()
        override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult =
            ToolResult.Text("perceived")
    }

    private class FakeActionTool : AiTool {
        override val name = "fake_action"
        override val description = "A fake action tool for testing."
        override val kind = ToolKind.ACTION
        override val parametersSchema: Map<String, Any?> = emptyMap()
        override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult =
            ToolResult.Text("acted")
    }

    private class FakeStagingActionTool : AiTool {
        override val name = "fake_staging"
        override val description = "A staging action that doesn't need approval."
        override val kind = ToolKind.ACTION
        override val requiresApproval = false
        override val parametersSchema: Map<String, Any?> = emptyMap()
        override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult =
            ToolResult.Text("staged")
    }

    private class FakeApprovalGate : ApprovalGate {
        var shouldApprove = false
        var wasConsulted = false
        override suspend fun await(toolName: String, args: Map<String, Any?>): Boolean {
            wasConsulted = true
            return shouldApprove
        }
    }
}
