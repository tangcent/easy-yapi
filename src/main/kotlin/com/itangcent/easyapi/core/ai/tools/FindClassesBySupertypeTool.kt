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
 * Resolves the supertype by name — fully qualified or simple, with an optional
 * `context` — via [PsiNameResolver.resolveAllClasses], then searches its
 * inheritors in the project scope via [ClassInheritorsSearch]. A simple
 * supertype name is probed against every matching type. The supertype itself
 * is excluded from the result so the agent gets only the concrete
 * implementations it cares about.
 *
 * Supports batch: pass `supertypes` (array) to probe multiple supertypes
 * in one call. Returns a JSON object mapping each supertype to its results.
 */
class FindClassesBySupertypeTool : AiTool, IdeaLog {

    override val name: String = "find_classes_by_supertype"

    override val description: String =
        "Find project classes that extend or implement the given supertype(s) " +
            "(class or interface) — each by simple or fully qualified name " +
            "(e.g. \"OncePerRequestFilter\"). Pass `supertype` for one or " +
            "`supertypes` (array) for batch. Returns FQNs of inheritors, " +
            "excluding the supertype itself (JSON array for one; name-to-array " +
            "map for batch). Use for inheritance-declared components: servlet " +
            "filters extending OncePerRequestFilter, interceptors implementing " +
            "HandlerInterceptor, argument resolvers implementing " +
            "HandlerMethodArgumentResolver."

    override val kind: ToolKind = ToolKind.PERCEPTION

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "supertype" to mapOf(
                "type" to "string",
                "description" to "Supertype class/interface name — simple or fully " +
                    "qualified — whose subclasses/implementations to find."
            ),
            "supertypes" to mapOf(
                "type" to "array",
                "items" to mapOf("type" to "string"),
                "description" to "Multiple supertype names to probe (batch mode)."
            ),
            "context" to mapOf(
                "type" to "string",
                "description" to "Optional: file path or class FQN whose import " +
                    "scope narrows simple supertype names."
            )
        )
    )

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val names = PsiNameResolver.extractStringList(
            args, "supertype", "supertypes", "supertypeFqn", "supertypeFqns"
        )
        if (names.isEmpty()) {
            return ToolResult.Error(
                "missing parameter: provide `supertype` (string) or `supertypes` (array)"
            )
        }

        val contextElement = PsiNameResolver.resolveContextArg(args, ctx.project)

        if (names.size == 1) {
            return ToolResult.Text(GsonUtils.toJson(searchOne(names[0], ctx, contextElement)))
        }
        val result = names.associateWith { searchOne(it, ctx, contextElement) }
        return ToolResult.Text(GsonUtils.toJson(result))
    }

    private suspend fun searchOne(
        supertypeName: String,
        ctx: ToolContext,
        contextElement: PsiElement?
    ): List<String> = read {
        val supertypes = PsiNameResolver.resolveAllClasses(
            supertypeName, ctx.project, contextElement
        )
        if (supertypes.isEmpty()) {
            LOG.info("supertype not resolvable in scope: $supertypeName")
            return@read emptyList<String>()
        }
        val scope = GlobalSearchScope.projectScope(ctx.project)
        val inheritors = supertypes.flatMap { supertype ->
            ClassInheritorsSearch.search(supertype, scope, false)
                .findAll()
                .filterIsInstance<PsiClass>()
                .mapNotNull { it.qualifiedName }
                .filter { it != supertypeName }
        }
            .distinct()
            .sorted()
        LOG.info("found ${inheritors.size} inheritor(s) of $supertypeName")
        inheritors
    }
}
