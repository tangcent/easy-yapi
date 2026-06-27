package com.itangcent.easyapi.ai.tools

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.itangcent.easyapi.core.threading.read
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.util.json.GsonUtils

/**
 * Perception tool that finds classes annotated with one or more annotation FQNs
 *.
 *
 * Uses [AnnotatedElementsSearch.searchPsiClasses] — the same API the dashboard
 * scanner uses. Resolves the annotation class first (project + library scope)
 * then searches for classes annotated with it in the project scope.
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
                "description" to "Fully qualified annotation name (e.g. \"org.springframework.web.bind.annotation.RestController\")."
            ),
            "annotationFqns" to mapOf(
                "type" to "array",
                "items" to mapOf("type" to "string"),
                "description" to "Multiple annotation FQNs to probe in one call (batch mode)."
            )
        )
    )

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val fqns = extractFqns(args)
        if (fqns.isEmpty()) return ToolResult.Error("missing parameter: provide `annotationFqn` (string) or `annotationFqns` (array)")

        if (fqns.size == 1) {
            return ToolResult.Text(GsonUtils.toJson(searchOne(fqns[0], ctx)))
        }
        val result = fqns.associateWith { searchOne(it, ctx) }
        return ToolResult.Text(GsonUtils.toJson(result))
    }

    private fun extractFqns(args: Map<String, Any?>): List<String> {
        val batch = args["annotationFqns"] as? List<*>
        if (batch != null) {
            return batch.mapNotNull { it?.toString()?.takeIf { s -> s.isNotBlank() } }
        }
        val single = args["annotationFqn"] as? String
        return if (single.isNullOrBlank()) emptyList() else listOf(single)
    }

    private suspend fun searchOne(annotationFqn: String, ctx: ToolContext): List<String> = read {
        val annotationClass = JavaPsiFacade.getInstance(ctx.project)
.findClass(annotationFqn, GlobalSearchScope.allScope(ctx.project))
        if (annotationClass == null) {
            LOG.info("annotation not resolvable in scope: $annotationFqn")
            return@read emptyList<String>()
        }
        if (!annotationClass.isAnnotationType) {
            LOG.info("resolved FQN is not an annotation type: $annotationFqn")
            return@read emptyList<String>()
        }
        val scope = GlobalSearchScope.projectScope(ctx.project)
        val hits = AnnotatedElementsSearch.searchPsiClasses(annotationClass, scope)
.findAll()
.mapNotNull { it.qualifiedName }
.distinct()
.sorted()
        LOG.info("found ${hits.size} class(es) annotated with $annotationFqn")
        hits
    }
}
