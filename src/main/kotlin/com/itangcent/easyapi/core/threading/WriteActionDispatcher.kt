package com.itangcent.easyapi.core.threading

import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext

/**
 * A coroutine dispatcher that executes blocks under IntelliJ's write action.
 *
 * This dispatcher ensures that all dispatched blocks have write access to
 * IntelliJ's data structures, which is required for modifying PSI, VFS, etc.
 *
 * Always dispatches via [ApplicationManager.getApplication().invokeLater] to
 * preserve coroutine ordering guarantees, then acquires the write lock with
 * [ApplicationManager.getApplication().runWriteAction].
 */
internal class WriteActionDispatcher : CoroutineDispatcher() {

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = true

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        val application = ApplicationManager.getApplication()

        if (application.isUnitTestMode) {
            // In unit test mode, run synchronously to avoid EDT deadlocks
            application.runWriteAction { block.run() }
        } else {
            application.invokeLater {
                application.runWriteAction { block.run() }
            }
        }
    }
}
