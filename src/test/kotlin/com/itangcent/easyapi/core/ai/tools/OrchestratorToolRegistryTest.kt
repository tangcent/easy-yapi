package com.itangcent.easyapi.core.ai.tools

import com.itangcent.easyapi.core.ai.AiToolSpec
import com.itangcent.easyapi.core.ai.agent.FakeAIService
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.Assert

/**
 * Tests the Phase-3 tool-set split (design §3.5 / FR-3.2, FR-3.3, D6 / T3.12).
 *
 * The orchestrator and sub-agent each get a restricted tool set so roles
 * cannot be crossed at the LLM level:
 *
 * - **Orchestrator** — `{ update_task, run_sub_agent, propose_rule_content }`.
 *   No perception tools (no `find_classes_by_annotation`, no
 *   `get_detection_prompt`); the orchestrator never touches PSI directly.
 *   No `report_findings` (sub-agent-only terminal action).
 * - **Sub-agent** — `{ find_classes_by_annotation, find_classes_by_supertype,
 *   get_psi_class_info, get_rule_detail, get_rule_context,
 *   get_script_object_api, list_rule_keys, report_findings }`. No
 *   `propose_rule_content`, no `run_sub_agent` — the sub-agent cannot recurse
 *   and cannot stage final rule content.
 *
 * `create_task_list` is intentionally absent from both sets: the task list
 * is seeded by the caller (FR-2.4), and sub-agents don't manage the
 * orchestrator's task list.
 */
class OrchestratorToolRegistryTest : EasyApiLightCodeInsightFixtureTestCase() {

    /**
     * FR-3.2 / FR-3.3 / T3.12 — the orchestrator's `ToolRegistry.schemas()`
     * MUST NOT advertise any perception tool. The orchestrator coordinates;
     * sub-agents perceive.
     *
     * Asserts neither `find_classes_by_annotation` nor `get_detection_prompt`
     * appear in the orchestrator's advertised schemas (representative
     * perception tools — the full set is checked by [testOrchestratorAdvertisesOnlyCoordinationTools]).
     */
    fun testOrchestratorDoesNotExposePerceptionTools() {
        val orchestratorTools = ToolRegistry(
            orchestratorToolRegistry(FakeAIService(), ToolRegistry(subAgentToolRegistry()))
        )
        val names = orchestratorTools.schemas().map { it.name }

        Assert.assertFalse(
            "orchestrator must not expose find_classes_by_annotation: $names",
            names.contains("find_classes_by_annotation")
        )
        Assert.assertFalse(
            "orchestrator must not expose get_detection_prompt: $names",
            names.contains("get_detection_prompt")
        )
    }

    /**
     * FR-3.2 / FR-3.3 / T3.12 — the sub-agent's `ToolRegistry.schemas()`
     * MUST NOT advertise `propose_rule_content` or `run_sub_agent`. The
     * sub-agent cannot stage final rule content and cannot recurse.
     */
    fun testSubAgentDoesNotExposeOrchestratorTools() {
        val subAgentTools = ToolRegistry(subAgentToolRegistry())
        val names = subAgentTools.schemas().map { it.name }

        Assert.assertFalse(
            "sub-agent must not expose propose_rule_content: $names",
            names.contains("propose_rule_content")
        )
        Assert.assertFalse(
            "sub-agent must not expose run_sub_agent: $names",
            names.contains("run_sub_agent")
        )
    }

    /**
     * Full set assertion — the orchestrator advertises EXACTLY the three
     * coordination tools and nothing else.
     *
     * Catches a regression where a perception tool is accidentally added
     * to the orchestrator's set (e.g. via a copy-paste from
     * [standardRuleTools]).
     */
    fun testOrchestratorAdvertisesOnlyCoordinationTools() {
        val orchestratorTools = ToolRegistry(
            orchestratorToolRegistry(FakeAIService(), ToolRegistry(subAgentToolRegistry()))
        )
        val names = orchestratorTools.schemas().map { it.name }

        Assert.assertEquals(
            "orchestrator should advertise exactly 3 tools: $names",
            3, names.size
        )
        Assert.assertTrue(
            "orchestrator should advertise update_task: $names",
            names.contains("update_task")
        )
        Assert.assertTrue(
            "orchestrator should advertise run_sub_agent: $names",
            names.contains("run_sub_agent")
        )
        Assert.assertTrue(
            "orchestrator should advertise propose_rule_content: $names",
            names.contains("propose_rule_content")
        )
    }

    /**
     * Full set assertion — the sub-agent advertises EXACTLY six perception
     * tools + `report_findings`.
     *
     * Catches a regression where an orchestrator tool is accidentally
     * added to the sub-agent's set.
     */
    fun testSubAgentAdvertisesOnlyPerceptionAndReportFindings() {
        val subAgentTools = ToolRegistry(subAgentToolRegistry())
        val names = subAgentTools.schemas().map { it.name }

        Assert.assertEquals(
            "sub-agent should advertise exactly 8 tools: $names",
            8, names.size
        )
        // Perception tools.
        Assert.assertTrue(names.contains("find_classes_by_annotation"))
        Assert.assertTrue(names.contains("find_classes_by_supertype"))
        Assert.assertTrue(names.contains("get_psi_class_info"))
        Assert.assertTrue(names.contains("get_rule_detail"))
        Assert.assertTrue(names.contains("get_rule_context"))
        Assert.assertTrue(names.contains("get_script_object_api"))
        Assert.assertTrue(names.contains("list_rule_keys"))
        // Sub-agent terminal action.
        Assert.assertTrue(names.contains("report_findings"))
    }

    /**
     * `create_task_list` is intentionally absent from BOTH sets — the task
     * list is seeded by the caller (FR-2.4), and sub-agents don't manage
     * the orchestrator's task list.
     */
    fun testCreateTaskListAbsentFromBothSets() {
        val orchestratorTools = ToolRegistry(
            orchestratorToolRegistry(FakeAIService(), ToolRegistry(subAgentToolRegistry()))
        )
        val subAgentTools = ToolRegistry(subAgentToolRegistry())

        Assert.assertFalse(
            "orchestrator must not advertise create_task_list",
            orchestratorTools.schemas().any { it.name == "create_task_list" }
        )
        Assert.assertFalse(
            "sub-agent must not advertise create_task_list",
            subAgentTools.schemas().any { it.name == "create_task_list" }
        )
    }

    /**
     * `report_findings` is sub-agent-only — the orchestrator must NOT
     * advertise it (each role has its own terminal action).
     */
    fun testReportFindingsAbsentFromOrchestrator() {
        val orchestratorTools = ToolRegistry(
            orchestratorToolRegistry(FakeAIService(), ToolRegistry(subAgentToolRegistry()))
        )
        Assert.assertFalse(
            "orchestrator must not advertise report_findings",
            orchestratorTools.schemas().any { it.name == "report_findings" }
        )
    }

    /**
     * Smoke test — the factory returns real [AiTool] instances with
     * non-empty schemas (catches a regression where a factory returns
     * empty lists or tools with empty schemas).
     */
    fun testFactoryReturnsNonEmptySchemas() {
        val orchestratorSchemas: List<AiToolSpec> = ToolRegistry(
            orchestratorToolRegistry(FakeAIService(), ToolRegistry(subAgentToolRegistry()))
        ).schemas()
        val subAgentSchemas: List<AiToolSpec> = ToolRegistry(subAgentToolRegistry()).schemas()

        Assert.assertTrue(
            "orchestrator schemas should be non-empty",
            orchestratorSchemas.isNotEmpty()
        )
        Assert.assertTrue(
            "sub-agent schemas should be non-empty",
            subAgentSchemas.isNotEmpty()
        )
        // Each schema should have a non-empty description + parameters schema.
        orchestratorSchemas.forEach { s ->
            Assert.assertTrue("orchestrator tool ${s.name} has empty description", s.description.isNotEmpty())
            Assert.assertTrue("orchestrator tool ${s.name} has empty schema", s.parametersJsonSchema.isNotEmpty())
        }
        subAgentSchemas.forEach { s ->
            Assert.assertTrue("sub-agent tool ${s.name} has empty description", s.description.isNotEmpty())
            Assert.assertTrue("sub-agent tool ${s.name} has empty schema", s.parametersJsonSchema.isNotEmpty())
        }
    }

    /**
     * The orchestrator's `propose_rule_content` tool is the
     * [OrchestratorProposeRuleContentTool] (which merges sub-agent
     * findings), NOT the Reactive-path [ProposeRuleContentTool]. Both
     * share the same `name` so the LLM uses them the same way, but the
     * orchestrator's set must wire the merging variant.
     */
    fun testOrchestratorUsesMergingProposeTool() {
        val tools = orchestratorToolRegistry(FakeAIService(), ToolRegistry(subAgentToolRegistry()))
        val proposeTool = tools.first { it.name == "propose_rule_content" }
        Assert.assertEquals(
            "orchestrator should use OrchestratorProposeRuleContentTool",
            OrchestratorProposeRuleContentTool::class.java,
            proposeTool::class.java
        )
    }

    /**
     * The orchestrator's `run_sub_agent` tool is the real
     * [RunSubAgentTool] (not a fake) — wired with the supplied
     * [com.itangcent.easyapi.core.ai.AIService] and sub-agent tool
     * registry.
     */
    fun testOrchestratorUsesRealRunSubAgentTool() {
        val fakeAiService = FakeAIService()
        val subAgentTools = ToolRegistry(subAgentToolRegistry())
        val tools = orchestratorToolRegistry(fakeAiService, subAgentTools)
        val runSubAgentTool = tools.first { it.name == "run_sub_agent" }
        Assert.assertEquals(
            "orchestrator should use RunSubAgentTool",
            RunSubAgentTool::class.java,
            runSubAgentTool::class.java
        )
    }

    /**
     * Sanity: the test's [AiRuntimeConfig] / project context isn't needed
     * for the registry factories — they take only [com.itangcent.easyapi.core.ai.AIService]
     * + [ToolRegistry]. This guards against a future refactor that adds
     * project coupling to the factory signatures.
     */
    fun testFactorySignaturesAreProjectAgnostic() {
        // Builds without a project — pure factory call.
        val tools = orchestratorToolRegistry(FakeAIService(), ToolRegistry(subAgentToolRegistry()))
        Assert.assertEquals(3, tools.size)
    }
}
