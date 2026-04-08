package com.itangcent.easyapi.exporter.springmvc

import com.intellij.psi.PsiParameter
import com.itangcent.easyapi.exporter.model.ParameterBinding
import com.itangcent.easyapi.psi.helper.AnnotationHelper
import com.itangcent.easyapi.psi.type.ResolvedMethod
import com.itangcent.easyapi.psi.type.searchParameterAnnotation
import com.itangcent.easyapi.psi.type.SpecialTypeHandler
import com.itangcent.easyapi.rule.RuleKeys
import com.itangcent.easyapi.rule.engine.RuleEngine

/**
 * Resolves parameter binding types for Spring MVC method parameters.
 *
 * Determines how each method parameter should be bound in an HTTP request:
 * - `@RequestBody` → Body
 * - `@RequestParam` → Query (or Form for file types)
 * - `@PathVariable` → Path
 * - `@RequestHeader` → Header
 * - `@CookieValue` → Cookie
 * - `@ModelAttribute` / `@RequestPart` → Form
 *
 * Also supports rule-based binding via `param.http.type` rule key.
 *
 * ## Inheritance Support
 * When a class implements an interface, parameter annotations on the interface method
 * are inherited by the implementation. This resolver walks up the super method chain
 * to find annotations declared on interface method parameters.
 *
 * @param annotationHelper Helper for reading annotations
 * @param ruleEngine Rule engine for custom binding resolution
 * @see ParameterBinding for the binding types
 */
class SpringParameterBindingResolver(
    private val annotationHelper: AnnotationHelper,
    private val ruleEngine: RuleEngine
) {
    /**
     * Resolves parameter binding for a parameter within a resolved method context.
     * This version supports inheritance - it will search for annotations on the parameter
     * in super methods (interfaces, base classes) if not found on the parameter directly.
     *
     * @param parameter The PSI parameter to resolve
     * @param resolvedMethod The resolved method context for inheritance support
     * @param parameterIndex The index of the parameter in the method's parameter list
     * @return The resolved parameter binding, or null if no binding annotation found
     */
    suspend fun resolve(
        parameter: PsiParameter,
        resolvedMethod: ResolvedMethod,
        parameterIndex: Int
    ): ParameterBinding? {
        val typeText = parameter.type.canonicalText
        if (isIgnoredType(typeText)) return ParameterBinding.Ignored

        if (hasParameterAnnotation(resolvedMethod, parameterIndex, SpringMvcConstants.Annotations.REQUEST_BODY)) {
            return ParameterBinding.Body
        }

        if (hasParameterAnnotation(resolvedMethod, parameterIndex, SpringMvcConstants.Annotations.REQUEST_PARAM)) {
            if (isFileType(typeText)) return ParameterBinding.Form
            return ParameterBinding.Query
        }

        if (hasParameterAnnotation(resolvedMethod, parameterIndex, SpringMvcConstants.Annotations.REQUEST_PART)) {
            return ParameterBinding.Form
        }

        if (hasParameterAnnotation(resolvedMethod, parameterIndex, SpringMvcConstants.Annotations.PATH_VARIABLE)) {
            return ParameterBinding.Path
        }
        if (hasParameterAnnotation(resolvedMethod, parameterIndex, SpringMvcConstants.Annotations.REQUEST_HEADER)) {
            return ParameterBinding.Header
        }
        if (hasParameterAnnotation(resolvedMethod, parameterIndex, SpringMvcConstants.Annotations.COOKIE_VALUE)) {
            return ParameterBinding.Cookie
        }
        if (hasParameterAnnotation(resolvedMethod, parameterIndex, SpringMvcConstants.Annotations.MODEL_ATTRIBUTE)) {
            return ParameterBinding.Form
        }
        if (hasParameterAnnotation(resolvedMethod, parameterIndex, SpringMvcConstants.Annotations.SESSION_ATTRIBUTE)) {
            return ParameterBinding.Ignored
        }

        return resolveByRule(parameter)
    }

    /**
     * Resolves parameter binding for a standalone parameter (no inheritance support).
     * Use [resolve] with [ResolvedMethod] for full inheritance support.
     *
     * @param parameter The PSI parameter to resolve
     * @return The resolved parameter binding, or null if no binding annotation found
     */
    suspend fun resolve(parameter: PsiParameter): ParameterBinding? {
        val typeText = parameter.type.canonicalText
        if (isIgnoredType(typeText)) return ParameterBinding.Ignored

        if (annotationHelper.hasAnn(parameter, SpringMvcConstants.Annotations.REQUEST_BODY)) {
            return ParameterBinding.Body
        }

        if (annotationHelper.hasAnn(parameter, SpringMvcConstants.Annotations.REQUEST_PARAM)) {
            if (isFileType(typeText)) return ParameterBinding.Form
            return ParameterBinding.Query
        }

        if (annotationHelper.hasAnn(parameter, SpringMvcConstants.Annotations.REQUEST_PART)) {
            return ParameterBinding.Form
        }

        if (annotationHelper.hasAnn(parameter, SpringMvcConstants.Annotations.PATH_VARIABLE)) {
            return ParameterBinding.Path
        }
        if (annotationHelper.hasAnn(parameter, SpringMvcConstants.Annotations.REQUEST_HEADER)) {
            return ParameterBinding.Header
        }
        if (annotationHelper.hasAnn(parameter, SpringMvcConstants.Annotations.COOKIE_VALUE)) {
            return ParameterBinding.Cookie
        }
        if (annotationHelper.hasAnn(parameter, SpringMvcConstants.Annotations.MODEL_ATTRIBUTE)) {
            return ParameterBinding.Form
        }
        if (annotationHelper.hasAnn(parameter, SpringMvcConstants.Annotations.SESSION_ATTRIBUTE)) {
            return ParameterBinding.Ignored
        }

        return resolveByRule(parameter)
    }

    /**
     * Checks if a parameter has the specified annotation, searching through the method hierarchy.
     */
    private fun hasParameterAnnotation(
        resolvedMethod: ResolvedMethod,
        parameterIndex: Int,
        annotationFqn: String
    ): Boolean {
        return resolvedMethod.searchParameterAnnotation(parameterIndex, annotationFqn) != null
    }

    private suspend fun resolveByRule(parameter: PsiParameter): ParameterBinding? {
        val rule = ruleEngine.evaluate(RuleKeys.PARAM_HTTP_TYPE, parameter)?.trim()?.lowercase()
        return when (rule) {
            "body" -> ParameterBinding.Body
            "query" -> ParameterBinding.Query
            "path" -> ParameterBinding.Path
            "header" -> ParameterBinding.Header
            "cookie" -> ParameterBinding.Cookie
            "form" -> ParameterBinding.Form
            else -> null
        }
    }

    private fun isFileType(typeText: String): Boolean {
        if (SpecialTypeHandler.isFileTypeCanonical(typeText)) return true
        return typeText.contains("MultipartFile") || typeText.contains("Part")
    }

    private fun isIgnoredType(typeText: String): Boolean {
        val t = typeText.removeSuffix("?")
        return t.endsWith(SpringMvcConstants.BINDING_RESULT) ||
                t.endsWith(SpringMvcConstants.MODEL) ||
                t.endsWith(SpringMvcConstants.MODEL_MAP) ||
                t.endsWith(SpringMvcConstants.MODEL_AND_VIEW) ||
                t.endsWith(SpringMvcConstants.Javax.HTTP_SERVLET_REQUEST) ||
                t.endsWith(SpringMvcConstants.Javax.HTTP_SERVLET_RESPONSE) ||
                t.endsWith(SpringMvcConstants.Javax.HTTP_SERVLET_SESSION) ||
                t.endsWith(SpringMvcConstants.Jakarta.HTTP_SERVLET_REQUEST) ||
                t.endsWith(SpringMvcConstants.Jakarta.HTTP_SERVLET_RESPONSE) ||
                t.endsWith(SpringMvcConstants.Jakarta.HTTP_SERVLET_SESSION)
    }
}
