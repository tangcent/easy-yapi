package com.itangcent.easyapi.core.ide

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.ide.support.NotificationUtils
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Helper utilities for working with IntelliJ's dumb mode.
 *
 * Dumb mode is a state where IntelliJ's indices are not fully available
 * (e.g., during project import, rebuild, or VCS operations). Operations
 * that require indexed data should wait for smart mode.
 *
 * ## Usage
 * ```kotlin
 * // Suspend until smart mode (unbounded)
 * DumbModeHelper.waitForSmartMode(project)
 *
 * // Suspend until smart mode with a timeout
 * DumbModeHelper.waitForSmartMode(project, timeout = 2.minutes)
 *
 * // Wait with a short timeout for user-triggered actions; shows a notification on timeout
 * if (!DumbModeHelper.waitForSmartModeOrNotify(project)) return
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
     * @param timeout Optional maximum time to wait. If the timeout elapses before smart mode
     *   is reached, a [TimeoutCancellationException] is thrown, which propagates as a normal
     *   coroutine cancellation and aborts the calling operation cleanly.
     *   Defaults to [DEFAULT_WAIT_TIMEOUT].
     */
    suspend fun waitForSmartMode(project: Project, timeout: Duration = DEFAULT_WAIT_TIMEOUT) {
        if (ApplicationManager.getApplication().isUnitTestMode) {
            return
        }

        val dumbService = DumbService.getInstance(project)
        if (!dumbService.isDumb) {
            return
        }

        withTimeout(timeout) {
            suspendCancellableCoroutine { continuation ->
                dumbService.runWhenSmart {
                    continuation.resume(Unit)
                }
            }
        }
    }

    /**
     * Waits up to [timeout] for smart mode, intended for user-triggered actions.
     *
     * If smart mode is reached in time, returns `true` and the caller may proceed.
     * If the timeout elapses while still in dumb mode, fires a warning notification
     * and returns `false` — the caller should abort the action.
     *
     * This gives a brief grace period (e.g. indexing is nearly done) without leaving
     * the user wondering why nothing happened after clicking.
     *
     * ## Usage
     * ```kotlin
     * backgroundAsync {
     *     if (!DumbModeHelper.waitForSmartModeOrNotify(project)) return@backgroundAsync
     *     // proceed with index-dependent work
     * }
     * ```
     *
     * @param project The project to check
     * @param timeout Maximum time to wait before notifying. Defaults to [USER_ACTION_WAIT_TIMEOUT].
     * @return `true` if smart mode was reached, `false` if timed out
     */
    suspend fun waitForSmartModeOrNotify(
        project: Project,
        timeout: Duration = USER_ACTION_WAIT_TIMEOUT
    ): Boolean {
        if (ApplicationManager.getApplication().isUnitTestMode) {
            return true
        }

        val dumbService = DumbService.getInstance(project)
        if (!dumbService.isDumb) {
            return true
        }

        return try {
            withTimeout(timeout) {
                suspendCancellableCoroutine { continuation ->
                    dumbService.runWhenSmart {
                        continuation.resume(Unit)
                    }
                }
            }
            true
        } catch (_: TimeoutCancellationException) {
            NotificationUtils.notifyWarning(
                project,
                "Indexing in Progress",
                "EasyAPI export was cancelled because IDEA is still indexing. Please try again once indexing is complete."
            )
            false
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

    /**
     * Maximum wait time for background operations (e.g. auto-scan on startup).
     * These can afford to wait longer since they run silently in the background.
     */
    val DEFAULT_WAIT_TIMEOUT: Duration = 5.minutes

    /**
     * Maximum wait time for user-triggered actions before showing a notification.
     * Short enough that the user gets immediate feedback if indexing is still running.
     */
    val USER_ACTION_WAIT_TIMEOUT: Duration = 5.seconds
}
