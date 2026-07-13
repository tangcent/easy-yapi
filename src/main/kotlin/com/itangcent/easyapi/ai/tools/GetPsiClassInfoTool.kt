package com.itangcent.easyapi.ai.tools

import com.intellij.psi.PsiElement
import com.itangcent.easyapi.core.threading.read
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.util.json.GsonUtils

/**
 * Perception tool that returns PSI class info for one or more classes
 *.
 *
 * Resolves each class by FQN (or simple name with optional `context`) inside a
 * read action and returns name, modifiers, annotations, fields (with additive
 * `typeFqn` — the [com.itangcent.easyapi.psi.type.ResolvedType.qualifiedName]
 * with type args encoded inline), and method signatures (with additive
 * `returnTypeFqn` and per-parameter `typeFqn`).
 *
 * All signature building is delegated to [PsiSignatureBuilder] — this tool
 * only resolves the PSI class and builds error messages.
 *
 * Supports batch: pass `fqns` (array) to inspect multiple classes in one call.
 * Returns a JSON object for a single class, or a JSON object mapping each FQN
 * to its info (or an error string) for batch mode.
 */
class GetPsiClassInfoTool : AiTool, IdeaLog {

    override val name: String = "get_psi_class_info"

    override val description: String =
        "Get info about Java/Kotlin class(es) by fully qualified name. Pass " +
            "`fqn` (string) for one, or `fqns` (array) to batch-inspect multiple. " +
            "Returns JSON {name, fqn, modifiers, annotations, fields:[{name, type}], " +
            "methods:[{name, signature}]} for a single class, or a JSON object " +
            "mapping each FQN to its info for batch mode."

    override val kind: ToolKind = ToolKind.PERCEPTION

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "fqn" to mapOf(
                "type" to "string",
                "description" to "Fully qualified class name (or simple name when `context` is supplied)."
            ),
            "fqns" to mapOf(
                "type" to "array",
                "items" to mapOf("type" to "string"),
                "description" to "Multiple fully qualified class names to inspect in one call (batch mode)."
            ),
            "context" to mapOf(
                "type" to "string",
                "description" to "Optional: file path or class FQN whose import scope " +
                    "is used to resolve simple-name `fqn`/`fqns` entries and field " +
                    "type FQNs."
            )
        )
    )

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val fqns = PsiNameResolver.extractStringList(args, "fqn", "fqns")
        if (fqns.isEmpty()) return ToolResult.Error("missing parameter: provide `fqn` (string) or `fqns` (array)")

        val contextElement = PsiNameResolver.resolveContextArg(args, ctx.project)

        if (fqns.size == 1) {
            val info = lookupOne(fqns[0], ctx, contextElement)
            if (info == null) {
                return ToolResult.Error(buildNotFoundMessage(fqns[0], ctx, contextElement))
            }
            return ToolResult.Text(GsonUtils.toJson(info))
        }
        val result = fqns.associateWith { lookupOne(it, ctx, contextElement) ?: "not found" }
        return ToolResult.Text(GsonUtils.toJson(result))
    }

    /**
     * Builds the error message for a missing class. When the lookup was a
     * simple name without context and `PsiNameResolver` found multiple
     * matches, the message guides the agent to `find_classes_by_name`
     * (Design Decision 4). Otherwise a plain "class not found" is returned.
     */
    private suspend fun buildNotFoundMessage(
        fqn: String,
        ctx: ToolContext,
        contextElement: PsiElement?
    ): String {
        // Ambiguity only applies to simple names (no dot) without a context
        // that could have nailed the resolution.
        if (!fqn.contains('.') && contextElement == null) {
            val matches = PsiNameResolver.resolveAllClasses(fqn, ctx.project, null)
            if (matches.size > 1) {
                return "ambiguous simple name '$fqn': ${matches.size} classes match, " +
                    "use find_classes_by_name to disambiguate"
            }
        }
        return "class not found: $fqn"
    }

    // All PSI access (name, fields, methods, annotations) must happen inside
    // the read action — PSI element getters require a read action.
    private suspend fun lookupOne(
        fqn: String,
        ctx: ToolContext,
        contextElement: PsiElement?
    ): Map<String, Any?>? = read {
        val psiClass = PsiNameResolver.resolveClass(fqn, ctx.project, contextElement)
            ?: return@read null
        // Implicit contextElement for type enrichment: when no explicit context
        // was supplied, use the resolved class's containing file (REQ-4 AC-9).
        val enrichmentContext = contextElement ?: psiClass.containingFile
        PsiSignatureBuilder.classToMap(psiClass, ctx.project, enrichmentContext)
    }
}
