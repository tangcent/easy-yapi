package com.itangcent.easyapi.psi.helper

import com.intellij.psi.PsiElement

/**
 * Helper interface for working with annotations on PSI elements.
 *
 * Provides methods to:
 * - Check for annotation presence
 * - Extract annotation attributes
 * - Parse annotation values
 *
 * Implementations handle language-specific annotation access
 * (Java, Kotlin, Groovy).
 *
 * @see UnifiedAnnotationHelper for the unified implementation
 */
interface AnnotationHelper {
    /**
     * Checks if the element has the specified annotation.
     *
     * @param element The PSI element to check
     * @param annFqn The fully qualified annotation name
     * @return true if the annotation is present
     */
    suspend fun hasAnn(element: PsiElement, annFqn: String): Boolean

    /**
     * Gets the annotation attributes as a map.
     *
     * @param element The PSI element
     * @param annFqn The fully qualified annotation name
     * @return Map of attribute names to values, or null if not found
     */
    suspend fun findAnnMap(element: PsiElement, annFqn: String): Map<String, Any?>?

    /**
     * Gets all repeatable annotations as a list of attribute maps.
     *
     * @param element The PSI element
     * @param annFqn The fully qualified annotation name
     * @return List of attribute maps, or null if not found
     */
    suspend fun findAnnMaps(element: PsiElement, annFqn: String): List<Map<String, Any?>>?

    /**
     * Gets a specific annotation attribute value.
     *
     * @param element The PSI element
     * @param annFqn The fully qualified annotation name
     * @param attr The attribute name
     * @return The attribute value, or null if not found
     */
    suspend fun findAttr(element: PsiElement, annFqn: String, attr: String): Any?

    /**
     * Gets a specific annotation attribute as a string.
     *
     * @param element The PSI element
     * @param annFqn The fully qualified annotation name
     * @param attr The attribute name
     * @return The attribute value as string, or null if not found
     */
    suspend fun findAttrAsString(element: PsiElement, annFqn: String, attr: String): String?
}