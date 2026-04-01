package com.itangcent.easyapi.exporter.springmvc

import com.intellij.psi.PsiClass
import com.itangcent.easyapi.exporter.ClassExporter
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.logging.IdeaLog

/**
 * ClassExporter adapter for Spring Boot Actuator endpoints.
 *
 * Exports API endpoints from classes annotated with Spring Boot Actuator
 * annotations like @Endpoint, @WebEndpoint, @ControllerEndpoint, etc.
 *
 * @see ActuatorEndpointScanner for the scanning logic
 * @see ActuatorEndpointRecognizer for endpoint detection
 */
class ActuatorEndpointExporter : ClassExporter {

    private val scanner = ActuatorEndpointScanner()
    private val recognizer = ActuatorEndpointRecognizer()

    override suspend fun export(psiClass: PsiClass): List<ApiEndpoint> {
        if (!recognizer.isApiClass(psiClass)) return emptyList()

        val className = psiClass.qualifiedName ?: psiClass.name ?: "Unknown"
        LOG.info("before parse actuator endpoint:$className")
        val endpoints = scanner.scan(psiClass)
        LOG.info("after parse actuator endpoint:$className, found ${endpoints.size} endpoints")
        return endpoints
    }

    companion object : IdeaLog
}
