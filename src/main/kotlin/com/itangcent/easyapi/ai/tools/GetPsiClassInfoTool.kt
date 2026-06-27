package com.itangcent.easyapi.ai.tools

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.itangcent.easyapi.core.threading.read
import com.itangcent.easyapi.util.json.GsonUtils

/**
 * Perception tool that returns PSI class info for one or more classes
 *.
 *
 * Resolves each class by FQN inside a read action and returns name, modifiers,
 * annotations, fields, and method signatures.
 *
 * Supports batch: pass `fqns` (array) to inspect multiple classes in one call.
 * Returns a JSON object for a single class, or a JSON object mapping each FQN
 * to its info (or an error string) for batch mode.
 */
class GetPsiClassInfoTool : AiTool {

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
                "description" to "Fully qualified class name."
            ),
            "fqns" to mapOf(
                "type" to "array",
                "items" to mapOf("type" to "string"),
                "description" to "Multiple fully qualified class names to inspect in one call (batch mode)."
            )
        )
    )

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val fqns = extractFqns(args)
        if (fqns.isEmpty()) return ToolResult.Error("missing parameter: provide `fqn` (string) or `fqns` (array)")

        if (fqns.size == 1) {
            val info = lookupOne(fqns[0], ctx)
                ?: return ToolResult.Error("class not found: ${fqns[0]}")
            return ToolResult.Text(GsonUtils.toJson(info))
        }
        val result = fqns.associateWith { lookupOne(it, ctx) ?: "not found" }
        return ToolResult.Text(GsonUtils.toJson(result))
    }

    private fun extractFqns(args: Map<String, Any?>): List<String> {
        val batch = args["fqns"] as? List<*>
        if (batch != null) {
            return batch.mapNotNull { it?.toString()?.takeIf { s -> s.isNotBlank() } }
        }
        val single = args["fqn"] as? String
        return if (single.isNullOrBlank()) emptyList() else listOf(single)
    }

    // All PSI access (name, fields, methods, annotations) must happen inside
    // the read action — PSI element getters require a read action.
    private suspend fun lookupOne(fqn: String, ctx: ToolContext): Map<String, Any?>? = read {
        val psiClass = JavaPsiFacade.getInstance(ctx.project)
.findClass(fqn, GlobalSearchScope.projectScope(ctx.project))
            ?: return@read null
        psiClass.toInfoMap()
    }

    private fun PsiClass.toInfoMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "fqn" to qualifiedName,
        "modifiers" to modifierList?.text,
        "annotations" to annotations.map { it.qualifiedName },
        "fields" to fields.map {
            mapOf("name" to it.name, "type" to it.type.presentableText)
        },
        "methods" to methods.map { it.toSignatureMap() }
    )

    private fun PsiMethod.toSignatureMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "modifiers" to modifierList?.text,
        "returnType" to returnType?.presentableText,
        "parameters" to parameterList.parameters.map { p ->
            mapOf("name" to p.name, "type" to p.type.presentableText)
        },
        "annotations" to annotations.map { it.qualifiedName }
    )
}
