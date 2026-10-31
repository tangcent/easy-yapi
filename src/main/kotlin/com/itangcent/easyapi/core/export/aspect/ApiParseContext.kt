package com.itangcent.easyapi.core.export.aspect

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiParameter
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.psi.type.ResolvedMethod

/**
 * Context handed to class-level aspect hooks.
 *
 * @param psiClass the class being exported
 * @param frameworkName the owning exporter's `ClassExporter.frameworkName`
 */
data class ClassParseContext(
    val psiClass: PsiClass,
    val frameworkName: String
)

/**
 * Context handed to method-level aspect hooks.
 *
 * @param resolvedMethod the parsed method, with generic type arguments substituted
 * @param frameworkName the owning exporter's `ClassExporter.frameworkName`
 */
data class MethodParseContext(
    val resolvedMethod: ResolvedMethod,
    val frameworkName: String
)

/**
 * Context handed to parameter-level aspect hooks.
 *
 * Deliberately carries only the raw [PsiParameter] and the framework name: no
 * parsed method information is exposed, so the exporter functions do not need
 * extra parameters to feed a context they cannot construct.
 */
data class ParamParseContext(
    val parameter: PsiParameter,
    val frameworkName: String
)

/**
 * Context handed to [ApiParseAspect.afterEndpoint].
 *
 * @param endpoint the freshly built endpoint; mutable fields may be rewritten in place
 * @param frameworkName the owning exporter's `ClassExporter.frameworkName`
 * @param resolvedMethod the parsed method that produced [endpoint]
 */
data class EndpointParseContext(
    val endpoint: ApiEndpoint,
    val frameworkName: String,
    val resolvedMethod: ResolvedMethod
)
