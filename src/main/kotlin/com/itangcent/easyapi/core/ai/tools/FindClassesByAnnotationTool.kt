package com.itangcent.easyapi.core.ai.tools

import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.itangcent.easyapi.core.internal.threading.read
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.util.json.GsonUtils

/**
 * Perception tool that finds classes annotated with one or more annotation FQNs
 * or simple names.
 *
 * Uses [AnnotatedElementsSearch.searchPsiClasses] — the same API the dashboard
 * scanner uses. Resolves the annotation class (or simple name with optional
 * `context`) via [PsiNameResolver.resolveAllClasses], then searches for classes
 * annotated with it in the project scope.
 *
 * Only sees annotation-declared components; pair with
 * [FindClassesBySupertypeTool] to cover inheritance-declared components
 * (filters extending `OncePerRequestFilter`,...).
 *
 * Supports batch: pass `annotationFqns` (array) to probe multiple annotations
 * in one call. Returns a JSON object mapping each annotation FQN to its results.
 */
class FindClassesByAnnotationTool : AiTool, IdeaLog {

    override val name: String = "find_classes_by_annotation"

    override val description: String =
        "Find project classes annotated with the given annotation(s). Pass " +
            "`annotationFqn` (string) for one, or `annotationFqns` (array) to " +
            "batch-probe multiple annotations. Returns a JSON array of FQNs for " +
            "a single annotation, or a JSON object mapping each annotation to " +
            "its array for batch mode."

    override val kind: ToolKind = ToolKind.PERCEPTION

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "annotationFqn" to mapOf(
                "type" to "string",
                "description" to "Fully qualified annotation name (or simple name when `context` is supplied)."
            ),
            "annotationFqns" to mapOf(
                "type" to "array",
                "items" to mapOf("type" to "string"),
                "description" to "Multiple annotation FQNs to probe in one call (batch mode)."
            ),
            "context" to mapOf(
                "type" to "string",
                "description" to "Optional: file path or class FQN whose import scope " +
                    "is used to resolve simple-name annotation entries."
            )
        )
    )

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val fqns = PsiNameResolver.extractStringList(args, "annotationFqn", "annotationFqns")
        if (fqns.isEmpty()) return ToolResult.Error("missing parameter: provide `annotationFqn` (string) or `annotationFqns` (array)")

        val contextElement = PsiNameResolver.resolveContextArg(args, ctx.project)

        if (fqns.size == 1) {
            return ToolResult.Text(GsonUtils.toJson(searchOne(fqns[0], ctx, contextElement)))
        }
        val result = fqns.associateWith { searchOne(it, ctx, contextElement) }
        return ToolResult.Text(GsonUtils.toJson(result))
    }

    private suspend fun searchOne(
        annotationFqn: String,
        ctx: ToolContext,
        contextElement: PsiElement?
    ): List<String> = read {
        val annotationClasses = PsiNameResolver.resolveAllClasses(
            annotationFqn, ctx.project, contextElement
        )
        if (annotationClasses.isEmpty()) {
            LOG.info("annotation not resolvable in scope: $annotationFqn")
            return@read emptyList<String>()
        }
        val validAnnotations = annotationClasses.filter { it.isAnnotationType }
        if (validAnnotations.isEmpty()) {
            LOG.info("resolved FQN is not an annotation type: $annotationFqn")
            return@read emptyList<String>()
        }
        val scope = GlobalSearchScope.projectScope(ctx.project)
        val hits = validAnnotations.flatMap { annotationClass ->
            AnnotatedElementsSearch.searchPsiClasses(annotationClass, scope)
                .findAll()
                .mapNotNull { it.qualifiedName }
        }
            .distinct()
            .sorted()
        LOG.info("found ${hits.size} class(es) annotated with $annotationFqn")
        hits
    }
}
