package com.itangcent.easyapi.exporter.jaxrs

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.itangcent.easyapi.psi.helper.AnnotationHelper

/**
 * Content types for JAX-RS resources.
 *
 * @param consumes Media types the resource accepts (from @Consumes)
 * @param produces Media types the resource produces (from @Produces)
 */
data class JaxRsContentTypes(val consumes: List<String>, val produces: List<String>)

/**
 * Resolves content types from JAX-RS @Consumes and @Produces annotations.
 *
 * Content types can be specified at both class and method level:
 * - Method-level annotations override class-level
 * - Multiple media types can be specified
 *
 * ## Example
 * ```java
 * @Consumes("application/json")
 * @Produces("application/json")
 * class UserResource {
 *     @GET
 *     @Produces({"application/json", "application/xml"})
 *     public User getUser() { ... }
 * }
 * ```
 *
 * @see JaxRsClassExporter for usage in export process
 */
class JaxRsContentTypeResolver(
    private val annotationHelper: AnnotationHelper
) {
    suspend fun resolve(psiClass: PsiClass, psiMethod: PsiMethod): JaxRsContentTypes {
        val classConsumes = attrList(psiClass, "Consumes")
        val classProduces = attrList(psiClass, "Produces")
        val methodConsumes = attrList(psiMethod, "Consumes")
        val methodProduces = attrList(psiMethod, "Produces")
        return JaxRsContentTypes(
            consumes = if (methodConsumes.isNotEmpty()) methodConsumes else classConsumes,
            produces = if (methodProduces.isNotEmpty()) methodProduces else classProduces
        )
    }

    private suspend fun attrList(target: Any, simpleName: String): List<String> {
        val fqn = listOf("javax.ws.rs.$simpleName", "jakarta.ws.rs.$simpleName")
        val raw = when (target) {
            is PsiClass -> fqn.firstNotNullOfOrNull { annotationHelper.findAttr(target, it, "value") }
            is PsiMethod -> fqn.firstNotNullOfOrNull { annotationHelper.findAttr(target, it, "value") }
            else -> null
        } ?: return emptyList()

        val values = when (raw) {
            is String -> listOf(raw)
            is List<*> -> raw.filterIsInstance<String>()
            else -> listOf(raw.toString())
        }
        return values.map { it.trim() }.filter { it.isNotEmpty() }
    }
}