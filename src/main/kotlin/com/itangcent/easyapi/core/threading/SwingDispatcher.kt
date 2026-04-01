package com.itangcent.easyapi.core.threading

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext

/**
 * A coroutine dispatcher that executes blocks on the Swing Event Dispatch Thread (EDT).
 *
 * This dispatcher is used for UI operations that must be performed on the EDT.
 * It uses [ModalityState.any()] to ensure dispatched work executes even while
 * a modal dialog is open, unlike the default NON_MODAL state which defers
 * execution until all modal dialogs close.
 *
 * The dispatcher handles several scenarios:
 * - If already on EDT, runs immediately
 * - In unit test mode, runs immediately
 * - Otherwise, invokes later with ModalityState.any()
 *
 * @see IdeDispatchers.Swing
 */
class SwingDispatcher : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        val application = ApplicationManager.getApplication()
        when {
            application.isDispatchThread -> block.run()
            application.isUnitTestMode -> block.run()
            else -> application.invokeLater(block, ModalityState.any())
        }
    }
}
