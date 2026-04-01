package com.itangcent.easyapi.exporter.core

import com.intellij.psi.PsiMethod

/**
 * Filters methods during API export.
 *
 * Implementations can filter out methods that should not be exported
 * as API endpoints (e.g., private methods, synthetic methods).
 *
 * @see EmptyMethodFilter for a filter that accepts all methods
 */
interface MethodFilter {
    /**
     * Checks whether the method should be included in export.
     *
     * @param method The method to check
     * @return true if the method should be exported
     */
    fun checkMethod(method: PsiMethod): Boolean
}

/**
 * A method filter that accepts all methods.
 *
 * Use this when no filtering is needed.
 */
class EmptyMethodFilter : MethodFilter {
    override fun checkMethod(method: PsiMethod): Boolean = true
}
