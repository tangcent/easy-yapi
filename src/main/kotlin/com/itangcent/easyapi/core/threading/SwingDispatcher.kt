package com.itangcent.easyapi.core.threading

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext

/**
 * A coroutine dispatcher that executes blocks on the Swing Event Dispatch Thread (EDT).
 *
 * This dispatcher is used for UI operations that must be performed on the EDT.
 * It uses a configurable [ModalityState] to control when dispatched work executes.
 *
 * Common modality states:
 * - [ModalityState.nonModal()] - Executes only when no modal dialogs are active (default, safest)
 * - [ModalityState.any()] - Executes even when modal dialogs are active
 * - [ModalityState.stateForComponent] - Executes for the specific dialog's modality
 *
 * The dispatcher handles several scenarios:
 * - If already on EDT, runs immediately
 * - In unit test mode, runs immediately
 * - Otherwise, invokes later with the configured ModalityState
 *
 * @param modalityState The modality state to use for scheduling. Defaults to [ModalityState.nonModal].
 * @see IdeDispatchers.Swing for the default non-modal dispatcher
 * @see IdeDispatchers.SwingAny for a dispatcher that works with any modality
 */
class SwingDispatcher(
    private val modalityState: ModalityState = ModalityState.nonModal()
) : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        val application = ApplicationManager.getApplication()
        when {
            application.isDispatchThread -> block.run()
            application.isUnitTestMode -> block.run()
            else -> application.invokeLater(block, modalityState)
        }
    }
}
