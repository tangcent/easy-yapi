package com.itangcent.easyapi.framework.springmvc

import com.intellij.psi.PsiParameter
import com.itangcent.easyapi.core.export.ParameterBinding
import com.itangcent.easyapi.core.internal.threading.read
import com.itangcent.easyapi.core.psi.helper.AnnotationHelper
import com.itangcent.easyapi.core.psi.helper.UnifiedAnnotationHelper
import com.itangcent.easyapi.core.psi.type.ResolvedMethod
import com.itangcent.easyapi.core.psi.type.superMethods

/**
 * The name Spring binds a method parameter by — the layer between the
 * `param.name` rule and the Java/Kotlin parameter name.
 *
 * Spring MVC's argument resolvers read the request value named by the binding
 * annotation's `value` / `name` attribute (aliases of each other), and fall back
 * to the parameter name only when neither is set:
 * - `@RequestParam`, `@RequestPart` → the query/form field name
 * - `@PathVariable` → the URI template variable, i.e. the `{placeholder}` in the path
 * - `@RequestHeader` → the header name
 * - `@CookieValue` → the cookie name
 *
 * `@ModelAttribute` is not covered: its name is the model attribute key, not a
 * request field — the bean's own fields are the request fields.
 *
 * Annotations declared on a super method (an interface method, for instance) are
 * honoured, matching [SpringParameterBindingResolver] and [SpringParamRequired].
 */
object SpringParamName {

    private val annotationHelper: AnnotationHelper = UnifiedAnnotationHelper()

    /**
     * @param parameter the method parameter
     * @param binding the binding resolved for [parameter]
     * @param resolvedMethod the owning method, used to look annotations up the
     *        super-method chain
     * @param parameterIndex index of [parameter] in [resolvedMethod]
     * @return the name declared on the binding annotation, or `null` when the
     *         annotation is absent or names nothing
     */
    suspend fun resolve(
        parameter: PsiParameter,
        binding: ParameterBinding?,
        resolvedMethod: ResolvedMethod? = null,
        parameterIndex: Int = -1
    ): String? {
        val annotations = when (binding) {
            ParameterBinding.Header -> listOf(SpringMvcConstants.Annotations.REQUEST_HEADER)
            ParameterBinding.Path -> listOf(SpringMvcConstants.Annotations.PATH_VARIABLE)
            ParameterBinding.Cookie -> listOf(SpringMvcConstants.Annotations.COOKIE_VALUE)
            ParameterBinding.Query, ParameterBinding.Form -> listOf(
                SpringMvcConstants.Annotations.REQUEST_PARAM,
                SpringMvcConstants.Annotations.REQUEST_PART
            )
            else -> return null
        }
        for (fqn in annotations) {
            val attrs = findAttrs(parameter, resolvedMethod, parameterIndex, fqn) ?: continue
            return declaredName(attrs)
        }
        return null
    }

    private fun declaredName(attrs: Map<String, Any?>): String? =
        sequenceOf(attrs["value"], attrs["name"])
            .mapNotNull { it?.toString()?.trim() }
            .firstOrNull { it.isNotEmpty() }

    /**
     * Reads the attributes of [fqn] from the parameter itself, falling back to the
     * parameter at the same index on each super method.
     */
    private suspend fun findAttrs(
        parameter: PsiParameter,
        resolvedMethod: ResolvedMethod?,
        parameterIndex: Int,
        fqn: String
    ): Map<String, Any?>? {
        annotationHelper.findAnnMap(parameter, fqn)?.let { return it }
        if (resolvedMethod == null || parameterIndex < 0) return null
        val superParameters = read {
            resolvedMethod.superMethods()
                .mapNotNull { it.psiMethod.parameterList.parameters.getOrNull(parameterIndex) }
                .toList()
        }
        for (superParameter in superParameters) {
            annotationHelper.findAnnMap(superParameter, fqn)?.let { return it }
        }
        return null
    }
}
