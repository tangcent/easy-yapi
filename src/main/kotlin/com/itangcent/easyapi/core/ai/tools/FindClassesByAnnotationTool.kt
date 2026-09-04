package com.itangcent.easyapi.core.ai.tools

import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.itangcent.easyapi.core.internal.threading.read
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.util.json.GsonUtils

/**
 * Perception tool that finds classes annotated with one or more annotation
 * names — fully qualified or simple.
 *
 * Uses [AnnotatedElementsSearch.searchPsiClasses] — the same API the dashboard
 * scanner uses. Resolves the annotation name (or simple name with optional
 * `context`) via [PsiNameResolver.resolveAllClasses], then searches for classes
 * annotated with it in the project scope. A simple annotation name is probed
 * against every matching annotation class, so e.g. `"RestController"` covers
 * both Spring and Spring-Boot variants without needing the FQN.
 *
 * Only sees annotation-declared components; pair with
 * [FindClassesBySupertypeTool] to cover inheritance-declared components
 * (filters extending `OncePerRequestFilter`,...).
 *
 * Supports batch: pass `annotations` (array) to probe multiple annotations
 * in one call. Returns a JSON object mapping each annotation to its results.
 */
class FindClassesByAnnotationTool : AiTool, IdeaLog {

    override val name: String = "find_classes_by_annotation"

    override val description: String =
        "Find project classes annotated with the given annotation(s) — each by " +
            "simple or fully qualified name (e.g. \"RestController\"). Pass " +
            "`annotation` for one or `annotations` (array) for batch. Returns " +
            "FQNs of matching classes (JSON array for one; name-to-array map " +
            "for batch). A simple name probes every matching annotation class."

    override val kind: ToolKind = ToolKind.PERCEPTION

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "annotation" to mapOf(
                "type" to "string",
                "description" to "Annotation name — simple or fully qualified."
            ),
            "annotations" to mapOf(
                "type" to "array",
                "items" to mapOf("type" to "string"),
                "description" to "Multiple annotation names to probe (batch mode)."
            ),
            "context" to mapOf(
                "type" to "string",
                "description" to "Optional: file path or class FQN whose import " +
                    "scope narrows simple annotation names."
            )
        )
    )

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val names = PsiNameResolver.extractStringList(
            args, "annotation", "annotations", "annotationFqn", "annotationFqns"
        )
        if (names.isEmpty()) {
            return ToolResult.Error(
                "missing parameter: provide `annotation` (string) or `annotations` (array)"
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
        annotationName: String,
        ctx: ToolContext,
        contextElement: PsiElement?
    ): List<String> = read {
        val annotationClasses = PsiNameResolver.resolveAllClasses(
            annotationName, ctx.project, contextElement
        )
        if (annotationClasses.isEmpty()) {
            LOG.info("annotation not resolvable in scope: $annotationName")
            return@read emptyList<String>()
        }
        val validAnnotations = annotationClasses.filter { it.isAnnotationType }
        if (validAnnotations.isEmpty()) {
            LOG.info("resolved FQN is not an annotation type: $annotationName")
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
        LOG.info("found ${hits.size} class(es) annotated with $annotationName")
        hits
    }
}
