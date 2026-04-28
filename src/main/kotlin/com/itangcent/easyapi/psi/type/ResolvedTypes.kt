package com.itangcent.easyapi.psi.type

import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.itangcent.easyapi.core.threading.read
import com.itangcent.easyapi.core.threading.readSync

/**
 * Represents a resolved type in the type system.
 *
 * This sealed class hierarchy provides a unified representation of types
 * resolved from PSI elements, supporting:
 * - Class types with generic type arguments
 * - Array types
 * - Primitive types
 * - Wildcard types
 * - Unresolved types (for error handling)
 *
 * ## Usage
 * ```kotlin
 * val resolved = TypeResolver.resolve(psiType)
 * when (resolved) {
 *     is ResolvedType.ClassType -> println("Class: ${resolved.psiClass.name}")
 *     is ResolvedType.ArrayType -> println("Array of: ${resolved.componentType}")
 *     is ResolvedType.PrimitiveType -> println("Primitive: ${resolved.kind}")
 *     is ResolvedType.UnresolvedType -> println("Unresolved: ${resolved.canonicalText}")
 *     is ResolvedType.WildcardType -> println("Wildcard")
 * }
 * ```
 */
sealed class ResolvedType {

    /**
     * Returns the canonical text representation of this resolved type.
     *
     * For class types, includes fully-qualified name with resolved type arguments
     * (e.g., `java.util.List<java.lang.String>`).
     * For primitives, returns the lowercase kind name (e.g., `int`, `boolean`).
     */
    abstract fun qualifiedName(): String

    /**
     * Returns the simple (unqualified) name of this resolved type.
     *
     * For class types, returns just the class name without package (e.g., `List`, `String`).
     * For array types, appends `[]` to the component's simple name.
     * For primitives, returns the lowercase kind name.
     * For wildcards, returns `?`.
     */
    abstract fun simpleName(): String

    /**
     * Returns the PSI element that provides context for rule evaluation.
     *
     * For class types, returns the PsiClass.
     * For array types, returns the component type's context element.
     * For primitives, unresolved types, and wildcards, returns null.
     */
    abstract fun contextElement(): PsiElement?

    /**
     * Represents a class or interface type with optional type arguments.
     *
     * @param psiClass The underlying PSI class
     * @param typeArgs The resolved type arguments (empty if raw type)
     */
    data class ClassType(
        val psiClass: PsiClass,
        val typeArgs: List<ResolvedType> = emptyList()
    ) : ResolvedType() {
        /**
         * Generic context for this class level only.
         *
         * Maps this class's own type parameter names to their resolved types.
         * For `List<String>`, this would be `{E → String}`.
         * For `HashMap<String, Integer>`, this would be `{K → String, V → Integer}`.
         *
         * This is a **local** context — it does NOT flatten the entire hierarchy.
         * To resolve a member from a superclass, use [contextForDeclaringClass] which
         * walks the hierarchy per-level, building a fresh context at each level.
         * This avoids the name-collision problem where different levels reuse `T`.
         */
        internal val genericContext: GenericContext by lazy {
            val map = LinkedHashMap<String, ResolvedType>()
            val params = psiClass.typeParameters
            for (i in params.indices) {
                val name = params[i].name ?: continue
                map[name] = typeArgs.getOrNull(i) ?: ResolvedType.UnresolvedType(name)
            }
            if (map.isEmpty()) GenericContext.EMPTY else GenericContext(map)
        }

        /**
         * Finds the per-level generic context for a given declaring class by walking
         * the supertype hierarchy from this ClassType.
         *
         * For example, given:
         *   ConcreteLayer3 extends Layer3<Integer>
         *   Layer3<T> extends Layer2<Pair<String, T>>
         *   Layer2<T> extends Layer1<Wrapper<T>>
         *   Layer1<T> { T value; }
         *
         * Calling `contextForDeclaringClass(Layer1)` from `ConcreteLayer3` returns
         * `{T → Wrapper<Pair<String, Integer>>}` — the correct binding for Layer1's T.
         *
         * Returns [genericContext] if the declaring class is this class itself,
         * or walks the hierarchy via [superClasses] to find the right context.
         */
        internal fun contextForDeclaringClass(declaringClass: PsiClass): GenericContext {
            val targetQN = declaringClass.qualifiedName
            if (targetQN == psiClass.qualifiedName) return genericContext
            return findContextForClass(targetQN, this) ?: genericContext
        }

        /**
         * Returns deduplicated, generics-resolved methods.
         * Deduplicates by name#paramCount, preferring the override from [psiClass].
         * Each [ResolvedMethod] gets [ResolvedMethod.ownerClassType] = this.
         *
         * Uses per-level generic context propagation: each method's types are resolved
         * using the context of its declaring class, not a flat merged context.
         */
        fun methods(): List<ResolvedMethod> {
            // Collect all non-constructor, non-Object methods
            val allMethods = psiClass.allMethods.filter { method ->
                !method.isConstructor &&
                method.containingClass?.qualifiedName != "java.lang.Object"
            }

            // Group by name + param count to detect generic erasure duplicates
            // (e.g., interface save(Req) and impl save(UserInfo) have same param count)
            val result = LinkedHashMap<String, PsiMethod>()
            for (method in allMethods) {
                val fullKey = methodSignatureKey(method)
                val countKey = "${method.name}#${method.parameterList.parametersCount}"

                // Check if there's already a method with the same full signature
                if (fullKey in result) {
                    // Same exact signature — prefer concrete class over interface
                    val existing = result[fullKey]!!
                    if (shouldPrefer(method, existing, psiClass)) {
                        result[fullKey] = method
                    }
                    continue
                }

                // Check if there's a method with same name+paramCount but different types
                // (could be a generic erasure duplicate or a true overload)
                val sameCountKey = result.entries.find { (k, v) ->
                    "${v.name}#${v.parameterList.parametersCount}" == countKey
                }

                if (sameCountKey != null) {
                    val existing = sameCountKey.value
                    // If one is from an interface and the other from a class, it's generic erasure
                    val existingIsInterface = existing.containingClass?.isInterface == true
                    val newIsInterface = method.containingClass?.isInterface == true
                    if (existingIsInterface != newIsInterface) {
                        // Generic erasure: prefer the concrete class method
                        if (!newIsInterface) {
                            result.remove(sameCountKey.key)
                            result[fullKey] = method
                        }
                        // else: existing is already the concrete one, skip
                        continue
                    }
                    // Both from same kind (both interface or both class) — true overload, keep both
                }

                result[fullKey] = method
            }
            return result.values.map { method ->
                ResolvedMethod(
                    name = method.name,
                    psiMethod = method,
                    containClass = psiClass,
                    ownerClassType = this
                )
            }
        }

        /**
         * Returns immediate supertypes as [ClassType] instances with resolved type args.
         * Excludes java.lang.Object.
         *
         * Each returned ClassType has its own per-level generic context built by resolving
         * the super type's type arguments using this class's context.
         */
        fun superClasses(): Sequence<ClassType> = sequence {
            for (superType in psiClass.superTypes) {
                val superClass = superType.resolve() ?: continue
                if (superClass.qualifiedName == "java.lang.Object") continue
                val args = superType.parameters.map {
                    TypeResolver.resolveAndSubstitute(it, genericContext)
                }
                yield(ClassType(superClass, args))
            }
        }

        /**
         * Returns all fields from this class and its superclasses, with types resolved
         * using per-level generic context propagation.
         *
         * Each field's type is resolved using the context of its declaring class,
         * ensuring that type parameter `T` in `Layer1<T>` gets the correct binding
         * even when multiple levels reuse the same name.
         */
        fun fields(): List<ResolvedField> {
            val result = mutableListOf<ResolvedField>()
            val fieldNames = mutableSetOf<String>()
            collectFieldsPerLevel(this, result, fieldNames)
            return result
        }

        fun annotations(): List<PsiAnnotation> = psiClass.annotations.toList()

        override fun qualifiedName(): String {
            val base = psiClass.qualifiedName ?: psiClass.name ?: "Anonymous"
            return if (typeArgs.isEmpty()) base
            else base + typeArgs.joinToString(prefix = "<", postfix = ">") { it.qualifiedName() }
        }

        override fun simpleName(): String = psiClass.name ?: psiClass.qualifiedName ?: "Anonymous"

        override fun contextElement(): PsiElement = psiClass
    }

    /**
     * Represents an array type with a component type.
     *
     * @param componentType The type of elements in the array
     */
    data class ArrayType(val componentType: ResolvedType) : ResolvedType() {
        override fun qualifiedName(): String = componentType.qualifiedName() + "[]"
        override fun simpleName(): String = componentType.simpleName() + "[]"
        override fun contextElement(): PsiElement? = componentType.contextElement()
    }

    /**
     * Represents a type that could not be resolved.
     *
     * @param canonicalText The canonical text representation of the unresolved type
     */
    data class UnresolvedType(val canonicalText: String) : ResolvedType() {
        override fun qualifiedName(): String = canonicalText
        override fun simpleName(): String = canonicalText.substringAfterLast('.')
        override fun contextElement(): PsiElement? = null
    }

    /**
     * Represents a primitive type (boolean, int, etc.).
     *
     * @param kind The specific primitive kind
     */
    data class PrimitiveType(val kind: PrimitiveKind) : ResolvedType() {
        override fun qualifiedName(): String = kind.name.lowercase()
        override fun simpleName(): String = kind.name.lowercase()
        override fun contextElement(): PsiElement? = null
    }

    /**
     * Represents a wildcard type with optional upper and lower bounds.
     *
     * @param upper The upper bound (? extends T), or null if none
     * @param lower The lower bound (? super T), or null if none
     */
    data class WildcardType(val upper: ResolvedType?, val lower: ResolvedType?) : ResolvedType() {
        override fun qualifiedName(): String = when {
            upper != null -> "? extends " + upper.qualifiedName()
            lower != null -> "? super " + lower.qualifiedName()
            else -> "?"
        }
        override fun simpleName(): String = "?"
        override fun contextElement(): PsiElement? = upper?.contextElement() ?: lower?.contextElement()
    }
}

/**
 * Enumeration of primitive type kinds.
 */
enum class PrimitiveKind {
    BOOLEAN,
    BYTE,
    CHAR,
    SHORT,
    INT,
    LONG,
    FLOAT,
    DOUBLE,
    VOID
}

/**
 * Represents a resolved method with its signature and metadata.
 *
 * Each method holds a reference to its [ownerClassType] for hierarchy navigation.
 * Types (return type, parameter types) are resolved lazily from the PSI method
 * using the owner's generic context, eliminating the need for callers to perform
 * explicit resolve+substitute steps.
 *
 * Use [superMethod] to find the super declaration, and the extension functions
 * [superMethods], [searchAnnotation] for composable hierarchy traversal.
 *
 * @param name The method name
 * @param psiMethod The PsiMethod (for overrides, this is the override from the concrete class)
 * @param containClass The concrete class this method is resolved from
 * @param ownerClassType The ClassType that produced this ResolvedMethod, for hierarchy navigation
 */
class ResolvedMethod(
    val name: String,
    val psiMethod: PsiMethod,
    val containClass: PsiClass? = null,
    val ownerClassType: ResolvedType.ClassType? = null
) {
    /**
     * The generic context used for resolving this method's types.
     * Derived from the owner's context for the method's declaring class.
     */
    private val genericContext: GenericContext by lazy {
        val owner = ownerClassType ?: return@lazy GenericContext.EMPTY
        val declaring = psiMethod.containingClass ?: return@lazy owner.genericContext
        owner.contextForDeclaringClass(declaring)
    }

    /** The resolved return type (with generics substituted). Lazily computed. */
    val returnType: ResolvedType by lazy {
        TypeResolver.resolveAndSubstitute(psiMethod.returnType, genericContext)
    }

    /** The resolved parameters (with generics substituted). Lazily computed. */
    val params: List<ResolvedParam> by lazy {
        psiMethod.parameterList.parameters.map { p ->
            ResolvedParam(
                name = p.name ?: "",
                psiParameter = p,
                ownerClassType = ownerClassType,
                genericContext = genericContext
            )
        }
    }

    /**
     * Finds the super declaration of this method in the immediate supertypes.
     * Returns a [ResolvedMethod] resolved in the super [ResolvedType.ClassType]'s generic context,
     * or null if no super declaration exists.
     *
     * Uses [PsiMethod.findSuperMethods] for correct JVM signature matching (including erasure),
     * then resolves the generic context from the owner's supertype hierarchy.
     */
    fun superMethod(): ResolvedMethod? {
        val owner = ownerClassType ?: return null
        val superPsiMethods = psiMethod.findSuperMethods()
        if (superPsiMethods.isEmpty()) return null

        // Build a lookup from class qualified name to the resolved supertype ClassType
        val superTypesByClass = owner.superClasses().associateBy { it.psiClass.qualifiedName }

        for (superPsiMethod in superPsiMethods) {
            val containingClass = superPsiMethod.containingClass ?: continue
            if (containingClass.qualifiedName == "java.lang.Object") continue

            val superClassType = superTypesByClass[containingClass.qualifiedName] ?: continue
            return ResolvedMethod(
                name = superPsiMethod.name,
                psiMethod = superPsiMethod,
                containClass = superClassType.psiClass,
                ownerClassType = superClassType
            )
        }
        return null
    }

    override fun toString(): String =
        "ResolvedMethod(name=$name, containClass=${containClass?.qualifiedName})"
}

/**
 * Represents a resolved field with its type and metadata.
 *
 * The field type is resolved lazily from the PSI field using the owner's generic context,
 * eliminating the need for callers to perform explicit resolve+substitute steps.
 *
 * @param name The field name
 * @param psiField The underlying PSI field
 * @param annotations The field annotations
 * @param containClass The containing class, if available
 * @param ownerClassType The ClassType that owns this field, for lazy type resolution
 */
class ResolvedField(
    val name: String,
    val psiField: PsiField,
    val annotations: List<PsiAnnotation>,
    val containClass: PsiClass? = null,
    val ownerClassType: ResolvedType.ClassType? = null
) {
    /** The resolved field type (with generics substituted). Lazily computed. */
    val type: ResolvedType by lazy {
        val ctx = ownerClassType?.genericContext ?: GenericContext.EMPTY
        TypeResolver.resolveAndSubstitute(psiField.type, ctx)
    }

    override fun toString(): String =
        "ResolvedField(name=$name, containClass=${containClass?.qualifiedName})"
}

/**
 * Represents a resolved method parameter.
 *
 * The parameter type is resolved lazily from the PSI parameter using the provided
 * generic context (inherited from the parent method's resolution context).
 *
 * @param name The parameter name
 * @param psiParameter The underlying PSI parameter
 * @param ownerClassType The ClassType that owns the method containing this parameter
 * @param genericContext The generic context for type resolution (from the parent method)
 */
class ResolvedParam internal constructor(
    val name: String,
    val psiParameter: PsiParameter,
    val ownerClassType: ResolvedType.ClassType? = null,
    private val genericContext: GenericContext = GenericContext.EMPTY
) {
    /** The resolved parameter type (with generics substituted). Lazily computed. */
    val type: ResolvedType by lazy {
        TypeResolver.resolveAndSubstitute(psiParameter.type, genericContext)
    }

    override fun toString(): String = "ResolvedParam(name=$name)"
}

/**
 * Collects fields per-level by walking the hierarchy via [ResolvedType.ClassType.superClasses].
 * Each level's fields are resolved using that level's own generic context.
 * Super fields come first (depth-first), then this class's own declared fields.
 *
 * @param rootClass The root class being queried (used as containClass for all fields)
 */
private fun collectFieldsPerLevel(
    classType: ResolvedType.ClassType,
    result: MutableList<ResolvedField>,
    fieldNames: MutableSet<String>,
    rootClass: PsiClass = classType.psiClass
) {
    // Recurse into supertypes first (depth-first so base fields come first)
    for (superClassType in classType.superClasses()) {
        collectFieldsPerLevel(superClassType, result, fieldNames, rootClass)
    }
    // Then this class's own declared fields (not allFields — just this level)
    for (field in classType.psiClass.fields) {
        if (fieldNames.add(field.name)) {
            result.add(
                ResolvedField(
                    name = field.name,
                    psiField = field,
                    annotations = field.annotations.toList(),
                    containClass = rootClass,
                    ownerClassType = classType
                )
            )
        }
    }
}

/**
 * Walks the supertype hierarchy from [root] to find the per-level generic context
 * for a class with the given qualified name.
 *
 * Uses BFS through [ResolvedType.ClassType.superClasses] which already does per-level
 * context propagation — each returned ClassType has its own correctly-scoped context.
 */
private fun findContextForClass(
    targetQN: String?,
    root: ResolvedType.ClassType
): GenericContext? {
    if (targetQN == null) return null
    val visited = HashSet<String>()
    val queue = ArrayDeque<ResolvedType.ClassType>()
    for (s in root.superClasses()) {
        val qn = s.psiClass.qualifiedName ?: continue
        if (qn == targetQN) return s.genericContext
        if (visited.add(qn)) queue.add(s)
    }
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        for (s in current.superClasses()) {
            val qn = s.psiClass.qualifiedName ?: continue
            if (qn == targetQN) return s.genericContext
            if (visited.add(qn)) queue.add(s)
        }
    }
    return null
}

/**
 * Holds the mapping of generic type parameters to their resolved types.
 *
 * Used internally during type resolution to substitute type parameters with actual types.
 *
 * **This class is internal to the type resolution system.** External callers should never
 * create, pass, or depend on [GenericContext]. Instead:
 * - Use [TypeResolver.resolve] to convert a [PsiType] to a [ResolvedType]
 * - Use [ResolvedType.ClassType.fields], [ResolvedType.ClassType.methods], and
 *   [ResolvedType.ClassType.superClasses] to access members with generics resolved automatically
 * - Use [PsiClassHelper.buildObjectModel] with a [ResolvedType] to build object models
 *
 * Generic context propagation is handled per-level internally by [ResolvedType.ClassType],
 * which builds a fresh context at each level of the class hierarchy.
 *
 * @param genericMap Map from type parameter names to resolved types
 */
internal data class GenericContext(val genericMap: Map<String, ResolvedType>) {
    companion object {
        /**
         * An empty generic context with no type parameter bindings.
         */
        val EMPTY: GenericContext = GenericContext(emptyMap())
    }
}

/**
 * Utility object for resolving PSI types to [ResolvedType] instances.
 *
 * Handles:
 * - Primitive types
 * - Class types with generic type arguments
 * - Array types
 * - Wildcard types
 * - Type parameter substitution
 */
object TypeResolver {
    /**
     * Resolves a PSI type to a [ResolvedType].
     *
     * This is the primary public API. Callers should resolve a PsiType once,
     * then use the returned [ResolvedType] to access fields, methods, and supertypes
     * — generic type parameters are resolved internally via per-level context propagation.
     *
     * Type parameters (e.g., `T` in `Result<T>`) that cannot be resolved without
     * a class hierarchy context will become [ResolvedType.UnresolvedType].
     * To get fully resolved types, use [ResolvedType.ClassType.methods] or
     * [ResolvedType.ClassType.fields] which handle generic propagation automatically.
     *
     * @param psiType The PSI type to resolve
     * @return The resolved type
     */
    fun resolve(psiType: PsiType): ResolvedType {
        if (psiType is PsiPrimitiveType) return resolvePrimitive(psiType)
        return resolveNonPrimitive(psiType, GenericContext.EMPTY)
    }

    /**
     * Resolves a PSI type with a generic context for type parameter substitution.
     *
     * This is an internal API used by [ResolvedType.ClassType] for per-level
     * generic context propagation. External callers should use [resolve] without
     * context and rely on [ResolvedType.ClassType.fields] / [ResolvedType.ClassType.methods]
     * for generic resolution.
     */
    internal fun resolve(psiType: PsiType?, context: GenericContext): ResolvedType {
        if (psiType == null) return ResolvedType.UnresolvedType("null")
        if (psiType is PsiPrimitiveType) return resolvePrimitive(psiType)
        return resolveNonPrimitive(psiType, context)
    }

    private fun resolvePrimitive(psiType: PsiPrimitiveType): ResolvedType {
        return when (psiType) {
            PsiTypes.voidType() -> ResolvedType.PrimitiveType(PrimitiveKind.VOID)
            PsiTypes.booleanType() -> ResolvedType.PrimitiveType(PrimitiveKind.BOOLEAN)
            PsiTypes.byteType() -> ResolvedType.PrimitiveType(PrimitiveKind.BYTE)
            PsiTypes.charType() -> ResolvedType.PrimitiveType(PrimitiveKind.CHAR)
            PsiTypes.shortType() -> ResolvedType.PrimitiveType(PrimitiveKind.SHORT)
            PsiTypes.intType() -> ResolvedType.PrimitiveType(PrimitiveKind.INT)
            PsiTypes.longType() -> ResolvedType.PrimitiveType(PrimitiveKind.LONG)
            PsiTypes.floatType() -> ResolvedType.PrimitiveType(PrimitiveKind.FLOAT)
            PsiTypes.doubleType() -> ResolvedType.PrimitiveType(PrimitiveKind.DOUBLE)
            else -> ResolvedType.UnresolvedType(psiType.canonicalText)
        }
    }

    private fun resolveNonPrimitive(psiType: PsiType, context: GenericContext): ResolvedType {
        return readSync {
            resolveNonPrimitiveUnderReadAction(psiType, context)
        }
    }

    private fun resolveNonPrimitiveUnderReadAction(psiType: PsiType, context: GenericContext): ResolvedType {
        val arrayType = psiType as? PsiArrayType
        if (arrayType != null) return ResolvedType.ArrayType(resolve(arrayType.componentType, context))

        val wildcard = psiType as? PsiWildcardType
        if (wildcard != null) {
            return ResolvedType.WildcardType(
                upper = resolve(wildcard.extendsBound, context),
                lower = resolve(wildcard.superBound, context)
            )
        }

        val classType = psiType as? com.intellij.psi.PsiClassType
        if (classType != null) {
            val psiClass = classType.resolve()
            if (psiClass != null) {
                if (psiClass is com.intellij.psi.PsiTypeParameter) {
                    val name = psiClass.name ?: return ResolvedType.UnresolvedType(psiType.canonicalText)
                    return context.genericMap[name] ?: ResolvedType.UnresolvedType(psiType.canonicalText)
                }

                val specialType = SpecialTypeHandler.resolveSpecialType(psiClass)
                if (specialType != null) return specialType

                val qualifiedName = psiClass.qualifiedName
                if (qualifiedName != null && SpecialTypeHandler.isDateTimeAsString(qualifiedName)) {
                    return ResolvedType.UnresolvedType(qualifiedName)
                }

                val args = classType.parameters.map { resolve(it, context) }
                return ResolvedType.ClassType(psiClass, args)
            }
            val canonicalText = classType.canonicalText
            if (SpecialTypeHandler.isFileType(canonicalText) || SpecialTypeHandler.isFileTypeCanonical(canonicalText)) {
                return ResolvedType.UnresolvedType("__file__")
            }
            if (SpecialTypeHandler.isDateTimeAsString(canonicalText)) {
                return ResolvedType.UnresolvedType(canonicalText)
            }
            context.genericMap[classType.canonicalText]?.let { return it }
            context.genericMap[classType.className]?.let { return it }
        }

        return substitute(ResolvedType.UnresolvedType(psiType.canonicalText), context)
    }

    /**
     * Resolves a canonical text string to a [ResolvedType].
     *
     * This function attempts to resolve a type name (like "java.lang.String", "com.example.User",
     * "java.util.List<java.lang.String>") to a proper ResolvedType by:
     * 1. Handling special types (file, primitives)
     * 2. Creating a PsiType from the text using JavaPsiFacade
     * 3. Resolving the PsiType to a ResolvedType
     *
     * Falls back to [ResolvedType.UnresolvedType] if resolution fails.
     *
     * @param canonicalText The canonical text representation of the type
     * @param project The project for class resolution
     * @param context The generic context for type parameter substitution
     * @return The resolved type, or UnresolvedType if resolution fails
     */
    internal fun resolveFromCanonicalText(
        canonicalText: String,
        project: com.intellij.openapi.project.Project,
        context: GenericContext = GenericContext.EMPTY
    ): ResolvedType {
        return resolveFromCanonicalText(canonicalText, project, null, context)
    }

    /**
     * Resolves a canonical text string to a [ResolvedType] with context element support.
     *
     * The context element helps resolve simple class names based on imports in the file.
     *
     * @param canonicalText The canonical text representation of the type
     * @param project The project for class resolution
     * @param contextElement The context element for import resolution (can be null)
     * @param context The generic context for type parameter substitution
     * @return The resolved type, or UnresolvedType if resolution fails
     */
    internal fun resolveFromCanonicalText(
        canonicalText: String,
        project: com.intellij.openapi.project.Project,
        contextElement: PsiElement?,
        context: GenericContext = GenericContext.EMPTY
    ): ResolvedType {
        if (canonicalText.isBlank()) return ResolvedType.UnresolvedType(canonicalText)

        val trimmed = canonicalText.trim()

        if (SpecialTypeHandler.isFileTypeName(trimmed)) {
            return ResolvedType.UnresolvedType("__file__")
        }

        val primitiveKind = resolvePrimitiveKind(trimmed)
        if (primitiveKind != null) {
            return ResolvedType.PrimitiveType(primitiveKind)
        }

        if (trimmed.endsWith("[]")) {
            val componentText = trimmed.removeSuffix("[]")
            val componentType = resolveFromCanonicalText(componentText, project, contextElement, context)
            return ResolvedType.ArrayType(componentType)
        }

        // 1. Try createTypeFromText first — works for fully qualified names and
        //    simple names when contextElement provides import scope.
        try {
            val resolved = readSync {
                val facade = JavaPsiFacade.getInstance(project)
                val psiType = facade.elementFactory.createTypeFromText(trimmed, contextElement)
                resolve(psiType, context)
            }
            if (resolved !is ResolvedType.UnresolvedType) return resolved
        } catch (_: Exception) {
            // Fall through to manual resolution
        }

        // 2. Handle generic types like "List<User>" where the base class or
        //    type arguments may be simple names that need import resolution.
        if (trimmed.contains('<') && trimmed.endsWith('>')) {
            val baseText = trimmed.substringBefore('<')
            val baseClass = readSync { resolveClassByName(baseText, project, contextElement) }
            if (baseClass != null) {
                val typeArgsText = trimmed.substringAfter('<').removeSuffix(">")
                val typeArgs = splitTypeArgs(typeArgsText).map { arg ->
                    resolveFromCanonicalText(arg.trim(), project, contextElement, context)
                }
                return ResolvedType.ClassType(baseClass, typeArgs)
            }
        }

        // 3. Try resolving as a plain class name via imports / same-package / default packages.
        val psiClass = readSync { resolveClassByName(trimmed, project, contextElement) }
        if (psiClass != null) {
            val specialType = SpecialTypeHandler.resolveSpecialType(psiClass)
            if (specialType != null) return specialType
            val qualifiedName = psiClass.qualifiedName
            if (qualifiedName != null && SpecialTypeHandler.isDateTimeAsString(qualifiedName)) {
                return ResolvedType.UnresolvedType(qualifiedName)
            }
            return ResolvedType.ClassType(psiClass)
        }

        // 4. Check generic context — the text might be a type parameter name.
        context.genericMap[trimmed]?.let { return it }

        return ResolvedType.UnresolvedType(canonicalText)
    }

    /**
     * Resolves a class name that may be simple (e.g. "User") or fully qualified.
     *
     * Resolution order (mirrors StandardPsiResolver / LinkResolver):
     * 1. Fully qualified lookup via JavaPsiFacade
     * 2. Same package as the context element
     * 3. Import statements in the context file
     * 4. Default packages (java.lang)
     *
     * @return The resolved PsiClass, or null if not found
     */
    private fun resolveClassByName(
        className: String,
        project: com.intellij.openapi.project.Project,
        contextElement: PsiElement?
    ): PsiClass? {
        if (className.isBlank()) return null
        val facade = JavaPsiFacade.getInstance(project)
        val scope = GlobalSearchScope.allScope(project)

        // Fully qualified name
        facade.findClass(className, scope)?.let { return it }

        if (contextElement == null) return null
        val javaFile = contextElement.containingFile as? PsiJavaFile ?: return null

        // Same package
        val packageName = javaFile.packageName
        if (packageName.isNotBlank()) {
            facade.findClass("$packageName.$className", scope)?.let { return it }
        }

        // Import statements
        val imports = javaFile.importList?.importStatements
        if (imports != null) {
            for (importStmt in imports) {
                val importRef = importStmt.qualifiedName ?: continue
                val candidate = when {
                    importRef.endsWith(".$className") -> importRef
                    importRef.endsWith(".*") -> importRef.removeSuffix("*") + className
                    else -> continue
                }
                facade.findClass(candidate, scope)?.let { return it }
            }
        }

        // Default packages
        facade.findClass("java.lang.$className", scope)?.let { return it }

        return null
    }

    /**
     * Splits a comma-separated type argument string, respecting nested angle brackets.
     * e.g. "String, Map<String, Integer>" → ["String", "Map<String, Integer>"]
     */
    private fun splitTypeArgs(typeArgsText: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        val current = StringBuilder()
        for (ch in typeArgsText) {
            when {
                ch == '<' -> { depth++; current.append(ch) }
                ch == '>' -> { depth--; current.append(ch) }
                ch == ',' && depth == 0 -> {
                    val arg = current.toString().trim()
                    if (arg.isNotEmpty()) result.add(arg)
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        val last = current.toString().trim()
        if (last.isNotEmpty()) result.add(last)
        return result
    }

    private fun resolvePrimitiveKind(typeName: String): PrimitiveKind? {
        return when (typeName) {
            "boolean", "java.lang.Boolean" -> PrimitiveKind.BOOLEAN
            "byte", "java.lang.Byte" -> PrimitiveKind.BYTE
            "char", "java.lang.Character" -> PrimitiveKind.CHAR
            "short", "java.lang.Short" -> PrimitiveKind.SHORT
            "int", "java.lang.Integer" -> PrimitiveKind.INT
            "long", "java.lang.Long" -> PrimitiveKind.LONG
            "float", "java.lang.Float" -> PrimitiveKind.FLOAT
            "double", "java.lang.Double" -> PrimitiveKind.DOUBLE
            "void", "java.lang.Void" -> PrimitiveKind.VOID
            else -> null
        }
    }

    /**
     * Resolves the generic type parameters for a class with given type arguments.
     *
     * @param psiClass The class to resolve parameters for
     * @param typeArgs The resolved type arguments
     * @return Map from type parameter names to resolved types
     */
    internal fun resolveGenericParams(psiClass: PsiClass, typeArgs: List<ResolvedType>): Map<String, ResolvedType> {
        val map = LinkedHashMap<String, ResolvedType>()

        val params = psiClass.typeParameters
        for (i in params.indices) {
            val name = params[i].name ?: continue
            map[name] = typeArgs.getOrNull(i) ?: ResolvedType.UnresolvedType(name)
        }

        collectSuperTypeBindings(psiClass, map)
        return map
    }

    /**
     * Recursively collect type parameter bindings from the super type hierarchy.
     * For class B<X> extends A<X, String>, this resolves A's T→X→(whatever X is) and R→String.
     *
     * Uses a scoped map per supertype level to avoid name collisions when different levels
     * in the hierarchy reuse the same type parameter name (e.g., both use `T`).
     */
    private fun collectSuperTypeBindings(psiClass: PsiClass, map: MutableMap<String, ResolvedType>) {
        // Build a context from the current bindings to substitute type args at this level
        val context = GenericContext(map)
        for (superType in psiClass.superTypes) {
            val classType = superType as? com.intellij.psi.PsiClassType ?: continue
            val superClass = classType.resolve() ?: continue
            if (superClass.qualifiedName == "java.lang.Object") continue

            val superParams = superClass.typeParameters
            val superArgs = classType.parameters

            // Build a scoped map for this supertype's params, resolved in the current context
            val superBindings = LinkedHashMap<String, ResolvedType>()
            for (i in superParams.indices) {
                val name = superParams[i].name ?: continue
                val arg = superArgs.getOrNull(i)
                superBindings[name] = resolveAndSubstitute(arg, context)
            }

            // Merge into the shared map, but only for names not already bound at a lower level
            for ((name, resolved) in superBindings) {
                if (!map.containsKey(name)) {
                    map[name] = resolved
                }
            }

            // Recurse into super class to handle multi-level inheritance,
            // using a merged context so deeper levels can resolve through the full chain.
            // superBindings take precedence over the shared map because they represent
            // the correct type parameter bindings at this level of the hierarchy.
            // For example, if AtaPageResult<T> extends AtaBaseResult<AtaPage<T>>,
            // superBindings has {T → AtaPage<VotePageQueryVO>} which is the correct
            // binding for AtaBaseResult's T, while the shared map has {T → VotePageQueryVO}
            // which is AtaPageResult's T.
            val mergedMap = LinkedHashMap(superBindings)
            for ((k, v) in map) {
                if (!mergedMap.containsKey(k)) {
                    mergedMap[k] = v
                }
            }
            collectSuperTypeBindingsScoped(superClass, map, GenericContext(mergedMap))
        }
    }

    /**
     * Scoped variant used during recursion to avoid polluting the shared map with
     * intermediate type param names from higher levels of the hierarchy.
     */
    private fun collectSuperTypeBindingsScoped(
        psiClass: PsiClass,
        map: MutableMap<String, ResolvedType>,
        context: GenericContext
    ) {
        for (superType in psiClass.superTypes) {
            val classType = superType as? com.intellij.psi.PsiClassType ?: continue
            val superClass = classType.resolve() ?: continue
            if (superClass.qualifiedName == "java.lang.Object") continue

            val superParams = superClass.typeParameters
            val superArgs = classType.parameters

            val superBindings = LinkedHashMap<String, ResolvedType>()
            for (i in superParams.indices) {
                val name = superParams[i].name ?: continue
                val arg = superArgs.getOrNull(i)
                superBindings[name] = resolveAndSubstitute(arg, context)
            }

            for ((name, resolved) in superBindings) {
                if (!map.containsKey(name)) {
                    map[name] = resolved
                }
            }

            val mergedMap = LinkedHashMap(superBindings)
            for ((k, v) in map) {
                if (!mergedMap.containsKey(k)) {
                    mergedMap[k] = v
                }
            }
            collectSuperTypeBindingsScoped(superClass, map, GenericContext(mergedMap))
        }
    }

    /**
     * Resolves a PSI type and substitutes type parameters in a single step.
     *
     * This is the preferred API for resolving member types (fields, method return types,
     * parameters) where the two-step resolve+substitute pattern was previously needed.
     *
     * @param psiType The PSI type to resolve
     * @param context The generic context containing type parameter bindings
     * @return The fully resolved and substituted type
     */
    internal fun resolveAndSubstitute(psiType: PsiType?, context: GenericContext): ResolvedType {
        val resolved = resolve(psiType, context)
        return substitute(resolved, context)
    }

    /**
     * Substitutes type parameters in a resolved type using the generic context.
     *
     * @param type The type to substitute
     * @param context The generic context containing type parameter bindings
     * @return The type with parameters substituted
     */
    internal fun substitute(type: ResolvedType, context: GenericContext): ResolvedType {
        return when (type) {
            is ResolvedType.UnresolvedType -> {
                context.genericMap[type.canonicalText]?.let { return it }
                var text = type.canonicalText
                for ((name, resolved) in context.genericMap) {
                    text = text.replace("\\b$name\\b".toRegex(), canonicalNameOf(resolved))
                }
                if (text == type.canonicalText) type else ResolvedType.UnresolvedType(text)
            }

            is ResolvedType.ArrayType -> ResolvedType.ArrayType(substitute(type.componentType, context))
            is ResolvedType.ClassType -> ResolvedType.ClassType(
                psiClass = type.psiClass,
                typeArgs = type.typeArgs.map { substitute(it, context) }
            )

            is ResolvedType.WildcardType -> ResolvedType.WildcardType(
                upper = type.upper?.let { substitute(it, context) },
                lower = type.lower?.let { substitute(it, context) }
            )

            is ResolvedType.PrimitiveType -> type
        }
    }

    private fun canonicalNameOf(type: ResolvedType): String = type.qualifiedName()
}

/**
 * Builds a signature key for method deduplication that includes parameter types,
 * correctly distinguishing overloaded methods like `add(UserInfo, MultipartFile)`
 * from `add(UserInfo, MultipartFile[])`.
 */
private fun methodSignatureKey(method: PsiMethod): String {
    val params = method.parameterList.parameters.joinToString(",") { it.type.canonicalText }
    return "${method.name}($params)"
}

/**
 * Returns true if [candidate] should replace [existing] in the deduplication map.
 * Prefers methods from the concrete [psiClass] over inherited ones,
 * and concrete class methods over interface methods.
 */
private fun shouldPrefer(candidate: PsiMethod, existing: PsiMethod, psiClass: PsiClass): Boolean {
    if (candidate.containingClass == psiClass) return true
    if (existing.containingClass == psiClass) return false
    val existingIsInterface = existing.containingClass?.isInterface == true
    val candidateIsClass = candidate.containingClass?.isInterface == false
    return existingIsInterface && candidateIsClass
}

/**
 * Checks if two methods are related via inheritance.
 * Returns true if [method1] is the same as [method2], or if one is a super method of the other.
 *
 * This is useful for navigation features where clicking on an interface method
 * should find the corresponding implementation method in the index.
 *
 * Uses read action context to avoid "Slow operations are prohibited on EDT" errors.
 */
suspend fun areMethodsRelated(method1: PsiMethod, method2: PsiMethod): Boolean {
    if (method1 == method2) return true
    return read {
        val superMethods1 = method1.findSuperMethods()
        if (method2 in superMethods1) return@read true
        val superMethods2 = method2.findSuperMethods()
        method1 in superMethods2
    }
}

// ========== Extension functions for hierarchy navigation ==========

/**
 * Walks up the super method chain, yielding each ancestor declaration.
 * Does NOT include `this` — only ancestors.
 */
fun ResolvedMethod.superMethods(): Sequence<ResolvedMethod> = sequence {
    var current = superMethod()
    while (current != null) {
        yield(current)
        current = current.superMethod()
    }
}

/**
 * Walks the entire supertype hierarchy from this ClassType in BFS order.
 * Excludes java.lang.Object. Includes transitive supertypes.
 */
fun ResolvedType.ClassType.allSuperClasses(): Sequence<ResolvedType.ClassType> = sequence {
    val visited = HashSet<String>()
    val queue = ArrayDeque<ResolvedType.ClassType>()
    for (s in superClasses()) {
        val qn = s.psiClass.qualifiedName ?: continue
        if (visited.add(qn)) queue.add(s)
    }
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        yield(current)
        for (s in current.superClasses()) {
            val qn = s.psiClass.qualifiedName ?: continue
            if (visited.add(qn)) queue.add(s)
        }
    }
}

/**
 * Searches this method and its super methods for an annotation with the given FQN.
 * Returns the first found [PsiAnnotation], or null.
 */
fun ResolvedMethod.searchAnnotation(annotationFqn: String): PsiAnnotation? {
    psiMethod.getAnnotation(annotationFqn)?.let { return it }
    for (sup in superMethods()) {
        sup.psiMethod.getAnnotation(annotationFqn)?.let { return it }
    }
    return null
}

/**
 * Searches this method and its super methods for any annotation from the given set.
 * Returns the first found [PsiAnnotation], or null.
 */
fun ResolvedMethod.searchAnnotation(annotationFqns: Set<String>): PsiAnnotation? {
    for (fqn in annotationFqns) {
        psiMethod.getAnnotation(fqn)?.let { return it }
    }
    for (sup in superMethods()) {
        for (fqn in annotationFqns) {
            sup.psiMethod.getAnnotation(fqn)?.let { return it }
        }
    }
    return null
}

/**
 * Searches this class and its supertypes for an annotation with the given FQN.
 * Returns the first found [PsiAnnotation], or null.
 */
fun ResolvedType.ClassType.searchAnnotation(annotationFqn: String): PsiAnnotation? {
    psiClass.getAnnotation(annotationFqn)?.let { return it }
    for (superClassType in allSuperClasses()) {
        superClassType.psiClass.getAnnotation(annotationFqn)?.let { return it }
    }
    return null
}

/**
 * Searches for an annotation on a method parameter by index, walking up the super method chain.
 * This handles the case where parameter annotations (like @RequestBody) are declared on
 * an interface method but not re-declared on the implementing class.
 *
 * @param parameterIndex The index of the parameter in the method's parameter list
 * @param annotationFqn The fully qualified name of the annotation to search for
 * @return The first found [PsiAnnotation], or null if not found
 */
fun ResolvedMethod.searchParameterAnnotation(parameterIndex: Int, annotationFqn: String): PsiAnnotation? {
    val params = psiMethod.parameterList.parameters
    if (parameterIndex < 0 || parameterIndex >= params.size) return null
    params[parameterIndex].getAnnotation(annotationFqn)?.let { return it }
    for (superMethod in superMethods()) {
        val superParams = superMethod.psiMethod.parameterList.parameters
        if (parameterIndex < superParams.size) {
            superParams[parameterIndex].getAnnotation(annotationFqn)?.let { return it }
        }
    }
    return null
}

/**
 * Searches for any annotation from a set on a method parameter by index, walking up the super method chain.
 *
 * @param parameterIndex The index of the parameter in the method's parameter list
 * @param annotationFqns The set of fully qualified annotation names to search for
 * @return The first found [PsiAnnotation], or null if not found
 */
fun ResolvedMethod.searchParameterAnnotation(parameterIndex: Int, annotationFqns: Set<String>): PsiAnnotation? {
    val params = psiMethod.parameterList.parameters
    if (parameterIndex < 0 || parameterIndex >= params.size) return null
    for (fqn in annotationFqns) {
        params[parameterIndex].getAnnotation(fqn)?.let { return it }
    }
    for (superMethod in superMethods()) {
        val superParams = superMethod.psiMethod.parameterList.parameters
        if (parameterIndex < superParams.size) {
            for (fqn in annotationFqns) {
                superParams[parameterIndex].getAnnotation(fqn)?.let { return it }
            }
        }
    }
    return null
}
