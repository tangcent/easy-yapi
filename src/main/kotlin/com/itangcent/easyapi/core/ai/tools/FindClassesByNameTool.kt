package com.itangcent.easyapi.core.ai.tools

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.itangcent.easyapi.core.internal.threading.read
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.psi.type.ResolvedType
import com.itangcent.easyapi.core.psi.type.TypeResolver
import com.itangcent.easyapi.core.util.json.GsonUtils

/**
 * Perception tool that resolves class simple names to their fully qualified
 * names via [PsiShortNamesCache], with an FQN short-circuit and batch mode.
 *
 * The agent often knows only a class's simple name (e.g. `"AuthResponse"`)
 * from a `find_classes_by_supertype` or `find_classes_by_annotation` result,
 * a method signature, or a doc comment. Without this tool the agent must
 * guess the package, which is unreliable for project-private types.
 *
 * Resolution strategy:
 * - **FQN short-circuit** (input contains `.`): direct
 *   [JavaPsiFacade.findClass] in [GlobalSearchScope.projectScope]. Returns a
 *   single-element or empty array. Does NOT invoke [PsiShortNamesCache]
 *   (per REQ-1 AC-6).
 * - **Simple name**: [PsiShortNamesCache.getClassesByName] in
 *   [GlobalSearchScope.projectScope], sorted alphabetically. When a
 *   `context` is supplied, a context-reachable match (resolved via
 *   [TypeResolver.resolveFromCanonicalText]) is moved to the front of the
 *   array so the agent gets a hint about which match to inspect first.
 *
 * Returns FQNs only (lean shape — matches `find_classes_by_annotation` /
 * `find_classes_by_supertype`). The agent disambiguates by passing the FQN
 * to `get_psi_class_info`.
 *
 * Supports batch: pass `names` (array) to resolve multiple simple names in
 * one call. Returns a JSON object mapping each name to its FQN array.
 */
class FindClassesByNameTool : AiTool, IdeaLog {

    override val name: String = "find_classes_by_name"

    override val description: String =
        "Resolve class simple names to their fully qualified names. Pass `name` " +
            "(string) for one, or `names` (array) to batch-resolve multiple. Returns " +
            "a JSON array of FQNs for a single name, or a JSON object mapping each " +
            "name to its array for batch. Use this when you only know a class's " +
            "simple name (e.g. \"AuthResponse\") and need the FQN to pass to " +
            "get_psi_class_info. If the input contains a dot it is treated as an " +
            "FQN and looked up directly. Optional `context` (file path or class FQN) " +
            "prefers matches reachable from that file's imports."

    override val kind: ToolKind = ToolKind.PERCEPTION

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "name" to mapOf(
                "type" to "string",
                "description" to "Class simple name (e.g. \"AuthResponse\") or FQN."
            ),
            "names" to mapOf(
                "type" to "array",
                "items" to mapOf("type" to "string"),
                "description" to "Multiple names to resolve in one call (batch mode)."
            ),
            "context" to mapOf(
                "type" to "string",
                "description" to "Optional: file path or class FQN whose import " +
                    "scope should be used to prefer matches reachable from that file."
            )
        )
    )

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val names = PsiNameResolver.extractStringList(args, "name", "names")
        if (names.isEmpty()) {
            return ToolResult.Error("missing parameter: provide `name` (string) or `names` (array)")
        }

        val contextElement = PsiNameResolver.resolveContextArg(args, ctx.project)

        return if (names.size == 1) {
            ToolResult.Text(GsonUtils.toJson(searchOne(names[0], ctx, contextElement)))
        } else {
            val result = names.associateWith { searchOne(it, ctx, contextElement) }
            ToolResult.Text(GsonUtils.toJson(result))
        }
    }

    /**
     * Resolves a single name to a list of FQNs.
     *
     * FQN short-circuit: returns a single-element or empty list without
     * touching [PsiShortNamesCache]. Simple name: all project-scope matches,
     * sorted alphabetically with the context-preferred match moved to the
     * front (if any).
     */
    private suspend fun searchOne(
        name: String,
        ctx: ToolContext,
        contextElement: PsiElement?
    ): List<String> = read {
        val project = ctx.project

        // FQN short-circuit — direct lookup, no PsiShortNamesCache.
        if (name.contains('.')) {
            val cls = JavaPsiFacade.getInstance(project)
                .findClass(name, GlobalSearchScope.projectScope(project))
            return@read listOfNotNull(cls?.qualifiedName)
        }

        // Optional context-preferred FQN.
        val preferredFqn: String? = if (contextElement != null) {
            val resolved = TypeResolver.resolveFromCanonicalText(name, project, contextElement)
            (resolved as? ResolvedType.ClassType)?.psiClass?.qualifiedName
        } else {
            null
        }

        // All project-scope matches, sorted alphabetically.
        val scope = GlobalSearchScope.projectScope(project)
        val fqns = PsiShortNamesCache.getInstance(project)
            .getClassesByName(name, scope)
            .mapNotNull { it.qualifiedName }
            .distinct()
            .sorted()
            .toMutableList()

        // Move the context-preferred FQN to the front (stable).
        if (preferredFqn != null && fqns.remove(preferredFqn)) {
            fqns.add(0, preferredFqn)
        }

        LOG.info("find_classes_by_name: '$name' → ${fqns.size} match(es)")
        fqns
    }
}
