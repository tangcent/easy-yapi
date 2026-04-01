package com.itangcent.easyapi.exporter.core

import com.intellij.psi.PsiClass

/**
 * Recognizes whether a [PsiClass] is an API class for a specific framework.
 *
 * Each framework (Spring MVC, JAX-RS, Feign, etc.) provides its own implementation.
 * Use [CompositeApiClassRecognizer] to combine them.
 */
interface ApiClassRecognizer {

    /**
     * The framework name this recognizer covers (for logging/debugging).
     */
    val frameworkName: String

    /**
     * The annotation FQNs this framework considers as API class markers.
     * Used for index-based scanning via [AnnotatedElementsSearch].
     */
    val targetAnnotations: Set<String>

    /**
     * Returns true if [psiClass] is an API class for this framework.
     * Implementations support meta-annotations (custom annotations annotated
     * with standard framework annotations).
     */
    suspend fun isApiClass(psiClass: PsiClass): Boolean
}
