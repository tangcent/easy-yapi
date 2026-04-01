package com.itangcent.easyapi.core.threading

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.di.OperationScopeElement
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext

/**
 * A coroutine dispatcher that executes blocks under IntelliJ's write action.
 *
 * This dispatcher ensures that all dispatched blocks have write access to
 * IntelliJ's data structures, which is required for modifying PSI, VFS, etc.
 *
 * The dispatcher handles several scenarios:
 * - If on EDT (or in unit test mode), runs under WriteCommandAction
 * - Otherwise, invokes later on EDT under WriteCommandAction
 *
 * @see IdeDispatchers.WriteAction
 */
class WriteActionDispatcher : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        val application = ApplicationManager.getApplication()
        val project = context[OperationScopeElement]?.scope?.getOrNull(Project::class)
        val writeAction = { WriteCommandAction.runWriteCommandAction(project) { block.run() } }
        
        when {
            application.isDispatchThread -> writeAction()
            application.isUnitTestMode -> writeAction()
            else -> application.invokeLater { writeAction() }
        }
    }
}
