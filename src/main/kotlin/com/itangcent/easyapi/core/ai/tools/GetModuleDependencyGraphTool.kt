package com.itangcent.easyapi.core.ai.tools

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.itangcent.easyapi.core.internal.threading.read
import com.itangcent.easyapi.core.logging.IdeaLog

/**
 * Perception tool that returns the workspace module dependency graph.
 *
 * For every IntelliJ `Module` in the workspace, renders its
 * `ModuleOrderEntry` dependencies on other workspace modules as a compact
 * adjacency list (`module: [dep, dep]`). `LibraryOrderEntry` / `SdkOrderEntry`
 * edges are excluded — only module-to-module edges are structural for app
 * clustering. The agent clusters the returned edges into connected components
 * to decide which modules form one app (the tool delivers edges; it does not
 * compute the grouping).
 *
 * Above [NODE_CAP] nodes a summary ("N modules in K disjoint clusters: …") is
 * returned instead of the full adjacency, so a monorepo does not blow the
 * response budget. The output is structural only — module names + edges; it
 * never carries env-var keys or values.
 *
 * Call only when the ambient `modules:` hint shows more than one API-bearing
 * module.
 *
 * @requires ReadAction context (PSI/index read via [ModuleRootManager]).
 */
class GetModuleDependencyGraphTool : AiTool, IdeaLog {

    override val name: String = "get_module_dependency_graph"

    override val description: String =
        "Return the workspace module dependency graph (module-name -> " +
            "depends-on module-names). Call only when the ambient `modules:` " +
            "hint shows more than one API-bearing module, to decide which " +
            "modules form one app (connected components). Above ~24 nodes a " +
            "summary ('N modules in K clusters') is returned instead."

    override val kind: ToolKind = ToolKind.PERCEPTION

    override val parametersSchema: Map<String, Any?> = emptyMap()

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val adjacency = read {
            val modules = ModuleManager.getInstance(ctx.project).modules
            val result = LinkedHashMap<String, MutableList<String>>()
            for (module in modules) {
                try {
                    val deps = ModuleRootManager.getInstance(module).orderEntries
                        .asSequence()
                        .filterIsInstance<ModuleOrderEntry>()
                        // Req 8.1: restrict to workspace (non-external) modules —
                        // a ModuleOrderEntry whose Module is null is an external /
                        // unloaded reference, not a structural workspace edge.
                        .filter { it.module != null }
                        .map { it.moduleName }
                        .filter { it.isNotBlank() && it != module.name }
                        .distinct()
                        .toList()
                    result[module.name] = deps.toMutableList()
                } catch (e: Exception) {
                    LOG.warn("module-dep-graph: error reading module ${module.name}", e)
                }
            }
            result
        }
        LOG.info(
            "module-dep-graph: ${adjacency.size} nodes, " +
                "${adjacency.values.sumOf { it.size }} edges"
        )
        return ToolResult.Text(renderGraph(adjacency))
    }

    companion object {
        /** Above this node count the tool returns a summary instead of the
         *  full adjacency list. */
        const val NODE_CAP: Int = 24

        /**
         * Pure, testable renderer for the module dependency graph.
         *
         * @param adjacency module name -> its `ModuleOrderEntry` dependency
         *  module names (library/SDK entries already excluded by the caller).
         *  Dependency targets that are absent as keys (a read failure omitted
         *  them, or they are external) are tolerated.
         * @param nodeCap node count above which the summary form is returned.
         * @return the textual representation fed back to the LLM. Never throws.
         */
        internal fun renderGraph(
            adjacency: Map<String, List<String>>,
            nodeCap: Int = NODE_CAP
        ): String {
            if (adjacency.isEmpty()) return "(no modules)"
            // Include dependency targets that are not themselves keys so the
            // summary reports the true node count (a module may be depended
            // on but have no entry of its own).
            val allNodes = adjacency.keys.toMutableSet()
            adjacency.values.forEach { deps ->
                deps.forEach { if (it !in allNodes) allNodes.add(it) }
            }
            if (allNodes.size > nodeCap) {
                return summarize(allNodes, adjacency)
            }
            return adjacency.keys.sorted().joinToString("\n") { name ->
                val deps = adjacency[name].orEmpty().distinct().sorted()
                "$name: [${deps.joinToString(", ")}]"
            }
        }

        private fun summarize(
            allNodes: Set<String>,
            adjacency: Map<String, List<String>>
        ): String {
            val components = connectedComponents(allNodes, adjacency)
            val parts = components
                .sortedWith(
                    compareByDescending<List<String>> { it.size }
                        .thenBy { it.first() }
                )
                .joinToString(", ") { cluster ->
                    val representative = cluster.minOrNull() ?: "?"
                    "$representative(${cluster.size})"
                }
            return "${allNodes.size} modules in ${components.size} disjoint clusters: $parts"
        }

        /**
         * Computes connected components over the *undirected* edge set
         * (module A depends on B ⟹ A and B are in the same component).
         */
        private fun connectedComponents(
            nodes: Set<String>,
            adjacency: Map<String, List<String>>
        ): List<List<String>> {
            // Undirected adjacency: forward (a -> deps) + reverse (dep -> a).
            val undirected = HashMap<String, MutableList<String>>()
            for (node in nodes) undirected[node] = mutableListOf()
            adjacency.forEach { (src, deps) ->
                for (dep in deps) {
                    undirected.getOrPut(src) { mutableListOf() }.add(dep)
                    undirected.getOrPut(dep) { mutableListOf() }.add(src)
                }
            }
            val visited = HashSet<String>()
            val components = mutableListOf<List<String>>()
            for (start in nodes.sorted()) {
                if (start in visited) continue
                val component = mutableListOf<String>()
                val queue = ArrayDeque<String>()
                queue.add(start)
                visited.add(start)
                while (queue.isNotEmpty()) {
                    val current = queue.removeFirst()
                    component.add(current)
                    for (neighbor in undirected[current].orEmpty()) {
                        if (neighbor in visited) continue
                        visited.add(neighbor)
                        queue.add(neighbor)
                    }
                }
                components.add(component.sorted())
            }
            return components
        }
    }
}
