package com.itangcent.easyapi.ai.tools

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.itangcent.easyapi.core.threading.read
import com.itangcent.easyapi.util.json.GsonUtils

/**
 * Perception tool that returns info about a single PSI method.
 *
 * Resolves the class by FQN, then walks its methods matching by name + (optional)
 * parameter count. Returns the signature, annotations, parameters, and the
 * doc-comment text.
 */
class GetPsiMethodInfoTool : AiTool {

    override val name: String = "get_psi_method_info"

    override val description: String =
        "Get info about a method in a class. Returns JSON {className, name, " +
            "signature, annotations, parameters, docComment}. `paramCount` is " +
            "optional and narrows the match when a class overloads the method name."

    override val kind: ToolKind = ToolKind.PERCEPTION

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "fqn" to mapOf(
                "type" to "string",
                "description" to "Fully qualified class name."
            ),
            "methodName" to mapOf(
                "type" to "string",
                "description" to "Method name."
            ),
            "paramCount" to mapOf(
                "type" to "integer",
                "description" to "Optional: parameter count, to disambiguate overloads."
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

        // All PSI access (name, signature, annotations, parameters, docComment)
        // must happen inside the read action — PSI element getters require one.
        val info = read {
            val psiClass = JavaPsiFacade.getInstance(ctx.project)
.findClass(fqn, GlobalSearchScope.projectScope(ctx.project))
                ?: return@read null
            val psiMethod = psiClass.methods.firstOrNull { m ->
                m.name == methodName &&
                    (paramCount == null || m.parameterList.parameters.size == paramCount)
            } ?: return@read null
            psiMethod.toInfoMap(fqn)
        } ?: return ToolResult.Error("method not found: $fqn#$methodName")

        return ToolResult.Text(GsonUtils.toJson(info))
    }

    private fun PsiMethod.toInfoMap(className: String): Map<String, Any?> = mapOf(
        "className" to className,
        "name" to name,
        "signature" to signatureString(),
        "annotations" to annotations.map { it.qualifiedName },
        "parameters" to parameterList.parameters.map { p ->
            mapOf("name" to p.name, "type" to p.type.presentableText)
        },
        "docComment" to docComment?.text
    )

    private fun PsiMethod.signatureString(): String =
        buildString {
            modifierList?.text?.takeIf { it.isNotBlank() }?.let { append(it).append(' ') }
            returnType?.presentableText?.let { append(it).append(' ') }
            append(name).append('(')
            parameterList.parameters.joinTo(this, ", ") { p ->
                "${p.type.presentableText} ${p.name}"
            }
            append(')')
        }
}
