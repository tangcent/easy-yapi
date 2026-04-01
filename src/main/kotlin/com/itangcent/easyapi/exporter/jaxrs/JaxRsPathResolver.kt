package com.itangcent.easyapi.exporter.jaxrs

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.itangcent.easyapi.psi.helper.AnnotationHelper

/**
 * Resolves API paths from JAX-RS @Path annotations.
 *
 * Combines class-level and method-level @Path values to form
 * the complete endpoint path.
 *
 * ## Path Resolution Rules
 * - Class @Path provides the base path
 * - Method @Path appends to the class path
 * - Leading/trailing slashes are normalized
 *
 * ## Example
 * ```java
 * @Path("/api")
 * class UserController {
 *     @Path("/users")
 *     @GET
 *     public List<User> getUsers() { ... }
 * }
 * // Result: /api/users
 * ```
 *
 * @see JaxRsClassExporter for usage in export process
 */
class JaxRsPathResolver(
    private val annotationHelper: AnnotationHelper
) {
    suspend fun classPath(psiClass: PsiClass): String {
        return (annotationHelper.findAttrAsString(psiClass, "javax.ws.rs.Path", "value")
            ?: annotationHelper.findAttrAsString(psiClass, "jakarta.ws.rs.Path", "value")
            ?: "").trim()
    }

    suspend fun methodPath(psiMethod: PsiMethod): String {
        return (annotationHelper.findAttrAsString(psiMethod, "javax.ws.rs.Path", "value")
            ?: annotationHelper.findAttrAsString(psiMethod, "jakarta.ws.rs.Path", "value")
            ?: "").trim()
    }

    suspend fun resolve(psiClass: PsiClass, psiMethod: PsiMethod): String {
        return normalize(join(classPath(psiClass), methodPath(psiMethod)))
    }

    private fun join(a: String, b: String): String {
        if (a.isBlank()) return b
        if (b.isBlank()) return a
        return a.trimEnd('/') + "/" + b.trimStart('/')
    }

    private fun normalize(path: String): String {
        return "/" + path.trim('/')
    }
}