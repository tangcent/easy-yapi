package com.itangcent.easyapi.psi.type

import com.intellij.psi.PsiClass
import com.intellij.psi.util.InheritanceUtil
import com.itangcent.easyapi.core.threading.readSync
import java.util.concurrent.ConcurrentHashMap

/**
 * Utility for checking PSI class inheritance relationships with caching.
 *
 * Provides efficient inheritance checks for common Java type hierarchies
 * (Collection, Map, etc.) with a 10-second TTL cache to avoid redundant
 * PSI traversals during intensive operations like API scanning.
 *
 * ## Thread Safety
 * The cache uses [ConcurrentHashMap] for thread-safe concurrent access.
 * Multiple coroutines can safely call any method concurrently.
 *
 * ## ReadAction Handling
 * All public methods automatically acquire ReadAction context when needed
 * for PSI access. Callers do NOT need to wrap calls in `readSync` — the
 * helper handles this internally. Cached results are returned without
 * acquiring ReadAction, making repeated lookups very fast.
 *
 * ## Cache Behavior
 * - Cache TTL: 10 seconds per entry
 * - Cache key: `"{sourceFQN}->{targetFQN}"` (e.g., `"java.util.ArrayList->java.util.Collection"`)
 * - Cache is cleared on [clearCache] or automatically on TTL expiry
 * - Fast-path FQN set lookups (e.g., [ClassNameConstants.COLLECTION_TYPES])
 *   bypass the cache entirely as they are O(1) set contains checks
 *
 * ## Usage
 * ```kotlin
 * // No readSync needed — InheritanceHelper handles it
 * val isCollection = InheritanceHelper.isCollection(psiClass)
 * val isMap = InheritanceHelper.isMap(psiClass)
 * val isInheritor = InheritanceHelper.isInheritor(psiClass, "java.lang.Exception")
 * ```
 *
 * @see ClassNameConstants for the FQN constants and type sets used here
 */
object InheritanceHelper {

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private const val CACHE_TTL_MS = 10_000L

    private class CacheEntry(val result: Boolean, val expiresAt: Long)

    /**
     * Checks if [psiClass] is equal to or inherits from [baseClassName].
     *
     * Results are cached for 10 seconds. If the cache is valid, returns the
     * cached result without acquiring ReadAction. Otherwise, acquires ReadAction
     * to perform the PSI traversal via [InheritanceUtil.isInheritor].
     *
     * @param psiClass the class to check
     * @param baseClassName the fully qualified name of the base class or interface
     * @return true if [psiClass] is or inherits from [baseClassName]
     */
    fun isInheritor(psiClass: PsiClass, baseClassName: String): Boolean {
        val fqn = readSync { psiClass.qualifiedName } ?: return false
        if (fqn == baseClassName) return true

        val cacheKey = "$fqn->$baseClassName"
        val now = System.currentTimeMillis()

        val cached = cache[cacheKey]
        if (cached != null && now < cached.expiresAt) {
            return cached.result
        }

        val result = readSync { InheritanceUtil.isInheritor(psiClass, baseClassName) }
        cache[cacheKey] = CacheEntry(result, now + CACHE_TTL_MS)
        return result
    }

    /**
     * Checks if [psiClass] represents a Java Collection type.
     *
     * Detection strategy (in order of speed):
     * 1. **FQN set lookup** — O(1) check against [ClassNameConstants.COLLECTION_TYPES]
     *    (includes Collection, List, Set, ArrayList, HashSet, LinkedList, etc.)
     * 2. **Kotlin prefix check** — classes under `kotlin.collections.` are treated as collections
     * 3. **Inheritance check** — delegates to [isInheritor] with `java.util.Collection`
     *    (handles custom subclasses like `class MyList extends ArrayList<String>`)
     *
     * @param psiClass the class to check
     * @return true if [psiClass] is a Collection type
     */
    fun isCollection(psiClass: PsiClass): Boolean {
        val fqn = readSync { psiClass.qualifiedName } ?: return false
        if (ClassNameConstants.COLLECTION_TYPES.contains(fqn)) return true
        if (fqn.startsWith(ClassNameConstants.KOTLIN_COLLECTIONS_PREFIX)) return true
        return isInheritor(psiClass, ClassNameConstants.JAVA_UTIL_COLLECTION)
    }

    /**
     * Checks if [psiClass] represents a Java Map type.
     *
     * Detection strategy (in order of speed):
     * 1. **FQN set lookup** — O(1) check against [ClassNameConstants.MAP_TYPES]
     *    (includes Map, HashMap, LinkedHashMap, TreeMap, ConcurrentMap, etc.)
     * 2. **Inheritance check** — delegates to [isInheritor] with `java.util.Map`
     *    (handles custom subclasses like `class MyMap extends HashMap<String, Object>`)
     *
     * @param psiClass the class to check
     * @return true if [psiClass] is a Map type
     */
    fun isMap(psiClass: PsiClass): Boolean {
        val fqn = readSync { psiClass.qualifiedName } ?: return false
        if (ClassNameConstants.MAP_TYPES.contains(fqn)) return true
        return isInheritor(psiClass, ClassNameConstants.JAVA_UTIL_MAP)
    }

    /**
     * Clears all cached inheritance check results.
     *
     * Useful for testing or when project state changes significantly
     * (e.g., branch switch, dependency change).
     */
    fun clearCache() {
        cache.clear()
    }
}
