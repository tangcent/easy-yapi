package com.itangcent.easyapi.core.ai.tools

import com.itangcent.easyapi.core.ai.agent.FakeAIService
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.Assert

/**
 * Guards the "tool names are unique inside one registry" invariant that
 * [ToolRegistry] silently relies on.
 *
 * `ToolRegistry` builds its lookup with `associateBy { it.name }`, so a
 * collision inside one registry throws nothing — the later entry overwrites
 * the earlier one. The shadowed tool becomes unreachable through
 * [ToolRegistry.dispatch] while still being advertised to the LLM by
 * [ToolRegistry.schemas] (which maps the raw list, not the map). Nothing at
 * runtime surfaces that, so it has to be caught here.
 *
 * Cross-registry reuse is deliberate and fine: `propose_rule_content` exists
 * in both [standardRuleTools] and [orchestratorToolRegistry] with different
 * implementations, and those two registries are never merged (see
 * [OrchestratorToolRegistryTest.testOrchestratorUsesMergingProposeTool]).
 * Only *intra*-registry collisions are guarded.
 */
class ToolRegistryNameGuardTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testStandardRuleToolsHaveUniqueNames() {
        assertUniqueNames("standardRuleTools()", standardRuleTools())
    }

    fun testOrchestratorRegistryHasUniqueNames() {
        assertUniqueNames(
            "orchestratorToolRegistry()",
            orchestratorToolRegistry(FakeAIService(), ToolRegistry(subAgentToolRegistry()))
        )
    }

    fun testSubAgentRegistryHasUniqueNames() {
        assertUniqueNames("subAgentToolRegistry()", subAgentToolRegistry())
    }

    /**
     * Names shared *across* registries are a deliberate, closed set: the two
     * `propose_rule_content` classes (each registry wires its own) and
     * `update_task`, which is the same class in both sets. A new shared name
     * means either a copy-paste mistake or a rename that silently changed an
     * LLM-facing tool name — both have to be deliberate.
     */
    fun testStandardAndOrchestratorShareOnlyDeliberateNames() {
        val standard = standardRuleTools().map { it.name }.toSet()
        val orchestrator = orchestratorToolRegistry(
            FakeAIService(), ToolRegistry(subAgentToolRegistry())
        ).map { it.name }.toSet()

        Assert.assertEquals(
            "standard ∩ orchestrator should be exactly " +
                "{propose_rule_content, update_task}: " +
                "standard=${standard.sorted()}, orchestrator=${orchestrator.sorted()}",
            setOf("propose_rule_content", "update_task"),
            standard.intersect(orchestrator)
        )
    }

    private fun assertUniqueNames(label: String, tools: List<AiTool>) {
        val names = tools.map { it.name }
        Assert.assertEquals(
            "$label must not contain duplicate tool names — ToolRegistry.byName " +
                "would silently shadow the earlier entry, leaving it advertised " +
                "but undispatched: $names",
            names.size,
            names.distinct().size
        )
    }
}
