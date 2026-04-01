package com.itangcent.easyapi.core.threading

import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        if (application.isReadAccessAllowed) {
            // Already have read access, run immediately
            block.run()
        } else {
            // Dispatch to IO thread pool and acquire read lock there
            // This avoids blocking the current thread
            Dispatchers.IO.dispatch(context) {
                application.runReadAction(block)
            }
        }
    }
}
