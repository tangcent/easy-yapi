package com.itangcent.easyapi.exporter.springmvc

import com.intellij.psi.PsiClass
import com.itangcent.easyapi.exporter.core.ApiClassRecognizer
import com.itangcent.easyapi.exporter.core.MetaAnnotationResolver

/**
 * Recognizes Spring Boot Actuator endpoint classes.
 *
 * Supports the following Actuator annotations:
 * - @Endpoint
 * - @WebEndpoint
 * - @ControllerEndpoint
 * - @RestControllerEndpoint
 *
 * @param enabled Whether Actuator endpoint recognition is enabled
 * @see SpringActuatorConstants for annotation constants
 */
class ActuatorEndpointRecognizer(
    private val enabled: Boolean = true
) : ApiClassRecognizer {

    override val frameworkName: String = "SpringActuator"

    override val targetAnnotations: Set<String> = SpringActuatorConstants.ENDPOINT_ANNOTATIONS

    override suspend fun isApiClass(psiClass: PsiClass): Boolean {
        if (!enabled) return false
        return MetaAnnotationResolver.hasMetaAnnotation(psiClass, SpringActuatorConstants.ENDPOINT_ANNOTATIONS)
    }
}
