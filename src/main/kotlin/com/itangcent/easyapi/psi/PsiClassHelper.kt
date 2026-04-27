package com.itangcent.easyapi.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiType
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.type.GenericContext

/**
 * Options for JSON model building.
 *
 * Controls which elements are included in the object model:
 * - READ_COMMENT - Include field comments
 * - READ_GETTER - Include properties from getter methods
 * - READ_SETTER - Include properties from setter methods
 */
object JsonOption {
    const val NONE = 0b0000
    const val READ_COMMENT = 0b0001
    const val READ_GETTER = 0b0010
    const val READ_SETTER = 0b0100
    const val READ_GETTER_OR_SETTER = READ_GETTER or READ_SETTER
    const val ALL = READ_GETTER_OR_SETTER or READ_COMMENT

    fun has(option: Int, flag: Int): Boolean = (option and flag) != 0
}

/**
 * Helper for building object models from PSI classes.
 *
 * Used to analyze class structures and extract field information
 * for request/response body modeling.
 *
 * Max depth and max elements are read from project configuration
 * (`max.deep` and `max.elements`) by the implementation.
 *
 * ## Usage
 * ```kotlin
 * val helper = DefaultPsiClassHelper.getInstance(project)
 * val model = helper.buildObjectModel(psiClass)
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
     * @param option Options for what to include (see JsonOption constants)
     * @return The object model, or null if the class cannot be analyzed
     */
    suspend fun buildObjectModel(
        psiClass: PsiClass,
        option: Int = JsonOption.ALL
    ): ObjectModel?

    /**
     * Builds an object model from a PSI type.
     *
     * @param psiType The type to analyze
     * @param option Options for what to include (see JsonOption constants)
     * @param genericContext The generic context for type substitution
     * @param contextElement Optional context element for import-aware type resolution
     * @return The object model, or null if the type cannot be analyzed
     */
    suspend fun buildObjectModelFromType(
        psiType: PsiType,
        option: Int = JsonOption.ALL,
        genericContext: GenericContext = GenericContext.EMPTY,
        contextElement: com.intellij.psi.PsiElement? = null
    ): ObjectModel?

    companion object {
        fun getInstance(project: Project): PsiClassHelper =
            DefaultPsiClassHelper.getInstance(project)
    }
}
