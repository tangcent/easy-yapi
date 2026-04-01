package com.itangcent.easyapi.exporter.core

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiModifierListOwner
import com.itangcent.easyapi.core.threading.IdeDispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Resolves meta-annotations: detects when a custom annotation is itself annotated
 * with a standard framework annotation.
 *
 * For example, given:
 * ```java
 * @RestController
 * public @interface CustomRestController {}
 * ```
 * Calling [hasMetaAnnotation] on a class annotated with @CustomRestController
 * will return true for "org.springframework.web.bind.annotation.RestController".
 */
object MetaAnnotationResolver {

    private const val CACHE_MAX_SIZE: Long = 1000
    private const val CACHE_EXPIRE_MINUTES: Long = 10

    /**
     * Cache: annotation qualified name -> set of target annotation FQNs it transitively carries.
     * Uses Guava Cache with:
     * - Maximum size limit to prevent memory bloat
     * - Time-based expiration to handle project changes (new/removed annotations)
     */
    private val cache: Cache<String, Boolean> = CacheBuilder.newBuilder()
        .maximumSize(CACHE_MAX_SIZE)
        .expireAfterWrite(CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES)
        .build()

    /**
     * Check whether [element] carries (directly or via meta-annotation) any of [targetAnnotations].
     *
     * Annotation types (e.g., `@interface CustomRestController`) are always excluded —
     * they define annotations, not API classes.
     */
    suspend fun hasMetaAnnotation(
        element: PsiModifierListOwner,
        targetAnnotations: Set<String>
    ): Boolean = withContext(IdeDispatchers.ReadAction) {
        // Annotation types are never API classes, even if meta-annotated with framework annotations
        if (element is PsiClass && element.isAnnotationType) return@withContext false

        val annotations = element.annotations
        annotations.any { ann ->
            val fqn = ann.qualifiedName ?: return@any false
            // Direct match
            if (fqn in targetAnnotations) return@any true
            // Meta-annotation check (cached)
            isMetaAnnotated(fqn, ann.resolveAnnotationType(), targetAnnotations)
        }
    }

    /**
     * Find which of [targetAnnotations] the [element] carries (directly or via meta-annotation).
     * Returns the first matching target annotation FQN, or null.
     *
     * Annotation types are always excluded.
     */
    suspend fun findMetaAnnotation(
        element: PsiModifierListOwner,
        targetAnnotations: Set<String>
    ): String? = withContext(IdeDispatchers.ReadAction) {
        if (element is PsiClass && element.isAnnotationType) return@withContext null

        val annotations = element.annotations
        for (ann in annotations) {
            val fqn = ann.qualifiedName ?: continue
            if (fqn in targetAnnotations) return@withContext fqn
            val annClass = ann.resolveAnnotationType() ?: continue
            for (target in targetAnnotations) {
                if (isMetaAnnotated(fqn, annClass, setOf(target))) {
                    return@withContext target
                }
            }
        }
        null
    }

    private fun isMetaAnnotated(
        annotationFqn: String,
        annotationClass: PsiClass?,
        targetAnnotations: Set<String>
    ): Boolean {
        if (annotationClass == null) return false
        return targetAnnotations.any { target ->
            val cacheKey = "$annotationFqn->$target"
            cache.get(cacheKey) {
                resolveMetaAnnotation(annotationClass, target, mutableSetOf())
            }
        }
    }

    private fun resolveMetaAnnotation(
        annotationClass: PsiClass,
        target: String,
        visited: MutableSet<String>
    ): Boolean {
        val fqn = annotationClass.qualifiedName ?: return false
        if (!visited.add(fqn)) return false // cycle guard
        // Skip java.lang.annotation.* to avoid infinite recursion
        if (fqn.startsWith("java.lang.annotation.")) return false
        if (fqn.startsWith("kotlin.annotation.")) return false

        val annotations = annotationClass.annotations
        for (ann in annotations) {
            val annFqn = ann.qualifiedName ?: continue
            if (annFqn == target) return true
            val nested = ann.resolveAnnotationType() ?: continue
            if (resolveMetaAnnotation(nested, target, visited)) return true
        }
        return false
    }

    /**
     * Clear the cache (useful for testing or project reload).
     */
    fun clearCache() {
        cache.invalidateAll()
    }
}
