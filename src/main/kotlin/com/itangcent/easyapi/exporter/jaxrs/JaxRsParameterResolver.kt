package com.itangcent.easyapi.exporter.jaxrs

import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiField
import com.intellij.psi.PsiParameter
import com.itangcent.easyapi.exporter.model.ApiParameter
import com.itangcent.easyapi.exporter.model.ParameterBinding
import com.itangcent.easyapi.psi.helper.AnnotationHelper
import com.itangcent.easyapi.psi.type.JsonType

/**
 * Resolves API parameters from JAX-RS parameter annotations.
 *
 * Supports various parameter binding annotations:
 * - `@QueryParam` - URL query parameters
 * - `@PathParam` - Path parameters
 * - `@HeaderParam` - HTTP headers
 * - `@CookieParam` - Cookies
 * - `@FormParam` - Form data
 * - `@BeanParam` - Aggregated parameters from a bean class
 *
 * ## Parameter Resolution
 * ```java
 * public void getUser(
 *     @QueryParam("id") String id,      // Query parameter
 *     @HeaderParam("Auth") String auth, // Header
 *     @BeanParam UserFilter filter      // Expanded bean
 * ) { ... }
 * ```
 *
 * @see JaxRsClassExporter for usage in export process
 * @see ParameterBinding for parameter types
 */
class JaxRsParameterResolver(
    private val annotationHelper: AnnotationHelper
) {
    suspend fun resolve(parameter: PsiParameter): List<ApiParameter> {
        val beanParam = isBeanParam(parameter)
        if (beanParam) {
            return expandBeanParam(parameter)
        }

        val name = parameter.name ?: "param"
        val type = JsonType.fromPsiType(parameter.type)

        return listOf(
            ApiParameter(
                name = resolveName(parameter) ?: name,
                type = type,
                required = false,
                binding = resolveBinding(parameter)
            )
        )
    }

    private suspend fun resolveBinding(parameter: PsiParameter): ParameterBinding? {
        if (hasAny(parameter, "QueryParam")) return ParameterBinding.Query
        if (hasAny(parameter, "PathParam")) return ParameterBinding.Path
        if (hasAny(parameter, "HeaderParam")) return ParameterBinding.Header
        if (hasAny(parameter, "CookieParam")) return ParameterBinding.Cookie
        if (hasAny(parameter, "FormParam")) return ParameterBinding.Form
        return ParameterBinding.Body
    }

    private suspend fun resolveName(parameter: PsiParameter): String? {
        return findValue(parameter, "QueryParam")
            ?: findValue(parameter, "PathParam")
            ?: findValue(parameter, "HeaderParam")
            ?: findValue(parameter, "CookieParam")
            ?: findValue(parameter, "FormParam")
    }

    private suspend fun isBeanParam(parameter: PsiParameter): Boolean {
        return hasAny(parameter, "BeanParam")
    }

    private suspend fun expandBeanParam(parameter: PsiParameter): List<ApiParameter> {
        val type = parameter.type as? PsiClassType ?: return emptyList()
        val psiClass = type.resolve() ?: return emptyList()
        val params = ArrayList<ApiParameter>()
        for (field in psiClass.allFields) {
            params.addAll(expandBeanField(field))
        }
        return params
    }

    private suspend fun expandBeanField(field: PsiField): List<ApiParameter> {
        val name = field.name ?: return emptyList()
        val type = JsonType.fromPsiType(field.type)
        val binding = when {
            hasAny(field, "QueryParam") -> ParameterBinding.Query
            hasAny(field, "PathParam") -> ParameterBinding.Path
            hasAny(field, "HeaderParam") -> ParameterBinding.Header
            hasAny(field, "CookieParam") -> ParameterBinding.Cookie
            hasAny(field, "FormParam") -> ParameterBinding.Form
            else -> ParameterBinding.Query
        }
        val alias = findValue(field, "QueryParam")
            ?: findValue(field, "PathParam")
            ?: findValue(field, "HeaderParam")
            ?: findValue(field, "CookieParam")
            ?: findValue(field, "FormParam")
        return listOf(ApiParameter(name = alias ?: name, type = type, binding = binding))
    }

    private suspend fun hasAny(target: Any, simpleName: String): Boolean {
        val fqn = listOf(
            "javax.ws.rs.$simpleName",
            "jakarta.ws.rs.$simpleName"
        )
        return when (target) {
            is PsiParameter -> fqn.any { annotationHelper.hasAnn(target, it) }
            is PsiField -> fqn.any { annotationHelper.hasAnn(target, it) }
            else -> false
        }
    }

    private suspend fun findValue(target: Any, simpleName: String): String? {
        val fqn = listOf(
            "javax.ws.rs.$simpleName",
            "jakarta.ws.rs.$simpleName"
        )
        return when (target) {
            is PsiParameter -> fqn.firstNotNullOfOrNull { annotationHelper.findAttrAsString(target, it, "value") }
            is PsiField -> fqn.firstNotNullOfOrNull { annotationHelper.findAttrAsString(target, it, "value") }
            else -> null
        }?.trim()?.takeIf { it.isNotEmpty() }
    }
}