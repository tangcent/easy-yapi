package com.itangcent.easyapi.exporter.springmvc

import com.intellij.psi.PsiParameter
import com.itangcent.easyapi.exporter.model.ParameterBinding
import com.itangcent.easyapi.psi.helper.AnnotationHelper
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
 * @param annotationHelper Helper for reading annotations
 * @param ruleEngine Rule engine for custom binding resolution
 * @see ParameterBinding for the binding types
 */
class SpringParameterBindingResolver(
    private val annotationHelper: AnnotationHelper,
    private val ruleEngine: RuleEngine
) {
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

        val rule = ruleEngine.evaluate(RuleKeys.PARAM_HTTP_TYPE, parameter)?.trim()?.lowercase()
        return when (rule) {
            "body" -> ParameterBinding.Body
            "query" -> ParameterBinding.Query
            "path" -> ParameterBinding.Path
            "header" -> ParameterBinding.Header
            "cookie" -> ParameterBinding.Cookie
            "form" -> ParameterBinding.Form
            else -> null // No annotation, no rule — caller decides default
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
