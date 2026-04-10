package com.itangcent.easyapi.dashboard

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.itangcent.easyapi.core.context.ActionContext

/**
 * Service for managing the API dashboard state and navigation.
 *
 * This service maintains:
 * - The action context for dashboard operations
 * - Reference to the dashboard panel for navigation
 *
 * @see ApiDashboardPanel for the UI component
 * @see ApiScanner for endpoint scanning
 */
@Service(Service.Level.PROJECT)
class ApiDashboardService(
    private val project: Project
) {
    private var actionContext: ActionContext? = null
    private var dashboardPanel: ApiDashboardPanel? = null

    companion object {
        /**
         * Gets the dashboard service instance for the given project.
         */
        fun getInstance(project: Project): ApiDashboardService = project.getService(ApiDashboardService::class.java)
    }

    /**
     * Gets or creates the action context for dashboard operations.
     *
     * @return The action context
     */
    fun getOrCreateContext(): ActionContext {
        val existing = actionContext
        if (existing != null) return existing
        val created = ActionContext.forProject(project)
        actionContext = created
        return created
    }

    /**
     * Sets the dashboard panel reference for navigation.
     */
    fun setDashboardPanel(panel: ApiDashboardPanel) {
        dashboardPanel = panel
    }

    /**
     * Navigates to and selects the specified class in the dashboard.
     * @return true if the endpoint was found and selected, false otherwise
     */
    suspend fun navigateToClass(psiClass: PsiClass): Boolean {
        return dashboardPanel?.selectByClass(psiClass) ?: false
    }

    /**
     * Navigates to and selects the specified method in the dashboard.
     * @return true if the endpoint was found and selected, false otherwise
     */
    suspend fun navigateToMethod(psiMethod: PsiMethod): Boolean {
        return dashboardPanel?.selectByMethod(psiMethod) ?: false
    }

    /**
     * Refreshes the API list in the dashboard.
     */
    fun refreshApis() {
        dashboardPanel?.refresh()
    }

    /**
     * Stops the service and cleans up resources.
     */
    fun stop() {
        actionContext?.stop()
        actionContext = null
        dashboardPanel = null
    }
}
