package com.itangcent.easyapi.core.threading

import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.CoroutineDispatcher
import java.util.concurrent.CancellationException
import kotlin.coroutines.CoroutineContext

/**
 * A coroutine dispatcher that executes blocks under IntelliJ's read action.
 *
 * This dispatcher ensures that all dispatched blocks have read access to
 * IntelliJ's data structures (PSI, VFS, etc.), which is required for most
 * read operations in the IntelliJ Platform.
 *
 * Uses IntelliJ's readAction coroutine builder to properly handle suspend functions.
 *
 * @see IdeDispatchers.ReadAction
 */
class ReadActionDispatcher : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        val application = ApplicationManager.getApplication()
        when {
            // Already have read access, run immediately
            application.isReadAccessAllowed -> {
                block.run()
            }

            else -> {
                // Dispatch to IO thread pool and acquire read lock there
                // This avoids blocking the current thread
                IdeDispatchers.Background.dispatch(context) {
                    try {
                        application.runReadAction(block)
                    } catch (_: CancellationException) {
                        // The coroutine was cancelled while acquiring the read lock.
                        // In newer IntelliJ versions, runReadAction uses a coroutine-based
                        // read mutex that can throw JobCancellationException when the parent
                        // coroutine completes during lock acquisition. Swallow it here to
                        // prevent it from leaking as an unhandled coroutine exception.
                    }
                }
            }
        }
    }
}
