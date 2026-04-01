package com.itangcent.easyapi.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiType
import com.itangcent.easyapi.core.context.ActionContext
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.type.GenericContext

/**
 * Helper for building object models from PSI classes.
 *
 * Used to analyze class structures and extract field information
 * for request/response body modeling.
 *
 * ## Usage
 * ```kotlin
 * val helper = DefaultPsiClassHelper.getInstance(project)
 * val model = helper.buildObjectModel(psiClass, actionContext)
 * 
 * model?.fields?.forEach { field ->
 *     println("${field.name}: ${field.type}")
 * }
 * ```
 *
 * @see DefaultPsiClassHelper for default implementation
 * @see ObjectModel for the model structure
 */
interface PsiClassHelper {
    /**
     * Builds an object model from a PSI class.
     *
     * @param psiClass The class to analyze
     * @param actionContext The action context
     * @param option Options for what to include (see JsonOption constants)
     * @param maxDepth Maximum recursion depth for nested types
     * @return The object model, or null if the class cannot be analyzed
     */
    suspend fun buildObjectModel(
        psiClass: PsiClass,
        actionContext: ActionContext,
        option: Int = JsonOption.ALL,
        maxDepth: Int = 8
    ): ObjectModel?

    /**
     * Builds an object model from a PSI type.
     *
     * @param psiType The type to analyze
     * @param actionContext The action context
     * @param maxDepth Maximum recursion depth for nested types
     * @param genericContext The generic context for type substitution
     * @return The object model, or null if the type cannot be analyzed
     */
    suspend fun buildObjectModelFromType(
        psiType: PsiType,
        actionContext: ActionContext,
        option: Int = JsonOption.ALL,
        maxDepth: Int = 8,
        genericContext: GenericContext = GenericContext.EMPTY
    ): ObjectModel?

    companion object {
        fun getInstance(project: Project): PsiClassHelper =
            DefaultPsiClassHelper.getInstance(project)
    }
}
