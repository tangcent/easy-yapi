package com.itangcent.easyapi.cache

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.threading.backgroundAsync
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.logging.IdeaLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * In-memory cache for API endpoints indexed by class name.
 *
 * ## Design Overview
 *
 * This cache serves as a fast, in-memory snapshot of API endpoints:
 * - **Startup**: `cacheReady` is completed once after the initial scan finishes
 * - **Validity**: `cacheValid` indicates whether the cached data is current
 * - **Updates**: Use `subscribe()` to receive notifications when cache is refreshed
 *
 * ## When to Use ApiIndex vs ApiScanner
 *
 * **Use ApiIndex when:**
 * - You need fast access to cached endpoints without PSI access
 * - You want to observe endpoint changes via `subscribe()`
 * - You can tolerate slightly stale data (updated asynchronously)
 *
 * **Use ApiScanner directly when:**
 * - You need real-time, up-to-date endpoints from PSI
 * - You need to scan specific files or classes on-demand
 * - You cannot tolerate any stale data
 *
 * ## Thread Safety
 *
 * - All public methods are thread-safe
 * - Uses mutex for concurrent access to the endpoint map
 * - Uses atomic boolean for validity flag
 *
 * ## Usage Example
 * ```kotlin
 * val apiIndex = ApiIndex.getInstance(project)
 *
 * // Wait for initial scan to complete, then get cached endpoints
 * val endpoints = apiIndex.endpoints()
 *
 * // Subscribe to cache updates
 * apiIndex.subscribe { endpoints ->
 *     println("Cache updated: ${endpoints.size} endpoints")
 * }
 *
 * // Check if cache is ready (initial scan completed)
 * if (apiIndex.isReady()) {
 *     // Cache has been populated at least once
 * }
 *
 * // Check if cache is valid (not invalidated)
 * if (apiIndex.isValid()) {
 *     // Cached data is current
 * }
 * ```
 *
 * @see ApiIndexManager for cache lifecycle management
 * @see ApiScanner for real-time endpoint scanning
 */
@Service(Service.Level.PROJECT)
class ApiIndex {

    @Volatile
    private var endpointsByClass: Map<String, List<ApiEndpoint>> = emptyMap()

    private val cacheMutex = Mutex()

    private val cacheValid = AtomicBoolean(false)

    @Volatile
    private var cacheReady = CompletableDeferred<Unit>()

    private val _endpointsFlow = MutableSharedFlow<List<ApiEndpoint>>(replay = 1)

    /**
     * Subscribe to cache updates.
     *
     * The [listener] is called with the full endpoint list whenever the cache is updated.
     * This is the recommended way to stay informed of endpoint changes.
     *
     * Note: The listener is called on a background thread. Use `withContext(IdeDispatchers.Swing)`
     * if you need to update UI components.
     *
     * @param listener Suspended function called with the updated endpoint list
     */
    fun subscribe(listener: suspend (List<ApiEndpoint>) -> Unit) {
        backgroundAsync {
            _endpointsFlow.collect { listener(it) }
        }
    }

    /**
     * Returns all cached endpoints.
     *
     * On first call, this method waits for the initial scan to complete (via `cacheReady`).
     * After the initial scan, it returns immediately with the cached data.
     *
     * If the cache has been invalidated (via `invalidate()`), returns an empty list.
     * Use `isValid()` to check if the cache contains current data.
     *
     * For real-time endpoints, use [ApiScanner] directly instead.
     *
     * @return List of all cached endpoints, or empty list if cache is invalid
     */
    suspend fun endpoints(): List<ApiEndpoint> {
        awaitCacheReady()
        return if (cacheValid.get()) {
            cacheMutex.withLock {
                endpointsByClass.values.flatten()
            }
        } else {
            emptyList()
        }
    }

    /**
     * Returns cached endpoints for a specific class.
     *
     * On first call, this method waits for the initial scan to complete (via `cacheReady`).
     * After the initial scan, it returns immediately with the cached data.
     *
     * If the cache has been invalidated (via `invalidate()`), returns an empty list.
     *
     * @param className Fully qualified class name
     * @return List of endpoints for the class, or empty list if not found or cache is invalid
     */
    suspend fun endpointsByClass(className: String): List<ApiEndpoint> {
        awaitCacheReady()
        return if (cacheValid.get()) {
            cacheMutex.withLock {
                endpointsByClass[className] ?: emptyList()
            }
        } else {
            emptyList()
        }
    }

    /**
     * Updates the cache with a complete list of endpoints.
     *
     * This method:
     * 1. Groups endpoints by class name
     * 2. Marks the cache as valid
     * 3. Completes `cacheReady` if this is the first update
     * 4. Emits the updated endpoint list to all subscribers
     *
     * Called by [ApiIndexManager] after a full scan completes.
     *
     * @param endpoints Complete list of endpoints to cache
     */
    suspend fun updateEndpoints(endpoints: List<ApiEndpoint>) {
        cacheMutex.withLock {
            endpointsByClass = endpoints.groupBy { it.className ?: "Unknown" }
            onCacheReady()
        }
        LOG.debug("Cache updated with ${endpoints.size} endpoints across ${endpointsByClass.size} classes")
        _endpointsFlow.emit(endpoints)
    }

    /**
     * Updates the cache for specific classes (incremental update).
     *
     * This method:
     * 1. Merges the provided endpoints with existing cache
     * 2. Removes classes with empty endpoint lists
     * 3. Marks the cache as valid
     * 4. Completes `cacheReady` if this is the first update
     * 5. Emits the updated endpoint list to all subscribers
     *
     * Called by [ApiIndexManager] for incremental scans.
     *
     * @param classEndpoints Map of class name to endpoints for that class
     */
    suspend fun updateEndpointsByClasses(classEndpoints: Map<String, List<ApiEndpoint>>) {
        val allEndpoints: List<ApiEndpoint>
        cacheMutex.withLock {
            val mutableMap = endpointsByClass.toMutableMap()
            classEndpoints.forEach { (className, endpoints) ->
                if (endpoints.isEmpty()) {
                    mutableMap.remove(className)
                } else {
                    mutableMap[className] = endpoints
                }
            }
            endpointsByClass = mutableMap
            onCacheReady()
            allEndpoints = mutableMap.values.flatten()
        }
        LOG.debug("Cache updated for ${classEndpoints.size} classes")
        _endpointsFlow.emit(allEndpoints)
    }

    /**
     * Removes endpoints for specific classes from the cache.
     *
     * Called by [ApiIndexManager] when classes are deleted or modified.
     *
     * @param classNames Set of class names to remove from cache
     */
    suspend fun removeEndpointsByClasses(classNames: Set<String>) {
        val allEndpoints: List<ApiEndpoint>
        cacheMutex.withLock {
            val mutableMap = endpointsByClass.toMutableMap()
            classNames.forEach { mutableMap.remove(it) }
            endpointsByClass = mutableMap
            allEndpoints = mutableMap.values.flatten()
        }
        LOG.debug("Removed endpoints for ${classNames.size} classes")
        _endpointsFlow.emit(allEndpoints)
    }

    /**
     * Invalidates the cache.
     *
     * This method:
     * 1. Marks the cache as invalid (cacheValid = false)
     * 2. Clears all cached endpoints
     * 3. Does NOT reset `cacheReady` (it remains completed)
     *
     * After invalidation:
     * - `isValid()` returns false
     * - `isReady()` still returns true (initial scan already completed)
     * - `endpoints()` returns empty list
     *
     * The cache will be repopulated by the next scan from [ApiIndexManager].
     */
    fun invalidate() {
        LOG.debug("Cache invalidated")
        cacheValid.set(false)
        endpointsByClass = emptyMap()
    }

    /**
     * Returns true if the cache contains valid (non-stale) data.
     *
     * Returns false after `invalidate()` is called, until the next update.
     */
    fun isValid(): Boolean = cacheValid.get()

    /**
     * Returns true if the initial scan has completed at least once.
     *
     * This is used to distinguish between:
     * - "Initial scan not yet completed" (isReady = false)
     * - "Initial scan completed, but cache invalidated" (isReady = true, isValid = false)
     *
     * Once `cacheReady` is completed during the first update, it remains completed
     * for the lifetime of this service instance.
     */
    fun isReady(): Boolean = cacheReady.isCompleted

    /**
     * Returns true if the given method is present in the current index.
     *
     * Safe to call from any thread without suspending — reads the volatile snapshot.
     *
     * @param method PSI method to check
     * @return true if the method is in the cached endpoints
     */
    fun containsMethod(method: com.intellij.psi.PsiMethod): Boolean {
        val className = method.containingClass?.qualifiedName ?: return false
        return endpointsByClass[className]?.any { it.sourceMethod == method } == true
    }

    private suspend fun awaitCacheReady() {
        if (!cacheReady.isCompleted) {
            cacheReady.await()
        }
    }

    private fun onCacheReady() {
        cacheValid.set(true)
        if (!cacheReady.isCompleted) {
            cacheReady.complete(Unit)
        }
    }

    companion object : IdeaLog {
        fun getInstance(project: Project): ApiIndex = project.service()
    }
}
