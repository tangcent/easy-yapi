package com.itangcent.easyapi.ide.support

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.threading.backgroundAsync
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Runs a suspend block with a visible progress indicator in the IDE status bar.
 *
 * The block executes inside [Task.Backgroundable] on a pooled thread.
 * Uses a custom executor to break inherited EDT/write-intent context.
 *
 * Usage:
 * ```kotlin
 * val result = runWithProgress(project, "Scanning APIs...") { indicator ->
 *     indicator.isIndeterminate = false
 *     for ((i, item) in items.withIndex()) {
 *         indicator.checkCanceled()
 *         indicator.fraction = i.toDouble() / items.size
 *         process(item)
 *     }
 * }
 * ```
 */
suspend fun <T> runWithProgress(
    project: Project,
    title: String,
    cancellable: Boolean = true,
    block: suspend (ProgressIndicator) -> T
): T {
    if (ApplicationManager.getApplication().isUnitTestMode || ApplicationManager.getApplication().isHeadlessEnvironment) {
        return block(EmptyProgressIndicator())
    }

    return suspendCancellableCoroutine { continuation ->
        backgroundAsync {
            try {
                ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, cancellable) {
                    override fun run(indicator: ProgressIndicator) {
                        // Use kotlinx.coroutines.runBlocking to bridge suspend function
                        val result = kotlinx.coroutines.runBlocking { block(indicator) }
                        continuation.resume(result)
                    }

                    override fun onThrowable(error: Throwable) {
                        continuation.resumeWithException(error)
                    }
                })
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }
}
