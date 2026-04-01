package com.itangcent.easyapi.core

import com.intellij.openapi.project.Project

/**
 * Project-level service for EasyAPI plugin.
 *
 * This service provides project-scoped functionality and serves as
 * a central point for project-level plugin components.
 *
 * @see EasyApiApplicationService for application-level service
 */
class EasyApiProjectService(private val project: Project) {
    companion object {
        /**
         * Gets the project service instance.
         *
         * @param project The project
         * @return The service instance
         */
        fun getInstance(project: Project): EasyApiProjectService = project.getService(EasyApiProjectService::class.java)
    }
}
