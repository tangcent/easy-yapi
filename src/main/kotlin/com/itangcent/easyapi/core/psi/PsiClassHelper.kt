package com.itangcent.easyapi.core.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.itangcent.easyapi.core.psi.model.ObjectModel
import com.itangcent.easyapi.core.psi.type.ResolvedType

/**
 * Options for JSON model building.
 *
 * Controls which elements are included in the object model:
 * - READ_COMMENT - Include field comments
 * - READ_GETTER - Include properties from getter methods
 * - READ_SETTER - Include properties from setter methods
 * - WRITABLE_ONLY - Keep only properties a request data binder can write (not part of [ALL])
 */
object JsonOption {
    const val NONE = 0b0000
    const val READ_COMMENT = 0b0001
    const val READ_GETTER = 0b0010
    const val READ_SETTER = 0b0100
    const val READ_GETTER_OR_SETTER = READ_GETTER or READ_SETTER
    const val ALL = READ_GETTER_OR_SETTER or READ_COMMENT

    /**
     * Keep only properties that a request data binder (Spring's `WebDataBinder` with its
     * default bean-property access) can write when it fills an object from request
     * parameters. Used for objects expanded into query/form parameters; a JSON body is
     * read by Jackson and keeps every serialized property, so body models never set it.
     *
     * A property is writable when it has a setter (declared or inherited), when it is a
     * constructor parameter or record component (constructor binding), or when it is a
     * public non-final field. Lombok `@Data` / `@Setter` / `@Value` /
     * `@AllArgsConstructor` / `@Builder` also count, for when the IDE does not see the
     * members Lombok generates. Read-only properties (a getter without a setter, a
     * private field without a setter, a Kotlin `val` outside the primary constructor)
     * are dropped.
     */
    const val WRITABLE_ONLY = 0b1000

    fun has(option: Int, flag: Int): Boolean = (option and flag) != 0
}

/**
 * Helper for building JSON models from PSI classes and resolved types.
 *
 * Supports all Java types — not just object classes, but also:
 * - Primitive types (int, boolean, etc.)
 * - Array types (String[], int[])
 * - Collection types (List<T>, Set<T>)
 * - Map types (Map<K, V>)
 * - Enum types
 * - Simple/wrapper types (String, Integer, etc.)
 *
 * The returned [ObjectModel] can be any variant:
 * - [ObjectModel.Object] for class types → `{}`
 * - [ObjectModel.Array] for collection/array types → `[]`
 * - [ObjectModel.Single] for primitive/simple types → `0`, `""`, `true`
 * - [ObjectModel.MapModel] for map types → `{"": ""}`
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
     * Builds a JSON model from a PSI class.
     *
     * Delegates to [buildObjectModel(ResolvedType)] internally, so the result
     * may be any [ObjectModel] variant (Object, Array, Single, MapModel, etc.)
     * depending on the actual type.
     *
     * @param psiClass The class to analyze
     * @param option Options for what to include (see JsonOption constants)
     * @return The JSON model, or null if the type cannot be analyzed (e.g., void)
     */
    suspend fun buildObjectModel(
        psiClass: PsiClass,
        option: Int = JsonOption.ALL
    ): ObjectModel?

    /**
     * Builds a JSON model from an already-resolved type.
     *
     * This is the preferred entry point when you have a [ResolvedType]
     * (e.g., from [ResolvedType.ClassType.fields] or [ResolvedType.ClassType.methods]).
     * No generic context is needed because the type is already fully resolved.
     *
     * Handles all [ResolvedType] variants:
     * - [ResolvedType.PrimitiveType] → [ObjectModel.Single]
     * - [ResolvedType.ArrayType] → [ObjectModel.Array]
     * - [ResolvedType.ClassType] → depends on the class (Object, Array, Single, MapModel)
     * - [ResolvedType.WildcardType] → resolved from upper bound
     *
     * @param resolvedType The resolved type to analyze
     * @param option Options for what to include (see JsonOption constants)
     * @return The JSON model, or null if the type cannot be analyzed (e.g., void)
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
