package com.itangcent.easyapi.util.ide

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.BranchChangeListener
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.itangcent.easyapi.core.threading.read
import com.itangcent.easyapi.logging.IdeaLog
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/**
 * Project-level service for checking class availability with caching.
 *
 * Provides efficient lookup of whether a class (by qualified name) exists
 * in the project's dependencies. Results are cached for 10 seconds to
 * avoid repeated PSI lookups during intensive operations.
 *
 * ## Usage
 * ```kotlin
 * val availabilityService = ProjectClassAvailabilityService.getInstance(project)
 * if (availabilityService.hasClassInProject("org.springframework.web.bind.annotation.RequestMapping")) {
 *     // Spring MVC is available
 * }
 * ```
 *
 * ## Thread Safety
 * The cache uses [ConcurrentHashMap] for thread-safe concurrent access.
 * Multiple coroutines can safely call [hasClassInProject] concurrently.
 *
 * ## Branch Change Handling
 * The cache is automatically cleared when the VCS branch changes, as
 * dependencies may differ between branches.
 *
 * @param project The IntelliJ project
 */
@Service(Service.Level.PROJECT)
class ProjectClassAvailabilityService(
    private val project: Project
) : Disposable, IdeaLog {

    private val psiFacade: JavaPsiFacade = JavaPsiFacade.getInstance(project)
    private val searchScope: GlobalSearchScope = GlobalSearchScope.allScope(project)

    /**
     * Cache for class availability results.
     * Key: class qualified name
     * Value: Pair of (exists, expiredAt) where expiredAt is the timestamp when the cache expires
     */
    private val cache = ConcurrentHashMap<String, Pair<Boolean, Long>>()

    /**
     * Cache TTL in milliseconds (10 seconds)
     */
    private val cacheTtlMs = 10.seconds.inWholeMilliseconds

    private val branchChangeListener = object : BranchChangeListener {
        override fun branchWillChange(branchName: String) {
            // no-op
        }

        override fun branchHasChanged(branchName: String) {
            LOG.info("Branch changed to '$branchName', clearing class availability cache")
            clearCache()
        }
    }

    /**
     * Starts listening for VCS branch changes.
     *
     * Should be called during plugin startup to enable automatic cache clearing
     * when the branch changes.
     */
    init {
        project.messageBus
            .connect(this)
            .subscribe(BranchChangeListener.VCS_BRANCH_CHANGED, branchChangeListener)
        LOG.info("ProjectClassAvailabilityService started")
    }

    /**
     * Checks if a class with the given qualified name exists in the project's dependencies.
     *
     * Results are cached for 10 seconds. If the cache is valid, returns the cached result.
     * Otherwise, performs a PSI lookup via [JavaPsiFacade.findClass] and caches the result.
     *
     * This method requires ReadAction context for PSI access. If not already in a read action,
     * it will acquire one automatically.
     *
     * @param qName The fully qualified name of the class to check
     * @return true if the class exists in the project's dependencies, false otherwise
     */
    suspend fun hasClassInProject(qName: String): Boolean {
        val now = System.currentTimeMillis()

        val cached = cache[qName]
        if (cached != null && now < cached.second) {
            return cached.first
        }

        val exists = read {
            psiFacade.findClass(qName, searchScope) != null
        }
        cache[qName] = exists to (now + cacheTtlMs)
        return exists
    }

    /**
     * Checks if any class from the given set exists in the project's dependencies.
     *
     * This is a convenience method that calls [hasClassInProject] for each qualified name
     * and returns true as soon as one is found.
     *
     * @param qNames The set of fully qualified names to check
     * @return true if at least one class exists in the project's dependencies
     */
    suspend fun hasAnyClassInProject(qNames: Set<String>): Boolean {
        return qNames.any { hasClassInProject(it) }
    }

    /**
     * Clears the cache. Useful when project dependencies change.
     */
    fun clearCache() {
        cache.clear()
    }

    override fun dispose() {
        // connection auto-disposed via connect(this)
    }

    companion object {
        /**
         * Gets the service instance for the given project.
         */
        fun getInstance(project: Project): ProjectClassAvailabilityService = project.service()
    }
}
