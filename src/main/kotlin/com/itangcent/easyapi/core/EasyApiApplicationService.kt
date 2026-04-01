package com.itangcent.easyapi.core

import com.intellij.openapi.application.ApplicationManager

/**
 * Application-level service for EasyAPI plugin.
 *
 * This service provides application-scoped functionality and serves as
 * a central point for application-level plugin components.
 *
 * @see EasyApiProjectService for project-level service
 */
class EasyApiApplicationService {
    companion object {
        /**
         * Gets the application service instance.
         *
         * @return The service instance
         */
        fun getInstance(): EasyApiApplicationService =
            ApplicationManager.getApplication().getService(EasyApiApplicationService::class.java)
    }
}
