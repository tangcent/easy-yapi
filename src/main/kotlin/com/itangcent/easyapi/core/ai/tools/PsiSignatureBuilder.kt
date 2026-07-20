package com.itangcent.easyapi.core.ai.tools

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiType
import com.itangcent.easyapi.core.psi.type.ResolvedType
import com.itangcent.easyapi.core.psi.type.TypeResolver

/**
 * Builds signatures (JSON-serializable maps and signature strings) for PSI
 * classes, methods, fields, and parameters consumed by the AI tool JSON output.
 *
 * Uses [ResolvedType.qualifiedName] as the golden standard for type FQNs —
 * i.e. resolves [PsiType] through [TypeResolver] and returns
 * [ResolvedType.ClassType.qualifiedName] for class types, or `null` for
 * primitives, arrays, wildcards, type parameters, and unresolved types.
 *
 * The returned FQN encodes type arguments inline (e.g.
 * `"com.example.Result<com.example.User>"`), so no separate
 * `typeArguments` field is emitted — minimizing token noise while preserving
 * full type information.
 *
 * When a [contextElement] is supplied, types are resolved via
 * [TypeResolver.resolveFromCanonicalText] so simple class names resolve via
 * the context element's import scope, with a fallback to the no-context path
 * ([TypeResolver.resolve]) for JDK types whose `createTypeFromText` resolution
 * may not complete via the context's import scope.
 *
 * This helper is a stateless `internal object` — it is the single place where
 * PSI elements are formatted into signatures, shared by all tools that need
 * class/method/field/parameter signatures. Tested in isolation.
 */
internal object PsiSignatureBuilder {

    // ------------------------------------------------------------------
    // Type FQN resolution
    // ------------------------------------------------------------------

    /**
     * Resolves [psiType] to its fully qualified name using [ResolvedType] as
     * the golden standard.
     *
     * @param psiType the PSI type to resolve (may be `null`).
     * @param project the IntelliJ project (used when [contextElement] is set).
     * @param contextElement optional [PsiElement] whose import scope is used
     *   to resolve simple-named types via [TypeResolver.resolveFromCanonicalText].
     * @return the [ResolvedType.ClassType.qualifiedName] for class types
     *   (including type arguments inline), or `null` for primitives, arrays,
     *   wildcards, type parameters, and unresolved types.
     */
    fun resolve(
        psiType: PsiType?,
        project: Project,
        contextElement: PsiElement? = null
    ): String? {
        // null or non-PsiClassType (primitive, array, wildcard) ⇒ no FQN.
        if (psiType == null || psiType !is PsiClassType) return null

        val withContext = resolveType(psiType, project, contextElement)
        if (withContext is ResolvedType.ClassType) {
            return withContext.qualifiedName()
        }
        // Fall back to no-context resolution for JDK types whose
        // `createTypeFromText` may not complete via the context's import scope.
        if (contextElement != null) {
            val withoutContext = resolveType(psiType, project, null)
            if (withoutContext is ResolvedType.ClassType) {
                return withoutContext.qualifiedName()
            }
        }
        return null
    }

    // ------------------------------------------------------------------
    // Field / parameter maps
    // ------------------------------------------------------------------

    /**
     * Builds the field map: `name` + `type` (presentable text) + `typeFqn`
     * (the [ResolvedType.qualifiedName] or `null`).
     */
    fun fieldToMap(
        field: PsiField,
        project: Project,
        contextElement: PsiElement?
    ): Map<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        map["name"] = field.name
        map["type"] = field.type.presentableText
        map["typeFqn"] = resolve(field.type, project, contextElement)
        return map
    }

    /**
     * Builds the parameter map: `name` + `type` (presentable text) +
     * `typeFqn` (the [ResolvedType.qualifiedName] or `null`).
     */
    fun paramToMap(
        param: PsiParameter,
        project: Project,
        contextElement: PsiElement?
    ): Map<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        map["name"] = param.name
        map["type"] = param.type.presentableText
        map["typeFqn"] = resolve(param.type, project, contextElement)
        return map
    }

    // ------------------------------------------------------------------
    // Class signature
    // ------------------------------------------------------------------

    /**
     * Builds the class info map: `name` + `fqn` + `modifiers` + `annotations`
     * + `fields` (each via [fieldToMap]) + `methods` (each via
     * [methodToSignatureMap]).
     *
     * @param psiClass the class to describe.
     * @param project the IntelliJ project (for type-FQN resolution).
     * @param contextElement optional [PsiElement] whose import scope is used
     *   to resolve field/method type FQNs.
     */
    fun classToMap(
        psiClass: PsiClass,
        project: Project,
        contextElement: PsiElement?
    ): Map<String, Any?> = mapOf(
        "name" to psiClass.name,
        "fqn" to psiClass.qualifiedName,
        "modifiers" to psiClass.modifierList?.text,
        "annotations" to psiClass.annotations.map { it.qualifiedName },
        "fields" to psiClass.fields.map {
            fieldToMap(it, project, contextElement)
        },
        "methods" to psiClass.methods.map {
            methodToSignatureMap(it, project, contextElement)
        }
    )

    // ------------------------------------------------------------------
    // Method signatures
    // ------------------------------------------------------------------

    /**
     * Builds the method signature map (used in class info's `methods` array):
     * `name` + `modifiers` + `returnType` (presentable text) + `returnTypeFqn`
     * + `parameters` (each via [paramToMap]) + `annotations`.
     *
     * Type arguments are encoded inline in the FQN string via
     * [ResolvedType.qualifiedName] — no separate `typeArguments` key.
     */
    fun methodToSignatureMap(
        psiMethod: PsiMethod,
        project: Project,
        contextElement: PsiElement?
    ): Map<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        map["name"] = psiMethod.name
        map["modifiers"] = psiMethod.modifierList?.text
        map["returnType"] = psiMethod.returnType?.presentableText
        map["returnTypeFqn"] = resolve(psiMethod.returnType, project, contextElement)
        map["parameters"] = psiMethod.parameterList.parameters.map { p ->
            paramToMap(p, project, contextElement)
        }
        map["annotations"] = psiMethod.annotations.map { it.qualifiedName }
        return map
    }

    /**
     * Builds the method info map (returned by `get_psi_method_info`):
     * `className` + `name` + `signature` (via [methodSignatureString]) +
     * `annotations` + `parameters` (each via [paramToMap]) + `docComment` +
     * `returnType` (presentable text) + `returnTypeFqn`, and — when
     * `detail="full"` — a truncated `body` field.
     *
     * @param className the owning class's FQN (used for the `className` field).
     * @param detail `"signature"` (default) omits the body; `"full"` includes
     *   a truncated `body` field.
     * @param maxBodyChars character budget for the `body` field (only when
     *   `detail="full"`).
     */
    fun methodToInfoMap(
        psiMethod: PsiMethod,
        className: String,
        project: Project,
        contextElement: PsiElement?,
        detail: String,
        maxBodyChars: Int
    ): Map<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        map["className"] = className
        map["name"] = psiMethod.name
        map["signature"] = methodSignatureString(psiMethod)
        map["annotations"] = psiMethod.annotations.map { it.qualifiedName }
        map["parameters"] = psiMethod.parameterList.parameters.map { p ->
            paramToMap(p, project, contextElement)
        }
        map["docComment"] = psiMethod.docComment?.text
        map["returnType"] = psiMethod.returnType?.presentableText
        map["returnTypeFqn"] = resolve(psiMethod.returnType, project, contextElement)
        if (detail == "full") {
            val rawBody = psiMethod.body?.text
            map["body"] = if (rawBody == null) {
                null
            } else {
                truncateBody(rawBody.removePrefix("{").removeSuffix("}").trim(), maxBodyChars)
            }
        }
        return map
    }

    /**
     * Builds the method signature string: `modifiers returnType name(paramType paramName, ...)`.
     */
    fun methodSignatureString(psiMethod: PsiMethod): String =
        buildString {
            psiMethod.modifierList?.text?.takeIf { it.isNotBlank() }?.let { append(it).append(' ') }
            psiMethod.returnType?.presentableText?.let { append(it).append(' ') }
            append(psiMethod.name).append('(')
            psiMethod.parameterList.parameters.joinTo(this, ", ") { p ->
                "${p.type.presentableText} ${p.name}"
            }
            append(')')
        }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Truncates `body` to at most `maxChars` characters (including the
     * truncation suffix). When the body fits, it is returned unchanged.
     *
     * The suffix is `" ... (truncated)"` (16 chars). The floor is
     * `suffix.length + 1` so at least 1 char of body is always shown.
     */
    private fun truncateBody(body: String, maxChars: Int): String {
        val suffix = " ... (truncated)"
        if (body.length <= maxChars) return body
        val effectiveLimit = maxOf(maxChars, suffix.length + 1)
        val bodyPortion = effectiveLimit - suffix.length
        return body.substring(0, bodyPortion) + suffix
    }

    /**
     * Resolves a [PsiType] to a [ResolvedType] using the golden standard
     * [TypeResolver]. When [contextElement] is provided, uses
     * [TypeResolver.resolveFromCanonicalText] to resolve simple names via
     * the context's import scope.
     */
    private fun resolveType(
        psiType: PsiType,
        project: Project,
        contextElement: PsiElement?
    ): ResolvedType {
        return if (contextElement != null) {
            TypeResolver.resolveFromCanonicalText(
                psiType.canonicalText, project, contextElement
            )
        } else {
            TypeResolver.resolve(psiType)
        }
    }
}
