package com.itangcent.easyapi.framework.springmvc

import com.intellij.psi.PsiParameter
import com.itangcent.easyapi.core.export.ParameterBinding
import com.itangcent.easyapi.core.internal.threading.read
import com.itangcent.easyapi.core.psi.helper.AnnotationHelper
import com.itangcent.easyapi.core.psi.helper.UnifiedAnnotationHelper
import com.itangcent.easyapi.core.psi.type.ResolvedMethod
import com.itangcent.easyapi.core.psi.type.searchParameterAnnotation

/**
 * Spring's own `required` semantics for a method parameter — the last layer of
 * `param.required` rule > annotation value > framework default (issue #1458).
 *
 * The rules mirror Spring MVC's argument resolvers:
 * - `@RequestParam`, `@RequestHeader`, `@CookieValue`, `@PathVariable`,
 *   `@RequestPart` are required unless `required = false` is declared, a
 *   `defaultValue` is supplied (which implicitly clears `required`), or the
 *   parameter is `java.util.Optional`.
 * - A parameter without any binding annotation is resolved by
 *   `RequestParamMethodArgumentResolver` in *default resolution* mode, whose
 *   fallback `NamedValueInfo` is `required = false` — so it stays optional.
 * - `@ModelAttribute` binds a whole bean and is not a required value; its
 *   individual fields are decided by `field.required`.
 *
 * Annotations declared on a super method (an interface method, for instance) are
 * honoured, matching [SpringParameterBindingResolver] which resolves the binding
 * through [ResolvedMethod.searchParameterAnnotation].
 */
object SpringParamRequired {

    private val annotationHelper: AnnotationHelper = UnifiedAnnotationHelper()

    /**
     * @param parameter the method parameter
     * @param binding the binding resolved for [parameter]
     * @param resolvedMethod the owning method, used to look annotations up the
     *        super-method chain
     * @param parameterIndex index of [parameter] in [resolvedMethod]
     */
    suspend fun resolve(
        parameter: PsiParameter,
        binding: ParameterBinding?,
        resolvedMethod: ResolvedMethod? = null,
        parameterIndex: Int = -1
    ): Boolean {
        return when (binding) {
            ParameterBinding.Header ->
                requiredUnlessOptional(parameter, findAttrs(parameter, resolvedMethod, parameterIndex, REQUEST_HEADER))
            ParameterBinding.Path ->
                requiredUnlessOptional(parameter, findAttrs(parameter, resolvedMethod, parameterIndex, PATH_VARIABLE))
            ParameterBinding.Cookie ->
                requiredUnlessOptional(parameter, findAttrs(parameter, resolvedMethod, parameterIndex, COOKIE_VALUE))
            ParameterBinding.Query, ParameterBinding.Form -> {
                val requestParam = findAttrs(parameter, resolvedMethod, parameterIndex, REQUEST_PARAM)
                if (requestParam != null) return requiredUnlessOptional(parameter, requestParam)
                val requestPart = findAttrs(parameter, resolvedMethod, parameterIndex, REQUEST_PART)
                if (requestPart != null) return requiredUnlessOptional(parameter, requestPart)
                // No binding annotation (default resolution) or @ModelAttribute.
                false
            }
            else -> false
        }
    }

    /**
     * The annotation is present: honour its `required` attribute (default `true`),
     * then Spring's two implicit opt-outs — a declared default value and an
     * `Optional` parameter type.
     */
    private suspend fun requiredUnlessOptional(parameter: PsiParameter, attrs: Map<String, Any?>?): Boolean {
        if (attrs == null) return false
        if (isFalse(attrs["required"])) return false
        if (hasDefaultValue(attrs)) return false
        if (isOptionalType(parameter)) return false
        return true
    }

    private fun isFalse(value: Any?): Boolean = value?.toString()?.trim()?.equals("false", ignoreCase = true) == true

    private fun hasDefaultValue(attrs: Map<String, Any?>): Boolean {
        val raw = attrs["defaultValue"]?.toString() ?: return false
        val value = raw.trim()
        // `ValueConstants.DEFAULT_NONE` is the annotation's "unset" marker.
        return value.isNotEmpty() && !value.contains('\u0000')
    }

    private suspend fun isOptionalType(parameter: PsiParameter): Boolean = read {
        val raw = parameter.type.canonicalText.removeSuffix("?")
        when (raw.substringBefore('<').trim()) {
            OPTIONAL_TYPE, "Optional" -> true
            else -> false
        }
    }

    /**
     * Reads the attributes of [fqn] from the parameter itself, falling back to the
     * super-method chain so interface-declared annotations are honoured.
     */
    private suspend fun findAttrs(
        parameter: PsiParameter,
        resolvedMethod: ResolvedMethod?,
        parameterIndex: Int,
        fqn: String
    ): Map<String, Any?>? {
        annotationHelper.findAnnMap(parameter, fqn)?.let { return it }
        if (resolvedMethod == null || parameterIndex < 0) return null
        val ann = read { resolvedMethod.searchParameterAnnotation(parameterIndex, fqn) } ?: return null
        return read {
            ann.parameterList.attributes.associate { attribute ->
                (attribute.name ?: "value") to attribute.value?.text?.trim('"')
            }
        }
    }

    private const val OPTIONAL_TYPE = "java.util.Optional"

    private val REQUEST_PARAM = SpringMvcConstants.Annotations.REQUEST_PARAM
    private val REQUEST_PART = SpringMvcConstants.Annotations.REQUEST_PART
    private val PATH_VARIABLE = SpringMvcConstants.Annotations.PATH_VARIABLE
    private val REQUEST_HEADER = SpringMvcConstants.Annotations.REQUEST_HEADER
    private val COOKIE_VALUE = SpringMvcConstants.Annotations.COOKIE_VALUE
}
