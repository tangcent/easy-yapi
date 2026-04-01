package com.itangcent.easyapi.ide

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Helper utilities for working with IntelliJ's dumb mode.
 *
 * Dumb mode is a state where IntelliJ's indices are not fully available
 * (e.g., during project import, rebuild, or VCS operations). Operations
 * that require indexed data should wait for smart mode.
 *
 * ## Usage
 * ```kotlin
 * // Suspend until smart mode
 * DumbModeHelper.waitForSmartMode(project)
 *
 * // Run action when smart
 * DumbModeHelper.runWhenSmart(project) {
 *     // Safe to use indices here
 * }
 *
 * // Check if currently in dumb mode
 * if (DumbModeHelper.isDumb(project)) {
 *     // Show message to user
 * }
 * ```
 */
object DumbModeHelper {

    /**
     * Suspends until the project is in smart mode.
     *
     * If already in smart mode, returns immediately.
     * In test mode, returns immediately without waiting.
     *
     * @param project The project to wait for
     */
    suspend fun waitForSmartMode(project: Project) {
        if (ApplicationManager.getApplication().isUnitTestMode) {
            return
        }

        val dumbService = DumbService.getInstance(project)
        if (!dumbService.isDumb) {
            return
        }

        suspendCancellableCoroutine { continuation ->
            dumbService.runWhenSmart {
                continuation.resume(Unit)
            }
        }
    }

    /**
     * Runs the given action when the project is in smart mode.
     *
     * If already in smart mode, runs immediately.
     *
     * @param project The project
     * @param action The action to run
     */
    fun runWhenSmart(project: Project, action: () -> Unit) {
        val dumbService = DumbService.getInstance(project)
        dumbService.runWhenSmart(action)
    }

    /**
     * Runs a read action when the project is in smart mode.
     *
     * This is the preferred way to perform PSI reads that require indices.
     *
     * @param project The project
     * @param action The read action to run
     */
    fun readWhenSmart(project: Project, action: () -> Unit) {
        val dumbService = DumbService.getInstance(project)
        dumbService.runWhenSmart {
            ApplicationManager.getApplication().runReadAction(action)
        }
    }

    /**
     * Checks if the project is currently in dumb mode.
     *
     * @param project The project to check
     * @return true if in dumb mode, false if in smart mode
     */
    fun isDumb(project: Project): Boolean {
        return DumbService.getInstance(project).isDumb
    }
}
