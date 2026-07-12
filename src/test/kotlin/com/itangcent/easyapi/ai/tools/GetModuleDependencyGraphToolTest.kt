package com.itangcent.easyapi.ai.tools

import com.itangcent.easyapi.ai.AiRuntimeConfig
import com.itangcent.easyapi.ai.AiProvider
import com.itangcent.easyapi.ai.agent.AgentMemory
import com.itangcent.easyapi.ai.agent.ApprovalGate
import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.config.source.RuleFileResolver
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert

/**
 * Tests for [GetModuleDependencyGraphTool].
 *
 * The IntelliJ `LightJavaCodeInsightFixture` supports only ONE module by
 * default, so a true multi-module fixture with `ModuleOrderEntry` edges is
 * impractical here. Per the task's guidance, the structural rendering logic
 * (adjacency list, 24-node cap summary collapse, connected-component
 * clustering, privacy) is extracted into the pure, testable
 * [GetModuleDependencyGraphTool.renderGraph] companion function and exercised
 * directly. The tool-kind / parametersSchema / registration are asserted
 * against the tool instance, and a smoke test runs `execute()` against the
 * single-module light fixture to confirm it returns a `ToolResult.Text` and
 * never throws.
 *
 * What is NOT covered here (limitation of the light fixture): a multi-module
 * fixture exercising live `ModuleOrderEntry` edges, `LibraryOrderEntry` /
 * `SdkOrderEntry` exclusion at the `ModuleRootManager` level, and a per-module
 * `ModuleRootManager` read throwing. The pure-function tests cover the
 * rendering contract that the `execute()` data-collection feeds; the
 * `execute()` smoke test covers the never-throws / best-effort contract.
 */
class GetModuleDependencyGraphToolTest : EasyApiLightCodeInsightFixtureTestCase() {

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
        approvals = NoOpApprovalGate()
    )

    // --- Pure rendering: adjacency list ---

    fun testRenderAdjacencyForSimpleGraph() {
        val adjacency = linkedMapOf(
            "a" to listOf("b", "c"),
            "b" to listOf("c"),
            "c" to emptyList()
        )
        val rendered = GetModuleDependencyGraphTool.renderGraph(adjacency)
        val lines = rendered.split("\n")
        // Order-stable: sorted by module name.
        Assert.assertEquals("a: [b, c]", lines[0])
        Assert.assertEquals("b: [c]", lines[1])
        Assert.assertEquals("c: []", lines[2])
    }

    fun testRenderAdjacencySortsForDeterminism() {
        // Unsorted input — the renderer must sort names + deps.
        val adjacency = linkedMapOf(
            "zeta" to listOf("alpha", "beta"),
            "alpha" to listOf("beta"),
            "beta" to emptyList()
        )
        val rendered = GetModuleDependencyGraphTool.renderGraph(adjacency)
        val lines = rendered.split("\n")
        Assert.assertEquals("alpha: [beta]", lines[0])
        Assert.assertEquals("beta: []", lines[1])
        Assert.assertEquals("zeta: [alpha, beta]", lines[2])
    }

    fun testRenderAdjacencyDeduplicatesDependencies() {
        // A duplicated dependency must collapse to one edge.
        val adjacency = mapOf(
            "a" to listOf("b", "b", "c")
        )
        val rendered = GetModuleDependencyGraphTool.renderGraph(adjacency)
        Assert.assertTrue("expected deduped deps, got: $rendered",
            rendered.contains("a: [b, c]"))
    }

    fun testRenderEmptyGraphReturnsPlaceholder() {
        val rendered = GetModuleDependencyGraphTool.renderGraph(emptyMap())
        // No modules — the tool returns a short placeholder, never an error.
        Assert.assertTrue("expected placeholder, got: $rendered", rendered.isNotBlank())
    }

    // --- Pure rendering: library / SDK exclusion (by design) ---
    //
    // The `execute()` implementation collects only `ModuleOrderEntry` edges
    // (it skips `LibraryOrderEntry` / `SdkOrderEntry`), so the adjacency map
    // handed to `renderGraph` never contains library/SDK names. This test
    // asserts the contract the data-collection layer must uphold: given a map
    // that already reflects module-only edges, no library/SDK token appears.

    fun testRenderOutputContainsOnlyModuleNamesAndEdges() {
        val adjacency = mapOf(
            "order-web" to listOf("order-service", "common"),
            "order-service" to listOf("common"),
            "common" to emptyList()
        )
        val rendered = GetModuleDependencyGraphTool.renderGraph(adjacency)
        // Only module names + the structural edge syntax should appear.
        Assert.assertTrue("should render order-web", rendered.contains("order-web"))
        Assert.assertTrue("should render order-service", rendered.contains("order-service"))
        Assert.assertTrue("should render common", rendered.contains("common"))
        // No library / SDK identifiers leak in (they never enter the map).
        Assert.assertFalse("guava must not appear", rendered.contains("guava"))
        Assert.assertFalse("jdk must not appear", rendered.contains("jdk"))
        Assert.assertFalse("stdlib must not appear", rendered.contains("stdlib"))
    }

    // --- Pure rendering: 24-node cap summary collapse ---

    fun testSummaryCollapseAboveNodeCap() {
        // > 24 modules → summary form, NOT the full adjacency list.
        val adjacency = (1..25).associate { i ->
            "mod$i" to (if (i < 25) listOf("mod${i + 1}") else emptyList())
        }
        val rendered = GetModuleDependencyGraphTool.renderGraph(adjacency)
        // Summary header, not the per-node adjacency lines.
        Assert.assertTrue(
            "expected summary form, got: $rendered",
            rendered.startsWith("25 modules in 1 disjoint clusters:")
        )
        // The per-node `name: [` adjacency lines must NOT appear.
        Assert.assertFalse(
            "summary must not include adjacency lines, got: $rendered",
            rendered.contains("mod1: [")
        )
    }

    fun testSummaryCollapseReportsDisjointClusters() {
        // 30 nodes split into 3 disjoint clusters of 10.
        val prefixes = listOf("a", "b", "c")
        val adjacency = buildMap {
            for (cluster in prefixes.indices) {
                val prefix = prefixes[cluster]
                for (i in 1..10) {
                    val name = "$prefix$i"
                    put(name, if (i < 10) listOf("$prefix${i + 1}") else emptyList())
                }
            }
        }
        val rendered = GetModuleDependencyGraphTool.renderGraph(adjacency)
        Assert.assertTrue(
            "expected 3 disjoint clusters, got: $rendered",
            rendered.startsWith("30 modules in 3 disjoint clusters:")
        )
    }

    fun testFullAdjacencyRenderedAtNodeCapBoundary() {
        // Exactly NODE_CAP (24) nodes — still the full adjacency form.
        val adjacency = (1..24).associate { i ->
            "mod$i" to emptyList<String>()
        }
        val rendered = GetModuleDependencyGraphTool.renderGraph(adjacency)
        Assert.assertFalse(
            "at the cap boundary the full adjacency should still render, got: $rendered",
            rendered.contains("disjoint clusters")
        )
        Assert.assertTrue(
            "should include first node adjacency, got: $rendered",
            rendered.contains("mod1: []")
        )
    }

    // --- Best-effort failure handling ---
    //
    // A light fixture cannot easily make `ModuleRootManager` throw for one
    // module. The contract is asserted at two levels: (1) the pure renderer
    // tolerates a partially-populated map (a module whose read failed is
    // simply absent from the map — it is omitted, not an error); (2) the
    // live `execute()` call returns a `ToolResult.Text` and never throws.

    fun testRendererToleratesPartiallyPopulatedMap() {
        // Module "broken" was omitted because its read failed; the others
        // render normally. The renderer must not require every module as a
        // key, and a dependency target with no key of its own is rendered
        // as a leaf in the summary path.
        val adjacency = mapOf(
            "a" to listOf("broken", "c"),
            "c" to emptyList()
        )
        val rendered = GetModuleDependencyGraphTool.renderGraph(adjacency)
        Assert.assertTrue("a's edges should render, got: $rendered",
            rendered.contains("a: [broken, c]"))
        Assert.assertTrue("c leaf should render, got: $rendered", rendered.contains("c: []"))
    }

    fun testExecuteOnLightFixtureReturnsTextAndNeverThrows() {
        // The light fixture has a single module; execute() must succeed and
        // return a Text result (best-effort: never throws).
        val result = runBlocking {
            GetModuleDependencyGraphTool().execute(emptyMap(), ctx())
        }
        Assert.assertTrue("expected Text result, got: $result", result is ToolResult.Text)
        Assert.assertTrue(
            "expected non-blank output, got: ${(result as ToolResult.Text).value}",
            (result).value.isNotBlank()
        )
    }

    // --- Privacy (NFR-5 / Req 8.5) ---

    fun testRenderedOutputCarriesNoEnvVarMaterial() {
        // The renderer is structural: only module names + edges. A typical
        // env-var secret must never appear even if a module were named after
        // an env key — and there is no channel for env-var *values* at all.
        val adjacency = mapOf(
            "order-service" to listOf("common"),
            "common" to emptyList()
        )
        val rendered = GetModuleDependencyGraphTool.renderGraph(adjacency)
        Assert.assertFalse("must not leak a secret token", rendered.contains("STRIPE_SECRET_KEY"))
        Assert.assertFalse("must not leak a bearer token", rendered.contains("Bearer"))
        Assert.assertFalse("must not leak a placeholder value", rendered.contains("\${"))
        Assert.assertFalse("must not leak an env-var value syntax", rendered.contains("sk-"))
    }

    // --- Tool-kind (NFR-6) ---

    fun testToolKindIsPerception() {
        val tool = GetModuleDependencyGraphTool()
        Assert.assertEquals(ToolKind.PERCEPTION, tool.kind)
    }

    fun testRequiresApprovalIsFalse() {
        Assert.assertFalse(GetModuleDependencyGraphTool().requiresApproval)
    }

    fun testParametersSchemaIsEmpty() {
        Assert.assertTrue(
            "parametersSchema must be empty (no args)",
            GetModuleDependencyGraphTool().parametersSchema.isEmpty()
        )
    }

    // --- Registration ---

    fun testIsRegisteredInStandardToolSet() {
        val names = standardRuleTools().map { it.name }
        Assert.assertTrue(
            "get_module_dependency_graph must be registered",
            names.contains("get_module_dependency_graph")
        )
    }

    private class NoOpApprovalGate : ApprovalGate {
        override suspend fun await(toolName: String, args: Map<String, Any?>): Boolean = true
    }
}
