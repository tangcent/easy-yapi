package com.itangcent.easyapi.core.ai.tools

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.itangcent.easyapi.core.internal.threading.read
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.util.json.GsonUtils

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
 * Resolves the supertype by FQN (or simple name with optional `context`) via
 * [PsiNameResolver.resolveAllClasses], then searches its inheritors in the
 * project scope via [ClassInheritorsSearch]. The supertype itself is excluded
 * from the result so the agent gets only the concrete implementations it
 * cares about.
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
                    "(or simple name when `context` is supplied) whose " +
                    "subclasses/implementations to find " +
                    "(e.g. \"org.springframework.web.filter.OncePerRequestFilter\" " +
                    "or \"org.springframework.web.servlet.HandlerInterceptor\")."
            ),
            "supertypeFqns" to mapOf(
                "type" to "array",
                "items" to mapOf("type" to "string"),
                "description" to "Multiple supertype FQNs to probe in one call (batch mode)."
            ),
            "context" to mapOf(
                "type" to "string",
                "description" to "Optional: file path or class FQN whose import scope " +
                    "is used to resolve simple-name supertype entries."
            )
        )
    )

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val fqns = PsiNameResolver.extractStringList(args, "supertypeFqn", "supertypeFqns")
        if (fqns.isEmpty()) return ToolResult.Error("missing parameter: provide `supertypeFqn` (string) or `supertypeFqns` (array)")

        val contextElement = PsiNameResolver.resolveContextArg(args, ctx.project)

        if (fqns.size == 1) {
            return ToolResult.Text(GsonUtils.toJson(searchOne(fqns[0], ctx, contextElement)))
        }
        val result = fqns.associateWith { searchOne(it, ctx, contextElement) }
        return ToolResult.Text(GsonUtils.toJson(result))
    }

    private suspend fun searchOne(
        supertypeFqn: String,
        ctx: ToolContext,
        contextElement: PsiElement?
    ): List<String> = read {
        val supertypes = PsiNameResolver.resolveAllClasses(
            supertypeFqn, ctx.project, contextElement
        )
        if (supertypes.isEmpty()) {
            LOG.info("supertype not resolvable in scope: $supertypeFqn")
            return@read emptyList<String>()
        }
        val scope = GlobalSearchScope.projectScope(ctx.project)
        val inheritors = supertypes.flatMap { supertype ->
            ClassInheritorsSearch.search(supertype, scope, false)
                .findAll()
                .filterIsInstance<PsiClass>()
                .mapNotNull { it.qualifiedName }
                .filter { it != supertypeFqn }
        }
            .distinct()
            .sorted()
        LOG.info("found ${inheritors.size} inheritor(s) of $supertypeFqn")
        inheritors
    }
}
