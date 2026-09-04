package com.itangcent.easyapi.core.ai.tools

import com.intellij.psi.PsiElement
import com.itangcent.easyapi.core.internal.threading.read
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.util.json.GsonUtils

/**
 * Perception tool that returns info about a single PSI method.
 *
 * Resolves the class by name — fully qualified or simple, with an optional
 * `context` for import-scope disambiguation — then walks its methods matching
 * by name + (optional) parameter count. Returns the signature, annotations,
 * parameters (with additive `typeFqn` — the
 * [com.itangcent.easyapi.core.psi.type.ResolvedType.qualifiedName] with type args
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
        "Get info about a method in a class. Class name — simple or fully " +
            "qualified — in `className`; method name in `methodName` (optional " +
            "`paramCount` narrows overloads). Returns JSON {className, name, " +
            "signature, annotations, parameters, docComment, returnType, " +
            "returnTypeFqn}. detail=\"full\" includes the method `body` " +
            "(truncated to `maxBodyChars`)."

    override val kind: ToolKind = ToolKind.PERCEPTION

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "className" to mapOf(
                "type" to "string",
                "description" to "Simple or fully qualified class name."
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
                "description" to "Optional: \"signature\" (default) or \"full\" " +
                    "(includes a truncated `body`)."
            ),
            "maxBodyChars" to mapOf(
                "type" to "integer",
                "description" to "Optional: max chars for `body` when detail=\"full\" " +
                    "(default $DEFAULT_MAX_BODY_CHARS)."
            ),
            "context" to mapOf(
                "type" to "string",
                "description" to "Optional: file path or class FQN whose import " +
                    "scope disambiguates a simple `className`."
            )
        ),
        "required" to listOf("className", "methodName")
    )

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        // `className` is the current parameter name; `fqn` is accepted as a
        // legacy alias so older persisted conversations keep working.
        val className = (args["className"] as? String) ?: (args["fqn"] as? String)
        val methodName = args["methodName"] as? String
        if (className.isNullOrBlank() || methodName.isNullOrBlank()) {
            return ToolResult.Error(
                "missing required parameter(s): className, methodName"
            )
        }
        val paramCount = (args["paramCount"] as? Number)?.toInt()
        val detail = (args["detail"] as? String)?.takeIf { it == "full" } ?: "signature"
        val maxBodyChars = (args["maxBodyChars"] as? Number)?.toInt()?.takeIf { it > 0 }
            ?: DEFAULT_MAX_BODY_CHARS
        val contextElement = PsiNameResolver.resolveContextArg(args, ctx.project)

        // All PSI access (name, signature, annotations, parameters, docComment,
        // body) must happen inside the read action — PSI element getters
        // require one. The detail="signature" fast path does NOT touch
        // psiMethod.body?.text.
        val info = read {
            val psiClass = PsiNameResolver.resolveClass(className, ctx.project, contextElement)
                ?: return@read null
            val psiMethod = psiClass.methods.firstOrNull { m ->
                m.name == methodName &&
                    (paramCount == null || m.parameterList.parameters.size == paramCount)
            } ?: return@read null
            // Implicit contextElement for type enrichment: when no explicit
            // context was supplied, use the resolved class's containing file.
            val enrichmentContext = contextElement ?: psiClass.containingFile
            PsiSignatureBuilder.methodToInfoMap(
                psiMethod = psiMethod,
                className = psiClass.qualifiedName ?: className,
                project = ctx.project,
                contextElement = enrichmentContext,
                detail = detail,
                maxBodyChars = maxBodyChars
            )
        } ?: return ToolResult.Error(
            buildNotFoundMessage(className, ctx, contextElement, methodName)
        )

        return ToolResult.Text(GsonUtils.toJson(info))
    }

    /**
     * Builds the error message for a missing class or method. When the
     * lookup was a simple name without context and [PsiNameResolver] found
     * multiple matches, the message lists the candidate FQNs so the agent can
     * retry with the fully qualified name or a `context`.
     */
    private suspend fun buildNotFoundMessage(
        className: String,
        ctx: ToolContext,
        contextElement: PsiElement?,
        methodName: String
    ): String {
        if (!className.contains('.') && contextElement == null) {
            val matches = PsiNameResolver.resolveAllClasses(className, ctx.project, null)
            if (matches.size > 1) {
                return PsiNameResolver.ambiguityMessage(className, matches)
            }
        }
        return "method not found: $className#$methodName"
    }
}
