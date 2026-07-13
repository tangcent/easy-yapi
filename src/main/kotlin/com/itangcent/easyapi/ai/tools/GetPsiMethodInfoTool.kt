package com.itangcent.easyapi.ai.tools

import com.intellij.psi.PsiElement
import com.itangcent.easyapi.core.threading.read
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.util.json.GsonUtils

/**
 * Perception tool that returns info about a single PSI method.
 *
 * Resolves the class by FQN (or simple name with optional `context`), then
 * walks its methods matching by name + (optional) parameter count. Returns
 * the signature, annotations, parameters (with additive `typeFqn` — the
 * [com.itangcent.easyapi.psi.type.ResolvedType.qualifiedName] with type args
 * encoded inline), return type info (`returnType` / `returnTypeFqn`), the
 * doc-comment text, and — when `detail="full"` — the method body (truncated
 * to `maxBodyChars`).
 *
 * All signature building is delegated to [PsiSignatureBuilder] — this tool
 * only resolves the PSI method and builds error messages.
 *
 * @see PsiNameResolver
 */
class GetPsiMethodInfoTool : AiTool, IdeaLog {

    companion object {
        /** Default character budget for the `body` field when `detail="full"`. */
        private const val DEFAULT_MAX_BODY_CHARS = 4000
    }

    override val name: String = "get_psi_method_info"

    override val description: String =
        "Get info about a method in a class. Returns JSON {className, name, " +
            "signature, annotations, parameters, docComment, returnType, " +
            "returnTypeFqn}. `paramCount` is optional and narrows the match " +
            "when a class overloads the method name. Pass `detail=\"full\"` " +
            "to include the method `body` (truncated to `maxBodyChars`)."

    override val kind: ToolKind = ToolKind.PERCEPTION

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "fqn" to mapOf(
                "type" to "string",
                "description" to "Fully qualified class name (or simple name when `context` is supplied)."
            ),
            "methodName" to mapOf(
                "type" to "string",
                "description" to "Method name."
            ),
            "paramCount" to mapOf(
                "type" to "integer",
                "description" to "Optional: parameter count, to disambiguate overloads."
            ),
            "detail" to mapOf(
                "type" to "string",
                "enum" to listOf("signature", "full"),
                "description" to "Optional: level of detail. \"signature\" (default) " +
                    "omits the body; \"full\" includes a truncated `body` field."
            ),
            "maxBodyChars" to mapOf(
                "type" to "integer",
                "description" to "Optional: max characters for the `body` field " +
                    "(only when detail=\"full\"). Defaults to $DEFAULT_MAX_BODY_CHARS."
            ),
            "context" to mapOf(
                "type" to "string",
                "description" to "Optional: file path or class FQN whose import scope " +
                    "is used to resolve simple-name `fqn` and parameter/return type FQNs."
            )
        ),
        "required" to listOf("fqn", "methodName")
    )

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val fqn = args["fqn"] as? String
        val methodName = args["methodName"] as? String
        if (fqn.isNullOrBlank() || methodName.isNullOrBlank()) {
            return ToolResult.Error("missing required parameter(s): fqn, methodName")
        }
        val paramCount = (args["paramCount"] as? Number)?.toInt()
        val detail = (args["detail"] as? String)?.takeIf { it == "full" } ?: "signature"
        val maxBodyChars = (args["maxBodyChars"] as? Number)?.toInt()?.takeIf { it > 0 }
            ?: DEFAULT_MAX_BODY_CHARS
        val contextElement = PsiNameResolver.resolveContextArg(args, ctx.project)

        // All PSI access (name, signature, annotations, parameters, docComment,
        // body) must happen inside the read action — PSI element getters
        // require one. The detail="signature" fast path does NOT touch
        // psiMethod.body?.text (REQ-3 AC-9).
        val info = read {
            val psiClass = PsiNameResolver.resolveClass(fqn, ctx.project, contextElement)
                ?: return@read null
            val psiMethod = psiClass.methods.firstOrNull { m ->
                m.name == methodName &&
                    (paramCount == null || m.parameterList.parameters.size == paramCount)
            } ?: return@read null
            // Implicit contextElement for type enrichment: when no explicit
            // context was supplied, use the resolved class's containing file
            // (REQ-4 AC-9).
            val enrichmentContext = contextElement ?: psiClass.containingFile
            PsiSignatureBuilder.methodToInfoMap(
                psiMethod = psiMethod,
                className = psiClass.qualifiedName ?: fqn,
                project = ctx.project,
                contextElement = enrichmentContext,
                detail = detail,
                maxBodyChars = maxBodyChars
            )
        } ?: return ToolResult.Error(buildNotFoundMessage(fqn, ctx, contextElement, methodName))

        return ToolResult.Text(GsonUtils.toJson(info))
    }

    /**
     * Builds the error message for a missing class or method. When the
     * lookup was a simple name without context and `PsiNameResolver` found
     * multiple matches, the message guides the agent to `find_classes_by_name`
     * (Design Decision 4).
     */
    private suspend fun buildNotFoundMessage(
        fqn: String,
        ctx: ToolContext,
        contextElement: PsiElement?,
        methodName: String
    ): String {
        if (!fqn.contains('.') && contextElement == null) {
            val matches = PsiNameResolver.resolveAllClasses(fqn, ctx.project, null)
            if (matches.size > 1) {
                return "ambiguous simple name '$fqn': ${matches.size} classes match, " +
                    "use find_classes_by_name to disambiguate"
            }
        }
        return "method not found: $fqn#$methodName"
    }
}
