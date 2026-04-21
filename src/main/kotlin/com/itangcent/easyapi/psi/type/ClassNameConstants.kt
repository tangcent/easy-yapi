package com.itangcent.easyapi.psi.type

/**
 * Centralized constants for Java/Kotlin fully qualified class names.
 *
 * Eliminates hardcoded string literals scattered across the codebase
 * by providing a single source of truth for commonly referenced class names.
 * Used by [InheritanceHelper], [SpecialTypeHandler], and other PSI utilities.
 *
 * ## Organization
 * - **Map types** — [JAVA_UTIL_MAP] and its common implementations
 * - **Collection types** — [JAVA_UTIL_COLLECTION] and its common implementations
 * - **Other core types** — [JAVA_LANG_ENUM], [JAVA_LANG_OBJECT]
 * - **Kotlin collections** — [KOTLIN_COLLECTIONS_PREFIX] for prefix-based matching
 *
 * ## Pre-built Sets
 * - [MAP_TYPES] — all Map-related FQNs for O(1) `contains` checks
 * - [COLLECTION_TYPES] — all Collection-related FQNs for O(1) `contains` checks
 *
 * @see InheritanceHelper for type checking using these constants
 * @see SpecialTypeHandler for special type detection (file, date, primitive wrappers)
 */
object ClassNameConstants {

    const val JAVA_UTIL_MAP = "java.util.Map"
    const val JAVA_UTIL_HASHMAP = "java.util.HashMap"
    const val JAVA_UTIL_LINKEDHASHMAP = "java.util.LinkedHashMap"
    const val JAVA_UTIL_TREEMAP = "java.util.TreeMap"
    const val JAVA_UTIL_CONCURRENTMAP = "java.util.concurrent.ConcurrentMap"
    const val JAVA_UTIL_CONCURRENTHASHMAP = "java.util.concurrent.ConcurrentHashMap"

    const val JAVA_UTIL_COLLECTION = "java.util.Collection"
    const val JAVA_UTIL_LIST = "java.util.List"
    const val JAVA_UTIL_SET = "java.util.Set"
    const val JAVA_UTIL_ARRAYLIST = "java.util.ArrayList"
    const val JAVA_UTIL_HASHSET = "java.util.HashSet"
    const val JAVA_UTIL_LINKEDLIST = "java.util.LinkedList"
    const val JAVA_UTIL_LINKEDHASHSET = "java.util.LinkedHashSet"
    const val JAVA_UTIL_VECTOR = "java.util.Vector"
    const val JAVA_UTIL_STACK = "java.util.Stack"
    const val JAVA_UTIL_TREESET = "java.util.TreeSet"

    const val JAVA_LANG_ENUM = "java.lang.Enum"
    const val JAVA_LANG_OBJECT = "java.lang.Object"

    const val KOTLIN_COLLECTIONS_PREFIX = "kotlin.collections."

    /**
     * All Map-related fully qualified class names.
     *
     * Used by [InheritanceHelper.isMap] for fast-path O(1) FQN lookup
     * before falling back to the more expensive inheritance traversal.
     */
    val MAP_TYPES: Set<String> = setOf(
        JAVA_UTIL_MAP,
        JAVA_UTIL_HASHMAP,
        JAVA_UTIL_LINKEDHASHMAP,
        JAVA_UTIL_TREEMAP,
        JAVA_UTIL_CONCURRENTMAP,
        JAVA_UTIL_CONCURRENTHASHMAP,
    )

    /**
     * All Collection-related fully qualified class names.
     *
     * Used by [InheritanceHelper.isCollection] for fast-path O(1) FQN lookup
     * before falling back to the more expensive inheritance traversal.
     */
    val COLLECTION_TYPES: Set<String> = setOf(
        JAVA_UTIL_COLLECTION,
        JAVA_UTIL_LIST,
        JAVA_UTIL_SET,
        JAVA_UTIL_ARRAYLIST,
        JAVA_UTIL_HASHSET,
        JAVA_UTIL_LINKEDLIST,
        JAVA_UTIL_LINKEDHASHSET,
        JAVA_UTIL_VECTOR,
        JAVA_UTIL_STACK,
        JAVA_UTIL_TREESET,
    )
}
