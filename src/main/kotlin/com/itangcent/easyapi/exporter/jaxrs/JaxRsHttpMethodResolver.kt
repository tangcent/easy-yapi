package com.itangcent.easyapi.exporter.jaxrs

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import com.itangcent.easyapi.core.threading.IdeDispatchers
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.psi.helper.AnnotationHelper
import kotlinx.coroutines.withContext

/**
 * Resolves HTTP methods from JAX-RS method annotations.
 *
 * Supports both standard method annotations (@GET, @POST, etc.)
 * and custom annotations meta-annotated with @HttpMethod.
 *
 * ## Supported Annotations
 * - `@GET`, `@POST`, `@PUT`, `@DELETE`, `@PATCH`, `@HEAD`, `@OPTIONS`
 * - Custom annotations annotated with `@HttpMethod`
 *
 * @see JaxRsClassExporter for usage in export process
 */
class JaxRsHttpMethodResolver(
    private val annotationHelper: AnnotationHelper
) {
    suspend fun resolve(psiMethod: PsiMethod): HttpMethod? = withContext(IdeDispatchers.ReadAction) {
        direct(psiMethod)?.let { return@withContext it }
        val anns = annotationHelper.findAnnMaps(psiMethod, "javax.ws.rs.HttpMethod").orEmpty() +
                annotationHelper.findAnnMaps(psiMethod, "jakarta.ws.rs.HttpMethod").orEmpty()
        if (anns.isNotEmpty()) return@withContext null
        val allAnnotations = (psiMethod as? PsiModifierListOwner)?.annotations.orEmpty()
        return@withContext resolveFromMeta(allAnnotations)
    }

    private suspend fun direct(psiMethod: PsiMethod): HttpMethod? {
        if (annotationHelper.hasAnn(psiMethod, "javax.ws.rs.GET") || annotationHelper.hasAnn(
                psiMethod,
                "jakarta.ws.rs.GET"
            )
        ) return HttpMethod.GET
        if (annotationHelper.hasAnn(psiMethod, "javax.ws.rs.POST") || annotationHelper.hasAnn(
                psiMethod,
                "jakarta.ws.rs.POST"
            )
        ) return HttpMethod.POST
        if (annotationHelper.hasAnn(psiMethod, "javax.ws.rs.PUT") || annotationHelper.hasAnn(
                psiMethod,
                "jakarta.ws.rs.PUT"
            )
        ) return HttpMethod.PUT
        if (annotationHelper.hasAnn(psiMethod, "javax.ws.rs.DELETE") || annotationHelper.hasAnn(
                psiMethod,
                "jakarta.ws.rs.DELETE"
            )
        ) return HttpMethod.DELETE
        if (annotationHelper.hasAnn(psiMethod, "javax.ws.rs.PATCH") || annotationHelper.hasAnn(
                psiMethod,
                "jakarta.ws.rs.PATCH"
            )
        ) return HttpMethod.PATCH
        if (annotationHelper.hasAnn(psiMethod, "javax.ws.rs.HEAD") || annotationHelper.hasAnn(
                psiMethod,
                "jakarta.ws.rs.HEAD"
            )
        ) return HttpMethod.HEAD
        if (annotationHelper.hasAnn(psiMethod, "javax.ws.rs.OPTIONS") || annotationHelper.hasAnn(
                psiMethod,
                "jakarta.ws.rs.OPTIONS"
            )
        ) return HttpMethod.OPTIONS
        return null
    }

    private fun resolveFromMeta(annotations: Array<out PsiAnnotation>): HttpMethod? {
        for (ann in annotations) {
            val meta = ann.resolveAnnotationType() ?: continue
            val hasHttpMethod =
                meta.annotations.any { it.qualifiedName == "javax.ws.rs.HttpMethod" || it.qualifiedName == "jakarta.ws.rs.HttpMethod" }
            if (!hasHttpMethod) continue
            val name = meta.qualifiedName ?: meta.name ?: continue
            val last = name.substringAfterLast('.').uppercase()
            runCatching { return HttpMethod.valueOf(last) }.getOrNull()
        }
        return null
    }
}