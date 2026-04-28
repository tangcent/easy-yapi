package com.itangcent.easyapi.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.type.ResolvedType

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
 * ## Usage
 * ```kotlin
 * val helper = PsiClassHelper.getInstance(project)
 *
 * // From a PsiClass:
 * val model = helper.buildObjectModel(psiClass)
 *
 * // From an already-resolved type (preferred — generics are already resolved):
 * val resolved = TypeResolver.resolve(psiType)
 * val model = helper.buildObjectModel(resolved)
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
     * Builds an object model from an already-resolved type.
     *
     * This is the preferred entry point when you have a [ResolvedType]
     * (e.g., from [ResolvedType.ClassType.fields] or [ResolvedType.ClassType.methods]).
     * No generic context is needed because the type is already fully resolved.
     */
    suspend fun buildObjectModel(
        resolvedType: ResolvedType,
        option: Int = JsonOption.ALL
    ): ObjectModel?

    companion object {
        fun getInstance(project: Project): PsiClassHelper =
            DefaultPsiClassHelper.getInstance(project)
    }
}
