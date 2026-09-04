package com.itangcent.easyapi.core.ai.tools

import com.intellij.psi.PsiElement
import com.itangcent.easyapi.core.internal.threading.read
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.util.json.GsonUtils

/**
 * Perception tool that returns PSI class info for one or more classes.
 *
 * Resolves each class by name — fully qualified or simple, with an optional
 * `context` for import-scope disambiguation — inside a read action and returns
 * name, modifiers, annotations, fields (with additive `typeFqn` — the
 * [com.itangcent.easyapi.core.psi.type.ResolvedType.qualifiedName] with type
 * args encoded inline), and method signatures (with additive `returnTypeFqn`
 * and per-parameter `typeFqn`).
 *
 * All signature building is delegated to [PsiSignatureBuilder] — this tool
 * only resolves the PSI class and builds error messages.
 *
 * Supports batch: pass `classNames` (array) to inspect multiple classes in one
 * call. Returns a JSON object for a single class, or a JSON object mapping
 * each requested name to its info (or an error string) for batch mode.
 */
class GetPsiClassInfoTool : AiTool, IdeaLog {

    override val name: String = "get_psi_class_info"

    override val description: String =
        "Get info about Java/Kotlin class(es) by simple or fully qualified name " +
            "(e.g. \"AuthResponse\"). Pass `className` for one, or `classNames` " +
            "(array) for batch. Returns JSON {name, fqn, modifiers, annotations, " +
            "fields, methods}. An ambiguous simple name errors with the " +
            "candidate FQNs — pass `context` (file path / class FQN) or retry " +
            "with a listed FQN."

    override val kind: ToolKind = ToolKind.PERCEPTION

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "className" to mapOf(
                "type" to "string",
                "description" to "Simple or fully qualified class name."
            ),
            "classNames" to mapOf(
                "type" to "array",
                "items" to mapOf("type" to "string"),
                "description" to "Multiple class names to inspect (batch mode)."
            ),
            "context" to mapOf(
                "type" to "string",
                "description" to "Optional: file path or class FQN whose import " +
                    "scope disambiguates simple class names."
            )
        )
    )

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val names = PsiNameResolver.extractStringList(
            args, "className", "classNames", "fqn", "fqns"
        )
        if (names.isEmpty()) {
            return ToolResult.Error(
                "missing parameter: provide `className` (string) or `classNames` (array)"
            )
        }

        val contextElement = PsiNameResolver.resolveContextArg(args, ctx.project)

        if (names.size == 1) {
            val info = lookupOne(names[0], ctx, contextElement)
            if (info == null) {
                return ToolResult.Error(buildNotFoundMessage(names[0], ctx, contextElement))
            }
            return ToolResult.Text(GsonUtils.toJson(info))
        }
        val result = names.associateWith {
            lookupOne(it, ctx, contextElement) ?: buildNotFoundMessage(it, ctx, contextElement)
        }
        return ToolResult.Text(GsonUtils.toJson(result))
    }

    /**
     * Builds the error message for a missing class. When the lookup was a
     * simple name without context and [PsiNameResolver] found multiple
     * matches, the message lists the candidate FQNs so the agent can retry
     * with the fully qualified name or a `context`. Otherwise a plain "class
     * not found" is returned.
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
                return PsiNameResolver.ambiguityMessage(fqn, matches)
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
        // was supplied, use the resolved class's containing file.
        val enrichmentContext = contextElement ?: psiClass.containingFile
        PsiSignatureBuilder.classToMap(psiClass, ctx.project, enrichmentContext)
    }
}
