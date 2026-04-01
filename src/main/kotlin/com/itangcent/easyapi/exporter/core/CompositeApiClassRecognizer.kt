package com.itangcent.easyapi.exporter.core

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.itangcent.easyapi.exporter.feign.FeignClientRecognizer
import com.itangcent.easyapi.exporter.jaxrs.JaxRsResourceRecognizer
import com.itangcent.easyapi.exporter.springmvc.ActuatorEndpointRecognizer
import com.itangcent.easyapi.exporter.springmvc.SpringControllerRecognizer
import com.itangcent.easyapi.settings.SettingBinder

/**
 * Composite recognizer that combines all framework-specific [ApiClassRecognizer]s.
 *
 * This is the single entry point for determining whether a [PsiClass] is an API class
 * across any supported framework. All code that needs to check "is this an API class?"
 * should use this class instead of checking annotations directly.
 */
@Service(Service.Level.PROJECT)
class CompositeApiClassRecognizer(private val project: Project) {

    private val recognizers: List<ApiClassRecognizer> by lazy {
        val settings = SettingBinder.getInstance(project).read()
        buildList {
            add(SpringControllerRecognizer())
            if (settings.jaxrsEnable) {
                add(JaxRsResourceRecognizer(enabled = true))
            }
            if (settings.feignEnable) {
                add(FeignClientRecognizer(enabled = true))
            }
            if (settings.actuatorEnable) {
                add(ActuatorEndpointRecognizer(enabled = true))
            }
        }
    }

    /**
     * Returns true if [psiClass] is an API class for any enabled framework.
     */
    suspend fun isApiClass(psiClass: PsiClass): Boolean {
        return recognizers.any { it.isApiClass(psiClass) }
    }

    /**
     * Returns the names of frameworks that recognize [psiClass] as an API class.
     */
    suspend fun matchingFrameworks(psiClass: PsiClass): List<String> {
        return recognizers.filter { it.isApiClass(psiClass) }.map { it.frameworkName }
    }

    /**
     * All annotation FQNs that any enabled framework considers as API class markers.
     * Useful for [AnnotatedElementsSearch] in scanning.
     */
    val allTargetAnnotations: Set<String>
        get() = recognizers.flatMap { it.targetAnnotations }.toSet()

    companion object {
        fun getInstance(project: Project): CompositeApiClassRecognizer = project.service()
    }
}
