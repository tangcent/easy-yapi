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
 * This service provides fast access to cached API endpoints without
 * requiring PSI access. The cache is:
 * - Updated by [ApiIndexManager] on file changes
 * - Invalidated when source files are modified
 * - Thread-safe using coroutines and mutex
 *
 * ## Usage
 * ```kotlin
 * val apiIndex = ApiIndex.getInstance(project)
 *
 * // Get all endpoints
 * val allEndpoints = apiIndex.endpoints()
 *
 * // Get endpoints for a specific class
 * val classEndpoints = apiIndex.endpointsByClass("com.example.UserController")
 *
 * // Observe changes
 * apiIndex.subscribe { endpoints ->
 *     println("Cache updated: ${endpoints.size} endpoints")
 * }
 * ```
 *
 * @see ApiIndexManager for cache updates
 * @see ApiScanner for endpoint scanning
 */
@Service(Service.Level.PROJECT)
class ApiIndex {

    @Volatile
    private var endpointsByClass: Map<String, List<ApiEndpoint>> = emptyMap()

    private val cacheMutex = Mutex()

    private val cacheValid = AtomicBoolean(false)

    private val cacheReady = CompletableDeferred<Unit>()

    private val _endpointsFlow = MutableSharedFlow<List<ApiEndpoint>>(replay = 1)

    /**
     * Subscribe to cache updates. The [listener] is called with the full endpoint list
     * whenever the cache is updated.
     */
    fun subscribe(listener: suspend (List<ApiEndpoint>) -> Unit) {
        backgroundAsync {
            _endpointsFlow.collect { listener(it) }
        }
    }

    suspend fun endpoints(): List<ApiEndpoint> {
        if (!cacheReady.isCompleted) {
            cacheReady.await()
        }
        return if (cacheValid.get()) {
            cacheMutex.withLock {
                endpointsByClass.values.flatten()
            }
        } else {
            emptyList()
        }
    }

    suspend fun endpointsByClass(className: String): List<ApiEndpoint> {
        if (!cacheReady.isCompleted) {
            cacheReady.await()
        }
        return if (cacheValid.get()) {
            cacheMutex.withLock {
                endpointsByClass[className] ?: emptyList()
            }
        } else {
            emptyList()
        }
    }

    suspend fun updateEndpoints(endpoints: List<ApiEndpoint>) {
        cacheMutex.withLock {
            endpointsByClass = endpoints.groupBy { it.className ?: "Unknown" }
            cacheValid.set(true)
            if (!cacheReady.isCompleted) {
                cacheReady.complete(Unit)
            }
        }
        LOG.debug("Cache updated with ${endpoints.size} endpoints across ${endpointsByClass.size} classes")
        _endpointsFlow.emit(endpoints)
    }

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
            cacheValid.set(true)
            if (!cacheReady.isCompleted) {
                cacheReady.complete(Unit)
            }
            allEndpoints = mutableMap.values.flatten()
        }
        LOG.debug("Cache updated for ${classEndpoints.size} classes")
        _endpointsFlow.emit(allEndpoints)
    }

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

    fun invalidate() {
        LOG.debug("Cache invalidated")
        cacheValid.set(false)
        endpointsByClass = emptyMap()
    }

    fun isValid(): Boolean = cacheValid.get()

    fun isReady(): Boolean = cacheReady.isCompleted

    /**
     * Returns true if the given method is present in the current index.
     * Safe to call from any thread without suspending — reads the volatile snapshot.
     */
    fun containsMethod(method: com.intellij.psi.PsiMethod): Boolean {
        val className = method.containingClass?.qualifiedName ?: return false
        return endpointsByClass[className]?.any { it.sourceMethod == method } == true
    }

    companion object : IdeaLog {
        fun getInstance(project: Project): ApiIndex = project.service()
    }
}
