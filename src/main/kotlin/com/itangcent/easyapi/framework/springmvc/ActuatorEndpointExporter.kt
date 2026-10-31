package com.itangcent.easyapi.framework.springmvc

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.itangcent.easyapi.core.internal.threading.read
import com.itangcent.easyapi.core.export.EndpointBuilder
import com.itangcent.easyapi.core.export.ClassExporter
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.aspect.ApiParseAspects
import com.itangcent.easyapi.core.export.aspect.EndpointParseContext
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.psi.type.ResolvedMethod
import com.itangcent.easyapi.core.psi.type.ResolvedType
import com.itangcent.easyapi.framework.spi.FrameworkRegistry

/**
 * Exports API endpoints from Spring Boot Actuator endpoint classes.
 *
 * Processes classes annotated with `@Endpoint`, `@WebEndpoint`, or `@RestControllerEndpoint`
 * and extracts actuator endpoint information.
 *
 * @param project The IntelliJ project
 * @see ClassExporter for the interface
 * @see ActuatorEndpointRecognizer for endpoint detection
 * @see ActuatorEndpointScanner for endpoint scanning
 */
class ActuatorEndpointExporter(
    private val project: Project
) : ClassExporter {

    override val frameworkName: String = "SpringActuator"

    override suspend fun isEnabled(): Boolean =
        FrameworkRegistry.getInstance(project).isEnabled("SpringActuator")

    private val scanner = ActuatorEndpointScanner(project, EndpointBuilder.getInstance(project))
    private val recognizer = ActuatorEndpointRecognizer()
    private val parseAspects get() = ApiParseAspects.getInstance(project)

    override suspend fun export(psiClass: PsiClass): List<ApiEndpoint> {
        if (!recognizer.isApiClass(psiClass)) return emptyList()

        val className = read {
            psiClass.qualifiedName ?: psiClass.name ?: "Unknown"
        }
        LOG.info("before parse actuator endpoint:$className")

        return parseAspects.aroundClass(frameworkName, psiClass) {
            val endpoints = read { scanner.scan(psiClass) }
            val ownerClassType = ResolvedType.ClassType(psiClass, emptyList())

            for (endpoint in endpoints) {
                // `sourceMethod` is a nullable field with a default (ApiModels.kt), so
                // this stays a defensive skip. ResolvedMethod.of self-protects its PSI
                // read, so the loop does not need an enclosing read action.
                val method = endpoint.sourceMethod ?: continue
                val resolvedMethod = ResolvedMethod.of(method, ownerClassType)
                parseAspects.afterEndpoint(EndpointParseContext(endpoint, frameworkName, resolvedMethod))
            }

            LOG.info("after parse actuator endpoint:$className, found ${endpoints.size} endpoints")
            endpoints
        }
    }

    companion object : IdeaLog
}
