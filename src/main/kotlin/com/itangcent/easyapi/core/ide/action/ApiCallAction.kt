package com.itangcent.easyapi.core.ide.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager
import com.itangcent.easyapi.core.internal.threading.IdeDispatchers
import com.itangcent.easyapi.core.internal.threading.backgroundAsync
import com.itangcent.easyapi.core.internal.threading.swing
import com.itangcent.easyapi.core.dashboard.ApiDashboardService
import com.itangcent.easyapi.core.ide.support.SelectionScope
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.logging.console
import kotlinx.coroutines.runBlocking

/**
 * Action to call (execute) an API from the editor.
 *
 * Opens the API Dashboard tool window and navigates to the selected
 * method or class, allowing the user to make HTTP requests directly
 * from the IDE.
 *
 * @see ApiDashboardService for the dashboard functionality
 */
class ApiCallAction : EasyApiAction(), IdeaLog {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selection = resolveScope(e) ?: return
        val console = project.console
        console.info("ApiCallAction.actionPerformed: project=${project.name}")

        backgroundAsync {
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("API Dashboard")

            swing {
                toolWindow?.activate {
                    runBlocking {
                        navigateToSelection(project, selection)
                    }
                }
            }
        }
    }

    private suspend fun navigateToSelection(project: com.intellij.openapi.project.Project, selection: SelectionScope) {
        val dashboardService = ApiDashboardService.getInstance(project)

        val psiMethod = selection.method()
        if (psiMethod != null) {
            IdeDispatchers.swingAsync {
                dashboardService.navigateToMethod(psiMethod)
            }
            return
        }

        val psiClass = selection.psiClass()
        if (psiClass != null) {
            dashboardService.navigateToClass(psiClass)
        }
    }
}
