package com.itangcent.easyapi.core.threading

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext

/**
 * A coroutine dispatcher that executes blocks on the Swing Event Dispatch Thread (EDT).
 *
 * This dispatcher is used for UI operations that must be performed on the EDT.
 * It uses [ModalityState.nonModal()] to ensure dispatched work executes only
 * when no modal dialogs are active. This prevents "Write-unsafe context" errors
 * that can occur when VFS operations are triggered during modal dialog transitions.
 *
 * The dispatcher handles several scenarios:
 * - If already on EDT, runs immediately
 * - In unit test mode, runs immediately
 * - Otherwise, invokes later with ModalityState.nonModal()
 *
 * @see IdeDispatchers.Swing
 */
class SwingDispatcher : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        val application = ApplicationManager.getApplication()
        when {
            application.isDispatchThread -> block.run()
            application.isUnitTestMode -> block.run()
            else -> application.invokeLater(block, ModalityState.nonModal())
        }
    }
}
