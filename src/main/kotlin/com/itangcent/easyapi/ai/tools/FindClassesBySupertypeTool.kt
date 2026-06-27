package com.itangcent.easyapi.ai.tools

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.itangcent.easyapi.core.threading.read
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.util.json.GsonUtils

/**
 * Perception tool that finds project classes that extend or implement one or
 * more supertypes.
 *
 * [FindClassesByAnnotationTool] only sees annotation-declared components, so
 * it misses classes whose contract is defined *by inheritance* — the most
 * common Spring Boot pattern. Servlet filters typically extend
 * `OncePerRequestFilter` and interceptors implement `HandlerInterceptor`,
 * with no annotation marking them as such. This tool closes that gap.
 *
 * Resolves the supertype by FQN (project + library scope), then searches its
 * inheritors in the project scope via [ClassInheritorsSearch]. The supertype
 * itself is excluded from the result so the agent gets only the concrete
 * implementations it cares about.
 *
 * Supports batch: pass `supertypeFqns` (array) to probe multiple supertypes
 * in one call. Returns a JSON object mapping each supertype FQN to its results.
 */
class FindClassesBySupertypeTool : AiTool, IdeaLog {

    override val name: String = "find_classes_by_supertype"

    override val description: String =
        "Find project classes that extend or implement the given supertype(s) " +
            "(class or interface). Pass `supertypeFqn` (string) for one, or " +
            "`supertypeFqns` (array) to batch-probe multiple. Returns a JSON " +
            "array of FQNs (excluding the supertype itself) for a single " +
            "supertype, or a JSON object mapping each to its array for batch. " +
            "Use for inheritance-declared components — e.g. servlet filters " +
            "extending OncePerRequestFilter, interceptors implementing " +
            "HandlerInterceptor, or argument resolvers implementing " +
            "HandlerMethodArgumentResolver."

    override val kind: ToolKind = ToolKind.PERCEPTION

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "supertypeFqn" to mapOf(
                "type" to "string",
                "description" to "Fully qualified name of the class or interface " +
                    "whose subclasses/implementations to find " +
                    "(e.g. \"org.springframework.web.filter.OncePerRequestFilter\" " +
                    "or \"org.springframework.web.servlet.HandlerInterceptor\")."
            ),
            "supertypeFqns" to mapOf(
                "type" to "array",
                "items" to mapOf("type" to "string"),
                "description" to "Multiple supertype FQNs to probe in one call (batch mode)."
            )
        )
    )

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val fqns = extractFqns(args)
        if (fqns.isEmpty()) return ToolResult.Error("missing parameter: provide `supertypeFqn` (string) or `supertypeFqns` (array)")

        if (fqns.size == 1) {
            return ToolResult.Text(GsonUtils.toJson(searchOne(fqns[0], ctx)))
        }
        val result = fqns.associateWith { searchOne(it, ctx) }
        return ToolResult.Text(GsonUtils.toJson(result))
    }

    private fun extractFqns(args: Map<String, Any?>): List<String> {
        val batch = args["supertypeFqns"] as? List<*>
        if (batch != null) {
            return batch.mapNotNull { it?.toString()?.takeIf { s -> s.isNotBlank() } }
        }
        val single = args["supertypeFqn"] as? String
        return if (single.isNullOrBlank()) emptyList() else listOf(single)
    }

    private suspend fun searchOne(supertypeFqn: String, ctx: ToolContext): List<String> = read {
        val supertype = JavaPsiFacade.getInstance(ctx.project)
.findClass(supertypeFqn, GlobalSearchScope.allScope(ctx.project))
        if (supertype == null) {
            LOG.info("supertype not resolvable in scope: $supertypeFqn")
            return@read emptyList<String>()
        }
        val scope = GlobalSearchScope.projectScope(ctx.project)
        val inheritors = ClassInheritorsSearch.search(supertype, scope, false)
.findAll()
.filterIsInstance<PsiClass>()
.mapNotNull { it.qualifiedName }
.filter { it != supertypeFqn }
.distinct()
.sorted()
        LOG.info("found ${inheritors.size} inheritor(s) of $supertypeFqn")
        inheritors
    }
}
